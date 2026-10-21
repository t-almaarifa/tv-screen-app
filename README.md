# تطبيق Android TV لشاشات العرض (Digital Signage)

تطبيق Android TV (Kotlin) يستهلك حصرًا عقد API الموثّق في [`docs/tv-device-api.md`](docs/tv-device-api.md) —
مسارات `/api/v1/tv/device/*` الست في مستودع [`t-almaarifa/association-portal`](https://github.com/t-almaarifa/association-portal)
(البوابة الموحّدة للجمعية، Next.js). هذا مستودع Gradle مستقل تمامًا — لا يشارك أي شفرة مع البوابة، ولا يُبنى
معها. نسخة `docs/tv-device-api.md` هنا منسوخة عن تلك الموجودة في `association-portal`؛ **المرجع الملزم عند
أي تعارض هو الشفرة الفعلية هناك** (`src/app/api/v1/tv/device/*`)، فحدِّث النسخة هنا كلما تغيّر العقد هناك.

## البنية

مشروع Gradle بوحدتين (multi-module)، مقصود منه فصل حقيقي لا تنظيمي فقط:

- **`:core`** — Kotlin/JVM صرف، بلا أي اعتمادية على Android. كل منطق القرار الصافي القابل للاختبار بوحدات
  (JUnit) يعيش هنا: مقارنة checksum، قرار ما الذي يُنزَّل، قرار التبديل الذرّي، آلة حالة الاقتران، تصنيف
  أحداث التشغيل (مؤكَّد/مُقدَّر)، سياسة النبض، وقرار الاستئناف بعد انقطاع الشبكة.
- **`:app`** — تطبيق Android TV الفعلي (Compose for TV، Media3، Room، WorkManager، Retrofit/OkHttp،
  EncryptedSharedPreferences). يعتمد على `:core` ولا يعيد تنفيذ أي من منطقه — فقط I/O وواجهة.

هذا الفصل يعني أن منطق القرار الأهم في المشروع (دورة المزامنة والتبديل الذرّي) قابل للاختبار والتحقق منه فعليًا
بمعزل عن Android SDK بالكامل — وهو ما يهم تحديدًا في بيئة لا يتوفر فيها Android SDK (انظر القسم التالي).

```
core/   منطق صاف (Kotlin/JVM) + اختبارات JUnit
app/    تطبيق Android TV (Compose for TV + Media3 + Room + WorkManager + Retrofit)
docs/   نسخة عقد API (المرجع الملزم في association-portal)
```

## التحقق من البناء في هذه البيئة (Sandbox) — وثيقة صريحة بما لم يُختبر

تنفيذ هذه المهمة جرى داخل بيئة Claude Code السحابية، التي **تمنع صراحةً** الوصول إلى `dl.google.com` على
مستوى سياسة الشبكة (رفض 403 من بروكسي الوكيل، مسجَّل كـ"رفض سياسة"، لا خطأ عابر). كل مسار لتنزيل:

- حزمة Android SDK (المنصّات، أدوات البناء) عبر `sdkmanager`، أو
- Android Gradle Plugin (AGP) عبر `google()` (التي تُحوِّل فعليًا إلى `dl.google.com`، تأكَّد ذلك
  بتتبّع إعادة التوجيه 301 من `maven.google.com` نفسها إلى `dl.google.com`)

يمر عبر هذا المضيف المحظور. هذا يعني أن **وحدة `:app` لم يُتحقق من بنائها فعليًا في هذه الجلسة** — لا
`compileDebugKotlin`، ولا `assembleDebug`، ولا أي شيء يتجاوز تحليل Gradle لملفات `build.gradle.kts`.
التشغيل الفعلي لـ`gradle :app:assembleDebug` في هذه البيئة يفشل بالضبط عند محاولة حلّ Android Gradle
Plugin:

```
Plugin [id: 'com.android.application', version: '8.5.2'] was not found in any of the following sources:
...
Plugin Repositories (could not resolve plugin artifact 'com.android.application:com.android.application.gradle.plugin:8.5.2')
```

أي **قبل** أي محاولة لتصريف شفرة Kotlin نفسها — الفشل في حلّ الاعتمادية الأولى لمحرّك AGP، لا في شفرة
المشروع.

**ما جرى التحقق منه فعليًا، بنجاح حقيقي (لا افتراض):**

```
gradle :core:test --configure-on-demand
```

```
BUILD SUCCESSFUL
32 اختبار JUnit5 — 0 فشل، 0 تجاهل
```

حيلة `--configure-on-demand` ضرورية هنا تحديدًا: جذر `build.gradle.kts` لا يُعلن أي مكوّن إضافي (كل
وحدة تُعلن مكوّناتها بنفسها في `build.gradle.kts` الخاص بها)، فتهيئة Gradle لـ`:core` وحدها لا تحتاج قط
حلّ AGP — وهذا ما يسمح لمنطق `:core` بالبناء والاختبار الحقيقي بمعزل تام عن Android SDK.

**ما لم يُختبر إطلاقًا، ويجب التحقق منه على جهاز بوصول كامل للإنترنت (و/أو Android SDK محليًا):**

1. تصريف `:app` الكامل (`gradle :app:assembleDebug`) — بما في ذلك صحة كل استيراد ودالة من مكتبات
   Compose for TV وMedia3 وRoom (KSP) وRetrofit/Moshi المذكورة أدناه.
2. **أعلى نقطة خطر محدَّدة:** تشغيل الصور عبر Media3 ExoPlayer يعتمد على
   `MediaItem.Builder#setImageDurationMs` مع
   `DefaultRenderersFactory#experimentalSetImageDecoderEnabled(true)` (ميزة "صور ضمن قائمة التشغيل"
   المضافة في Media3 1.3+). لم تُتحقق هذه التوقيعات مقابل الشفرة الفعلية لـ`androidx.media3:media3-exoplayer:1.4.1`
   لتعذّر تنزيل الاعتمادية؛ فهذا أول موضع يجب فحصه عند أول بناء حقيقي
   (`app/src/main/kotlin/.../player/ExoPlayerFactory.kt` و`PlaylistController.kt`).
3. توافق `androidx.tv:tv-material:1.0.0` / `tv-foundation:1.0.0` مع `compose-bom:2024.09.03` المُعلَن،
   وأسماء كل دالة Compose المستعملة منها (`MaterialTheme`, `Text`, `darkColorScheme`) تمامًا كما وردت.
4. أي محاكٍ أو جهاز Android TV فعلي (API 28+) — **لا يتوفر لهذه الجلسة جهاز ولا محاكي إطلاقًا**، فكل
   سيناريوهات التشغيل الفعلي (QR قابل للمسح فعليًا من الشاشة، تدفّق الفيديو، الإقلاع التلقائي
   BOOT_COMPLETED، مهلات الشبكة الحقيقية) غير مُختبرة ميدانيًا — فقط مُراجَعة يدويًا بعناية مقابل توثيق كل
   مكتبة مُستعملة.
5. محاكاة انقطاع الشبكة أثناء التنزيل والاستئناف بعده **مُختبرة كمنطق قرار صافٍ في `:core`**
   (`SyncPlannerTest`: "connection drops mid-download" و"resuming after the outage") — أي أن القرار
   نفسه (ما الذي يُعاد تنزيله، متى يُسمح بالتبديل الذرّي) مُختبر فعليًا بوحدات ناجحة، لكن مسار I/O الفعلي
   (`MediaDownloader` يكتب فعليًا على القرص عبر `OkHttp`/`Retrofit`) غير مُختبر تكامليًا لعدم توفر بيئة
   Android فعلية أو حتى Robolectric (لم يُضَف عمدًا لتفادي اعتمادية بناء إضافية غير محلولة الفائدة هنا).

**خلاصة صادقة:** هذا تطبيق Android TV كامل الشفرة، مبني بعناية مطابقة لعقد API الفعلي حرفيًا، لكنه **لم
يُبنَ ولم يُشغَّل فعليًا على أي جهاز** في هذه الجلسة — فقط طبقة المنطق الصافي (`:core`) مُختبرة ومُتحقَّقة
فعلًا. ابدأ أي مراجعة لاحقة بمحاولة `gradle :app:assembleDebug` على جهاز بوصول كامل لـ`dl.google.com`.

## البناء على جهاز عادي (بوصول كامل للإنترنت)

```bash
./gradlew :core:test            # منطق core فقط
./gradlew :app:assembleDebug    # يتطلب Android SDK (compileSdk 34) و minSdk 28
./gradlew test                  # كل الوحدات
```

رابط الخادم الافتراضي مضبوط على `https://portal.t-almaarifa.org.sa` (`app/build.gradle.kts`،
`buildConfigField DEFAULT_BASE_URL`)؛ لتغييره عند البناء:

```bash
./gradlew :app:assembleDebug -PsignageBaseUrl=https://portal.example.sa
```

أو تعديله على الجهاز نفسه بعد التثبيت قبل أول اقتران (`ServerConfig`، القسم التالي، الفجوة 1).

## عقد API

كل طلب/استجابة في `network/dto/DeviceApiDtos.kt` و`network/DeviceApi.kt` مطابق حرفيًا لِـ
[`docs/tv-device-api.md`](docs/tv-device-api.md) — وهو مُستخرج من الشفرة الفعلية
(`src/app/api/v1/tv/device/*` في جذر المستودع)، لا من الوثيقة التصميمية `docs/signage-module.md`. أي
تعديل مستقبلي على مسارات `/api/v1/tv/device/*` في الخادم يجب أن يُحدَّث أولًا في ذلك الملف، ثم تُطابَق DTOs
هنا عليه.

## الفجوات مقابل العقد الموثّق (وثّقها الكود بدل افتراضها)

1. **لا آلية اكتشاف لعنوان الخادم.** العقد لا يحدد كيف يعرف جهاز جديد تمامًا أي خادم يرسل إليه
   `pair/request` أصلًا. هذا التطبيق يحل المشكلة بقيمة مُدمَجة وقت البناء (`DEFAULT_BASE_URL`) قابلة
   للتجاوز محليًا (`ServerConfig`، مخزَّنة في `SharedPreferences` عادية، ليست سرًا) — وليست قيمة يرسلها
   الخادم أبدًا. أي آلية تزويد (provisioning) أرسخ (QR/NFC يحمل العنوان، أو دفع عبر MDM) تحتاج قرارًا
   وتوثيقًا منفصلين، لم يرد ذكرهما في `docs/tv-device-api.md`.
2. **لا دعم Range على الخادم اليوم.** `openPrivateFile` (`src/lib/services/files.ts`) لا يُرسل
   `Accept-Ranges` ولا يتعامل مع ترويسة `Range` إطلاقًا. الاستئناف الفعلي إذن هو "أعد تنزيل الملف كاملًا
   من جديد"، لا استئناف حقيقي بالبايت — هذا مُطبَّق ومُختبر صراحة في `:core`
   (`DownloadResumption`/`DownloadResumptionTest`)، ومطبَّق في `MediaDownloader` (يحذف الملف المؤقت
   الناقص ويبدأ من الصفر). إن أضاف الخادم دعم Range مستقبلًا، يتكيّف التطبيق تلقائيًا بلا تحديث (القرار
   الصافي يتحقق من `acceptsRanges` في كل مرة).
3. **لا صيغة مُعرَّفة لمحتوى رمز QR.** العقد يذكر فقط الرمز النصي المكوَّن من 6 أرقام (`code`)، لا رابطًا
   ولا صيغة JSON للمسح. التطبيق يُرمِّز نص الرمز نفسه في QR (تسهيلًا لإدخاله بدقة في لوحة الإدارة عبر
   هاتف، لا قناة اقتران بديلة).
4. **لا حد معدّل (rate limiting) في العقد نفسه** (موثَّق صراحة في آخر `tv-device-api.md`) — التطبيق مع
   ذلك لا "يُغرق" الخادم: النبض يتبع `poll_interval_seconds` الذي يُقرره الخادم حصرًا (مع تحديد دفاعي
   [5، 3600] ثانية في `HeartbeatPolicy` ضد قيمة خادم مُعطَّلة)، واستطلاع الاقتران على فاصل ثابت 3 ثوانٍ من
   جهة التطبيق فقط (العقد لا يحدد فاصلًا هنا أصلًا).
5. **أخطاء تنزيل الوسيطة الخاصة بالفحص الأمني** (`ATTACHMENT_INFECTED` دائم، `ATTACHMENT_NOT_SCANNED`
   مؤقت، `ATTACHMENT_FILE_MISSING`) تُسجَّل كحدث `ERROR` محليًا، ولا تُسقط العنصر من قائمة التشغيل
   المستهدفة تلقائيًا — يستمر الجهاز بإعادة محاولتها كل دورة مزامنة، ولا يُفعِّل أي نسخة جزئية (التزامًا
   بـFR-25). هذا قرار تصميمي مقصود، لا عيبًا: لا يتضمن العقد أي نص لتجاهل عنصر فاشل بشكل دائم.

## دورة الحياة المنفَّذة

1. **أول تشغيل** (`pairing/PairingViewModel.kt`): `POST pair/request`، عرض `code` كنص وQR
   (`ui/PairingScreen.kt`، `pairing/QrCode.kt`)، استطلاع `POST pair/status` كل 3 ثوانٍ حتى
   `approved`/`rejected`/`expired` (آلة الحالة الصافية في `core/PairingState.kt`، مُختبرة بالكامل).
2. **بعد الاعتماد**: بيانات الاعتماد (`tv_<device_id>.<secret>`) تُخزَّن في `EncryptedSharedPreferences`
   فور وصولها من أول استطلاع ناجح (`data/credentials/CredentialStore.kt`) — لا تُطلب مرة ثانية أبدًا
   (العقد يسلّمها مرة واحدة فقط).
3. **خدمة خلفية** (`service/SignageForegroundService.kt`، Foreground Service حقيقي بنوع `dataSync`):
   حلقة نبض بفاصل يُمليه الخادم، فحص `publication_changed`، مزامنة عند الحاجة
   (`data/repo/SyncRepository.kt`)، تنزيل الناقص فقط بمقارنة checksum (`core/SyncPlanner.kt`)، وتبديل
   ذرّي حصري عبر معاملة Room واحدة (`data/db/Daos.kt#replaceActive`) لا يحدث إلا باكتمال كل الملفات
   والتحقق من بصماتها (`core/SyncPlanner.shouldActivate`).
4. **التشغيل** (`ui/PlaybackScreen.kt` + `player/*`): حلقة ExoPlayer لا نهائية
   (`Player.REPEAT_MODE_ALL`) تقرأ حصرًا من Room (`ActivePublicationDao.observe()`، Flow)، بلا أي اتصال
   شبكة من شاشة التشغيل نفسها — تستمر بآخر نسخة صالحة محليًا عند فقد الشبكة تلقائيًا (ببساطة: لا شيء
   يُغيّر ما تعرضه الشاشة سوى معاملة Room ناجحة).
5. **`BOOT_COMPLETED`** (`boot/BootReceiver.kt`): إعادة تشغيل الخدمة وجدولة WorkManager وفتح `MainActivity`
   تلقائيًا (التطبيق ليس في مطلق launcher عادي يتطلب لمسة يد).
6. **سجل تشغيل محلي** (`data/db/Entities.kt#PlaybackEventEntity` + `data/repo/EventLogRepository.kt`):
   كل حدث يُصنَّف "مؤكَّد" أو "مُقدَّر" عند إنشائه فقط عبر `core/PlaybackEvents.kt#PlaybackEventFactory`
   (المصنع الوحيد المسموح له بذلك)، ويُرفع دفعة (حتى 200 عنصر، حد الخادم) عند أول فرصة اتصال ناجحة —
   سواء من حلقة النبض نفسها أو من `sync/ManifestSyncWorker.kt` الدوري (شبكة WorkManager، كل 15 دقيقة،
   الحد الأدنى المسموح للعمل الدوري في WorkManager نفسه).

## الاختبارات

`core/src/test/kotlin/.../core/*Test.kt` — 32 اختبار JUnit5، كلها فعليًا ناجحة في هذه الجلسة:

| الملف | يغطي |
|---|---|
| `ChecksumTest` | تطابق SHA-256 مع قيم معروفة، مقارنة بلا حساسية لحالة الأحرف |
| `SyncPlannerTest` | جهاز جديد بلا شيء محليًا، نسخة مطابقة فعلًا (لا عمل)، إخلاء وسيطة غير مرجعية، **قطع الاتصال أثناء التنزيل** (لا تبديل)، **الاستئناف بعده** (تبديل بعد اكتمال التحقق)، تعارض checksum محلي قديم، نسخة غير محلولة (لا جدول نشط ولا افتراضي) |
| `PairingStateMachineTest` | كل انتقالات `pending/approved/rejected/expired`، التقاط السر لمرة واحدة والاحتفاظ به عبر استطلاعات لاحقة، فشل صريح إن اعتُمد الجهاز دون أي سر مُلتقَط قط |
| `RetryScheduleTest` | نمو أسّي، سقف أعلى، لا يقل الـjitter عن القيمة الأساسية |
| `DownloadResumptionTest` | لا استئناف بلا بايتات مكتوبة، لا استئناف مع خادم اليوم (`acceptsRanges=false`)، استئناف حقيقي مع خادم افتراضي يدعم Range |
| `PlaybackEventsTest` | الأحداث المُلاحَظة مباشرة = مؤكَّدة، استمرار العرض دون اتصال = مُقدَّر دائمًا، تقسيم الدفعات على حد 200 حدثًا |
| `HeartbeatPolicyTest` | تحديد فاصل خادم سيئ القيمة، القرار بإعادة جلب النسخة المنشورة من `publication_changed` فقط |

لا اختبارات Android (`app/src/test`, `androidTest`) تجاوزت مرحلة الكتابة — لا يمكن تشغيلها هنا (القسم
أعلاه). `app/build.gradle.kts` جاهزة بتبعيات JUnit5 وAndroidX Test لإضافتها لاحقًا.
