# راهنمای توسعه — RhinoShot v0.2.0

این سند برای کسی است که می‌خواهد اپ را ادامه دهد: چه چیزی هست، چطور بیلد می‌شود،
چطور قابلیت جدید اضافه می‌شود و چه قراردادی با بک‌اند آینده دارد.

---

## ۱. پشته‌ی فنی

| بخش | انتخاب | دلیل |
|-----|--------|------|
| زبان | Kotlin 2.0.20 | استاندارد اندروید |
| UI | Jetpack Compose + Material3 | RTL کامل با `LayoutDirection.Rtl` |
| دوربین | CameraX 1.3.4 | Preview + ImageCapture + ImageAnalysis در یک pipeline |
| بینایی | MediaPipe Face Landmarker 0.10.14 | ۴۶۸ نقطه + blendshape + ماتریس تبدیل چهره |
| ذخیره | حافظه‌ی خصوصی برنامه + MediaStore | بدون سرور، بدون مجوز شبکه |
| حداقل SDK | 26 (اندروید ۸) | APIهای لازم (adaptive icon، scoped storage) |

### نسخه‌ها (در `build.gradle.kts`)
```kotlin
// app/build.gradle.kts
compileSdk = 34      // targetSdk = 34
compose-bom = 2024.09.00
camerax = 1.3.4
tasks-vision = 0.10.14
```

---

## ۲. معماری

```
UI (Compose)  ──▶  StateFlow<FaceState?>  ◀──  ImageAnalysis.Analyzer
   │                                                (Background Executor)
   ├── data/    : مدل دامنه (Shot, Session, PatientInfo) + ذخیره‌سازی
   ├── vision/  : تحلیل تصویر و تبدیل به راهنمای قابل‌فهم
   └── util/    : خروجی، گالری، اشتراک‌گذاری، کنتاکت‌شیت
```

* **جریان داده یک‌طرفه است**: `FaceAnalyzer` فقط `FaceState` منتشر می‌کند؛
  تصمیم‌گیری در `PoseEvaluator` است؛ UI فقط نتیجه را نمایش می‌دهد.
* **ماشین حالت در `MainActivity`** (سه حالت: HOME / CAPTURE / REVIEW) — عمداً ساده
  نگه داشته شده تا مهاجرت به Navigation Compose بعداً راحت باشد.
* **هیچ I/O شبکه‌ای** وجود ندارد و مجوزش هم حذف شده است.

### فایل‌ها
```
app/src/main/java/com/rhinoshot/app/
├── MainActivity.kt          ماشین حالت + مجوز دوربین + تم RTL
├── data/Shots.kt            ★ تعریف ۹ نما (زاویه، تحمل، لبخند، کادر) و اندازه‌های چاپ
├── data/Session.kt          مدل جلسه + ShotRecord + نوشتن session.json
├── data/SessionStore.kt     ساخت/خواندن/حذف پوشه‌های جلسه
├── vision/FaceAnalyzer.kt   ★ MediaPipe → yaw/pitch/roll، لبخند، کادر، نور، تاری
├── vision/FaceState.kt      مدل داده + Calibration (صفر و علامت‌ها)
├── vision/ImageQuality.kt   روشنایی و تاری (Tenengrad)
├── vision/PoseEvaluator.kt  ★ تبدیل اندازه‌گیری به گیت و پیام فارسی
├── ui/CaptureScreen.kt      ★ پیش‌نمایش، گاید، شاتر خودکار
├── ui/CalibrationDialog.kt  کالیبراسیون یک‌باره‌ی جهت‌ها
├── ui/HomeScreen.kt         مشخصات بیمار، رضایت‌نامه، جلسه‌های قبلی
├── ui/ReviewScreen.kt       شبکه‌ی ۳×۳، عکس دوباره، خروجی، حذف
├── ui/Common.kt             کارت‌ها و ردیف‌های وضعیت
└── util/Exporter.kt         کنتاکت‌شیت، چاپ، گالری، FileProvider
```

---

