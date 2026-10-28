# ساخت، امضا و نصب روی آیفون

این راهنما سه مسیر دارد. مسیر ۱ رایگان است و برای تست روی آیفون خودتان کافی است.

| مسیر | هزینه | اعتبار | مناسب برای |
|------|-------|--------|-----------|
| **۱) Free Personal Team** | رایگان | **۷ روز** (باید دوباره نصب شود) | تست سریع روی یک آیفون |
| **۲) TestFlight** | ۹۹ دلار/سال | ۹۰ روز برای هر بیلد | چند نفر از تیم/کلینیک |
| **۳) App Store** | ۹۹ دلار/سال | دائمی | انتشار عمومی |

> **چرا `.ipa` آماده تحویل داده نشد؟** ساخت `.ipa` نیازمند macOS + Xcode +
> امضای اپل است. این پروژه در محیط لینوکسی نوشته شده، پس به‌جای `.ipa` یک
> **پروژه‌ی کامل و آماده‌ی Xcode** تحویل داده شده که با یک دستور باز می‌شود و
> خروجی `.ipa` در چند دقیقه روی سیستم شما ساخته می‌شود.

---

## ۰. خلاصه‌ی سریع (مسیر ۱)

```bash
cd RhinoShot-iOS
./bootstrap.sh
```

1. در Xcode: تارگت `RhinoShot` → `Signing & Capabilities` → تیم: **Personal Team**
   → Bundle ID یکتا (مثلاً `ir.rhinoshot.clinic`).
2. آیفون را با کابل وصل کنید، در بالای Xcode آن را انتخاب کنید، ⌘R بزنید.
3. روی آیفون: `Settings ▸ General ▸ VPN & Device Management` → روی پروفایل
   «Developer» بزنید → **Trust**.
4. در iOS 16 به بعد: `Settings ▸ Privacy & Security ▸ Developer Mode` → روشن →
   گوشی را ری‌استارت کنید → برنامه را دوباره اجرا کنید.

تمام. اپ روی گوشی نصب شد. (بعد از ۷ روز اعتبار تمام می‌شود و باید دوباره
نصب شود.)

---

## ۱. پیش‌نیازها

| مورد | حداقل | توضیح |
|------|-------|-------|
| macOS | 13 Ventura | Xcode فقط روی مک اجرا می‌شود |
| Xcode | 15.0 (ترجیحاً 16.x) | Swift 5.9 و iOS 16 SDK |
| آیفون | iOS 16.0 | دستگاه باید در Xcode دیده شود |
| Apple ID | رایگان کافی است | برای مسیر ۱ |
| اینترنت | برای بارگیری SPM | فقط بار اول (حدود ۲۰۰ مگابایت) |
| Homebrew | اختیاری | فقط برای نصب خودکار XcodeGen |

ابزارهای پروژه با `brew` نصب می‌شوند:

```bash
brew install xcodegen
```

`bootstrap.sh` خودش این کار را انجام می‌دهد اگر XcodeGen نصب نباشد.

---

## ۲. مسیر ۱ — Apple ID رایگان (نصب مستقیم روی آیفون)

### ۲.۱ ساخت پروژه

```bash
cd RhinoShot-iOS
./bootstrap.sh
```

* `project.yml` → پروژه‌ی `RhinoShot.xcodeproj` را می‌سازد.
* مدل `RhinoShot/Resources/face_landmarker.task` را بررسی می‌کند.
* Xcode را باز می‌کند.

> پروژه عمداً به‌صورت **متنی** نگهداری می‌شود (`project.yml`). اگر خواستید
> تنظیمات را در Xcode تغییر دهید، تغییر را در `project.yml` هم بنویسید و دوباره
> `xcodegen generate` را اجرا کنید، وگرنه با بازسازی پروژه پاک می‌شود.

### ۲.۲ امضا

1. در Xcode: `RhinoShot` (پروژه‌ی سمت چپ) → تب `Signing & Capabilities`.
2. گزینه‌ی **Automatically manage signing** روشن باشد.
3. در `Team` گزینه‌ی **Personal Team** (یا تیم سازمانی) را انتخاب کنید.
   * اگر لیست خالی است: `Xcode ▸ Settings ▸ Accounts ▸ + ▸ Apple Account`
     و با Apple ID خود وارد شوید.
