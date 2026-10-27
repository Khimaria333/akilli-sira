package com.akillisira.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spotify Web API (kullanıcının kendi Client ID'siyle, PKCE — gizli anahtar yok).
 * Giriş dönüşü için Spotify'ın izin verdiği yerel adres (http://127.0.0.1) kullanılır:
 * Ahenk giriş sırasında kısa süreliğine bu adresi dinler.
 *
 * Spotify kuralları (2026): geliştirici hesabı Premium olmalı, bir Client ID'ye en fazla 5 kişi bağlanır;
 * sıraya ekleme ve çalmayı başlatma da Premium ister.
 */
public final class SpotifyApi {

    public static final String PACKAGE = "com.spotify.music";
    public static final int PORT = 43827;
    public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
    private static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing "
            + "playlist-modify-private playlist-modify-public";

    private final SharedPreferences prefs;
    private final Map<String, String> uriCache = new ConcurrentHashMap<>();
    private final DiskCache disk = DiskCache.of("sp_uri", 4000);

    public SpotifyApi(Context c) {
        prefs = c.getSharedPreferences("spotify", Context.MODE_PRIVATE);
    }

    public String clientId() { return prefs.getString("clientId", null); }
    public boolean connected() { return prefs.getString("refresh", null) != null && clientId() != null; }
    public String userName() { return prefs.getString("name", null); }

    /** Premium yoksa ya da çalma API'si reddederse true olur; o zaman uygulama içinden geçiş yöntemi kullanılır. */
    public boolean playerBlocked() { return prefs.getBoolean("playerBlocked", false); }
    void setPlayerBlocked(boolean b) { prefs.edit().putBoolean("playerBlocked", b).apply(); }

    public void disconnect() {
        prefs.edit().remove("access").remove("refresh").remove("exp").remove("name").remove("playerBlocked").apply();
    }

    // =====================================================================
    // Giriş (PKCE + yerel dönüş adresi)
    // =====================================================================

    public interface LoginCallback { void done(boolean ok, String message); }

    private volatile ServerSocket server;

    /** Tarayıcıda Spotify girişini açar; dönüşü yerel adreste yakalar. cb arka plan iş parçacığında çağrılır. */
    public void login(Activity a, String clientId, LoginCallback cb) {
        final String cid = clientId.trim();
        prefs.edit().putString("clientId", cid).apply();
        final String verifier = random(64);
        final String state = random(16);
        String challenge;
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            challenge = Base64.encodeToString(h, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            cb.done(false, "Şifreleme hatası");
            return;
        }
        stopServer();
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"))) {
                server = ss;
                ss.setSoTimeout(5 * 60 * 1000);
                while (true) {
                    try (Socket s = ss.accept()) {
                        String line = readLine(s.getInputStream());
                        // "GET /callback?code=...&state=... HTTP/1.1"
                        if (line == null || !line.startsWith("GET /callback")) {
                            respond(s, 404, "Bulunamadı");
                            continue;
                        }
                        String q = line.split(" ")[1];
                        q = q.contains("?") ? q.substring(q.indexOf('?') + 1) : "";
                        String code = param(q, "code"), st = param(q, "state"), err = param(q, "error");
                        if (err != null) {
                            respond(s, 200, page("Bağlantı iptal edildi", "Ahenk'e dönüp tekrar deneyebilirsin."));
                            cb.done(false, "Spotify girişi iptal edildi (" + err + ")");
                            return;
                        }
                        if (code == null || !state.equals(st)) {
                            respond(s, 400, page("Bir şeyler ters gitti", "Ahenk'e dönüp tekrar dene."));
                            cb.done(false, "Spotify'dan geçersiz yanıt geldi");
                            return;
                        }
                        String msg = exchange(cid, code, verifier);
                        boolean ok = msg == null;
                        respond(s, 200, ok ? page("✓ Spotify bağlandı", "Ahenk'e dönebilirsin.")
                                : page("Bağlanamadı", msg));
                        cb.done(ok, ok ? "Spotify bağlandı" : msg);
                        return;
                    }
                }
            } catch (java.net.SocketTimeoutException e) {
                cb.done(false, "Spotify girişi zaman aşımına uğradı");
            } catch (Exception e) {
                if (server != null) cb.done(false, "Giriş başlatılamadı: " + e.getMessage());
            } finally {
                server = null;
            }
        }).start();

        String url = "https://accounts.spotify.com/authorize?response_type=code"
                + "&client_id=" + enc(cid)
                + "&redirect_uri=" + enc(REDIRECT)
                + "&code_challenge_method=S256&code_challenge=" + enc(challenge)
                + "&scope=" + enc(SCOPES)
                + "&state=" + enc(state);
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            stopServer();
            cb.done(false, "Tarayıcı açılamadı");
        }
    }

    private void stopServer() {
        ServerSocket s = server;
        server = null;
        if (s != null) try { s.close(); } catch (Exception ignored) {}
    }

    /** Kodu jetona çevirir; başarılıysa null, değilse hata mesajı. */
    private String exchange(String cid, String code, String verifier) {
        try {
            String body = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
                    + "&client_id=" + enc(cid) + "&code_verifier=" + enc(verifier);
            Resp r = http("POST", "https://accounts.spotify.com/api/token", body, "application/x-www-form-urlencoded", null);
            if (r.code != 200) return "Spotify jeton vermedi (" + r.code + "): " + errText(r.body);
            saveTokens(new JSONObject(r.body));
            prefs.edit().remove("playerBlocked").apply();
            Resp me = call("GET", "/me", null);
            if (me.code == 200) {
                JSONObject o = new JSONObject(me.body);
                prefs.edit().putString("name", o.optString("display_name", o.optString("id", ""))).apply();
            }
            return null;
        } catch (Exception e) {
            return "Bağlantı hatası: " + e.getMessage();
        }
    }

    private void saveTokens(JSONObject o) {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString("access", o.optString("access_token"));
        ed.putLong("exp", System.currentTimeMillis() + (o.optLong("expires_in", 3600) - 60) * 1000);
        String rt = o.optString("refresh_token", "");
        if (!rt.isEmpty()) ed.putString("refresh", rt);
        ed.apply();
    }

    private synchronized String token() throws Exception {
        String acc = prefs.getString("access", null);
        if (acc != null && System.currentTimeMillis() < prefs.getLong("exp", 0)) return acc;
        String rt = prefs.getString("refresh", null), cid = clientId();
        if (rt == null || cid == null) throw new Exception("Spotify bağlı değil");
        Resp r = http("POST", "https://accounts.spotify.com/api/token",
                "grant_type=refresh_token&refresh_token=" + enc(rt) + "&client_id=" + enc(cid),
                "application/x-www-form-urlencoded", null);
        if (r.code == 400 || r.code == 401) {   // izin geri alındı
            disconnect();
            throw new Exception("Spotify bağlantısı sona erdi, yeniden bağlan");
        }
        if (r.code != 200) throw new Exception("Spotify jeton yenilenemedi (" + r.code + ")");
        saveTokens(new JSONObject(r.body));
        return prefs.getString("access", null);
    }

    // =====================================================================
    // Uç noktalar
    // =====================================================================

    /** Şarkının Spotify adresi (spotify:track:…); bulunamazsa null. Ağ iş parçacığında. */
    public String findTrack(String artist, String title) {
        if (!connected()) return null;
        String key = Text.key(artist, title);
        String c = uriCache.get(key);
        if (c == null) c = disk.get(key, 30 * DiskCache.DAY);
        if (c != null) return c.isEmpty() ? null : c;
        try {
            String q = "track:" + Text.cleanTitle(title) + " artist:" + Text.mainArtist(artist);
            Resp r = call("GET", "/search?type=track&limit=5&q=" + enc(q), null);
            if (r.code != 200) return null;
            JSONArray items = new JSONObject(r.body).optJSONObject("tracks").optJSONArray("items");
            String best = null;
            String wantT = Text.norm(Text.cleanTitle(title)), wantA = Text.norm(Text.mainArtist(artist));
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                String uri = it.optString("uri");
                String n = Text.norm(Text.cleanTitle(it.optString("name")));
                JSONArray ar = it.optJSONArray("artists");
                String an = ar != null && ar.length() > 0 ? Text.norm(ar.optJSONObject(0).optString("name")) : "";
                boolean tOk = n.equals(wantT) || n.startsWith(wantT) || wantT.startsWith(n);
                boolean aOk = an.contains(wantA) || wantA.contains(an);
                if (tOk && aOk) { best = uri; break; }
                if (best == null && (tOk || aOk) && i == 0) best = uri;
            }
            uriCache.put(key, best == null ? "" : best);
            disk.put(key, best == null ? "" : best);
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    /** Sıraya ekle (Premium). 0 = tamam, 1 = Premium/izin yok, 2 = açık cihaz yok, 3 = diğer hata. */
    public int addToQueue(String uri) {
        try {
            Resp r = call("POST", "/me/player/queue?uri=" + enc(uri), "");
            return result(r);
        } catch (Exception e) {
            return 3;
        }
    }

    /** Verilen şarkıları sırayla çal (Premium). */
    public int play(List<String> uris) {
        try {
            JSONObject o = new JSONObject();
            JSONArray a = new JSONArray();
            for (String u : uris) a.put(u);
            o.put("uris", a);
            Resp r = call("PUT", "/me/player/play", o.toString());
            return result(r);
        } catch (Exception e) {
            return 3;
        }
    }

    private int result(Resp r) {
        if (r.code >= 200 && r.code < 300) return 0;
        if (r.code == 403) { setPlayerBlocked(true); return 1; }
        if (r.code == 404) return 2;
        return 3;
    }

    /** Listeyi hesabına yeni bir çalma listesi olarak kaydeder; başarılıysa listenin bağlantısı. */
    public String savePlaylist(String name, List<String> uris) {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("public", false);
            o.put("description", "Ahenk ile hazırlandı");
            Resp r = call("POST", "/me/playlists", o.toString());
            if (r.code != 200 && r.code != 201) return null;
            JSONObject pl = new JSONObject(r.body);
            String id = pl.optString("id");
            JSONArray a = new JSONArray();
            for (int i = 0; i < uris.size() && i < 100; i++) a.put(uris.get(i));
            Resp add = call("POST", "/playlists/" + id + "/items", new JSONObject().put("uris", a).toString());
            if (add.code >= 300) return null;
            JSONObject ext = pl.optJSONObject("external_urls");
            return ext != null ? ext.optString("spotify", "spotify:playlist:" + id) : "spotify:playlist:" + id;
        } catch (Exception e) {
            return null;
        }
    }

    // =====================================================================
    // HTTP
    // =====================================================================

    static final class Resp {
        int code;
        String body = "";
    }

    private Resp call(String method, String path, String json) throws Exception {
        Resp r = http(method, "https://api.spotify.com/v1" + path, json, "application/json", token());
        if (r.code == 401) {   // jeton süresi beklenenden önce dolmuş
            prefs.edit().putLong("exp", 0).apply();
            r = http(method, "https://api.spotify.com/v1" + path, json, "application/json", token());
        }
        if (r.code == 429) {
            Thread.sleep(1500);
            r = http(method, "https://api.spotify.com/v1" + path, json, "application/json", token());
        }
        return r;
    }

    private static Resp http(String method, String url, String body, String type, String bearer) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setRequestMethod(method);
            h.setConnectTimeout(8000);
            h.setReadTimeout(10000);
            if (bearer != null) h.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) {
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", type);
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                h.setFixedLengthStreamingMode(b.length);
                try (OutputStream os = h.getOutputStream()) { os.write(b); }
            }
            Resp r = new Resp();
            r.code = h.getResponseCode();
            InputStream in = r.code >= 400 ? h.getErrorStream() : h.getInputStream();
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                r.body = bo.toString(StandardCharsets.UTF_8.name());
            }
            return r;
        } finally {
            h.disconnect();
        }
    }

    private static String errText(String body) {
        try {
            JSONObject o = new JSONObject(body);
            String d = o.optString("error_description", "");
            return d.isEmpty() ? o.optString("error", body) : d;
        } catch (Exception e) {
            return body;
        }
    }

    // ---- yerel sunucu yardımcıları ----

    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n' && sb.length() < 4096) if (c != '\r') sb.append((char) c);
        return sb.toString();
    }

    private static void respond(Socket s, int code, String html) throws Exception {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length
                + "\r\nConnection: close\r\n\r\n";
        OutputStream os = s.getOutputStream();
        os.write(head.getBytes(StandardCharsets.US_ASCII));
        os.write(b);
        os.flush();
    }

    private static String page(String title, String text) {
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>Ahenk</title></head><body style='margin:0;background:#0B0B10;color:#F4F4F8;font-family:sans-serif;"
                + "display:flex;align-items:center;justify-content:center;min-height:100vh;text-align:center;padding:24px'>"
                + "<div><div style='font-size:42px;font-weight:900;background:linear-gradient(90deg,#FF2E63,#8B3DFF,#2BD9FF);"
                + "-webkit-background-clip:text;color:transparent'>Ahenk</div><h2>" + title + "</h2><p style='color:#A3A3B5'>"
                + text + "</p></div></body></html>";
    }

    private static String param(String q, String name) {
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(name)) {
                try { return URLDecoder.decode(kv.substring(i + 1), "UTF-8"); } catch (Exception e) { return null; }
            }
        }
        return null;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }

    private static String random(int n) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
}