## ۳. ریاضیات زاویه‌ی سر

MediaPipe یک ماتریس تبدیل ۴×۴ (column-major) می‌دهد. استخراج:

```kotlin
// m = float[16] به ترتیب column-major: element(row, col) = m[col*4 + row]
val r00 = m[0];  val r10 = m[1];  val r20 = m[2]
val r21 = m[6];  val r22 = m[10]

pitch = atan2(r21, r22)                       // چرخش حول محور X (سر بالا/پایین)
yaw   = atan2(-r20, hypot(r21, r22))          // چرخش حول محور Y (چپ/راست)
roll  = atan2(r10, r00)                       // چرخش حول محور Z (چرخش سر)
```

سپس `Calibration` آن را به **قرارداد مورد استفاده‌ی اپ** تبدیل می‌کند:

```
subjectYaw   = yawSign  × rawYaw   + yawOffset
subjectPitch = pitchSign × rawPitch + pitchOffset
```

* `yawSign` بسته به دوربین (جلو: ‎−۱، عقب: ‎+۱) و مدل گوشی است.
* `yawOffset` صفر شخصی سر بیمار را حذف می‌کند.
* مقادیر پیش‌فرض در `Calibration.load()` و بازنویسی در `CalibrationDialog`.

### چرا کالیبراسیون لازم است
علامت ماتریس به جهت‌گیری مدل کانونی MediaPipe و به آینه‌بودن دوربین جلو وابسته است و
بین گوشی‌ها فرق می‌کند. بدون کالیبراسیون، «۴۵ درجه به راست» ممکن است «۴۵ درجه به
چپ» باشد. دیالوگ کالیبراسیون این را **خودکار** حل می‌کند؛ تست دستی هم با
`FilterChip`/تست‌های واحد ممکن است.

---

## ۴. بیلد

### محیط
```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk   # platform 34 + build-tools 34
./gradlew assembleDebug
```

### فایل‌های مهم بیلد
* `app/build.gradle.kts` — پیکربندی، امضا، ABIها، دانلود خودکار مدل
* `keystore.properties` — مشخصات کلید (در `03-SIGNING` و داخل خود پروژه)
* `keystore/rhinoshot-release.jks` — کلید release
* `gradle.properties` — `org.gradle.jvmargs=-Xmx2048m` (روی ماشین‌های کم‌رم
  به ۱۲۵۰m کم کنید و کامپایل Kotlin را در فرآیند Gradle اجرا کنید:
  `-Pkotlin.compiler.execution.strategy=in-process`)

### دانلود خودکار مدل
```kotlin
val downloadModel by tasks.registering {
    outputs.file(modelFile)
    doLast { /* دانلود face_landmarker.task در صورت نبود */ }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadModel) }
```
اگر اینترنت نبود، بیلد **نمی‌شکند**؛ فقط پیام هشدار چاپ می‌شود و باید فایل را
دستی در `app/src/main/assets/` گذاشت.

### ABIها
```kotlin
ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
```
حجم APK را از ۵۹MB به ۳۵MB می‌رساند. برای انتشار عمومی می‌توانید فقط
`arm64-v8a` را نگه دارید (حدود ۱۲MB).

---

## ۵. توسعه‌ی قابلیت‌ها

### ۵.۱ تغییر یا افزودن نما
همه‌ی نماها در یک جا هستند: `data/Shots.kt`

```kotlin
Shot(
    id = "profile_right",
    fileName = "03_profile_right",
    title = "نیم‌رخ راست",
    hint = "سر را کامل به سمت راست خودتان بچرخانید",
    yaw = 75f, pitch = 0f, tolYaw = 12f,
    side = "نیم‌رخ راست بیمار",   // در session.json و EXIF ثبت می‌شود
)
```
* `yaw`/`pitch = null` یعنی آن محور کنترل نشود.
* `manual = true` شاتر خودکار را غیرفعال می‌کند (مثل کلوزآپ بینی).
* `framing = Framing.NOSE` گیت کادر مخصوص کلوزآپ را فعال می‌کند.
* افزودن نما فقط کافی است `CLINIC_SHOTS` را ویرایش کنید — بقیه‌ی اپ
  (شمارش، پیشرفت، بازبینی، خروجی) خودکار هماهنگ می‌شود.

