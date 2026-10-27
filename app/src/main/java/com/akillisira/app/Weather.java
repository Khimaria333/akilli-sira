package com.akillisira.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Hava durumu (Open-Meteo: ücretsiz, anahtarsız). Şehir aranarak ya da yaklaşık konumla çalışır.
 * Serbest akışta öneriyi hafifçe havaya doğru kaydırır; Keşfet'te "Havaya göre" listesi hazırlar.
 */
public final class Weather {
    private Weather() {}

    public enum Sky { CLEAR, CLOUDY, OVERCAST, FOG, RAIN, SNOW, STORM }

    public static final class Now {
        public Sky sky;
        public double temp;
        public boolean day;
        public long at;

        public String emoji() {
            switch (sky) {
                case CLEAR: return day ? "☀️" : "🌙";
                case CLOUDY: return day ? "⛅" : "☁️";
                case OVERCAST: return "☁️";
                case FOG: return "🌫️";
                case RAIN: return "🌧️";
                case SNOW: return "❄️";
                default: return "⛈️";
            }
        }

        public String word() {
            switch (sky) {
                case CLEAR: return day ? "Güneşli" : "Açık";
                case CLOUDY: return "Parçalı bulutlu";
                case OVERCAST: return "Kapalı";
                case FOG: return "Sisli";
                case RAIN: return "Yağmurlu";
                case SNOW: return "Karlı";
                default: return "Fırtınalı";
            }
        }

        public String label() {
            return emoji() + " " + word() + ", " + Math.round(temp) + "°";
        }

        /** Serbest akışta öneriyi kaydıracak Last.fm etiketleri. */
        public List<String> tags() {
            List<String> t = new ArrayList<>();
            switch (sky) {
                case RAIN: t.add("melancholic"); t.add("rain"); t.add("chill"); t.add("acoustic"); break;
                case STORM: t.add("dark"); t.add("melancholic"); t.add("atmospheric"); break;
                case SNOW: t.add("winter"); t.add("calm"); t.add("acoustic"); break;
                case FOG: t.add("atmospheric"); t.add("chill"); t.add("ambient"); break;
                case OVERCAST: t.add("chill"); t.add("mellow"); break;
                case CLOUDY: t.add("chill"); t.add("indie"); break;
                default:
                    if (day && temp >= 24) { t.add("summer"); t.add("happy"); t.add("upbeat"); }
                    else if (day) { t.add("happy"); t.add("feel good"); }
                    else { t.add("chill"); t.add("night"); }
            }
            return t;
        }

        /** Keşfet için cümle: Vibe ayrıştırıcısı bunu sahnelere çevirir ("yağmur akşam"). */
        public String sentence() {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            String time = h < 6 ? "gece" : h < 11 ? "sabah" : h < 16 ? "" : h < 19 ? "akşamüstü" : h < 23 ? "akşam" : "gece";
            String sky;
            switch (this.sky) {
                case RAIN: sky = "yağmur"; break;
                case STORM: sky = "fırtına"; break;
                case SNOW: sky = "karlı kış"; break;
                case FOG: sky = "sakin"; break;
                case OVERCAST: sky = "sakin"; break;
                case CLOUDY: sky = temp >= 22 ? "yaz" : ""; break;
                default: sky = day ? (temp >= 24 ? "yaz güneşli" : "güneşli keyifli") : "";
            }
            if (temp <= 5 && this.sky != Sky.SNOW) sky = (sky + " soğuk").trim();
            String s = (sky + " " + time).trim();
            return s.isEmpty() ? "keyifli" : s;
        }
    }

    public static final class Place {
        public String name;
        public double lat, lon;

        @Override
        public String toString() { return name; }
    }

    private static final long TTL = 30L * 60 * 1000;
    private static volatile Now cached;
    private static volatile boolean fetching;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    public static boolean isOn(Context c) { return prefs(c).getBoolean("weatherOn", false); }

    public static void setOn(Context c, boolean on) { prefs(c).edit().putBoolean("weatherOn", on).apply(); }

