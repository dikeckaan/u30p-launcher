# U30P Launcher — Tasarım Dokümanı

**Tarih:** 2026-07-29
**Cihaz:** ZTE MU5358 (U30 Pro) — Android 15 / SDK 35, arm64-v8a, 240×240 px @ density 160, 30 Hz, root (Magisk)
**Paket:** `com.kaandikec.u30plauncher`

## 1. Amaç

`com.ufitools.dashboard` launcher'ının yerini alacak, **UFI-TOOLS bağımlılığı olmayan**, yuvarlak 240×240 ekrana optimize, sade ve düşük kaynak tüketen bir launcher.

Birincil öncelik sırası:

1. **Hafiflik** — CPU ve RAM tüketimi ölçülebilir şekilde minimum
2. **Kullanım kolaylığı** — tek bakışta okunan bilgi, az sayıda net etkileşim
3. Bağımsızlık — kurulum, ayar ve veri için hiçbir dış uygulamaya ihtiyaç duymamak

## 2. Mevcut durumun analizi

### 2.1 Yerini alacağımız sistem

`U30Pro高级桌面 (1).js`, UFI-TOOLS web arayüzüne yüklenen bir eklenti. Yaptıkları:

- APK'yı `pan.kanokano.cn`'den indirip `pm install -r -d` + `cmd package set-home-activity`
- Ayarları `content://com.ufitools.dashboard.localapi/settings` provider'ı üzerinden okuyup yazmak (şifre, yenileme aralığı, `traffic_source`, statik/dinamik duvar kağıdı)
- `sha256sum` doğrulamalı otomatik güncelleme

Bağımlılık iki katmanda: kurulum/ayar yönetimi UFI-TOOLS web arayüzünden yapılıyor, ve launcher veriyi `traffic_source=kano` ile UFI-TOOLS'tan alıyor.

### 2.2 Veri kaynaklarının doğrulanması

Mevcut dashboard'ın gösterdiği her alanın stock Android API'lerinde mevcut olduğu cihazda doğrulandı:

| Alan | Kaynak | Doğrulanan değer |
|---|---|---|
| Operatör, teknoloji | `ServiceState` | `KAANCELL`, LTE |
| Bant, EARFCN, bant genişliği | `ServiceState` / `CellIdentityLte` | `mBands=[3]`, `mChannelNumber=1279`, `mCellBandwidths=[15000]` |
| PCI, TAC, CI | `CellIdentityLte` | `mPci=344 mTac=21054 mCi=88103723` |
| RSRP / RSRQ / SINR | `SignalStrength` | `-100` |
| Trafik (WAN / LAN / VPN) | `/proc/net/dev` | `sipa_eth0`, `br0`, `tun0` |
| Batarya (%, mA, °C) | `BatteryManager` | `%20`, `-565 mA`, `35.0 °C` |
| VPN durumu | `tun0` UP + trafik | `10.10.10.1/30`, aktif |
| İstemci sayısı | `/proc/net/arp` (root) | 1 istemci (`192.168.0.169` on `br0`) |

Ayrıca cihazda **80 portunda web arayüzü yok** (`1146` = ttyd, `8080`/`8443` = UFI-TOOLS). HTTP/goform yolu zaten kapalı; native Android API tek uygulanabilir yol.

### 2.3 Cihaza özgü iki engel

**Konum servisi kapalı.** `location_mode=0`. Android, konum servisi kapalıyken `ServiceState` içindeki hücre kimliğini ve operatör adını **izinler verilmiş olsa bile** maskeler. İki sonucu var:

- Operatör adı `TelephonyManager.networkOperatorName`'den okunur — bu alan konuma bağlı değildir.
- PCI / EARFCN / TAC / CI / bant, root ile `dumpsys telephony.registry` çıktısından `CellIdentityParser` ile okunur. Kullanıcının sistem ayarını değiştirmeye gerek kalmaz. API yolu değer döndürdüğünde o tercih edilir; fallback yalnızca maskeleme varken devreye girer.

**Logcat kapalı.** `persist.sys.ztelog.enable=0`; `log -t TEST` ile yazılan bir satır bile geri okunamıyor (`logcat -g` → "0 B readable"). Bu yüzden hata ayıklama logcat'e güvenemez: `CrashGuard` yığın izini `filesDir/crash.log` dosyasına yazar ve teşhis oradan yapılır.

### 2.4 Ekran

`ro.config.window_is_round` **set edilmemiş** — Android ekranı yuvarlak saymıyor ve dairesel maske uygulamıyor. Köşe güvenli alanı elle yönetilecek.

