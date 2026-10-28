# راهنمای توسعه‌ی iOS

## ۱. معماری

```
┌──────────────────────────────────────────────────────────┐
│ MainActor (SwiftUI)                                      │
│  AppState ── Stage: home / capture / review              │
│  HomeView · CaptureView · ReviewView · CalibrationSheet   │
└───────────────▲───────────────────────────┬───────────────┘
                │ @Published                 │ onPhotoCaptured(Data)
┌───────────────┴──────────────┐            ▼
│ CameraController (NSObject)  │      Exporter.writeJPEG → JPG + EXIF
│  · AVCaptureSession          │      Session.records → session.json
│  · AVCapturePhotoOutput      │
│  · AVCaptureVideoDataOutput  │
└───────────────┬──────────────┘
                │ BGRA frames (serial video queue)
                ▼
┌──────────────────────────────────────────────────────────┐
│ FaceAnalyzer (MediaPipe Tasks Vision 1.1.1)              │
│  FaceLandmarker(runningMode: .video)                     │
│  → FaceState (yaw/pitch/roll, smile, box, quality)      │
└───────────────┬──────────────────────────────────────────┘
                ▼
┌──────────────────────────────────────────────────────────┐
│ Calibration (UserDefaults) → PoseEvaluator → Evaluation   │
└──────────────────────────────────────────────────────────┘
```

* **sessionQueue**: پیکربندی و راه‌اندازی نشست دوربین (فرایند AVFoundation باید
  خارج از main thread انجام شود).
* **videoQueue**: صف سریال تحلیل فریم‌ها. MediaPipe در حالت `.video` به فریم‌های
  با زمان‌بندی یکنواخت نیاز دارد؛ همان صف این ترتیب را تضمین می‌کند.
* **MainActor**: تمام UI و انتشار وضعیت‌ها.

## ۲. پیکربندی MediaPipe

```swift
let baseOptions = BaseOptions()
baseOptions.modelAssetPath = modelPath            // داخل Bundle
let options = FaceLandmarkerOptions()
options.baseOptions = baseOptions
options.runningMode = .video
options.numFaces = 1
options.outputFaceBlendshapes = true              // برای تشخیص لبخند
options.outputFacialTransformationMatrixes = true // برای زاویه‌ی سر
landmarker = try FaceLandmarker(options: options)
```

* بسته: `https://github.com/google-ai-edge/mediapipe.git` از نسخه‌ی `1.1.1`
  (تگ `v1.1.1`)، محصول `MediaPipeTasksVision`.
* مدل: `RhinoShot/Resources/face_landmarker.task` (همان فایل نسخه‌ی اندروید).
  لینک مستقیم:
  `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task`
* مسیر مدل در زمان اجرا: `Bundle.main.path(forResource: "face_landmarker", ofType: "task")`.

### استخراج زاویه از ماتریس تبدیل

مدل، ماتریس ۴×۴ تبدیل چهره را می‌دهد. کد در `FaceAnalyzer.rotationAngles(from:)`:

```
r00 r10 r20        pitch = atan2(r21, r22)
r10 r11 r21        yaw   = atan2(-r20, hypot(r21, r22))
r20 r21 r22        roll  = atan2(r10, r00)
```

(MediaPipe ماتریس را **ستونی** ذخیره می‌کند؛ یعنی `element(r,c) = data[c*4+r]`.)
برای اطمینان، چیدمان به‌صورت خودکار تشخیص داده می‌شود: در ماتریس صُلب، سطر
آخر `[0 0 0 1]` است. اگر ساختار معکوس بود، همان بردار استفاده می‌شود (نتیجه فقط
وارونه‌ی علامت زاویه‌هاست که کالیبراسیون آن را اصلاح می‌کند).

## ۳. تغییر نماها و آستانه‌ها

همه در `RhinoShot/Models/Shots.swift`:

```swift
Shot(id: "profile_right", fileName: "03_profile_right", title: "نیم‌رخ راست",
     hint: "…", yaw: 75, pitch: 0, tolYaw: 12, side: "نیم‌رخ راست بیمار")
```

* `yaw`/`pitch`: زاویه‌ی هدف (درجه). `nil` یعنی آن محور کنترل نمی‌شود.
* `tolYaw`/`tolPitch`: تلرانس سخت‌گیرانه. در حالت عملیاتی ۱٫۳۵ برابر می‌شود
  (`toleranceScale` در `PoseEvaluator.evaluate`).