4. در `Bundle Identifier` یک شناسه‌ی یکتا بگذارید، مثلاً
   `ir.rhinoshot.clinic.<نام‌شما>` (شناسه‌ی `ir.rhinoshot.clinic` قبلاً رزرو شده
   و برای اپ رایگان قابل استفاده نیست).
5. اگر بخش `Signing Certificate` خطا داد: تیک
   `Manage Version and Build Number` را بردارید و شماره را دستی
   `0.2.0 (1)` بگذارید.

### ۲.۳ اجرا روی گوشی

1. آیفون را با کابل وصل کنید و روی صفحه‌ی آیفون «Trust This Computer» را بزنید.
2. در Xcode، بالای صفحه: ابزار «Run» → نام آیفون خود را انتخاب کنید → ⌘R.
3. اگر پیام `Sign to Run Locally` آمد: در تنظیمات گوشی
   `Settings ▸ General ▸ VPN & Device Management` → پروفایل توسعه‌دهنده → **Trust**.
4. در iOS 16+:
   `Settings ▸ Privacy & Security ▸ Developer Mode` → **Enable** → گوشی را
   ری‌استارت کنید → اپ را دوباره باز کنید.
5. اولین اجرا، اپ مجوز **دوربین** می‌خواهد؛ «Allow» را بزنید.

### ۲.۴ محدودیت‌های مسیر رایگان

| مورد | محدودیت |
|------|---------|
| اعتبار اپ | ۷ روز از زمان نصب |
| تعداد اپ | حداکثر ۳ اپ هم‌زمان با یک Apple ID |
| شناسه‌ی اپ | حداکثر ۱۰ شناسه در هفته |
| مجوز Push / App Groups | ندارد |
| مناسب برای | تست، دمو به پزشک، توسعه |

برای تمدید: همان مراحل ۲.۳ را تکرار کنید (نصب دوباره = تمدید ۷ روز).

---

## ۳. مسیر ۲ — TestFlight (پرداختی، پیشنهادی برای کلینیک)

1. عضویت در **Apple Developer Program** (۹۹ دلار/سال) بگیرید.
2. در `developer.apple.com` یک **Bundle ID** بسازید: مثلاً `ir.rhinoshot.clinic`.
3. در App Store Connect → **My Apps ▸ + ▸ New App**:
   * Name: `RhinoShot`
   * Primary Language: فارسی (fa)
   * Bundle ID: همان بالا
   * SKU: `RHINO-001`
4. در Xcode: تیم پولی خود را انتخاب کنید → `Product ▸ Archive`.
5. در Organizer: `Distribute App ▸ App Store Connect ▸ Upload`.
   (یا از خط فرمان: `xcrun altool --upload-app` / `xcodebuild -exportArchive` — بخش ۵)
6. چند دقیقه تا یک ساعت صبر کنید تا بیلد پردازش شود.
7. App Store Connect ▸ **TestFlight** ▸ گروه بسازید و ایمیل تسترها را وارد کنید.
   * **Internal testers** (تا ۱۰۰ نفر): بیلد چند دقیقه بعد می‌رسد.
   * **External testers**: نیاز به بررسی **Beta App Review** دارد (۱–۳ روز).
8. تسترها اپ **TestFlight** را از App Store نصب می‌کنند و RhinoShot را از داخل آن.

---

## ۴. مسیر ۳ — انتشار در App Store

1. همان بخش ۳ تا مرحله‌ی ۵ (Upload) انجام شود.
2. در App Store Connect: صفحه‌ی اپ → نسخه‌ی جدید → توضیحات فارسی/انگلیسی،
   اسکرین‌شات‌ها (۶.۷ اینچ و ۵.۵ اینچ)، تیم‌بندی، محدودیت سنی «۱۷+».
3. بخش **App Privacy**: اپ هیچ داده‌ای جمع‌آوری یا ارسال نمی‌کند →
   «Data Not Collected» را انتخاب کنید (مانیفست `PrivacyInfo.xcprivacy` در پروژه
   آماده است).
4. Export Compliance: اپ فقط رمزنگاری سیستمی iOS را استفاده می‌کند →
   `ITSAppUsesNonExemptEncryption = false` (در Info.plist تنظیم شده) →
   پاسخ سؤال encryption: «No».
5. `Submit for Review`.

---

## ۵. ساخت از خط فرمان و تولید `.ipa`