    public static boolean hasPlace(Context c) { return prefs(c).contains("weatherLat"); }

    public static String placeName(Context c) { return prefs(c).getString("weatherCity", null); }

    public static void setPlace(Context c, String name, double lat, double lon) {
        prefs(c).edit().putString("weatherCity", name)
                .putFloat("weatherLat", (float) lat).putFloat("weatherLon", (float) lon).apply();
        cached = null;
    }

    /** Önbellekteki hava (yoksa ya da eskiyse null). */
    public static Now current() {
        Now n = cached;
        return n != null && System.currentTimeMillis() - n.at < TTL * 3 ? n : null;
    }

    /** Eskiyse arka planda tazeler. done: bitince (başarılı olsa da olmasa da) çağrılır, arka plan iş parçacığında. */
    public static void refresh(Context c, boolean force, Runnable done) {
        Now n = cached;
        if (!hasPlace(c) || (!force && n != null && System.currentTimeMillis() - n.at < TTL) || fetching) {
            if (done != null) done.run();
            return;
        }
        fetching = true;
        double lat = prefs(c).getFloat("weatherLat", 0), lon = prefs(c).getFloat("weatherLon", 0);
        new Thread(() -> {
            try {
                Now r = fetch(lat, lon);
                if (r != null) cached = r;
            } finally {
                fetching = false;
                if (done != null) done.run();
            }
        }).start();
    }

    /** Ağ iş parçacığında, önbelleği atlayarak. */
    public static Now fetchNow(Context c) {
        if (!hasPlace(c)) return null;
        Now n = cached;
        if (n != null && System.currentTimeMillis() - n.at < TTL) return n;
        Now r = fetch(prefs(c).getFloat("weatherLat", 0), prefs(c).getFloat("weatherLon", 0));
        if (r != null) cached = r;
        return r != null ? r : n;
    }

    private static Now fetch(double lat, double lon) {
        try {
            JSONObject o = new JSONObject(get(String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                            + "&current=temperature_2m,weather_code,is_day&timezone=auto", lat, lon)));
            JSONObject cur = o.optJSONObject("current");
            if (cur == null) return null;
            Now n = new Now();
            n.temp = cur.optDouble("temperature_2m", 15);
            n.day = cur.optInt("is_day", 1) == 1;
            n.sky = sky(cur.optInt("weather_code", 0));
            n.at = System.currentTimeMillis();
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    /** WMO hava kodu → gökyüzü. */
    static Sky sky(int code) {
        if (code == 0) return Sky.CLEAR;
        if (code <= 2) return Sky.CLOUDY;
        if (code == 3) return Sky.OVERCAST;
        if (code == 45 || code == 48) return Sky.FOG;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Sky.SNOW;
        if (code >= 95) return Sky.STORM;
        if (code >= 51) return Sky.RAIN;
        return Sky.CLOUDY;
    }

    /** Şehir ara (ağ iş parçacığında çağır). */
    public static List<Place> search(String q) {
        List<Place> out = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=6&language=tr&format=json&name="
                    + URLEncoder.encode(q.trim(), "UTF-8")));
            JSONArray r = o.optJSONArray("results");
            for (int i = 0; r != null && i < r.length(); i++) {
                JSONObject x = r.optJSONObject(i);
                if (x == null) continue;
                Place p = new Place();
                String admin = x.optString("admin1", "");
                String country = x.optString("country", "");
                p.name = x.optString("name") + (admin.isEmpty() || admin.equals(x.optString("name")) ? "" : ", " + admin)
                        + (country.isEmpty() ? "" : " · " + country);
                p.lat = x.optDouble("latitude");
                p.lon = x.optDouble("longitude");
                out.add(p);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(8000);
        h.setReadTimeout(8000);
        h.setRequestProperty("User-Agent", "Ahenk/1.0 (Android)");
        try {
            InputStream in = h.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString(StandardCharsets.UTF_8.name());
        } finally {
            h.disconnect();
        }
    }
}