* `needSmile`: برای نمای خندان (blendshape ‎`mouthSmile*` میانگین ≥ ۰٫۳۵).
* `manual: true`: شاتر دستی (نمای ۹).
* `framing: .nose`: دروازه‌ی کادر مخصوص (بینی در مرکز + فاصله‌ی نزدیک).

آستانه‌های کیفیت تصویر در `PoseEvaluator`:
روشنایی سخت‌گیرانه `0.18…0.94`، آسان `0.12…0.97`،
شارپنس سخت‌گیرانه `0.012` (هشدار `0.022`)، آسان `0.006` (هشدار `0.010`).

## ۴. کالیبراسیون

`Calibration` در `FaceState.swift`، ذخیره در `UserDefaults` با کلید
`rhinoshot_calibration`:

| فیلد | معنی |
|------|------|
| `yawSignFront` / `yawSignBack` | علامت yaw برای دوربین جلو/عقب |
| `pitchSign` | علامت pitch |
| `yawOffset` / `pitchOffset` | صفر کردن انحراف شخصی سر بیمار |

گام‌ها (در `CalibrationSheet`):
۱. بیمار مستقیم به دوربین → «ثبت زاویه‌ی صفر».
۲. بیمار سر را به راست خودش بچرخاند (‎>۱۸°) → «ثبت جهت راست».
۳. بیمار چانه را بالا ببرد (‎>۱۲°) → «ثبت جهت بالا».

پس از آن، مقادیر برای همیشه روی همان گوشی می‌مانند.

## ۵. ساختار داده‌ی `session.json`

```json
{
  "app": "RhinoShot-iOS",
  "app_version": "0.2.0",
  "session_id": "A123_20261025_142530",
  "created_at": "2026-10-25T14:25:30Z",
  "patient_code": "A123",
  "patient_name": "",
  "note": "",
  "consent_at": "2026-10-25T14:25:30Z",
  "print_size": "raw",
  "shots_total": 9,
  "shots_captured": 9,
  "shots": [
    {
      "shot": "frontal",
      "file": "01_frontal.jpg",
      "title": "روبروی کامل",
      "side": "",
      "captured_at": "2026-10-25T14:26:02Z",
      "yaw_deg": -1.2,
      "pitch_deg": 0.8,
      "roll_deg": 0.4,
      "smile": 0.02,
      "brightness": 0.58,
      "sharpness": 0.031,
      "face_width_ratio": 0.44,
      "face_height_ratio": 0.61,
      "auto_captured": true
    }
  ]
}
```

همان اسکیمای نسخه‌ی اندروید (فقط کلید `app` متفاوت است) تا ابزارهای پایتونی
و اسکریپت‌های کلینیک روی هر دو خروجی کار کنند.

## ۶. EXIF هر عکس

با `ImageIO` نوشته می‌شود:

| کلید | مقدار |
|------|-------|
| `Exif.UserComment` | `yaw=…;pitch=…;smile=…` |
| `Exif.DateTimeOriginal` | زمان ثبت |
| `TIFF.Software` | `RhinoShot-iOS 0.2.0` |
| `TIFF.Make` / `TIFF.Model` | مدل دستگاه |
| `ImageDescription` | عنوان نما + سمت (مثلاً «نیم‌رخ راست بیمار») |

جهت (Orientation) اصلی دوربین حفظ می‌شود تا عکس در هر برنامه‌ای درست باز شود.

## ۷. مسیرهای فایل

| مورد | مسیر |
|------|------|
| ریشه‌ی جلسه‌ها | `Library/Application Support/RhinoShot/` |
| یک جلسه | `Library/Application Support/RhinoShot/<کد>_<yyyyMMdd_HHmmss>/` |
| عکس‌ها | `NN_<label>.jpg` |
| متادیتا | `session.json`، `consent.txt` |
| برگه‌ی تماس | `contact_sheet.jpg` |
| نسخه‌ی چاپی | `print_10x15/` ، `print_13x18/` |

پوشه‌ها با `isExcludedFromBackup = true` از پشتیبان iCloud کنار گذاشته می‌شوند.

## ۸. اندازه‌های چاپ

