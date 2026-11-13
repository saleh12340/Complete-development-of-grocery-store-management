# بقالة العزي للمواد الغذائية

تطبيق Android Native مستقل لإدارة عمليات البقالة والمبيعات والمشتريات والمخزون والحسابات والمحاسبة التجارية.

## هوية المشروع

- الاسم: بقالة العزي للمواد الغذائية — Al Azzi Grocery Management
- المستودع المعتمد: https://github.com/saleh12340/The-latest-grocery-store-management-app
- الفرع: `main`
- Android namespace / applicationId: `com.saleh.alazzi.grocery`
- الحد الأدنى: Android 8.0 (API 26)
- compileSdk / targetSdk: 36
- Java / Kotlin JVM bytecode target: 17

## التقنيات المعتمدة

- Kotlin وJetpack Compose وMaterial 3 (RTL + Light/Dark Mode + Adaptive Layout)
- MVVM وViewModel وCoroutines وStateFlow/Flow
- Room v2 (`MIGRATION_1_2` الآمنة غير الهدمية) وSQLite
- Navigation Compose
- Android Bluetooth APIs وESC/POS Raster (`384` dots / 58mm و`576` dots / 80mm)
- Android Canvas/Bitmap و`PdfDocument` (دعم التصدير متعدد الصفحات)
- Android Sharesheet و`FileProvider` وتكامل WhatsApp (`+967` بدون تكرار)
- نظام نسخ احتياطي مشفر AES-256-GCM + فحص استباقي للعلاقات قبل الاستعادة

## المزايا والوحدات المكتملة

- **لوحة التحكم الرئيسية**: ملخص فوري لمبيعات ومشتريات اليوم، ديون العملاء، مستحقات الموردين، رصيد الصندوق، تنبيهات المخزون المنخفض، اختصارات سريعة، وأحدث العمليات.
- **حسابات العملاء والموردين المستقلة**: بحث، فرز، فلترة، أرشفة، كشف حساب كامل قابل للضغط لفتح الفواتير، تسجيل دفعات، تسوية رسمية، وتصحيح رصيد بصلاحية المدير مع توثيق السبب.
- **شاشة إدخال الفاتورة الاحترافية**: رقم الفاتورة التالي، طرق الدفع (نقدي/آجل/جزئي)، ترتيب الإدخال (الإجمالي -> الكمية -> الصنف) مع حساب سعر الواحدة تلقائياً، جدول RTL قابل للتعديل والحذف، وإدخال صوتي ذكي عربي (`VoiceInvoiceParser`).
- **إدارة الفواتير والمرتجعات والدفعات**: سجل فواتير مع بحث وفلترة، دفعات متعددة مرتبطة مباشرة بالفاتورة الأصلية (`invoice_payments`)، مرتجعات جزئية محمية، وإلغاء الفواتير بعكس الأثر المالي والمخزني (`VOID`).
- **المخزون والأصناف**: تعديل، أرشفة آمنة دون حذف الفواتير، حدود تنبيه النقص، تصنيفات ووحدات، متوسط التكلفة المرجح، وسجل حركات الصنف.
- **الصندوق النقدي والتقارير**: جرد ومطابقة الصندوق الدفتري مقابل الفعلي (`cash_reconciliations`)، 20 مؤشراً مالياً ومخزنياً بفترات محددة أو مخصصة، الأصناف الأكثر مبيعاً والراكدة، وفحص المطابقة المحاسبية الآلي (`runIntegrityAudit`).
- **الطباعة والتصدير**: معاينة الفاتورة، حفظ/مشاركة PDF وPNG، حفظ الطابعة الافتراضية واختبار الاتصال، وإرسال مباشر عبر WhatsApp.
- **الأمان والصلاحيات والتدقيق**: تشفير PBKDF2-HMAC-SHA256، أدوار (`MANAGER`, `CASHIER`, `LIMITED`)، سجل تدقيق `activity_logs`، وسجل أخطاء وتشخيص محلي `app_error_logs`.

## البناء والتحقق

```bash
gradle :app:testDebugUnitTest
gradle :app:compileDebugAndroidTestKotlin
gradle :app:assembleRelease
```
