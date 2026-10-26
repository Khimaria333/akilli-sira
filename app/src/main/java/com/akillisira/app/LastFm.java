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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Last.fm: dinleyicilerin sanatçılara verdiği etiketler ("arabesk", "turkish rap", "greek"...)
 * ve benzer sanatçılar. Tarzı ve dili Deezer'den çok daha isabetli anlatır.
 * Ücretsiz bir API anahtarı gerekir; yoksa hiçbir şey yapmaz.
 */
public class LastFm {
    private static final String BASE = "https://ws.audioscrobbler.com/2.0/?format=json";

    private volatile String key;
    private final Map<String, Map<String, Integer>> tagCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> similarCache = new ConcurrentHashMap<>();
    private final Map<String, List<String[]>> tagTracksCache = new ConcurrentHashMap<>();
    private long lastCall;

    // Telefona kaydedilen önbellek
    private final DiskCache dArtist = DiskCache.of("lfm_artist", 4000);
    private final DiskCache dTrack = DiskCache.of("lfm_track", 6000);
    private final DiskCache dSimilar = DiskCache.of("lfm_similar", 1500);
    private final DiskCache dTagTop = DiskCache.of("lfm_tagtop", 200);

    private static String encodeTags(Map<String, Integer> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> en : m.entrySet()) {
            if (sb.length() > 0) sb.append('\t');
            sb.append(en.getKey().replace('\t', ' ')).append('\t').append(en.getValue());
        }
        return sb.toString();
    }

    private static Map<String, Integer> decodeTags(String s) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) return m;
        String[] p = s.split("\t");
        for (int i = 0; i + 1 < p.length; i += 2) {
            try { m.put(p[i], Integer.parseInt(p[i + 1])); } catch (Exception ignored) {}
        }
        return m;
    }

    /** Etiket listesini okur (artist.gettoptags / track.gettoptags yanıtı). */
    private static Map<String, Integer> parseTags(JSONObject o) {
        Map<String, Integer> out = new LinkedHashMap<>();
        JSONObject tt = o.optJSONObject("toptags");
        JSONArray arr = tt != null ? tt.optJSONArray("tag") : null;
        if (arr != null) {
            for (int i = 0; i < arr.length() && out.size() < 15; i++) {
                JSONObject t = arr.optJSONObject(i);
                if (t == null) continue;
                String name = t.optString("name").toLowerCase(Locale.forLanguageTag("tr")).trim();
                int count = t.optInt("count");
                if (name.isEmpty() || count < 5 || NOISE.contains(name)) continue;
                out.put(name, count);
            }
        }
        return out;
    }

    /** Şarkının kendi etiketleri (ruh hali için daha isabetli). Boş olabilir. */
    public Map<String, Integer> trackTags(String artist, String title) {
        if (!enabled() || artist == null || title == null) return Collections.emptyMap();
        String k = Text.norm(Text.mainArtist(artist)) + "|" + Text.norm(title);
        String disk = dTrack.get(k, 45 * DiskCache.DAY);
        if (disk != null) return decodeTags(disk);
        Map<String, Integer> out;
        try {
            out = parseTags(call("track.gettoptags", "&autocorrect=1&artist=" + enc(Text.mainArtist(artist))
                    + "&track=" + enc(Text.cleanTitle(title))));
        } catch (IOException e) {
            return Collections.emptyMap();
        }
        dTrack.put(k, encodeTags(out));
        return out;
    }

    public void setKey(String k) {
        key = k == null || k.trim().isEmpty() ? null : k.trim();
    }

    public boolean enabled() {
        return key != null;
    }

    // Anlamsız / kişisel etiketler
    private static final Set<String> NOISE = new HashSet<>(Arrays.asList(
        "seen live", "favorites", "favourites", "favorite", "favourite", "my favorite", "love", "loved",
        "awesome", "beautiful", "amazing", "good", "best", "all", "music", "under 2000 listeners",
        "albums i own", "spotify", "check out", "favorite artists", "male vocalists", "female vocalists",
        "singer-songwriter", "vocal", "fav", "favs", "great", "cool", "deezer"));

    // =====================================================================

    /** Sanatçının etiketleri: ad → ağırlık (0-100). Boş olabilir. */
    public Map<String, Integer> artistTags(String artist) {
        if (!enabled() || artist == null || artist.isEmpty()) return Collections.emptyMap();
        String k = Text.norm(Text.mainArtist(artist));
        Map<String, Integer> c = tagCache.get(k);
        if (c != null) return c;
        String disk = dArtist.get(k, 30 * DiskCache.DAY);
        if (disk != null) {
            Map<String, Integer> m = decodeTags(disk);
            tagCache.put(k, m);
            return m;
        }
        Map<String, Integer> out;
        try {
            out = parseTags(call("artist.gettoptags", "&autocorrect=1&artist=" + enc(Text.mainArtist(artist))));
        } catch (IOException e) {
            return Collections.emptyMap();   // ağ hatası: önbelleğe yazma, sonra tekrar dene
        }
        tagCache.put(k, out);
        dArtist.put(k, encodeTags(out));
        return out;
    }

    /** Benzer sanatçı adları (en benzerden başlayarak). */
    public List<String> similarArtists(String artist, int limit) {
        if (!enabled()) return Collections.emptyList();
        String k = Text.norm(Text.mainArtist(artist));
        List<String> c = similarCache.get(k);
        if (c != null) return c;
        String disk = dSimilar.get(k, 30 * DiskCache.DAY);
        if (disk != null) {
            List<String> l = new ArrayList<>();
            if (!disk.isEmpty()) Collections.addAll(l, disk.split("\t"));
            similarCache.put(k, l);
            return l;
        }
        List<String> out = new ArrayList<>();
        try {
            JSONObject o = call("artist.getsimilar", "&autocorrect=1&limit=" + limit + "&artist=" + enc(Text.mainArtist(artist)));
            JSONObject sa = o.optJSONObject("similarartists");
            JSONArray arr = sa != null ? sa.optJSONArray("artist") : null;
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject a = arr.optJSONObject(i);
                    if (a != null && !a.optString("name").isEmpty()) out.add(a.optString("name"));
                }
            }
        } catch (IOException e) {
            return Collections.emptyList();
        }
        similarCache.put(k, out);
        dSimilar.put(k, String.join("\t", out));
        return out;
    }

    /** Bir etiketin en çok dinlenen şarkıları: {sanatçı, şarkı}. */
    public List<String[]> tagTopTracks(String tag, int limit) {
        if (!enabled()) return Collections.emptyList();
        String k = tag.toLowerCase(Locale.ROOT) + "|" + limit;
        List<String[]> c = tagTracksCache.get(k);
        if (c != null) return c;
        String disk = dTagTop.get(k, 7 * DiskCache.DAY);
        if (disk != null) {
            List<String[]> l = new ArrayList<>();
            String[] p = disk.isEmpty() ? new String[0] : disk.split("\t");
            for (int i = 0; i + 1 < p.length; i += 2) l.add(new String[]{p[i], p[i + 1]});
            tagTracksCache.put(k, l);
            return l;
        }
        List<String[]> out = new ArrayList<>();
        try {
            JSONObject o = call("tag.gettoptracks", "&limit=" + limit + "&tag=" + enc(tag));
            JSONObject tr = o.optJSONObject("tracks");
            JSONArray arr = tr != null ? tr.optJSONArray("track") : null;
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject t = arr.optJSONObject(i);
                    if (t == null) continue;
                    JSONObject ar = t.optJSONObject("artist");
                    String an = ar != null ? ar.optString("name") : "";
                    String tn = t.optString("name");
                    if (!an.isEmpty() && !tn.isEmpty()) out.add(new String[]{an, tn});
                }
            }
        } catch (IOException e) {
            return Collections.emptyList();
        }
        tagTracksCache.put(k, out);
        StringBuilder sb = new StringBuilder();
        for (String[] at : out) {
            if (sb.length() > 0) sb.append('\t');
            sb.append(at[0].replace('\t', ' ')).append('\t').append(at[1].replace('\t', ' '));
        }
        dTagTop.put(k, sb.toString());
        return out;
    }

    /** Anahtarı dener; sorun yoksa null, varsa açıklama döner. Ağ isteği yapar. */
    public static String test(String k) {
        LastFm l = new LastFm();
        l.setKey(k);
        try {
            JSONObject o = l.call("artist.gettoptags", "&artist=Tarkan");
            if (o.has("toptags")) return null;
            return "Beklenmeyen yanıt";
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    // =====================================================================
    // Etiketlerden çıkarımlar
    // =====================================================================

    private static final String[] TR_MARKS = {"turkish", "türk", "turk", "türkçe", "turkce", "arabesk", "arabesque",
            "anatolian", "anadolu", "fasıl", "fasil", "istanbul", "turkey", "türkiye", "türkü", "turku"};
    private static final String[] FOREIGN_MARKS = {"greek", "english", "american", "british", "german", "french",
            "spanish", "italian", "k-pop", "kpop", "korean", "japanese", "j-pop", "russian", "arabic", "persian",
            "iranian", "latin", "laiko", "rebetiko", "bulgarian", "serbian", "balkan", "azeri", "azerbaijani",
            "bollywood", "hindi", "brazilian", "uk ", "usa"};

    /** Etiketlere göre dil: Lang.TR / Lang.FOREIGN / Lang.UNKNOWN */
    public static int langFromTags(Map<String, Integer> tags) {
        if (tags == null || tags.isEmpty()) return Lang.UNKNOWN;
        int tr = 0, fo = 0;
        for (Map.Entry<String, Integer> en : tags.entrySet()) {
            String t = en.getKey() + " ";
            for (String m : TR_MARKS) if (t.contains(m)) { tr += en.getValue(); break; }
            for (String m : FOREIGN_MARKS) if (t.contains(m)) { fo += en.getValue(); break; }
        }
        if (tr == 0 && fo == 0) return Lang.UNKNOWN;
        if (tr >= fo) return Lang.TR;
        return fo >= 30 ? Lang.FOREIGN : Lang.UNKNOWN;
    }

    /** İki etiket kümesinin benzerliği 0..1 (dil etiketleri hariç, ağırlıklı kosinüs). */
    public static double similarity(Map<String, Integer> a, Map<String, Integer> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return -1;
        double dot = 0, na = 0, nb = 0;
        for (Map.Entry<String, Integer> en : a.entrySet()) {
            if (isLangTag(en.getKey())) continue;
            double va = en.getValue();
            na += va * va;
            Integer vb = b.get(en.getKey());
            if (vb != null) dot += va * vb;
        }
        for (Map.Entry<String, Integer> en : b.entrySet()) {
            if (isLangTag(en.getKey())) continue;
            nb += (double) en.getValue() * en.getValue();
        }
        if (na == 0 || nb == 0) return -1;
        return dot / Math.sqrt(na * nb);
    }

    /** İstenen etiketlerle örtüşme 0..1 (ruh hali / sahne hedefleri için). */
    public static double overlap(Map<String, Integer> tags, List<String> want) {
        if (tags == null || tags.isEmpty() || want == null || want.isEmpty()) return -1;
        double best = 0, sum = 0;
        for (String w : want) {
            for (Map.Entry<String, Integer> en : tags.entrySet()) {
                if (en.getKey().contains(w)) {
                    double v = en.getValue() / 100.0;
                    sum += v;
                    best = Math.max(best, v);
                    break;
                }
            }
        }
        return Math.min(1, best * 0.7 + sum * 0.3);
    }

    /** En güçlü ortak etiket (açıklama için). */
    public static String sharedTag(Map<String, Integer> a, Map<String, Integer> b) {
        if (a == null || b == null) return null;
        String best = null;
        int bestV = 0;
        for (Map.Entry<String, Integer> en : a.entrySet()) {
            if (isLangTag(en.getKey()) && !en.getKey().contains("arabesk") && !en.getKey().contains(" ")) continue;
            Integer vb = b.get(en.getKey());
            if (vb == null) continue;
            int v = Math.min(en.getValue(), vb);
            if (v > bestV) { bestV = v; best = en.getKey(); }
        }
        return best;
    }

    public static boolean isLangTag(String t) {
        return t.equals("turkish") || t.equals("türkçe") || t.equals("turkce") || t.equals("türk") || t.equals("turkey")
                || t.equals("greek") || t.equals("english") || t.equals("american") || t.equals("british");
    }

    // =====================================================================

    private JSONObject call(String method, String params) throws IOException {
        String k = key;
        if (k == null) throw new IOException("Last.fm anahtarı yok");
        // Last.fm sınırı: saniyede ~5 istek
        synchronized (this) {
            long wait = 210 - (System.currentTimeMillis() - lastCall);
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            lastCall = System.currentTimeMillis();
        }
        String url = BASE + "&method=" + method + "&api_key=" + enc(k) + params;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "AkilliSira/1.0 (Android)");
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new IOException("HTTP " + code);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject o;
            try {
                o = new JSONObject(bo.toString(StandardCharsets.UTF_8.name()));
            } catch (org.json.JSONException je) {
                throw new IOException("Last.fm yanıtı okunamadı");
            }
            if (o.has("error")) {
                int err = o.optInt("error");
                String msg = o.optString("message");
                if (err == 10 || err == 26) throw new IOException("Geçersiz API anahtarı");
                if (err == 6) return new JSONObject();   // sanatçı bulunamadı
                throw new IOException("Last.fm: " + msg);
            }
            return o;
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
