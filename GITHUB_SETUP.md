# إعداد GitHub مرة واحدة

المستودع الثابت هو:

`https://github.com/geot0yq/Potenk`

## ما يلزم لإصدارات Release

Workflow البناء لا يضع مفاتيح داخل الكود. قبل نشر أول Release، أضف أسرار
المستودع التالية في GitHub Settings → Secrets and variables → Actions:

- `ANDROID_KEYSTORE_B64` — محتوى keystore بصيغة Base64
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

استخدم مفتاح التوقيع الأصلي فقط إذا كان متاحًا لديك. إذا لم يكن متاحًا،
استخدم مفتاحًا جديدًا محفوظًا خارج المستودع؛ النسخ الموقعة به لن تُحدّث
النسخة الأصلية الموقعة بمفتاح مختلف.

الصلاحيات المطلوبة لـ `GITHUB_TOKEN` تقتصر على `contents: write` في مهمة
الإصدار، بينما مهام الفحص والبناء تعمل بقراءة فقط.

## طريقة التشغيل

1. ادفع التغييرات إلى `main`.
2. راجع نجاح الاختبارات وLint وبناء Debug.
3. أنشئ GitHub Release بعلامة إصدار جديدة.
4. سيبني Workflow النسختين Release، يفحصهما، ويرفع `Developer.apk` و
   `BuildHost.apk` و`SHA256SUMS.txt` تلقائيًا.

تطبيق Developer يراقب عنوان GitHub Releases الثابت للمستودع العام، ولا يحتاج
رابط بناء جديدًا لكل تحديث.

## البناء التلقائي من Build Host

لبدء البناء مباشرة بعد وصول طلب من Developer، أدخل في شاشة Build Host رمز
GitHub يملك صلاحية تشغيل Actions وقراءة محتويات المستودع. يحفظ التطبيق الرمز
داخل Android Keystore ولا يكتبه في السجل أو ملفات المشروع. يطلق التطبيق
`android.yml` على فرع `main`، وينتظر نفس تشغيل Workflow، ثم ينزّل artifact
ذلك التشغيل فقط قبل فحص APK وتسليمه لمثبّت Android الرسمي.

يفضل استخدام Fine-grained token بصلاحيات `Actions: Read and write` و
`Contents: Read` للمستودع فقط. لا تضع الرمز داخل الكود أو `signing.properties`.