```bash
cd RhinoShot-iOS
xcodegen generate                       # اگر پروژه را ندارید

# ۱) آرشیو
xcodebuild -project RhinoShot.xcodeproj -scheme RhinoShot \
           -configuration Release \
           -destination 'generic/platform=iOS' \
           -archivePath build/RhinoShot.xcarchive \
           archive

# ۲) خروجی ipa
xcodebuild -exportArchive \
           -archivePath build/RhinoShot.xcarchive \
           -exportOptionsPlist ExportOptions.plist \
           -exportPath build/ipa
```

`ExportOptions.plist` (نمونه برای توزیع توسعه‌ای/آزمایشی):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>development</string>          <!-- یا ad-hoc / app-store-connect / enterprise -->
    <key>teamID</key>
    <string>XXXXXXXXXX</string>            <!-- Team ID از developer.apple.com -->
    <key>signingStyle</key>
    <string>automatic</string>
    <key>uploadSymbols</key>
    <true/>
</dict>
</plist>
```

* `method: development` → نصب مستقیم روی گوشی‌های ثبت‌شده در Provisioning Profile.
* `method: ad-hoc` → نصب روی گوشی‌های ثبت‌شده در **UDID** بدون App Store
  (برای تیم کلینیک که چند گوشی مشخص دارد مناسب است).
* `method: app-store-connect` → آپلود به TestFlight/App Store.

---

## ۶. عیب‌یابی

| مشکل | راه‌حل |
|------|--------|
| `Could not resolve package dependencies` | اینترنت/VPN را بررسی کنید؛ پکیج از `github.com/google-ai-edge/mediapipe` و باینری از `dl.google.com` می‌آید. یک‌بار با اینترنت دیگر امتحان کنید. |
| `Signing for "RhinoShot" requires a development team` | در `Signing & Capabilities` یک Team انتخاب کنید (Personal Team کافی است). |
| `No profiles for 'ir.rhinoshot.clinic' were found` | Bundle ID را یکتا کنید یا در `Accounts` دوباره وارد Apple ID شوید. |
| `Unable to find a destination` | گوشی را وصل کنید و Trust را بزنید، یا از شبیه‌ساز استفاده کنید (شبیه‌ساز دوربین ندارد!). |
| اپ روی گوشی باز نمی‌شود | `Developer Mode` را روشن و گوشی را ری‌استارت کنید؛ پروفایل را Trust کنید. |
| `Unable to find a destination` / `Failed to codesign` | نسخه‌ی iOS گوشی و پروژه را چک کنید؛ پروژه حداقل iOS 16 می‌خواهد. |
| تصویر دوربین سیاه است | روی شبیه‌ساز تست نکنید؛ روی دستگاه واقعی اجرا کنید. |
| «مدل تشخیص چهره بارگذاری نشد» | فایل `RhinoShot/Resources/face_landmarker.task` را بررسی کنید؛ `bootstrap.sh` آن را چک می‌کند. |
| زاویه‌ها برعکس‌اند | در صفحه‌ی عکاسی دکمه‌ی **کالیبراسیون** → سه گام را انجام دهید (هر گوشی یک‌بار). |
| خودکار شاتر نمی‌زند | حالت کنترل کیفیت را روی «آسان» بگذارید و ۰٫۹ ثانیه وضعیت درست را حفظ کنید. |
| `The package product 'MediaPipeTasksVision' is required...` | پروژه را بازسازی کنید: `xcodegen generate`. |

---

## ۷. نکته‌های امنیتی و حریم خصوصی

* اپ **هیچ درخواست شبکه‌ای** نمی‌فرستد؛ داده‌ی بیمار هرگز از گوشی خارج نمی‌شود
  مگر با اقدام صریح اپراتور (گالری / AirDrop / اشتراک‌گذاری).
* پوشه‌ی داده با `isExcludedFromBackup` از پشتیبان iCloud کنار گذاشته شده است.
* اگر خواستید گوشی را به کلینیک تحویل دهید: از صفحه‌ی «بازبینی» گزینه‌ی
  **«حذف کامل این جلسه»** را بزنید؛ کل پوشه پاک می‌شود.
* برای چند دستگاه در کلینیک، مسیر **TestFlight** یا **Ad-Hoc** را انتخاب کنید تا
  همه با یک بیلد هم‌نسخه باشند.
