# U30P Launcher

ZTE MU5358 (U30 Pro) için, 240×240 yuvarlak ekrana optimize, **UFI-TOOLS bağımlılığı olmayan** hafif launcher.

Yerini aldığı `com.ufitools.dashboard` ile karşılaştırma (cihazda ölçüldü):

| | U30P Launcher | UFI dashboard 1.6.1 |
|---|---|---|
| RAM (PSS) | **20.1 MB** | 75.9 MB |
| APK | **40 KB** | 2.2 MB |
| CPU (boşta) | %0.0 | — |
| Bağımlılık | yok | UFI-TOOLS web arayüzü |

## Durum

**Varsayılan HOME olarak kurulu ve çalışıyor.** adb, `wireless-adb-keeper` modülü nedeniyle **55555** portunda.

## Kullanım

Üç bilgi sayfası yatay kaydırmayla döner:

1. **Durum** — saat, operatör, sinyal + teknoloji + RSRP, ↓↑ hız, pil / VPN / istemci
2. **Detay** — bugün ve bu ay trafik, istemci sayısı, VPN, SoC ve pil sıcaklığı
3. **Mühendislik** — bant, bant genişliği, RSRP/RSRQ/SINR/PCI/EARFCN/TAC/CI

**Uzun bas (600 ms)** → kilit açılır: `Aksiyonlar ↔ Uygulamalar ↔ WiFi ↔ Ayarlar`. 30 sn hareketsizlikte kendiliğinden kilitlenir.

- **Uygulamalar** — kurulu uygulamalar, ikonlarıyla, kaydırmalı
- **WiFi** — SSID, parola ve bağlı istemcilerin IP'leri

Yeniden başlat ve veri kes, kilit açıkken bile **1 sn basılı tutma** ister; dış kenarda dolan halka geri bildirim verir, parmak kalkınca iptal olur.

Sayfa 1'in üç teması var: **Stacked** (varsayılan), **Arc** (çember sinyal göstergesi), **Balanced** (trafik sayaçları ana ekranda).

## Derleme

Gradle ve AGP kullanılmaz. Gerekenler: Android SDK build-tools 35.0.1, `android.jar` API 35, `kotlinc`, bir JDK.

```bash
./build.sh          # build/u30p-launcher.apk
./run-tests.sh      # JVM birim testleri (cihaz gerekmez)
```

## Cihaza kurma

```bash
./dev.sh install    # derler, kurar, izinleri ve Magisk su politikasını verir
./dev.sh start      # ekranı uyandırır, mevcut launcher kilidini açar, başlatır
./dev.sh shot ad    # ekran görüntüsü
./dev.sh cfg --es theme arc     # adb'den ayar
./measure.sh        # performans bütçesini ölçer
```

### Geri dönüş

```bash
# Eski launcher'a dön
adb -s 192.168.0.1:55555 shell cmd package set-home-activity \
    com.ufitools.dashboard/.LauncherActivity
# Keyguard'i geri ac
adb -s 192.168.0.1:55555 shell su -c 'locksettings set-disabled false'
# Magisk modulunu geri al
adb -s 192.168.0.1:55555 shell su -c \
    'cp /data/adb/modules/ufi_default_launcher/service.sh.bak \
        /data/adb/modules/ufi_default_launcher/service.sh'
rm /data/adb/modules/force-u30pro-launcher/disable   # cihazda, root ile
```

Band tuşuna **1 kez** basmak launcher'lar arasında geçiş yapar: yeni → UFI dashboard → ZTE stock → yeni. Bu, ekran hiç açılmasa bile çalışan fiziksel geri dönüş yoludur.

## Cihaza özgü iki tuzak

**Logcat kapalı** (`persist.sys.ztelog.enable=0`) — yazılan log geri okunamaz. Çökme izleri `filesDir/crash.log` dosyasına yazılır.

**Konum servisi kapalı** — Android hücre kimliğini izinlerden bağımsız maskeler. Operatör adı `TelephonyManager`'dan, hücre kimliği root ile `dumpsys`'ten okunur; sistem ayarı değiştirilmez.

**adb düşerse:** `./dev.sh revive` — cihazın `ttyd` web terminali (port 1146) üzerinden `adbd`'yi geri başlatır. Bu kanal adbd'ye bağlı olmadığı için emniyet ağıdır.

Bu oturumda `adbd` iki kez kendiliğinden durdu (cihaz yeniden başlamadan). `wireless-adb-keeper` bunu yakalamıyor: döngüsü yalnızca `service.adb.tcp.port` değerine bakıyor, port zaten doğruyken `init.svc.adbd=stopped` durumunu görmüyor. Modüle şu koşulun eklenmesi boşluğu kapatır:

```sh
[ "$(getprop init.svc.adbd)" = "running" ] || setprop ctl.start adbd
```

## Belgeler

- Tasarım: `docs/superpowers/specs/2026-07-29-u30p-launcher-design.md`
- Uygulama planı: `docs/superpowers/plans/2026-07-29-u30p-launcher.md`
