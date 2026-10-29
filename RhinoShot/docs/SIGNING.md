# امضا و انتشار — RhinoShot

## کلید فعلی

| مورد | مقدار |
|------|-------|
| فایل کلید | `keystore/rhinoshot-release.jks` |
| نام مستعار (alias) | `rhinoshot` |
| الگوریتم | RSA 2048 / SHA256withRSA |
| اعتبار | ۳۰ سال (۱۰۹۵۰ روز) |
| گواهی | `CN=RhinoShot Clinic, OU=Photo Studio, O=RhinoShot, L=Tehran, C=IR` |
| SHA-256 گواهی | `69b2fb70f026914e0c960b217b940e77f569d9e2f568b1c7a4f00047195ce920` |
| رمز کی‌استور و کلید | `rhinoshot` (در `keystore.properties`) |

> ⚠️ این کلید داخل بسته‌ی تحویلی هست تا بتوانید نسخه‌های بعدی را با **همان امضا**
> بسازید (وگرنه گوشی نصب به‌روز را رد می‌کند). برای استفاده‌ی جدی، یک نسخه از
> کلید را در جای امن نگه دارید و رمزها را از `keystore.properties` به یک
> secret manager منتقل کنید. کلید عمومی (گواهی) هیچ ریسکی ندارد؛ کلید خصوصی را
> همراه کسی share نکنید.

## ساخت نسخه‌ی امضاشده

```bash
# ۱) فایل‌های کلید در ریشه‌ی پروژه باشند:
#      keystore/rhinoshot-release.jks
#      keystore.properties  (storeFile, storePassword, keyAlias, keyPassword)
# ۲) بیلد:
./gradlew assembleRelease
# ۳) خروجی:
app/build/outputs/apk/release/app-release.apk
```

اگر `keystore.properties` نبود، بیلد **نمی‌شکند** و نسخه‌ی release با کلید debug
امضا می‌شود (فقط برای تست). برای انتشار واقعی، وجود `keystore.properties` ضروری است.

## بررسی امضا

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk
```

## نصب روی گوشی

```bash
adb install -r RhinoShot-v0.2.0-release.apk
```
یا کپی فایل روی گوشی و باز کردن آن (باید «نصب از منابع ناشناس» برای فایل‌منیجر
یا مرورگر فعال باشد).

## به‌روزرسانی نسخه

هر بار که نسخه را بالا می‌برید، **هر دو** مقدار را در `app/build.gradle.kts`
تغییر دهید (وگرنه نصب به‌روز رد می‌شود):

```kotlin
versionCode = 3        // عدد صحیح صعودی
versionName = "0.3.3"
```

و برای هماهنگی، `TAG_SOFTWARE` در `ui/CaptureScreen.kt` را هم عوض کنید:
```kotlin
exif.setAttribute(ExifInterface.TAG_SOFTWARE, "RhinoShot 0.3.3")
```

## چرخش کلید (در صورت نیاز)

چرخش کلید باعث می‌شود کاربران مجبور شوند اپ را حذف و دوباره نصب کنند
(داده‌ی جلسه‌های قبلی از بین می‌رود). برای کلینیک توصیه نمی‌شود؛ کلید فعلی
۳۰ سال اعتبار دارد.

## نکته‌ی امنیتی

* کلید امضا فقط ثابت می‌کند APK از ساختگاه شماست؛ **کاربر** دسترسی کامل به
  حافظه‌ی اپ دارد. برای محافظت از داده‌ی بیمار، به ریسک‌سنجی و رمزگذاری
  سطح‌دستگاه تکیه کنید (Android Keystore / رمز الگویی گوشی).
* مجوز شبکه در اپ حذف شده، بنابراین حتی اگر کسی اپ را دست‌کاری کند، داده‌ی
  بیمار نمی‌تواند از دستگاه خارج شود.
