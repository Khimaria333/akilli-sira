package com.akillisira.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Deezer'in ücretsiz, anahtarsız herkese açık API'si.
 * Şarkıyı bulmak, BPM ve ses yüksekliği (enerji) almak, benzer sanatçıları ve
 * sanatçı radyosunu çekmek için kullanılır. Çalma YT Music'te olur; Deezer sadece veri kaynağı.
 */
public class Deezer {
    private static final String BASE = "https://api.deezer.com";
    private final Map<Long, Track> detailCache = new ConcurrentHashMap<>();
    private final Map<Long, Object[]> albumCache = new ConcurrentHashMap<>();   // albüm → {genreId, genreName, year}

    // ---------- Şarkıyı bulma ----------

    /** YT Music'te çalan şarkıyı Deezer'de bulur. Bulamazsa en azından sanatçıyı bulmaya çalışır. */
    public Track findSeed(String title, String artist) throws IOException {
        String t = Text.cleanTitle(title);
        String a = Text.mainArtist(artist);

        List<Track> hits = search("artist:\"" + a + "\" track:\"" + t + "\"", 5);
        if (hits.isEmpty()) hits = search(a + " " + t, 8);

        Track best = null;
        int bestScore = -1;
        String nt = Text.norm(t), na = Text.norm(a);
        for (Track h : hits) {
            int sc = 0;
            String ht = Text.norm(h.title), ha = Text.norm(Text.mainArtist(h.artist));
            if (ht.equals(nt)) sc += 3; else if (ht.contains(nt) || nt.contains(ht)) sc += 1;
            if (ha.equals(na)) sc += 3; else if (ha.contains(na) || na.contains(ha)) sc += 1;
            if (sc > bestScore) { bestScore = sc; best = h; }
        }
        if (best != null && bestScore >= 3) {
            best.title = title;   // ekranda YT Music'teki ad görünsün
            return best;
        }

        // Şarkı yok ama sanatçı varsa onunla devam et
        JSONObject r = get("/search/artist?limit=1&q=" + enc(a));
        JSONArray data = r.optJSONArray("data");
        if (data != null && data.length() > 0) {
            JSONObject o = data.optJSONObject(0);
            if (o == null) return null;
            Track s = new Track();
            s.title = title;
            s.artist = artist;
            s.artistId = o.optLong("id");
            return s;
        }
        return null;
    }

    public List<Track> search(String q, int limit) throws IOException {
        return parseTracks(get("/search?limit=" + limit + "&q=" + enc(q)), null);
    }

    // ---------- Aday kaynakları ----------

    public List<Track> artistTop(long artistId, int limit, String source) throws IOException {
        return parseTracks(get("/artist/" + artistId + "/top?limit=" + limit), source);
    }

    public List<Track> artistRadio(long artistId, int limit) throws IOException {
        List<Track> all = parseTracks(get("/artist/" + artistId + "/radio?limit=" + limit), "radio");
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    public List<Long> related(long artistId, int limit) throws IOException {
        JSONObject r = get("/artist/" + artistId + "/related?limit=" + limit);
        List<Long> out = new ArrayList<>();
        JSONArray data = r.optJSONArray("data");
        if (data == null) return out;
        for (int i = 0; i < data.length() && out.size() < limit; i++) {
            JSONObject a = data.optJSONObject(i);
            if (a != null) out.add(a.optLong("id"));
        }
        return out;
    }

    /** Ruh hali / dil sorgusuyla çalma listesi bulur (en az 20 şarkılı olanlar). */
    public List<Long> searchPlaylists(String q, int limit) throws IOException {
        JSONObject r = get("/search/playlist?limit=" + (limit * 3) + "&q=" + enc(q));
        List<Long> out = new ArrayList<>();
        JSONArray data = r.optJSONArray("data");
        if (data == null) return out;
        for (int i = 0; i < data.length() && out.size() < limit; i++) {
            JSONObject o = data.optJSONObject(i);
            if (o != null && o.optInt("nb_tracks") >= 20) out.add(o.optLong("id"));
        }
        return out;
    }

    public List<Track> playlistTracks(long playlistId, int limit, String source) throws IOException {
        return parseTracks(get("/playlist/" + playlistId + "/tracks?limit=" + limit), source);
    }

    // ---------- BPM / enerji ----------

    /** /track/{id} çağrısıyla BPM ve ses yüksekliğini doldurur (önbellekli). */
    public void fillDetails(Track t) {
        if (t.deezerId <= 0 || t.detailsLoaded) return;
        Track cached = detailCache.get(t.deezerId);
        if (cached != null) {
            t.bpm = cached.bpm;
            t.gain = cached.gain;
            if (t.year == 0) t.year = cached.year;
            if (t.albumId == 0) t.albumId = cached.albumId;
            if (t.coverUrl == null) { t.coverUrl = cached.coverUrl; t.coverBigUrl = cached.coverBigUrl; }
            t.detailsLoaded = true;
            return;
        }
        try {
            JSONObject o = get("/track/" + t.deezerId);
            t.bpm = o.optDouble("bpm", 0);
            t.gain = o.has("gain") ? o.optDouble("gain", Double.NaN) : Double.NaN;
            if (t.durationSec == 0) t.durationSec = o.optInt("duration");
            t.year = yearOf(o.optString("release_date"));
            JSONObject alb = o.optJSONObject("album");
            if (alb != null && t.albumId == 0) t.albumId = alb.optLong("id");
            if (t.coverUrl == null) {
                JSONObject al = o.optJSONObject("album");
                if (al != null) {
                    t.coverUrl = emptyToNull(al.optString("cover_medium"));
                    t.coverBigUrl = emptyToNull(al.optString("cover_xl"));
                }
            }
            if (t.artistId == 0) {
                JSONObject ar = o.optJSONObject("artist");
                if (ar != null) t.artistId = ar.optLong("id");
            }
            t.detailsLoaded = true;
            detailCache.put(t.deezerId, t);
        } catch (IOException ignored) {
            // detay gelmezse şarkı yine aday kalır, sadece BPM'i bilinmez
        }
    }

    /** Albümün türünü (ve yılını) doldurur; albüm başına bir kez sorulur. */
    public void fillGenre(Track t) {
        if (t.albumId <= 0 || t.genreId != 0) return;
        Object[] c = albumCache.get(t.albumId);
        if (c == null) {
            try {
                JSONObject o = get("/album/" + t.albumId);
                int gid = o.optInt("genre_id", 0);
                String gname = null;
                JSONObject gs = o.optJSONObject("genres");
                JSONArray gd = gs != null ? gs.optJSONArray("data") : null;
                if (gd != null && gd.length() > 0) {
                    JSONObject g0 = gd.optJSONObject(0);
                    if (g0 != null) {
                        gname = emptyToNull(g0.optString("name"));
                        if (gid <= 0) gid = g0.optInt("id", 0);
                    }
                }
                int y = yearOf(o.optString("release_date"));
                c = new Object[]{gid > 0 ? gid : -1, gname, y};
            } catch (IOException e) {
                return;
            }
            albumCache.put(t.albumId, c);
        }
        t.genreId = (Integer) c[0];
        t.genre = (String) c[1];
        int ay = (Integer) c[2];
        // Derleme albümleri yılı bozabilir: en eski yılı kullan
        if (ay > 0 && (t.year == 0 || ay < t.year)) t.year = ay;
    }

    public void fillGenresAll(List<Track> tracks) {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (Track t : tracks) fs.add(pool.submit(() -> fillGenre(t)));
            for (Future<?> f : fs) {
                try { f.get(); } catch (Exception ignored) {}
            }
        } finally {
            pool.shutdown();
        }
    }

