# Simple WG VPN

تطبيق Android بسيط لـ WireGuard VPN، مبني بـ Kotlin + Jetpack Compose، كيستعمل المكتبة
الرسمية ديال WireGuard (`com.wireguard.android:tunnel`) — نفس المكتبة اللي كتستعملها
تطبيقات WireGuard الرسمية.

هاد التطبيق ما فيهش أي سيرفر مدمج. خاصك دير كونفيغ (.conf) من:
- سيرفر WireGuard ديالك شخصيا (VPS فيه `wg-quick` معمر)، أو
- مزود VPN قانوني كيدعم WireGuard ويعطيك ملف .conf (بحال Mullvad).

## شنو كيدير

- كتلصق أو كتستورد محتوى ملف `.conf` بصيغة wg-quick العادية (`[Interface]` / `[Peer]`).
- كتكبس "حفظ" باش يتسجل محليا فداخل التطبيق فقط (ماشي مشارك مع تطبيقات أخرى).
- كتكبس "اتصل"، كيطلب منك Android الإذن ديال VPN مرة وحدة، ومنبعد كيبدا التونيل.

## كيفاش تبنيه (Build APK)

1. حمّل **Android Studio** (مجاني): https://developer.android.com/studio
2. حل المجلد `SimpleWireGuardVPN` بـ Android Studio (`File > Open`).
3. خلي Android Studio يدير Gradle sync (كيوقع أوتوماتيك، غادي يحمل المكتبات، خاصك
   أنترنت فهاد الخطوة).
4. من القائمة: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
5. من بعد ما يسالي، غادي تبان رسالة فيها رابط `locate` — كيوديك للملف
   `app/build/outputs/apk/debug/app-debug.apk`. هادو هو الـ APK ديالك.
6. نقلو للهاتف وثبتو (خاصك تسمح بـ "تثبيت من مصادر غير معروفة" أول مرة).

> ملاحظة: إيلا بغيتي APK موقّع (signed) باش تنشره رسميا، خاصك تدير keystore ديالك
> وتبدل `release` build type — Android Studio فيه خطوات ديال هادشي فـ
> `Build > Generate Signed Bundle / APK`.

## كيفاش تجيب كونفيغ WireGuard

### الخيار 1: سيرفر ديالك (VPS)
1. شري VPS صغير (DigitalOcean, Hetzner, OVH...).
2. ثبت WireGuard عليه (`apt install wireguard` فـ Ubuntu/Debian).
3. دير `wg genkey` باش تولد المفاتيح، وصيفط ملف `/etc/wireguard/wg0.conf` فالسيرفر
   و config ديال العميل (اللي غادي تلصقو فالتطبيق).
4. فتح السيرفر الـ port (عادة UDP 51820) فالـ firewall.
5. شغل `wg-quick up wg0` فالسيرفر.

كاين أدلة كاملة بزاف فالويب على "wireguard server setup ubuntu" إيلا بغيتي تفاصيل أكثر.

### الخيار 2: مزود VPN جاهز
شي خدمات بحال Mullvad كتعطيك ملف `.conf` جاهز من الموقع ديالها مباشرة بعد
الاشتراك — غير تحمل الملف وتلصق المحتوى ديالو فالتطبيق.

## الهيكلة ديال الكود

```
app/src/main/java/com/example/simplewg/
  MainActivity.kt      ← الواجهة (Compose UI)
  TunnelManager.kt      ← التعامل مع مكتبة WireGuard (حفظ، اتصال، قطع)
```

## ملاحظة أمنية

- PrivateKey ديال الكونفيغ كيتسجل فـ internal storage ديال التطبيق (محمي من طرف
  Android، ماشي قارئ من تطبيقات أخرى)، بصح ماشي مشفر إضافيا. إيلا بغيتي حماية
  أكثر، زيد `androidx.security:security-crypto` وبدل `TunnelManager` باش
  يستعمل `EncryptedFile`.
