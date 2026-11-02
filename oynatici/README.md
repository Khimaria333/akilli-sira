# Ahenk Oynatıcı (iPhone + Android)

Ahenk'in seçim motoruyla çalışan, **YouTube'un resmi gömülü oynatıcısını** kullanan ayrı sürüm.
Oynatıcı Ahenk'in içinde olduğu için sıradaki şarkıyı Ahenk kendisi çalar: iPhone'da da YouTube müziğiyle
tam otomatik akış olur. Spotify, Premium ya da hesap bağlamak gerekmez.

Mevcut Ahenk'ten (Android uygulaması ve `cloud/` Spotify bulutu) **tamamen ayrıdır**: ayrı Worker
(`ahenk-oynatici`), ayrı veritabanı, ayrı adres. Seçim motoru `cloud/src/` ile ortaktır.

## Kurulum

1. Google Cloud'da **YouTube Data API v3**'ü aç, bir **API key** oluştur (ücretsiz).
2. GitHub → Settings → Secrets and variables → Actions → `YOUTUBE_API_KEY` olarak ekle.
3. `oynatici` dalına her push'ta **Ahenk Oynatıcı (Cloudflare)** akışı yayınlar; adres özet sayfasında yazar
   (ör. `https://ahenk-oynatici.KULLANICI.workers.dev`).
4. Adresi telefonda aç → ana ekrana ekle (iPhone: Safari → Paylaş → Ana Ekrana Ekle; Android: Chrome ⋮ → Ana ekrana ekle).

## YouTube kuralları (neden böyle)

- Video kutusu her zaman görünür ve en az 200×200 pikseldir.
- Uygulamadan çıkınca ya da ekran kapanınca müzik durur (arka planda çalma YouTube API kurallarına aykırı).
  "Ekran açık kalsın" seçeneği çalarken telefonun kendiliğinden kilitlenmesini önler.
- Sesi ayırma, indirme, reklam engelleme yoktur.

## Kota

YouTube Data API'nin varsayılan kotası günde ~100 aramadır. Her şarkının eşleşmesi D1'de saklanır;
bir şarkı tüm kullanıcılar için yalnızca bir kez aranır. Kota dolarsa daha önce eşleşmiş şarkılar çalmaya
devam eder. Gerekirse Google'dan ücretsiz kota artışı istenebilir.

## Teknik

- `src/worker.js`: `/api/next` (sıradaki + YouTube videosu), `/api/yt`, `/api/search`, `/api/meta`. Durumsuz.
- `src/youtube.js`: YouTube Data API araması ve eşleşme puanı (Topic / resmi kanal öne, cover / remix / canlı geri).
- `public/`: web uygulaması (PWA). Dinleme geçmişi ve ayarlar telefonda (localStorage).
- `test/api.test.mjs`: `node --experimental-sqlite oynatici/test/api.test.mjs`