Yararlı geometri (merkez 120,120 / yarıçap 120): y=±60'ta kullanılabilir genişlik 208 px, y=±80'de 179 px, y=±95'te 147 px. Üst ve alt ~20 px şeritler dar ama saat gibi kısa metinler için yeterli.

### 2.5 Magisk modülü çakışması

İki modül her boot'ta HOME'u zorla `com.ufitools.dashboard`'a sabitliyor:

**`force-u30pro-launcher`** — ZTE stock launcher'ı `pm disable-user` ile kapatıyor, `set-home-activity` yapıyor, 30 sn boyunca 10 kez `am start` ile öne zorluyor.

**`ufi_default_launcher`** (v2.0) — `cmd role add-role-holder android.app.role.HOME`, 4 kez `set-home-activity` + `am start`. Ayrıca band tuşu daemon'u: 1 tık = launcher toggle (yalnızca UFI ↔ ZTE), 3 tık = DPI toggle, 6 tık = UFI Button app.

Sonuç: `set-home-activity` ile ayarlansa bile yeni launcher **sonraki boot'ta ilk 30-40 sn içinde ~14 kez ezilir**.

Ayrıca `force-u30pro-launcher` ZTE stock launcher'ı devre dışı bıraktığı için, UFI dashboard kaldırılırsa cihazda hiç HOME kalmaz.

**Karar:** Faz 1'de modüllere hiç dokunulmaz; Faz 2'de `ufi_default_launcher` bize uyarlanır ve `force-u30pro-launcher` kaldırılır (bkz. §8).

**Faz 2'de öğrenilen:** `force-u30pro-launcher`'ın `pm disable-user com.zte.mifavor.ufi.home` satırı **zorunludur**. Modülü devre dışı bırakırken bu satır taşınmadığında, ZTE stock launcher boot'ta kendini öne çekip yeni launcher'ı eziyor — orijinal script'in yorumu da bunu söylüyordu. Satır `ufi_default_launcher`'a taşındı; band tuşuyla ZTE'ye geçilirken paket tekrar etkinleştiriliyor.

**Cihazda ayrı bir keyguard var.** Operatör, teknoloji ve anlık hızı gösterip "uzun basıp aç" diyen ekran bir launcher değil, ZTE keyguard'ıdır (`isKeyguardShowing=true`). Launcher'ın penceresi onun arkasında `noSurface` durumunda kalır. İçeriği Sayfa 1 ile birebir çakıştığı ve her uyandırmada fazladan bir uzun basma dayattığı için kapatıldı:

```
adb shell su -c 'locksettings set-disabled true'     # geri al: false
```

PIN veya şifre tanımlı olmadığı (`locksettings verify` boş kimlikle geçiyor) için bu bir güvenlik zayıflaması değil.

## 3. Mimari

Kotlin · minSdk 33 · compileSdk 35 · **hiçbir bağımlılık yok** (AndroidX dahil değil) · Gradle'sız manuel derleme.

### 3.1 Modüller

| Modül | Sorumluluk | Bağımlılık |
|---|---|---|
| `Snapshot` | Ekranda gösterilen her şeyi tutan immutable veri sınıfı. Tek doğruluk kaynağı. | — |
| `DataHub` | Kaynakları toplar, `Snapshot` üretir, değiştiğinde dinleyiciyi uyarır. | kaynaklar |
| `TelephonySource` | `TelephonyCallback` — operatör, teknoloji, bant, RSRP/RSRQ/SINR, PCI/EARFCN/TAC/CI | push |
| `BatterySource` | `ACTION_BATTERY_CHANGED` — yüzde, akım, sıcaklık | push |
| `NetSource` | `/proc/net/dev` byte seviyesinde parse — WAN/LAN/VPN hız ve sayaçları | poll |
| `RootShell` | Tek kalıcı `su` process; reboot, uçak modu, `screen_off_timeout`, ARP | lazy |
| `UsageStore` | Bugün/bu ay trafik; gün ve ay devri, reboot sonrası devamlılık | SharedPrefs |
| `Prefs` | Tema, yenileme aralığı, açık sayfalar | SharedPrefs |
| `PageView` (soyut) | `onDraw(canvas, snapshot)` — sayfaların ortak arayüzü | Canvas |
| `StackedTheme` / `ArcTheme` / `BalancedTheme` | Aynı `Snapshot`'ı farklı çizen üç strateji | PageView |

**Sınır netliği:** Kaynaklar `DataHub`'ı, `DataHub` temaları tanımaz. Tema yalnızca `Snapshot` alıp çizer. Yeni tema = tek dosya; yeni veri alanı = tek satır.