| قالب | پیکسل در ۳۰۰ dpi |
|------|------------------|
| `10x15` | 1181 × 1772 |
| `13x18` | 1535 × 2126 |
| `raw` | بدون تغییر (پیشنهاد برای بایگانی) |

## ۹. افزودن بک‌اند (مرحله‌ی بعد)

نقطه‌ی ورود از پیش آماده است: در `Exporter` یک تابع
`upload(session:endpoint:token:)` اضافه کنید و آن را از صفحه‌ی بازبینی صدا بزنید.
هیچ مجوز شبکه‌ای در حال حاضر در `Info.plist` تعریف نشده است؛ برای HTTPS
نیازی به `NSAppTransportSecurity` نیست، ولی برای رمزنگاری پیام‌ها
`CryptoKit` (`AES.GCM`) را به‌عنوان وابستگی استاندارد اضافه کنید.

## ۱۰. بازسازی پروژه

```bash
xcodegen generate      # یا ./bootstrap.sh
```

`RhinoShot.xcodeproj` یک فایل تولیدی است؛ آن را در گیت کامیت نکنید
(در `.gitignore` هست). تغییرات واقعی پروژه را در `project.yml` بنویسید.

---

## ۱۱. بازسازی سه‌بعدی چهره (نسخه‌ی ۰٫۳٫۰)

### ورودی
هنگام ثبت هر عکس، landmarkهای MediaPipe در `FaceState.landmarks` نگه داشته می‌شوند
(به‌همراه نسبت `aspect`) و در `Session.writeLandmarks()` به فایل `landmarks.json`
کنار عکس‌ها نوشته می‌شوند:

```json
{
  "app": "RhinoShot-iOS",
  "app_version": "0.3.0",
  "session_id": "b12_20261025_181500",
  "shots": {
    "frontal": { "count": 468, "aspect": 1.3333, "points": [x0, y0, z0, ...] }
  }
}
```

همین ساختار در نسخه‌ی اندروید هم استفاده می‌شود؛ یعنی پوشه‌ی یک جلسه بین دو
پلتفرم قابل جابه‌جایی است.

### توپولوژی
فایل `Resources/canonical_face_model.bin` (۱۶٬۴۰۴ بایت) مدل canonical
مدیاپیپ است: ۴۶۸ رأس و ۸۹۸ مثلث. قالب فایل:

| آفست | نوع | توضیح |
|------|-----|--------|
| ۰ | ۴ بایت | `RHM1` |
| ۴ | UInt32 | تعداد رأس‌ها (۴۶۸) |
| ۸ | UInt32 | تعداد مثلث‌ها (۸۹۸) |
| ۱۲ | Float32[] | مختصات رأس‌ها (۱۴۰۴ عدد) |
| … | Int32[] | اندیس رأس‌های هر مثلث (۲۶۹۴ عدد) |

ترتیب رأس‌ها دقیقاً همان ترتیب landmarkهای MediaPipe است؛ یعنی رأس *i* همان
landmark *i* است.

### مقیاس و مختصات (`ThreeD/FaceMesh.swift`)
* مقیاس: فاصله‌ی گوشه‌های بیرونی چشم (landmarkهای ۳۳ و ۲۶۳) = **۸۸ میلی‌متر**
* مرکز مدل: نوک بینی (landmark ۱)
* محور‌ها: `x` به راست، `y` به بالا، `z` به جلو (مثبت) — واحد میلی‌متر
* عمق (`z`) تخمین تک‌چشمی است و نویز دارد ⇒ فقط همان کانال با سه تکرار
  هموار می‌شود (ضریب ۰٫۴۵)
* نرمال‌ها از مثلث‌ها محاسبه می‌شوند

### نمایش (`Views/ThreeDView.swift`)
`SceneView` با گزینه‌ی `allowsCameraControl` ⇒ چرخش با یک انگشت و زوم با دو انگشت.
مشهدارها: نمای اولیه، تمرکز روی بینی، نمایش سطح/تور (`SCNMaterial.FillMode`)
و خروجی **OBJ**.

### محدودیت پزشکی
مدل از یک عکس تک‌چشمی ساخته می‌شود. برای **مشاهده و گفت‌وگو** مناسب است و
نباید مبنای اندازه‌گیری دقیق جراحی قرار گیرد. این هشدار در صفحه‌ی نمایش سه‌بعدی
و در صفحه‌ی «درباره‌ی اپ» نوشته شده است.
