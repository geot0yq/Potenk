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