### 3.2 Veri akışı

```
TelephonyCallback ─┐
BATTERY_CHANGED   ─┼─→ DataHub ─→ Snapshot ─→ (değişti mi?) ─→ invalidate()
/proc/net/dev poll ┘                              │
                                                  └─ değişmediyse: hiçbir şey yapma
RootShell ─(5 sn kadans)─→ istemci sayısı
```

Telefon ve batarya **push**, yalnızca trafik sayaçları **poll**. "Yenileme aralığı" ayarı sadece `/proc/net/dev` okuma sıklığını etkiler; sinyal ve batarya olay geldiğinde güncellenir.

`onPause` → tüm callback'ler kaldırılır, poll durur, su shell kapanır. Ekran kapalıyken uygulama hiçbir şey yapmaz.

## 4. Performans bütçesi

Bunlar kabul kriteridir, sonradan yapılacak optimizasyon değil.

| Metrik | Hedef | Ölçülen | Sonuç |
|---|---|---|---|
| RAM (PSS, ekran açık) | < 20 MB | **19.8 MB** | ✓ |
| RAM (PSS, ekran kapalı) | < 12 MB | **27.2 MB** | ✗ hedef gerçekçi değildi, aşağıya bakınız |
| CPU (ekran açık, boşta) | < %1 | **%0.0** | ✓ `top` çözünürlüğünün altında |
| CPU (ekran kapalı) | %0.0 | **%0.0** | ✓ |
| Soğuk açılış | < 250 ms | **360 ms** | ✗ 1.4× aşıldı |
| APK boyutu | < 150 KB | **48 KB** | ✓ hedefin 3 katı altında |

Ölçüm: `./measure.sh` (cihazda, 2026-07-29; HOME olarak çalışırken, uygulama listesi ve WiFi sayfası dahil).

### Mevcut launcher ile karşılaştırma

Asıl anlamlı ölçü, yerini aldığımız uygulama:

| | U30P Launcher | `com.ufitools.dashboard` 1.6.1 | Fark |
|---|---|---|---|
| RAM (PSS, çalışırken) | 19.8 MB | 75.9 MB | **3.8× daha az** |
| RSS | 91 MB | 149 MB | 1.6× daha az |
| APK | 48 KB | 2.2 MB | **45× daha küçük** |

### `hardwareAccelerated` kararı ölçümle sabitlendi

Aynı APK, yalnızca manifest bayrağı değiştirilerek:

| | `false` | `true` | Fark |
|---|---|---|---|
| RAM (PSS, ekran açık) | 20.1 MB | 47.2 MB | **27 MB fazla** |
| RAM (PSS, ekran kapalı) | 27.8 MB | 55.1 MB | 27 MB fazla |
| RSS | 91 MB | 120 MB | 29 MB fazla |
| Soğuk açılış | 360 ms | 690 ms | **1.9× yavaş** |

240×240 düz renkli bir arayüzde GPU bağlamının bedeli budur. `false` sabitlendi.

### Tutturulamayan iki hedef

**Ekran kapalıyken 12 MB:** Ölçülen 27.8 MB. Polling gerçekten duruyor (CPU %0.0) ama Activity yalnızca *paused*, *destroyed* değil — pencere yüzeyi ve tema kaynakları süreçte kalıyor. 12 MB hedefi, sürecin tamamen boşaltıldığı varsayımına dayanıyordu; bir HOME uygulaması için bu doğru değil. Gerçekçi hedef: **< 30 MB**, ve asıl kazanç CPU'nun tam sıfırlanması.

**Soğuk açılış 250 ms:** Ölçülen 360 ms. Bunun büyük kısmı süreç başlatma (zygote fork + sınıf yükleme); uygulama kodunun payı küçük. Gerçekçi hedef: **< 400 ms**. Karşılaştırma için mevcut launcher aynı testte ölçülmedi çünkü HOME olarak zaten sürekli ayakta.

### Bunu sağlayan yedi karar

1. **`android:hardwareAccelerated="false"`** — 240×240 düz renkli arayüzde GL context ve texture atlas israf. İki mod da ölçülüp veriye göre sabitlenecek.
2. **`onDraw` içinde sıfır allocation** — `Paint`, `Rect`, `StringBuilder`, `char[]` önceden ayrılır. `String.format` yok; elle sayı formatlayıcı.
3. **Değişmediyse çizme** — yeni `Snapshot` öncekine eşitse `invalidate()` çağrılmaz.
4. **`/proc/net/dev` byte seviyesinde parse** — yeniden kullanılan `ByteArray`; `String`/`split`/`Regex` yok.
5. **Ekran kapalıyken tam sessizlik** — manifest'te periyodik `BroadcastReceiver`, `Service` veya `JobScheduler` yok.
6. **Root'a cimri erişim** — su shell tembel açılır, 30 sn boşta kapanır. İstemci sayısı 5 sn'de bir okunur.
7. **Yenileme aralığı ayarı** — 0.5 / 1 / 2 / 5 sn, varsayılan 1 sn.

