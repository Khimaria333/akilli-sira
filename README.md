# Akıllı Sıra

YouTube Music'te bir şarkı açtığında, ona en uygun şarkıyı (BPM, enerji, benzer sanatçı) bulup
şarkı bitince YT Music'e otomatik olarak o şarkıyı çaldıran kişisel Android uygulaması.

## Nasıl çalışır
1. **Dinler:** Bildirim erişimiyle YT Music'te çalan şarkıyı ve kaçıncı saniyede olduğunu okur.
2. **Veri toplar:** Deezer'in ücretsiz API'sinden şarkının BPM'ini ve ses yüksekliğini (enerji) alır.
   Aynı sanatçının radyosu, benzer sanatçılar ve sanatçının popüler şarkılarından ~36 aday toplar.
3. **Puanlar:** BPM uyumu (yarı/çift tempo dahil) en fazla 40, enerji uyumu 25, benzerlik 18,
   popülerlik 8 puan. Aynı sanatçı arka arkaya gelirse ve yakında çalınan şarkılar için ceza var.
4. **Geçer:** Şarkının bitmesine ~1 saniye kala YT Music'e "şunu çal" komutu gönderir.
   YT Music kendi şarkısına geçmişse bunu fark eder ve düzeltir.

"Hava" ayarı: Sakinleştir (biraz daha yavaş/sakin), Aynı kalsın, Hareketlendir (biraz daha hızlı/enerjik).

## APK nasıl elde edilir
Bu klasör GitHub'a yüklendiğinde `.github/workflows/build.yml` APK'yı bulutta otomatik derler
ve deponun **Releases** sayfasına `AkilliSira.apk` olarak koyar.

## Telefona kurulum
1. Releases sayfasından `AkilliSira.apk` dosyasını indir, aç, "bilinmeyen kaynaklara izin ver" de, kur.
2. Uygulamayı aç → **Bildirim erişimini aç** → Akıllı Sıra'yı aç.
   - Anahtar gri ise: uygulamada **Uygulama bilgisi** → sağ üst ⋮ → **Kısıtlanmış ayarlara izin ver** → tekrar dene.
3. YT Music'te bir şarkı aç. Uygulamada "Sıradaki" birkaç saniye içinde dolar.

## Bilinen sınırlar
- YT Music'in resmi bir API'si yok. Geçiş, Android'in standart "aramayla çal" komutuyla yapılıyor;
  YT Music bazen farklı bir sürüm (canlı, cover) açabilir.
- BPM verisi Deezer'den geliyor; bazı şarkılarda boş ya da iki katı olabiliyor.
- "Mood" şimdilik ses yüksekliği + benzer sanatçı üzerinden yaklaşık hesaplanıyor.
- Arka planda çalma için YT Music Premium gerekir.
