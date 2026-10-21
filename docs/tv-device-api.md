# عقد API الخاص بجهاز Android TV (وحدة شاشات العرض)

> **نسخة منسوخة** عن `docs/tv-device-api.md` في مستودع
> [`t-almaarifa/association-portal`](https://github.com/t-almaarifa/association-portal). المسارات المذكورة
> أدناه (`src/app/api/v1/tv/device/*` وما تستدعيه) تنتمي لذلك المستودع، لا لهذا المستودع. عند أي تعارض بين
> هذه النسخة وتلك، أو أي تعديل مستقبلي على الخادم، فالمرجع الملزم هو الشفرة الفعلية هناك — حدِّث هذه النسخة
> لتطابقها.

هذا توثيق دقيق للمسارات التي يستهلكها جهاز Android TV حصرًا تحت `/api/v1/tv/device/*`، مستخرج مباشرة من الكود الفعلي في `src/app/api/v1/tv/device/*` وما يستدعيه من `src/lib/tv/http.ts`، `src/lib/tv/rules.ts`، `src/lib/tv/device-auth.ts`، `src/lib/tv/pairing.ts`، `src/lib/tv/device-sync.ts`، `src/lib/tv/publications.ts`، `src/lib/wf/scan.ts`، `src/lib/api.ts` و`src/lib/wf/result.ts` — وليس من `docs/signage-module.md` التصميمي. أي فرق بين الاثنين فالمرجع الملزم هنا هو الكود.

هذا الملف لا يغطي مسارات لوحة الإدارة (`/api/v1/tv/devices`, `/groups`, `/media`, `/playlists`, `/schedules`, `/publications`, `/monitoring`, `/sites`) ولا مسارات اعتماد/رفض الاقتران من لوحة الإدارة (`/api/v1/tv/devices/pair/approve|reject`) — تلك لجلسة موظف (`requireTvApi`)، لا لجهاز.

## المسارات الستة

| الطريقة | المسار | الغرض |
|---|---|---|
| POST | `/api/v1/tv/device/pair/request` | طلب رمز اقتران جديد (بلا هوية مسبقة) |
| POST | `/api/v1/tv/device/pair/status` | استطلاع حالة طلب الاقتران، واسترجاع بيانات الاعتماد مرة واحدة |
| POST | `/api/v1/tv/device/heartbeat` | نبض دوري: يبلّغ الخادم بحالة الجهاز، ويعرف فاصل السحب التالي |
| GET | `/api/v1/tv/device/manifest` | قراءة النسخة المنشورة الحالية (قائمة العناصر المطلوب عرضها) |
| GET | `/api/v1/tv/device/media/{id}/download` | تنزيل ملف وسيطة واحدة من النسخة المنشورة الحالية |
| POST | `/api/v1/tv/device/events` | رفع دفعة من أحداث سجل التشغيل |

## المصادقة

مساران الاقتران (`pair/request`, `pair/status`) بلا هوية مسبقة — الجهاز ليس له بعد بيانات اعتماد. البقية (`heartbeat`, `manifest`, `media/{id}/download`, `events`) تتطلب ترويسة:

```
Authorization: Bearer tv_<device_id>.<secret>
```

يتحقق `verifyDeviceCredential` (`src/lib/tv/device-auth.ts`) من الصيغة بالتعبير النمطي:

```
^tv_([0-9a-f-]{36})\.([A-Za-z0-9_-]{32,64})$
```

أي أن `device_id` معرّف UUID (36 محرفًا بما فيها الشرطات)، و`secret` نص من 32 إلى 64 محرفًا (أحرف/أرقام/`_`/`-`). ثم يُطابَق `sha256(secret)` بما خُزِّن (`credentialHash`)، ويُرفض إن كان الجهاز غير موجود أو مُلغى اعتماده (`credentialRevokedAt` ليس null). الرد 401 موحّد في كل حالات الفشل (صيغة غير صالحة، جهاز غير موجود، سر خاطئ، اعتماد مُلغى) بلا أي تمييز بينها في الرسالة — هذا مقصود (لا يميّز مهاجم بين سر خاطئ ومعرّف غير موجود).

لا جلسة ولا كوكيز ولا فحص CSRF على مسارات الجهاز (الجهاز لا يحمل كوكيز المتصفح أصلًا) — خلافًا لمسارات لوحة الإدارة.

## الوحدة المعطّلة

إن كانت وحدة شاشات العرض (`MODULE_ID = "tv"`) معطّلة من لوحة الإدارة:

- **القراءة تبقى متاحة:** `GET /manifest` و`GET /media/{id}/download` يعملان بلا تغيير (بيانات اعتماد صالحة تكفي).
- **الكتابة تُرفض بـ 409 `MODULE_DISABLED`:** `heartbeat`, `events`, وكذلك `pair/request`/`pair/status` (طريقتاهما POST فقط أصلًا).

هذا الفحص يسبق فحص بيانات الاعتماد في مساري الاقتران (لا حاجة لاعتماد بعد)، ويلي فحص بيانات الاعتماد في البقية (401 أولًا إن كانت بيانات الاعتماد غير صالحة، حتى لو كانت الوحدة معطّلة أيضًا).

## شكل الخطأ الموحّد

كل خطأ (من `apiError` أو `failResponse`) بالشكل:

```json
{
  "code": "STRING_CODE",
  "message_ar": "رسالة عربية",
  "field_errors": { "field_name": "رسالة الحقل" },
  "correlation_id": "uuid"
}
```

مع ترويسة `x-correlation-id` وترويسة `cache-control: no-store`. استجابات النجاح (`apiJson`/`resResponse`) تحمل `cache-control: no-store` فقط (بلا `x-correlation-id`).

## 1) `POST /api/v1/tv/device/pair/request`

طلب رمز اقتران جديد. لا جسم طلب (لا تُرسل أي شيء). بلا هوية مسبقة.

الاستجابة — **201 Created**:

```json
{
  "code": "123456",
  "poll_token": "base64url-string",
  "expires_at": "2026-10-21T10:05:00.000Z"
}
```

- `code`: رمز من 6 أرقام (000000–999999)، يُعرض على شاشة الجهاز ليدخله الموظف في لوحة الإدارة.
- `poll_token`: 32 بايتًا عشوائية بترميز base64url. يُستعمل في `pair/status` دون غيره.
- `expires_at`: ISO 8601، بعد `pairing_code_ttl_seconds` ثانية من الآن (300 ثانية افتراضيًا — `TvSettings`، قابل للتعديل من لوحة الإدارة).

الأخطاء:

| الحالة | code | السبب |
|---|---|---|
| 409 | `MODULE_DISABLED` | الوحدة معطّلة |

لا حد لعدد مرات الطلب في الكود الحالي (كل طلب يُنشئ سجل اقتران جديد مستقل).

## 2) `POST /api/v1/tv/device/pair/status`

استطلاع حالة طلب اقتران سابق. `poll_token` في جسم الطلب لا في الرابط (لا يظهر في سجلات الوصول).

الطلب:

```json
{ "poll_token": "base64url-string" }
```

- حد أقصى لحجم الجسم: 1024 بايت.
- `poll_token` نص إلزامي، طوله بين 16 و128 محرفًا (وإلا 401 `UNAUTHENTICATED`، لا 422).

الاستجابة — **200 OK**، حسب حالة الطلب:

| الحالة | الجسم |
|---|---|
| قيد الانتظار | `{ "status": "pending" }` |
| مرفوض | `{ "status": "rejected" }` |
| منتهي الصلاحية | `{ "status": "expired" }` |
| معتمَد، أول استطلاع بعد الاعتماد | `{ "status": "approved", "device_id": "uuid", "credential": "tv_<device_id>.<secret>" }` |
| معتمَد، استطلاع لاحق | `{ "status": "approved", "device_id": "uuid" }` (بلا `credential`) |

بيانات الاعتماد (`credential`) تُسلَّم **مرة واحدة فقط** في أول استطلاع بعد الاعتماد من لوحة الإدارة، ثم تُمحى فورًا من قاعدة البيانات (`pendingSecret = null`) ولا تُعاد أبدًا — على الجهاز حفظها من أول استجابة.

إن كان الطلب `PENDING` وتجاوز وقته `expires_at`، يُحوَّل تلقائيًا إلى `EXPIRED` عند هذا الاستطلاع نفسه ويُعاد `{ "status": "expired" }` (لا تحويل دوري منفصل — التحويل يحدث عند أول استطلاع بعد الانتهاء).

الأخطاء:

| الحالة | code | السبب |
|---|---|---|
| 422 | `VALIDATION_FAILED` | `poll_token` مفقود أو ليس نصًا |
| 401 | `UNAUTHENTICATED` | `poll_token` طوله خارج [16, 128]، أو غير معروف في قاعدة البيانات |
| 409 | `MODULE_DISABLED` | الوحدة معطّلة |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` ليس `application/json` |
| 413 | `PAYLOAD_TOO_LARGE` | الجسم أكبر من 1024 بايت |
| 400 | `INVALID_JSON` | جسم غير قابل للتحليل كـJSON |

لا حد لعدد مرات الاستطلاع في الكود الحالي (لا throttling ولا حد أدنى بين طلبين)، لكن `poll_token` نفسه عشوائي وغير قابل للتخمين، و`code` المعروض على الشاشة صالح 300 ثانية فقط.

## 3) `POST /api/v1/tv/device/heartbeat`

نبض دوري: يُحدِّث حالة الجهاز في قاعدة البيانات، ويعيد للجهاز فاصل السحب التالي وما إذا تغيّرت النسخة المنشورة.

المصادقة: مطلوبة. الوحدة المعطّلة → 409 `MODULE_DISABLED`.

الطلب — حد أقصى 2048 بايت، جميع الحقول اختيارية، لا حقول إضافية مسموحة:

```json
{
  "app_version": "string (1-40 حرفًا) | null",
  "current_publication_id": "uuid | null",
  "storage_free_bytes": "integer ≥ 0 | null",
  "last_error": "string (1-500 حرفًا) | null"
}
```

- أي خاصية غير هذه الأربع → 422 `VALIDATION_FAILED` باسم الخاصية.
- `storage_free_bytes` يُقصّ إلى عدد صحيح (`Math.floor`) إن أُرسل عددًا عشريًا.
- ممنوع إرسال حقول هوية (`actor`, `actor_id`, `actorId`, `actor_email`, `actorEmail`, `on_behalf_of`, `onBehalfOf`, `performed_by`, `performedBy`) → 422 `IDENTITY_FROM_INPUT_REJECTED` إن وُجد أي منها.

الاستجابة — **200 OK**:

```json
{
  "server_time": "2026-10-21T10:00:00.000Z",
  "poll_interval_seconds": 30,
  "publication_changed": true
}
```

- `poll_interval_seconds`: من إعدادات الوحدة (`heartbeat_interval_seconds`، 30 ثانية افتراضيًا).
- `publication_changed`: `true` إن اختلف `current_publication_id` المُرسَل عن معرّف النسخة المنشورة الحالية فعليًا على الخادم (بما في ذلك الحالة التي لا توجد فيها نسخة منشورة أصلًا، وهي `null`) — إشارة للجهاز بأن عليه استدعاء `GET /manifest` من جديد.
- النبض يصفّر أي تنبيه انقطاع سابق على الجهاز (`offlineAlertedAt = null`).

الأخطاء:

| الحالة | code | السبب |
|---|---|---|
| 401 | `UNAUTHENTICATED` | بيانات اعتماد غير صالحة أو مُلغاة |
| 409 | `MODULE_DISABLED` | الوحدة معطّلة |
| 422 | `VALIDATION_FAILED` | حقل غير معروف، أو قيمة لا تطابق الشكل المذكور أعلاه |
| 422 | `IDENTITY_FROM_INPUT_REJECTED` | حقل هوية في الجسم |
| 415 / 413 / 400 | كما في القسم السابق | نوع المحتوى / حجم الجسم / JSON غير صالح |

لا حد معدّل (rate limit) مطبَّق في الكود؛ `poll_interval_seconds` إرشاد للجهاز فقط، لا يُنفَّذ قسرًا على الخادم.

## 4) `GET /api/v1/tv/device/manifest`

قراءة مجرَّدة للنسخة المنشورة الحالية للجهاز (الحل الفعلي يحدث عند حفظ جدول أو تغيير عضوية مجموعة، لا هنا).

المصادقة: مطلوبة. تعمل حتى لو كانت الوحدة معطّلة. لا جسم طلب.

الاستجابة — **200 OK**:

إن لم توجد نسخة منشورة بعد لهذا الجهاز:

```json
{ "publication_id": null, "version": 0, "generated_at": null, "items": [], "ttl_seconds": 30 }
```

إن وُجدت:

```json
{
  "publication_id": "uuid",
  "version": 3,
  "generated_at": "2026-10-21T09:00:00.000Z",
  "items": [
    {
      "media_id": "uuid",
      "checksum": "string",
      "type": "IMAGE | VIDEO",
      "order": 0,
      "seconds": 10,
      "download_url": "/api/v1/tv/device/media/<media_id>/download"
    }
  ],
  "ttl_seconds": 30
}
```

- `items` مُرتَّبة بحسب `order`، وهي لقطة جاهزة محسوبة مسبقًا (`itemsSnapshot`) — لا حساب حي هنا.
- `ttl_seconds` = `heartbeat_interval_seconds` من إعدادات الوحدة (نفس قيمة `poll_interval_seconds` بالنبض، 30 ثانية افتراضيًا) — إرشاد لمدة صلاحية هذه القراءة قبل إعادة الاستعلام.
- `download_url` رابط نسبي ثابت الصيغة: `/api/v1/tv/device/media/{media_id}/download` — لا توقيع ولا انتهاء صلاحية في الرابط نفسه (التفويض يُفحص عند كل طلب تنزيل مستقل، انظر القسم التالي).

الأخطاء:

| الحالة | code | السبب |
|---|---|---|
| 401 | `UNAUTHENTICATED` | بيانات اعتماد غير صالحة أو مُلغاة |

## 5) `GET /api/v1/tv/device/media/{id}/download`

تنزيل ملف وسيطة واحدة. `{id}` هو `media_id` كما يظهر في `items` ضمن `GET /manifest`.

المصادقة: مطلوبة. تعمل حتى لو كانت الوحدة معطّلة. لا جسم طلب.

التفويض ضيّق جدًا ومقصود: الوسيطة يجب أن تكون ضمن لقطة النسخة المنشورة الحالية لهذا الجهاز نفسه (لا أي نسخة أخرى، ولا نسخة لجهاز آخر)، وأن تكون حالتها الحالية في قاعدة البيانات `ACTIVE` (لا `VOID`) — وإلا 404 `NOT_FOUND` بلا تمييز بين "غير موجودة" و"غير مصرَّح بها" (القسم 13، نمط إخفاء الوجود).

الاستجابة — **200 OK**: محتوى الملف الخام (ليس JSON)، بترويسات:

| الترويسة | القيمة |
|---|---|
| `content-type` | `media_type` الفعلي للوسيطة، أو `text/plain; charset=utf-8` إن لم يُصنَّف نوعها |
| `content-length` | حجم الملف بالبايت |
| `content-disposition` | `attachment` باسم مُولَّد وآمن |
| `x-content-type-options` | `nosniff` |
| `content-security-policy` | `default-src 'none'; sandbox` |
| `cache-control` | `private, no-store` |
| `x-scan-status` | حالة فحص الملف ضد البرمجيات الضارة (`CLEAN` أو غيرها) |
| `x-scan-notice` | (فقط إن كانت الحالة غير `CLEAN`) نص تنبيه مُرمَّز بـ`encodeURIComponent` |
| `referrer-policy` | `no-referrer` |

الأخطاء:

| الحالة | code | السبب |
|---|---|---|
| 401 | `UNAUTHENTICATED` | بيانات اعتماد غير صالحة أو مُلغاة |
| 404 | `NOT_FOUND` | `id` ليس UUID صالحًا، أو ليس ضمن اللقطة المنشورة الحالية لهذا الجهاز، أو الوسيطة غير `ACTIVE` |
| 403 | `ATTACHMENT_INFECTED` | الملف مُصنَّف `INFECTED` (مُحجور، لا يُنزَّل أبدًا مهما كان الإعداد) |
| 423 | `ATTACHMENT_NOT_SCANNED` | متغيّر البيئة `ATTACHMENTS_REQUIRE_CLEAN_SCAN=true` والحالة ليست `CLEAN` بعد |
| 410 | `ATTACHMENT_FILE_MISSING` | الملف غير موجود في المخزن رغم وجود سجله |

**ملاحظة توافق:** العقد التصميمي الأصلي (`docs/signage-module.md` §3.2.4) افترض روابط S3 موقَّعة؛ التنفيذ الفعلي الحالي يخدِّم الملف مباشرة من هذا المسار نفسه بلا تخزين كائنات أو روابط موقَّعة منفصلة — هذا الملف يوثّق السلوك الفعلي، لا الافتراض التصميمي.

## 6) `POST /api/v1/tv/device/events`

رفع دفعة من أحداث سجل التشغيل (تنزيل بدأ/انتهى، بدء عرض، خطأ، نبضة داخلية من تطبيق الجهاز).

المصادقة: مطلوبة. الوحدة المعطّلة → 409 `MODULE_DISABLED`.

الطلب — حد أقصى 65536 بايت (64KB)، خاصية واحدة مسموحة:

```json
{
  "events": [
    {
      "type": "DOWNLOAD_START | DOWNLOAD_DONE | PLAY_START | ERROR | HEARTBEAT",
      "occurred_at": "ISO 8601 datetime",
      "confirmed": true,
      "details": { "...": "كائن اختياري، حتى 2000 حرف JSON" }
    }
  ]
}
```

- `events`: مصفوفة من 1 إلى 200 عنصرًا إلزاميًا (فارغة أو أكبر من 200 → 422).
- `type`: إلزامي، أحد القيم الخمس بالضبط (حساس لحالة الأحرف).
- `occurred_at`: نص يُحلَّل بـ`Date.parse` بنجاح (أي صيغة يقبلها `Date.parse`، ويُفضَّل ISO 8601).
- `confirmed`: قيمة منطقية إلزامية — **الجهاز** هو من يضعها، والخادم لا يُعيد تصنيفها أبدًا (لا يرفعها الخادم إلى "مؤكَّد" ولا يخفضها).
- `details`: كائن اختياري (افتراضه `{}`)، يُرفض إن كان غير كائن، أو مصفوفة، أو تجاوز تمثيله كنص JSON 2000 حرف.
- أي خاصية غير معروفة على مستوى الجسم أو على مستوى أي حدث → 422 `VALIDATION_FAILED` تحديدًا باسمها ومسارها (مثل `events[2].foo`).
- ممنوع إرسال حقول هوية (نفس قائمة heartbeat أعلاه) → 422 `IDENTITY_FROM_INPUT_REJECTED`.

الاستجابة — **201 Created**:

```json
{ "accepted": 3 }
```

`accepted` = عدد العناصر في `events` كما أُرسلت (كلها تُقبل معًا أو تُرفض الدفعة كاملة عند أول خطأ تحقق — لا قبول جزئي). كل حدث يُسجَّل مرتبطًا بمعرّف النسخة المنشورة الحالية للجهاز وقت الاستدعاء (`publication_id: null` إن لم توجد نسخة منشورة بعد).

الأخطاء:

| الحالة | code | السبب |
|---|---|---|
| 401 | `UNAUTHENTICATED` | بيانات اعتماد غير صالحة أو مُلغاة |
| 409 | `MODULE_DISABLED` | الوحدة معطّلة |
| 422 | `VALIDATION_FAILED` | أي مخالفة للشكل أعلاه (انظر تفاصيل كل حقل) |
| 422 | `IDENTITY_FROM_INPUT_REJECTED` | حقل هوية في الجسم |
| 415 / 413 / 400 | كما في الأقسام السابقة | نوع المحتوى / حجم الجسم (64KB) / JSON غير صالح |

لا حد معدّل (rate limit) مطبَّق في الكود — لا حد أدنى بين دفعتين، ولا حد على عدد الدفعات في الفترة الزمنية.

## ملخص حدود المعدّل (rate limiting)

لا يوجد أي تطبيق لحدود معدّل (rate limiting) على أي من المسارات الستة في الكود الحالي — لا middleware، ولا فحص في `src/lib/tv/http.ts` أو `src/lib/api.ts`، ولا أي استخدام لمكتبة أو جدول حدود طلبات. القيود الوحيدة القائمة فعليًا هي:

- أحجام الأجسام القصوى لكل مسار (مذكورة أعلاه لكل مسار على حدة).
- صلاحية رمز الاقتران الزمنية (`pairing_code_ttl_seconds`، 300 ثانية افتراضيًا) — تحدّ من عمر محاولة اقتران واحدة، لا من معدّل الطلبات.
- طول `events` الأقصى للدفعة الواحدة (200 عنصرًا) — حد على حجم الدفعة، لا على تكرارها.