## 5. Arayüz

### 5.1 Sayfalar

Üç bilgi sayfası yatay kaydırmayla döner, altta nokta göstergesi. Kilit yok — bilgi her zaman erişilebilir.

**Sayfa 1 — Durum** (tema seçilebilir, varsayılan Stacked)
Saat · operatör · sinyal çubuğu + teknoloji + RSRP · ↓ ve ↑ hız (38 dp) · alt şerit: batarya, VPN kalkanı (yalnızca bağlıyken), istemci sayısı (yalnızca >0), kilit ipucu.

**Sayfa 2 — Detay**
Bugün / bu ay trafik · istemci sayısı / VPN durumu · CPU / pil sıcaklığı.

**Sayfa 3 — Mühendislik**
Başlık: teknoloji + bant + bant genişliği. RSRP / RSRQ · SINR / PCI · EARFCN / TAC · CI.

### 5.2 Temalar

Üçü de yalnızca Sayfa 1'i etkiler, aynı `Snapshot`'ı farklı çizer:

- **Stacked** (varsayılan) — süssüz, 5 satır, en büyük tipografi, en düşük çizim maliyeti
- **Arc** — çember sinyal göstergesi, hero olarak indirme hızı (52 dp)
- **Balanced** — 8 alan, trafik sayaçları ana ekranda

### 5.3 Kilit ve aksiyonlar

**Uzun bas (600 ms)** → kilit açılır, aksiyon katmanı gelir: `Aksiyonlar ↔ Ayarlar`. **30 sn hareketsizlikte** otomatik kilitlenir ve Sayfa 1'e döner.

**Çift koruma:** Yıkıcı aksiyonlar kilit açıkken bile **1 sn basılı tutma** ister; dış kenarda (r≈116) dolan halka geri bildirim verir, bırakınca iptal.

| Aksiyon | Etkileşim | Uygulama |
|---|---|---|
| Yeniden başlat | 1 sn basılı tut | `reboot` (root) |
| Veri kes / aç | 1 sn basılı tut | `cmd connectivity airplane-mode enable\|disable` (root) |
| Ekran süresi | tek dokunuş, değerler arasında döner | `settings put system screen_off_timeout` (root) — 15 sn / 1 dk / 5 dk / hiç |

Cihazda `svc data` ve `svc wifi` **yok**; veri kesmenin tek temiz yolu uçak modu. WiFi AP kapatma **bilinçli olarak kapsam dışı**: adb bağlantısı AP üzerinden geliyor, kapatmak hem interneti hem uzaktan erişimi keser.

## 6. Ayarlar

İki giriş noktası, tek kaynak (`SharedPreferences`); uygulama `onResume`'da okur.

**Cihazda:** Tema (Stacked / Arc / Balanced), Yenileme (0.5 / 1 / 2 / 5 sn), Detay sayfası aç-kapa, Mühendislik sayfası aç-kapa.

**adb'den:** manifest'te tanımlı tek receiver, özel action ile, `android:permission="android.permission.DUMP"` korumalı — shell ve root bu izne sahip, normal uygulamalar değil. Tanımlı receiver tetiklenmedikçe sıfır maliyetlidir.

**Bileşen açıkça verilmeli.** Android 8+ implicit broadcast'lerin manifest'te tanımlı bir receiver'ı uyandırmasını engeller; `-a` tek başına sessizce başarısız olur (`result=0` döner ama `onReceive` çalışmaz).

```
adb shell am broadcast -n com.kaandikec.u30plauncher/.CfgReceiver \
    -a com.kaandikec.u30plauncher.CFG --es theme arc
adb shell am broadcast -n com.kaandikec.u30plauncher/.CfgReceiver \
    -a com.kaandikec.u30plauncher.CFG --ei refresh_ms 2000
```

Kısayol: `./dev.sh cfg --es theme arc`

## 7. Hata yönetimi

Bir launcher çökerse cihaz kullanılamaz hale gelir. Bu yüzden:

