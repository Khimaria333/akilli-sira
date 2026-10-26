package com.akillisira.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube Music'in web sitesinin kullandığı arama uç noktası.
 * Şarkıyı arar ve ilk "şarkı" sonucunun videoId'sini döndürür,
 * böylece YT Music'te music.youtube.com/watch?v=... bağlantısını doğrudan açabiliriz.
 * Resmi değildir; YouTube değiştirirse güncellemek gerekebilir.
 */
public final class YtMusic {
    private YtMusic() {}

    private static final String URL_SEARCH = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false";
    // Sadece "Şarkılar" filtresi (ytmusicapi'deki ile aynı)
    private static final String SONGS_PARAM = "EgWKAQIIAWoMEA4QChADEAQQCRAF";
    private static final Pattern VIDEO_ID = Pattern.compile("\"videoId\":\"([A-Za-z0-9_-]{11})\"");
    private static final Map<String, String> cache = new ConcurrentHashMap<>();
    private static final DiskCache disk = DiskCache.of("yt_video", 4000);

    public static String watchUrl(String videoId) {
        return "https://music.youtube.com/watch?v=" + videoId;
    }

    /** Bulamazsa null döner. Ağ isteği yapar, ana iş parçacığında çağırma. */
    public static String findVideoId(String artist, String title) {
        String q = Text.mainArtist(artist) + " " + Text.cleanTitle(title);
        String hit = cache.get(q);
        if (hit != null) return hit;
        hit = disk.get(q, 30 * DiskCache.DAY);
        if (hit != null && !hit.isEmpty()) {
            cache.put(q, hit);
            return hit;
        }
        try {
            String body = "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\","
                    + "\"clientVersion\":\"1.20251020.01.00\",\"hl\":\"tr\",\"gl\":\"TR\"}},"
                    + "\"query\":" + jsonString(q) + ",\"params\":\"" + SONGS_PARAM + "\"}";
            String res = post(URL_SEARCH, body);
            Matcher m = VIDEO_ID.matcher(res);
            if (m.find()) {
                String id = m.group(1);
                cache.put(q, id);
                disk.put(q, id);
                return id;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(' ');
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    private static String post(String url, String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Origin", "https://music.youtube.com");
        c.setRequestProperty("Referer", "https://music.youtube.com/");
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36");
        try {
            OutputStream os = c.getOutputStream();
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.close();
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }
}
