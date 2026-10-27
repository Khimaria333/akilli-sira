# Ahenk Bulut (Spotify · iPhone dahil her cihaz)

Ahenk'in Spotify için bulutta çalışan sürümü. Cloudflare'de küçük bir servis her dakika Spotify'ına
"ne çalıyor?" diye sorar; yeni şarkı başlayınca sıradakini seçer (tarz, dönem, tempo, enerji, ruh hali)
ve şarkının son dakikasında **Spotify'ın kendi sırasına** ekler. Telefonun kilitliyken, uygulama kapalıyken
çalışır; iPhone, Android, bilgisayar ya da hoparlör fark etmez. Ayarlar ve durum için ana ekrana eklenebilen
bir web uygulaması vardır.

## Kurulum (bir kere)

1. **Cloudflare** (ücretsiz): https://dash.cloudflare.com/sign-up → kaydol.
   Sol menüden **Workers & Pages**'i bir kez aç, `*.workers.dev` alt alan adını seç.
2. **API anahtarı:** Profil → My Profile → API Tokens → Create Token → Create Custom Token. İzinler:
   - Account · Workers Scripts · Edit
   - Account · D1 · Edit
   - Account · Account Settings · Read
3. **Account ID:** Cloudflare ana sayfasında sağda yazar.
4. **GitHub:** depo → Settings → Secrets and variables → Actions → New repository secret:
   - `CLOUDFLARE_API_TOKEN`
   - `CLOUDFLARE_ACCOUNT_ID`
   - (isteğe bağlı) `LASTFM_KEY`: herkes için tarz etiketleri
5. **Actions → "Ahenk bulut (Cloudflare)" → Run workflow.** Bitince özet sayfasında adres yazar
   (ör. `https://ahenk.seninadin.workers.dev`).

## Kullanım

1. Adresi telefonda aç (iPhone'da Safari → Paylaş → **Ana Ekrana Ekle**).
2. Ekrandaki adımlarla Spotify geliştirici panelinde kendi uygulamanı aç; Redirect URI olarak
   `https://ADRESIN/callback` ekle, Client ID'yi yapıştır, **Spotify ile bağlan**.
3. Spotify'da müzik aç. Gerisi otomatik.

Arkadaşların aynı adresi kullanabilir: ya kendi Client ID'lerini alırlar ya da sen panelde
**User Management**'a Spotify e-postalarını eklersin (Spotify kuralı: Client ID başına en fazla 5 kişi).

## Spotify kuralları (2026)

- Geliştirici hesabı ve sıraya ekleme için **Spotify Premium** gerekir.
- Bir Client ID'ye en fazla **5 kullanıcı** bağlanabilir.
- Ses analizi (BPM vb.) Spotify'da kapalı; Ahenk bunları Deezer ve Last.fm'den alır.

## Teknik

- `src/worker.js`: API + dakikalık görev (cron). `src/engine.js`: seçim motoru. `src/data.js`: ruh hali / tür / cümle tabloları.
- Veritabanı: Cloudflare D1 (`schema.sql`). Önbellek toplu okunur/yazılır; ücretsiz planın çağrı başına istek sınırlarına uyar.
- `public/`: web uygulaması (PWA).