### ۵.۲ تنظیم آستانه‌های کیفیت
همه در `vision/PoseEvaluator.kt` به‌صورت `private const val` هستند:

```kotlin
private const val MIN_FACE_WIDTH = 0.20f
private const val MAX_FACE_WIDTH = 0.72f
private const val CENTER_TOL_X = 0.14f
private const val NOSE_MIN_FACE_HEIGHT = 0.42f
```
و برای نور/تاری دو حالت وجود دارد:
```kotlin
enum class QualityMode { STRICT, RELAXED }
```
که از دکمه‌ی «کنترل کیفیت» در صفحه‌ی عکاسی قابل تعویض است.

**روش تیون:** `ImageQuality.measure()` مقادیر خام را برمی‌گرداند
(`brightness` بین ۰ و ۱، `sharpness` نسبت گرادیان به روشنایی). یک لاگ موقت
اضافه کنید یا در حالت debug از پنل پایین صفحه‌ی عکاسی بخوانید و آستانه را طوری
بگذارید که روی ۹۰٪ عکس‌های خوب پاس شود و ۱۰٪ بد رد شود.

### ۵.۳ افزودن سنجه‌ی جدید (مثال: باز بودن دهان برای نمای دهان)
1. در `FaceAnalyzer` مقدار را از blendshape بگیرید و به `FaceState` اضافه کنید.
2. در `PoseEvaluator.evaluate` یک `Check` جدید بسازید (مسدودکننده یا هشدار).
3. اگر در `session.json` هم لازم است، فیلد را به `ShotRecord` و `toJson()` اضافه کنید.

### ۵.۴ تغییر رفتار شاتر
```kotlin
// ui/CaptureScreen.kt
LaunchedEffect(relaxed.ready, shotIndex, capturing, shot.manual) {
    ...
    val t0 = SystemClock.elapsedRealtime()
    while (SystemClock.elapsedRealtime() - t0 < PoseEvaluator.HOLD_MS) { ... }
    captureNow(auto = true)
}
```
`relaxed` با ضریب تحمل ۱٫۳۵ محاسبه می‌شود تا لرزش جزئی زاویه، زمان‌سنج را
ریست نکند.

### ۵.۵ حالت ویدیویی برای فتوگرامتری (مرحله‌ی ۳)
برای گرفتن ویدیوی مداری ۱۰–۱۵ ثانیه‌ای:
```kotlin
val recorder = Recorder.Builder()
    .setQualitySelector(QualitySelector.from(Quality.HD))
    .build()
recorder.prepareRecording(ctx, FileOutputOptions.Builder(file).build())
    .withAudioEnabled().start(ContextCompat.getMainExecutor(ctx)) { event ->
        if (event is VideoRecordEvent.Finalize) { /* آپلود به سرور */ }
    }
```
ویدیو را در `session.dir` با نام `orbit.mp4` بگذارید تا مرحله‌ی ۳ آن را به سرور
بفرستد.

---

## ۶. تست

### تست واحد (پیشنهاد می‌شود اضافه شود)
```kotlin
class PoseEvaluatorTest {
    @Test fun `yaw instruction points to the right direction`() {
        val shot = CLINIC_SHOTS.first { it.id == "profile_right" }   // هدف +75
        val check = PoseEvaluator.evaluate(shot, fakeState, yaw = 40f, pitch = 0f, QualityMode.STRICT)
        assertTrue(check.issues.first().message.contains("راست"))
    }
}
```
برای اجرا: `testImplementation("junit:junit:4.13.2")` و `testOptions { unitTests.isReturnDefaultValues = true }`.

### تست روی گوشی
چک‌لیست کامل در `TESTING.md`.

---

## ۷. قرارداد با بک‌اند آینده (مرحله‌ی ۲/۳)

