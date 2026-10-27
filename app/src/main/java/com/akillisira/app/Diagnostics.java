package com.akillisira.app;

import java.util.ArrayList;
import java.util.List;

/** Sağlık kontrolü: servisler, izinler ve son hatalar tek bakışta. */
public final class Diagnostics {
    private Diagnostics() {}

    public static final class Item {
        public final boolean ok;
        public final boolean warnOnly;
        public final String title, detail;

        Item(boolean ok, boolean warnOnly, String title, String detail) {
            this.ok = ok; this.warnOnly = warnOnly; this.title = title; this.detail = detail;
        }
    }

    /** Arka planda çalıştırır, sonuçları ana iş parçacığında verir. */
    public static void run(MainActivity a, Engine e, java.util.function.Consumer<List<Item>> done) {
        // İzinler ana iş parçacığında okunur
        final boolean notifAccess = a.hasNotificationAccess();
        final boolean overlay = a.canOverlay();
        final boolean notifs = a.notificationsAllowed();
        final boolean battery = a.batteryUnrestricted();
        final boolean ytInstalled = a.getPackageManager().getLaunchIntentForPackage(Engine.YTM) != null;
        final boolean spInstalled = a.getPackageManager().getLaunchIntentForPackage(Engine.SPOTIFY) != null;
        final boolean spotifyMode = e.isSpotify();
        final boolean spConnected = e.spotify.connected();
        final boolean spBlocked = e.spotify.playerBlocked();
        final boolean ytConnected = e.controller != null;
        final boolean wakeOn = WakeScheduler.isOn(a);
        final boolean exact = WakeScheduler.exactAllowed(a);
        final boolean lfmOn = e.lastfm.enabled();
        final String lfmKey = e.getLastFmKey();
        final List<String> log = new ArrayList<>(e.log);
        final int cache = DiskCache.totalEntries();

        new Thread(() -> {
            List<Item> out = new ArrayList<>();
            // Deezer
            long t0 = System.currentTimeMillis();
            boolean dz;
            try {
                dz = !new Deezer().search("Tarkan", 1).isEmpty();
            } catch (Exception ex) {
                dz = false;
            }
            long dzMs = System.currentTimeMillis() - t0;
            out.add(new Item(dz, false, "Deezer (tempo, enerji, listeler)",
                    dz ? "Çalışıyor · " + dzMs + " ms" : "Ulaşılamıyor. İnternet bağlantını kontrol et."));
            // Last.fm
            if (lfmOn) {
                String err = LastFm.test(lfmKey);
                out.add(new Item(err == null, false, "Last.fm (tarz etiketleri)", err == null ? "Çalışıyor" : err));
            } else {
                out.add(new Item(false, true, "Last.fm (tarz etiketleri)",
                        "Bağlı değil. Bağlarsan öneriler çok daha isabetli olur."));
            }
            if (!spotifyMode || ytInstalled) {
                // YT Music araması
                String vid = YtMusic.findVideoId("Tarkan", "Şımarık");
                out.add(new Item(vid != null, !spotifyMode, "YouTube Music araması",
                        vid != null ? "Çalışıyor" : "Şarkı bağlantısı bulunamadı; geçişler arama ile denenir."));
                out.add(new Item(ytInstalled, spotifyMode, "YouTube Music uygulaması",
                        !ytInstalled ? "Telefonda bulunamadı" : ytConnected && !spotifyMode ? "Yüklü ve bağlı" : "Yüklü; bir şarkı açınca bağlanır"));
            }
            if (spotifyMode || spInstalled) {
                out.add(new Item(spInstalled, !spotifyMode, "Spotify uygulaması",
                        !spInstalled ? "Telefonda bulunamadı" : ytConnected && spotifyMode ? "Yüklü ve bağlı" : "Yüklü; bir şarkı açınca bağlanır"));
                if (spConnected) {
                    String uri = e.spotify.findTrack("Tarkan", "Şımarık");
                    out.add(new Item(uri != null && !spBlocked, spBlocked, "Spotify hesabı",
                            uri == null ? "Bağlı ama arama çalışmadı; Ayarlar'dan yeniden bağlan"
                                    : spBlocked ? "Bağlı, ama Premium olmadığı için sıraya ekleme kapalı; geçişler uygulama üzerinden"
                                    : "Bağlı ✓ sıradaki şarkı Spotify sırasına eklenir"));
                } else {
                    out.add(new Item(false, true, "Spotify hesabı",
                            "Bağlı değil. Bağlarsan geçişler Spotify'ın kendi sırasıyla, daha yumuşak olur."));
                }
            }
            // İzinler
            out.add(new Item(notifAccess, false, "Bildirim erişimi", notifAccess ? "Açık" : "Kapalı: çalan şarkı okunamaz"));
            out.add(new Item(overlay, false, "Üstte gösterme", overlay ? "Açık" : "Kapalı: arka planda geçiş yapılamaz"));
            out.add(new Item(notifs, true, "Bildirimler", notifs ? "Açık" : "Kapalı: kilit ekranı kontrolü görünmez"));
            out.add(new Item(battery, true, "Pil kısıtlaması", battery ? "Kaldırılmış" : "Açık: telefon uygulamayı kapatabilir"));
            if (wakeOn) {
                out.add(new Item(exact, false, "Tam zamanlı alarm", exact ? "İzin var" : "İzin yok: alarm gecikebilir"));
            }
            out.add(new Item(true, true, "Önbellek", cache + " kayıt telefonda"));
            // Son hata
            String lastErr = null;
            for (String l : log) {
                String low = l.toLowerCase();
                if (low.contains("hata") || low.contains("hesaplanamadı") || low.contains("⚠️") || low.contains("açılamadı")
                        || low.contains("geçilemedi")) { lastErr = l; break; }
            }
            out.add(new Item(lastErr == null, true, "Son hata", lastErr == null ? "Yok" : lastErr));
            a.runOnUiThread(() -> done.accept(out));
        }).start();
    }
}
