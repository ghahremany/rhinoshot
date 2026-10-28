package com.rhinoshot.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.rhinoshot.app.data.CLINIC_SHOTS
import com.rhinoshot.app.data.PrintSize
import com.rhinoshot.app.data.Session
import java.io.File
import java.io.FileOutputStream

/**
 * Everything that leaves the app folder: the contact sheet, the print-sized
 * copies and the export into the device gallery.
 */
object Exporter {

    /** Decodes a JPEG and applies the EXIF orientation. */
    fun loadOriented(file: File, maxSize: Int = 1600): Bitmap? {
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > maxSize) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null
            val rotation = runCatching {
                when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            if (rotation == 0f) bmp
            else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        }.getOrNull()
    }

    /** 3x3 contact sheet with the Persian titles - one image for the patient file. */
    fun buildContactSheet(session: Session): File? {
        val cellW = 480
        val cellH = 640
        val sheet = Bitmap.createBitmap(cellW * 3, cellH * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(18, 22, 26))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val labelBg = Paint().apply { color = Color.argb(190, 0, 0, 0) }

        CLINIC_SHOTS.forEachIndexed { index, shot ->
            val col = index % 3
            val row = index / 3
            val left = col * cellW
            val top = row * cellH
            val file = session.fileFor(shot)
            val bmp = loadOriented(file, maxSize = 900)
            if (bmp != null) {
                val scale = minOf(cellW.toFloat() / bmp.width, (cellH - 60).toFloat() / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                val dst = RectF(
                    left + (cellW - w) / 2f,
                    top + (cellH - 60 - h) / 2f,
                    left + (cellW + w) / 2f,
                    top + (cellH - 60 + h) / 2f
                )
                canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                bmp.recycle()
            } else {
                canvas.drawColor(Color.rgb(30, 34, 38))
            }
            canvas.drawRect(left.toFloat(), (top + cellH - 60).toFloat(), (left + cellW).toFloat(), (top + cellH).toFloat(), labelBg)
            canvas.drawText(shot.title, left + cellW / 2f, top + cellH - 22f, titlePaint)
        }

        val out = File(session.dir, "contact_sheet.jpg")
        val ok = runCatching {
            FileOutputStream(out).use { stream ->
                sheet.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            }
        }.getOrDefault(false)
        sheet.recycle()
        return if (ok) out else null
    }

    /** Center-cropped copies in the exact pixel size of the requested print format. */
    fun buildPrintSized(session: Session, printSize: PrintSize): List<File> {
        if (printSize == PrintSize.RAW) return emptyList()
        val outDir = File(session.dir, "print_${printSize.id}").apply { mkdirs() }
        val result = mutableListOf<File>()
        CLINIC_SHOTS.forEach { shot ->
            val src = loadOriented(session.fileFor(shot), maxSize = 2400) ?: return@forEach
            val targetRatio = printSize.widthPx.toFloat() / printSize.heightPx
            val cropped = centerCrop(src, targetRatio) ?: return@forEach
            val scaled = Bitmap.createScaledBitmap(cropped, printSize.widthPx, printSize.heightPx, true)
            if (scaled !== cropped) cropped.recycle()
            src.recycle()
            val out = File(outDir, "${shot.fileName}.jpg")
            runCatching {
                FileOutputStream(out).use { stream ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 94, stream)
                }
            }.getOrNull()
            scaled.recycle()
            if (out.exists()) result.add(out)
        }
        return result
    }

    private fun centerCrop(src: Bitmap, targetRatio: Float): Bitmap? = runCatching {
        val srcRatio = src.width.toFloat() / src.height
        if (srcRatio > targetRatio) {
            val newWidth = (src.height * targetRatio).toInt()
            val x = (src.width - newWidth) / 2
            Bitmap.createBitmap(src, x, 0, newWidth, src.height)
        } else {
            val newHeight = (src.width / targetRatio).toInt()
            val y = (src.height - newHeight) / 2
            Bitmap.createBitmap(src, 0, y, src.width, newHeight)
        }
    }.getOrNull()

    /**
     * Copies the session into the device gallery (Pictures/RhinoShot/...).
     * On Android 10+ this uses MediaStore, on older versions the public folder.
     */
    fun exportToGallery(context: Context, files: List<File>): Int {
        var ok = 0
        for (file in files) {
            if (!file.exists()) continue
            val copied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyWithMediaStore(context, file)
            } else {
                copyToPublicFolder(file)
            }
            if (copied) ok++
        }
        return ok
    }

    private fun copyWithMediaStore(context: Context, file: File): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/RhinoShot/" + (file.parentFile?.name ?: "session")
            )
        }
        val resolver = context.contentResolver
        val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        } ?: return false
        true
    }.getOrDefault(false)

    private fun copyToPublicFolder(file: File): Boolean = runCatching {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "RhinoShot/${file.parentFile?.name ?: "session"}"
        ).apply { mkdirs() }
        val target = File(dir, file.name)
        file.inputStream().use { input ->
            FileOutputStream(target).use { out -> input.copyTo(out) }
        }
        true
    }.getOrDefault(false)

    /** Share sheet for the WhatsApp / e-mail hand-off to the surgeon. */
    fun shareIntent(context: Context, files: List<File>): Intent {
        val uris = files.filter { it.exists() }.map {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it)
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, "ارسال تصاویر")
    }
}