اپ در آینده این دو endpoint را صدا می‌زند. فرمت‌ها همین‌طور ثابت بمانند:

### ۷.۱ ارسال جلسه برای ساخت مدل سه‌بعدی
`POST /v1/sessions/{session_id}/model`
```json
{
  "patient_code": "R1234",
  "shot_count": 9,
  "shots": [
    { "shot": "frontal", "file": "01_frontal.jpg", "yaw_deg": 0.4, "pitch_deg": -0.2 },
    { "shot": "nose_closeup", "file": "09_nose_closeup.jpg", "yaw_deg": 1.1, "pitch_deg": 0.0 }
  ],
  "files": ["01_frontal.jpg", "02_frontal_smile.jpg", "..."],
  "print_size": "10x15",
  "device": { "model": "SM-A525F", "manufacturer": "Samsung", "app_version": "0.2.0" }
}
```
پاسخ:
```json
{ "job_id": "job_7f3a", "status": "queued", "eta_seconds": 90 }
```

### ۷.۲ وضعیت و دریافت خروجی
`GET /v1/jobs/{job_id}` →
```json
{ "job_id": "job_7f3a", "status": "running|done|failed", "progress": 0.62, "model_url": "https://…/model.glb" }
```
* مدل خروجی: **GLB** (بافت + مش در یک فایل، مناسب Filament/SceneView).
* کنارش یک `model_meta.json` برگردانید: `{"source":"3dmm","method":"flame","mae_mm":1.8}`
  تا اپ بتواند دقت را به پزشک نشان دهد.

### ۷.۳ نکات پروتکل
* تصاویر با **multipart/form-data** یا base64 (کمتر از ۲MB در هر عکس) فرستاده شوند.
* همه‌ی درخواست‌ها با `Authorization: Bearer <token>` و TLS.
* برای داده‌ی پزشکی: رمزنگاری در حالت سکون (AES-256) + لاگ دسترسی + حذف زمان‌بندی‌شده.
* اگر بک‌اند آماده نشد، اپ بدون آن هم کامل کار می‌کند (حالت فعلی، آفلاین).

---

## ۸. امضا و انتشار

* کلید: `keystore/rhinoshot-release.jks`، alias `rhinoshot` — جزئیات در `SIGNING.md`.
* `keystore.properties` را در ریشه‌ی پروژه نگه دارید (در `.gitignore` نیست چون پروژه
  داخلی کلینیک است؛ برای پروژه‌ی عمومی حتماً از ریپو خارجش کنید).
* نسخه‌ی افزایش‌دهنده در `app/build.gradle.kts`:
  ```kotlin
  versionCode = 3
  versionName = "0.3.0"
  ```
  `versionCode` باید هر بار بیشتر شود وگرنه گوشی نصب به‌روز را رد می‌کند.
* قبل از انتشار: `versionCode`، `versionName` و `TAG_SOFTWARE` در
  `ui/CaptureScreen.kt` را با هم هماهنگ کنید.

---

## ۹. کارهای باقی‌مانده‌ی فنی

| مورد | اولویت | توضیح |
|------|---------|--------|
| تست واحد برای `PoseEvaluator` و `Exporter` | بالا | بدون آن، تغییر آستانه‌ها ریسک است |
| تست روی ۳ مدل گوشی مختلف | بالا | آستانه‌ی نور/تاری گوشی‌به‌گوشی فرق می‌کند |
| حالت landscape / تبلت | متوسط | فعلاً `screenOrientation="portrait"` است |
| `MotionEvent` برای تایمر عکسبرداری | متوسط | فعلاً شاتر خودکار است، دستی هم هست |
| استفاده از `RunningMode.VIDEO_STREAM` | متوسط | برای کاهش تأخیر تحلیل در گوشی‌های ضعیف |
| ریست خودکار کالیبراسیون با تعویض گوشی | کم | در حال حاضر دستی |
| لاگ و آمار استفاده (بدون داده‌ی بیمار) | کم | برای بهبود آستانه‌ها در طول زمان |