- Global `UncaughtExceptionHandler`: hatayı diske yazar, süreci öldürmek yerine Activity'yi yeniden başlatır
- `onDraw` ve veri toplama try/catch içinde; bir kaynak patlarsa o alan `—` gösterir, diğerleri çalışır
- **Root yok:** bilgi sayfaları normal çalışır, istemci sayısı gizlenir, aksiyon satırları sönük ve dokunulamaz (sebep yazılı)
- **İzin yok:** sinyal alanları `—`; Ayarlar sayfasında eksik izin ve düzeltme komutu gösterilir
- **SIM yok / servis yok:** operatör yerine "SIM yok", hız 0

## 8. Dağıtım

### Faz 1 — HOME'a dokunmadan test

Magisk modülleri **değiştirilmez**, `set-home-activity` **çağrılmaz**. Uygulama normal bir uygulama gibi açılır; geri dönüş HOME tuşudur.

```
adb install -r u30p-launcher.apk
adb shell pm grant com.kaandikec.u30plauncher android.permission.READ_PHONE_STATE
adb shell pm grant com.kaandikec.u30plauncher android.permission.ACCESS_FINE_LOCATION
adb shell am start -n com.kaandikec.u30plauncher/.LauncherActivity
```

### Faz 2 — kalıcı hale getirme (launcher kanıtlandıktan sonra)

`ufi_default_launcher/service.sh` içindeki sabit bizim pakete çevrilir ve `launcher_toggle` üç yönlü döngüye genişletilir (yeni → UFI → ZTE). `force-u30pro-launcher` kaldırılır; ZTE stock launcher'ı kapatan tek satırı `ufi_default_launcher`'a taşınır. Geri dönüş: eski `service.sh`'in yerine konması.

**Eski launcher hiçbir zaman silinmez.** Geri dönüş her zaman tek komut:

```
adb shell cmd package set-home-activity com.ufitools.dashboard/.LauncherActivity
```

adb bağlantısı WiFi AP üzerinden geldiği ve AP'ye dokunulmadığı için bu yol her zaman açık kalır.

### Emniyet ağı

`wireless-adb-keeper` v1.0.1 Magisk modülü kuruldu (`/data/adb/modules_update/`). Sonraki reboot'ta etkinleşir ve adb'yi **55555** portuna sabitler. Reboot sonrası bağlantı: `adb connect 192.168.0.1:55555`.

## 9. Derleme

Gradle ve AGP kullanılmaz — makinede JDK 25 var, AGP 8.x JDK 17-21 destekliyor; ayrıca Gradle ağ indirmesi gerektiriyor. Bunun yerine doğrudan SDK araçlarıyla derleme:

```
aapt2 compile → aapt2 link (manifest + minimal kaynak) → kotlinc → d8 → zip → zipalign → apksigner
```

Bu, hem toolchain riskini sıfırlar hem de en küçük APK'yı üretir. Araçlar doğrulandı: aapt2 2.19, d8 8.6.2, apksigner 0.9, `android.jar` API 35 — hepsi JDK 25 üzerinde çalışıyor.

## 10. Test

**JVM birim testleri** (Android bağımlılığı olmayan saf fonksiyonlar — parse ve format mantığı bu yüzden ayrılıyor):

- `/proc/net/dev` parse (eksik arayüz, bozuk satır, sayaç sarması)
- Sayı formatlama (B/KB/MB/GB, hız birimleri, negatif ve sıfır)
- `UsageStore` gün ve ay devri, reboot sonrası sayaç devamlılığı
- `Snapshot` eşitliği — yanlış `invalidate` atlaması olmaması

**Cihaz testleri:**

- Her sayfanın screenshot'ı mockup ile karşılaştırılır
- §4 bütçe tablosu `dumpsys meminfo` / `top` / `am start -W` ile ölçülür ve sonuçlar bu dokümana yazılır
- Root yok / izin yok senaryoları izinler geri alınarak doğrulanır

## 11. Kapsam dışı

Bilinçli olarak dahil edilmeyenler:

- **Duvar kağıdı (statik / GIF / video)** — 240×240'ta bile sürekli CPU ve pil tüketir; hafiflik hedefiyle doğrudan çelişir
- **Web arayüzü / WebView** — tek başına 40-60 MB RAM
- **Uygulama çekmecesi** — istenmedi; cihazda uygulama açma ihtiyacı band tuşu modülüyle karşılanıyor
- **Otomatik güncelleme** — dağıtım adb üzerinden yapılıyor
- **Şifre / PIN** — basit kilit yeterli görüldü
- **WiFi AP açma-kapama** — adb erişimini kesme riski
