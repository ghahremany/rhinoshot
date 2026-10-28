import Foundation
import UIKit
import Photos
import ImageIO
import UniformTypeIdentifiers

/// Everything that leaves the app folder: the contact sheet, the print-sized
/// copies and the export into the device photo library.
enum Exporter {

    // MARK: - Images

    static func loadImage(at url: URL, maxSize: CGFloat = 1600) -> UIImage? {
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxSize
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }

    /// Writes the captured JPEG and enriches its EXIF metadata.
    static func writeJPEG(_ data: Data, to url: URL, shot: Shot, state: FaceState?) {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            try? data.write(to: url, options: .atomic)
            return
        }

        // Keep the original properties (orientation!) and add our own.
        var properties = (CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]) ?? [:]

        var exif = (properties[kCGImagePropertyExifDictionary] as? [CFString: Any]) ?? [:]
        exif[kCGImagePropertyExifUserComment] = "yaw=\(state?.yawRaw ?? 0);pitch=\(state?.pitchRaw ?? 0);smile=\(state?.smile ?? 0)"
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
        exif[kCGImagePropertyExifDateTimeOriginal] = formatter.string(from: Date())
        properties[kCGImagePropertyExifDictionary] = exif

        var tiff = (properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any]) ?? [:]
        tiff[kCGImagePropertyTIFFSoftware] = "RhinoShot-iOS 0.2.0"
        tiff[kCGImagePropertyTIFFMake] = UIDevice.current.model
        tiff[kCGImagePropertyTIFFModel] = UIDevice.current.systemName
        properties[kCGImagePropertyTIFFDictionary] = tiff

        properties[kCGImagePropertyDescription] = shot.side.isEmpty ? shot.title : "\(shot.title) - \(shot.side)"

        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            output, UTType.jpeg.identifier as CFString, 1, nil
        ) else {
            try? data.write(to: url, options: .atomic)
            return
        }
        CGImageDestinationAddImage(destination, image, properties as CFDictionary)
        if CGImageDestinationFinalize(destination) {
            try? output.write(to: url, options: .atomic)
        } else {
            try? data.write(to: url, options: .atomic)
        }
    }

    /// 3x3 contact sheet with the Persian titles - one image for the patient file.
    static func buildContactSheet(session: Session) -> URL? {
        let cellW = 480
        let cellH = 640
        let size = CGSize(width: cellW * 3, height: cellH * 3)
        let renderer = UIGraphicsImageRenderer(size: size)

        let image = renderer.image { context in
            UIColor(red: 18 / 255, green: 22 / 255, blue: 26 / 255, alpha: 1).setFill()
            context.fill(CGRect(origin: .zero, size: size))

            for (index, shot) in CLINIC_SHOTS.enumerated() {
                let col = index % 3
                let row = index / 3
                let origin = CGPoint(x: CGFloat(col * cellW), y: CGFloat(row * cellH))

                if let photo = loadImage(at: session.fileURL(for: shot), maxSize: 900) {
                    let scale = min(
                        CGFloat(cellW) / photo.size.width,
                        CGFloat(cellH - 60) / photo.size.height
                    )
                    let w = photo.size.width * scale
                    let h = photo.size.height * scale
                    let rect = CGRect(
                        x: origin.x + (CGFloat(cellW) - w) / 2,
                        y: origin.y + (CGFloat(cellH - 60) - h) / 2,
                        width: w,
                        height: h
                    )
                    photo.draw(in: rect)
                } else {
                    UIColor(red: 30 / 255, green: 34 / 255, blue: 38 / 255, alpha: 1).setFill()
                    context.fill(CGRect(x: origin.x, y: origin.y, width: CGFloat(cellW), height: CGFloat(cellH)))
                }

                UIColor.black.withAlphaComponent(0.75).setFill()
                context.fill(CGRect(
                    x: origin.x, y: origin.y + CGFloat(cellH - 60),
                    width: CGFloat(cellW), height: 60
                ))

                let attributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.boldSystemFont(ofSize: 34),
                    .foregroundColor: UIColor.white
                ]
                let text = NSAttributedString(string: shot.title, attributes: attributes)
                let textSize = text.size()
                text.draw(at: CGPoint(
                    x: origin.x + (CGFloat(cellW) - textSize.width) / 2,
                    y: origin.y + CGFloat(cellH - 38)
                ))
            }
        }

        guard let data = image.jpegData(compressionQuality: 0.92) else { return nil }
        let url = session.directory.appendingPathComponent("contact_sheet.jpg")
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    /// Center-cropped copies in the exact pixel size of the requested print format.
    static func buildPrintSized(session: Session, printSize: PrintSize) -> [URL] {
        guard let target = printSize.pixelSize else { return [] }
        let folder = session.directory.appendingPathComponent("print_\(printSize.rawValue)", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)

        var result: [URL] = []
        for shot in CLINIC_SHOTS {
            guard let source = loadImage(at: session.fileURL(for: shot), maxSize: 2400) else { continue }
            let cropped = centerCrop(source, ratio: CGFloat(target.width) / CGFloat(target.height))
            let scaled = UIGraphicsImageRenderer(size: CGSize(width: target.width, height: target.height)).image { _ in
                cropped.draw(in: CGRect(x: 0, y: 0, width: target.width, height: target.height))
            }
            guard let data = scaled.jpegData(compressionQuality: 0.94) else { continue }
            let url = folder.appendingPathComponent("\(shot.fileName).jpg")
            do {
                try data.write(to: url, options: .atomic)
                result.append(url)
            } catch {
                continue
            }
        }
        return result
    }

    private static func centerCrop(_ image: UIImage, ratio: CGFloat) -> UIImage {
        let size = image.size
        let imageRatio = size.width / size.height
        var rect = CGRect(origin: .zero, size: size)
        if imageRatio > ratio {
            let width = size.height * ratio
            rect = CGRect(x: (size.width - width) / 2, y: 0, width: width, height: size.height)
        } else {
            let height = size.width / ratio
            rect = CGRect(x: 0, y: (size.height - height) / 2, width: size.width, height: height)
        }
        guard let cgImage = image.cgImage?.cropping(to: rect.applying(CGAffineTransform(scaleX: image.scale, y: image.scale))) else {
            return image
        }
        return UIImage(cgImage: cgImage, scale: image.scale, orientation: image.imageOrientation)
    }

    // MARK: - Photo library

    static func exportToPhotos(files: [URL], completion: @escaping (Int) -> Void) {
        let existing = files.filter { FileManager.default.fileExists(atPath: $0.path) }
        guard !existing.isEmpty else {
            completion(0)
            return
        }
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async { completion(0) }
                return
            }
            PHPhotoLibrary.shared().performChanges({
                for url in existing {
                    PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: url)
                }
            }, completionHandler: { success, _ in
                DispatchQueue.main.async {
                    completion(success ? existing.count : 0)
                }
            })
        }
    }
}

/// Share sheet for the hand-off to the surgeon.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