    private static int yearOf(String date) {
        if (date == null || date.length() < 4) return 0;
        try {
            int y = Integer.parseInt(date.substring(0, 4));
            return y >= 1900 && y <= 2100 ? y : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Birçok şarkının detayını paralel ama Deezer'in hız sınırını aşmadan çeker. */
    public void fillDetailsAll(List<Track> tracks) {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (Track t : tracks) fs.add(pool.submit(() -> fillDetails(t)));
            for (Future<?> f : fs) {
                try { f.get(); } catch (Exception ignored) {}
            }
        } finally {
            pool.shutdown();
        }
    }

    // ---------- Yardımcılar ----------

    private static List<Track> parseTracks(JSONObject r, String source) {
        List<Track> out = new ArrayList<>();
        JSONArray data = r.optJSONArray("data");
        if (data == null) return out;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null || !"track".equals(o.optString("type", "track"))) continue;
            Track t = new Track();
            t.deezerId = o.optLong("id");
            t.title = o.optString("title_short", o.optString("title"));
            if (t.title.isEmpty()) t.title = o.optString("title");
            t.durationSec = o.optInt("duration");
            t.rank = o.optInt("rank");
            JSONObject ar = o.optJSONObject("artist");
            if (ar != null) {
                t.artist = ar.optString("name");
                t.artistId = ar.optLong("id");
            }
            JSONObject al = o.optJSONObject("album");
            if (al != null) {
                t.albumId = al.optLong("id");
                t.coverUrl = emptyToNull(al.optString("cover_medium"));
                t.coverBigUrl = emptyToNull(al.optString("cover_xl"));
                if (t.coverBigUrl == null) t.coverBigUrl = emptyToNull(al.optString("cover_big"));
            }
            if (source != null) t.sources.add(source);
            if (t.deezerId > 0 && !t.title.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** GET + JSON. Deezer'in "Quota limit exceeded" hatasında biraz bekleyip tekrar dener. */
    JSONObject get(String path) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                JSONObject o = new JSONObject(httpGet(BASE + path));
                JSONObject err = o.optJSONObject("error");
                if (err != null) {
                    if (err.optInt("code") == 4) { // hız sınırı
                        sleep(1200L * (attempt + 1));
                        continue;
                    }
                    if (err.optInt("code") == 800) return new JSONObject(); // bulunamadı
                    throw new IOException("Deezer: " + err.optString("message"));
                }
                return o;
            } catch (IOException e) {
                last = e;
                sleep(600);
            } catch (org.json.JSONException e) {
                last = new IOException("Deezer yanıtı okunamadı");
            }
        }
        throw last != null ? last : new IOException("Deezer'e ulaşılamadı");
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "AkilliSira/0.1 (Android)");
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new IOException("HTTP " + code);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
