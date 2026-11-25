# Edge Back
سحب من حافة الشاشة (يمين/يسار) = زر رجوع. مع تحديث تلقائي من GitHub Releases.

## الإعداد
1. في `app/build.gradle.kts` غيّر `OWNER/REPO` إلى اسم حسابك/اسم المستودع.
2. سوّ keystore مرة وحدة (مهم! نفس المفتاح لكل التحديثات):
   keytool -genkeypair -v -keystore release.jks -alias edge -keyalg RSA -keysize 2048 -validity 10000
3. حوّله base64: `base64 -w0 release.jks`
4. في GitHub → Settings → Secrets → Actions أضف:
   KEYSTORE_BASE64 , KS_PASS , KEY_ALIAS , KEY_PASS
5. اصدار نسخة: 
   git tag v1.0.0 && git push origin v1.0.0
   (كل tag جديد يبني APK وينزله في Releases، والتطبيق يكتشفه تلقائياً)
