<div dir="rtl">

# RhinoShot — اپ عکاسی پزشکی با بازسازی سه‌بعدی چهره


**🌐 صفحه‌ی معرفی پروژه:** <https://ghahremany.github.io/rhinoshot/>

<div align="center">

**راهنمای عکاسی استاندارد کلینیک + مدل سه‌بعدی قابل چرخش از چهره**
نسخه‌ی `0.3.3` · بسته‌ی اندروید: `com.rhinoshot.app` · بسته‌ی iOS: `ir.rhinoshot.clinic`

</div>

---

## اپ چه کاری انجام می‌دهد؟

RhinoShot یک اپ **کاملاً آفلاین** برای عکاسی استاندارد کلینیک (رینوپلاستی/جراحی زیبایی) است.
اپ با دوربین، **۹ نمای استاندارد** را به‌صورت هدایت‌شده می‌گیرد و در پایان از همان عکس‌ها
یک **مدل سه‌بعدی قابل چرخش** از چهره می‌سازد.

### ۹ نمای استاندارد

| # | نما | # | نما | # | نما |
|---|-----|---|-----|---|-----|
| ۱ | روبروی کامل | ۴ | نیم‌رخ چپ | ۷ | سر بالا |
| ۲ | روبروی خندان | ۵ | سه‌رخ راست | ۸ | سر پایین |
| ۳ | نیم‌رخ راست | ۶ | سه‌رخ چپ | ۹ | کلوزآپ بینی |

### قابلیت‌ها

* **تشخیص لحظه‌ای زاویه‌ی سر** با MediaPipe Face Landmarker (yaw / pitch / roll) و راهنمای فارسی
  («سر را ۲۳° به سمت راست بچرخانید»).
* **تشخیص لبخند و باز بودن چشم‌ها** از blendshapeها + گیت‌های کیفیت تصویر
  (فاصله، مرکزیت کادر، روشنایی، تاری).
* **شاتر خودکار** پس از ۹۰۰ میلی‌ثانیه پایداری وضعیت، یا ثبت دستی.
* **مدل سه‌بعدی قابل چرخش** (۴۶۸ رأس / ۸۹۸ مثلث) با چرخش و زوم، نمای سطح/تور و
  **خروجی OBJ** برای نرم‌افزارهای سه‌بعدی.
* **منوی همبرگری**: نمایش سه‌بعدی، راهنمای استفاده، درباره‌ی اپ و توسعه‌دهنده، حذف همه‌ی داده‌ها.
* **رضایت‌نامه**، **رمزنگاری داده‌ها** (فقط روی دستگاه)، **حذف کامل** جلسه و داده‌ی بیمار.
* خروجی ساختارمند: `session.json`، `consent.txt`، `landmarks.json` و کنتاکت‌شیت ۳×۳.

> ⚠️ **هشدار پزشکی:** مدل سه‌بعدی از یک عکس تک‌چشمی بازسازی می‌شود؛ برای **مشاهده و
> گفت‌وگو با بیمار** مناسب است، ولی جایگزین اندازه‌گیری دقیق جراحی (سنسور عمق، سی‌تی‌اسکن
> یا عکس‌برداری استاندارد) نیست.

---

## ساختار پروژه

```
RhinoShot/          اپ اندروید (Kotlin + Jetpack Compose + CameraX + MediaPipe)
RhinoShot-iOS/      اپ iOS (SwiftUI + SceneKit + MediaPipeTasks)
```

| مسیر | توضیح |
|------|-------|
| `RhinoShot/app/src/main/java/com/rhinoshot/app/ui/` | صفحه‌های Compose: خانه، عکاسی، بازبینی، سه‌بعدی، درباره‌ی اپ |
| `RhinoShot/app/src/main/java/com/rhinoshot/app/util/FaceMesh.kt` | ساخت مش سه‌بعدی و خروجی OBJ |
| `RhinoShot/app/src/main/java/com/rhinoshot/app/vision/` | تحلیل چهره، ارزیابی زاویه و کیفیت تصویر |
| `RhinoShot/app/src/main/assets/canonical_face_model.bin` | توپولوژی canonical مدیاپیپ (little-endian) |
| `RhinoShot-iOS/RhinoShot/ThreeD/FaceMesh.swift` | همان منطق مش برای iOS |
| `RhinoShot-iOS/RhinoShot/Views/ThreeDView.swift` | نمایشگر سه‌بعدی SceneKit |

---

## ساخت اندروید

```bash
# JDK 17 و Android SDK (platform 34 + build-tools 34) لازم است
git clone https://github.com/ghahremany/rhinoshot.git
cd rhinoshot/RhinoShot

echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleRelease      # خروجی: app/build/outputs/apk/release/
./gradlew assembleDebug
```

* مدل MediaPipe در اولین بیلد به‌صورت خودکار دانلود و در `app/src/main/assets/` قرار می‌گیرد.
* برای ساخت release باید **کلید امضای خودتان** را بسازید (کلید این پروژه به‌دلیل امنیت
  منتشر نشده است):

```bash
keytool -genkeypair -v -keystore my-release.jks -alias my-alias \
        -keyalg RSA -keysize 2048 -validity 10000
cat > keystore.properties <<'EOF'
storeFile=my-release.jks
storePassword=••••
keyAlias=my-alias
keyPassword=••••
EOF
```

* بدون `keystore.properties` بیلد با کلید دیباگ انجام می‌شود.

## ساخت iOS

```bash
cd RhinoShot-iOS
./bootstrap.sh      # نیازمند Xcode 16 و XcodeGen (brew install xcodegen)
open RhinoShot.xcodeproj
```

پروژه با **XcodeGen** تولید می‌شود تا فایل‌های متنی و قابل بازبینی در گیت بمانند.
بسته‌ی `.ipa` از پیش ساخته ارائه نمی‌شود (نیازمند macOS و Xcode).

---

## حریم خصوصی

اپ **هیچ دسترسی شبکه‌ای ندارد** و مجوز `INTERNET` در مانیفست حذف شده است.
تصاویر، landmarkها و مدل سه‌بعدی فقط در حافظه‌ی خصوصی خود دستگاه ذخیره می‌شوند و
تنها با اقدام صریح اپراتور منتقل می‌شوند. قابلیت «حذف همه‌ی داده‌ها» پاک‌سازی کامل
انجام می‌دهد.

---

## توسعه‌دهنده

**محمد جواد قهرمانی** — سازنده‌ی اپ «بامین تهویه»
(<https://github.com/ghahremany/bamin-tahvie-app>) · [پروفایل گیت‌هاب](https://github.com/ghahremany)

---

## پروانه‌ها و منابع

* [MediaPipe Tasks Vision](https://github.com/google-ai-edge/mediapipe) — Apache 2.0 (تشخیص چهره)
* [مدل Face Landmarker](https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task) — float16/1
* [Canonical Face Model](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/modules/face_geometry/data/canonical_face_model.obj) — Apache 2.0 (توپولوژی مش)
* Jetpack Compose / CameraX / AndroidX — Apache 2.0

</div>
