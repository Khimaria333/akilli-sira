<p align="center"><img src="docs/ahenk.png" width="128" alt="Ahenk"></p>

# Ahenk

*Şarkıdan şarkıya, ahenkle.*

YouTube Music'te çalan şarkıyı dinler; tarzına, dönemine, temposuna, enerjisine ve ruh haline en uygun
sıradaki şarkıyı seçer ve şarkı bitince sesi yumuşakça kısıp ona geçer. Kişisel bir Android uygulaması.

## Neler yapar
- **Akıllı geçiş:** Önce tarz (benzer sanatçılar, Last.fm etiketleri, tür), sonra dönem, tempo (yarı/çift tempo dahil) ve enerji.
- **Keşfet:** Ruh halini cümleyle anlat ("sporda sert rap", "akşamüstü sahilde sakin bir şeyler"), ruh hali ya da tür seç, ya da ara.
- **Öğrenir ama sen söyleyince:** ❤️ beğeniler tarz profilini oluşturur; 👎 ve "Kötü geçiş" bir daha gelmez. Hızlı geçmelerden bir şey çıkarmaz.
- **Her yerden kontrol:** Kilit ekranı bildirimi, widget, hızlı ayarlar kutucuğu, kısayollar, uyku zamanlayıcı, araba modu, müzikle uyandıran alarm, otomatik ruh hali planı.
- **Koşu modu:** Adım temponu ölçer, adımlarına uyan BPM'de şarkı seçer.
- **Bağlam:** Hava durumuna göre ruh hali, kulaklık takınca uygun liste, arabada yol modu.
- **Hazır listeler:** Günün karışımı, yeni çıkanlar (bildirimli), favoriler, tarzına göre, Ahenk Blend (arkadaşınla ortak liste).
- **YouTube Music'e aktar:** Keşfet listesini YouTube Music'te sıralı liste olarak aç.
- **Geçmiş ve paylaşım:** Dinleme haritası, haftalık enerji, haftalık ve aylık paylaşım kartları.
- **Görünüm:** Koyu ya da AMOLED siyah, beş vurgu rengi (kapak rengi dahil); dört boyutlu widget.
- **Uygulama içi güncelleme:** Yeni sürüm çıkınca tek dokunuşla kurulur.

## Kurulum
1. [Releases](../../releases/latest) sayfasından `Ahenk.apk` dosyasını indir, aç, "bilinmeyen kaynaklara izin ver" de, kur.
2. Uygulamayı aç; kısa tanıtımdan sonra **Bildirim erişimi** ve **Diğer uygulamaların üzerinde göster** izinlerini ver.
   - Bildirim erişimi anahtarı gri ise: **Uygulama bilgisi** → sağ üst ⋮ → **Kısıtlanmış ayarlara izin ver**.
3. YouTube Music'te bir şarkı aç; Ahenk sıradakini birkaç saniye içinde hazırlar.
4. İsteğe bağlı: Ayarlar'dan ücretsiz bir Last.fm API anahtarı bağla; tarz eşleşmesi çok daha isabetli olur.

## Derleme
`.github/workflows/build.yml` her gönderimde APK'yı bulutta derler; `main` dalındaki her sürüm Releases'e `Ahenk.apk` olarak eklenir.

## Bilinen sınırlar
- YouTube Music'in resmi bir API'si yok; geçiş, şarkının YouTube Music bağlantısını açarak yapılır.
- BPM ve enerji verisi Deezer'den gelir; bazı şarkılarda eksik olabilir.
- Arka planda çalma için YouTube Music Premium gerekir.
