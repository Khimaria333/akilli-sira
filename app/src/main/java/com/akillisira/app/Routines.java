package com.akillisira.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Otomatik ruh hali planı: "hafta içi 08:00–10:00 Odak", "cuma 20:00–24:00 Parti" gibi.
 * Elle seçilmiş bir mod (Keşfet, tür, araba) her zaman önce gelir.
 */
public final class Routines {
    private Routines() {}

    public static final class Routine {
        public String id = String.valueOf(System.nanoTime());
        public int days = 0b1111111;           // bit 0 = Pazartesi ... bit 6 = Pazar
        public int sh = 8, sm = 0, eh = 10, em = 0;
        public String kind = "mood";           // mood | genre | vibe
        public String value = "odak";
        public boolean on = true;

        public String label() {
            if ("mood".equals(kind)) {
                Moods.Mood m = Moods.mood(value);
                return m != null ? m.emoji + " " + m.label : value;
            }
            if ("genre".equals(kind)) {
                Genres.Genre g = Genres.byId(value);
                return g != null ? g.emoji + " " + g.label : value;
            }
            return "✍️ " + value;
        }

        public String timeText() {
            return String.format(Locale.ROOT, "%02d:%02d–%02d:%02d", sh, sm, eh, em) + "  ·  " + daysText(days);
        }

        public boolean activeAt(Calendar c) {
            if (!on) return false;
            int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            int s = sh * 60 + sm, e = eh * 60 + em;
            int day = WakeScheduler.dayIndex(c);
            if (s <= e) {
                return (days & (1 << day)) != 0 && now >= s && now < e;
            }
            // gece yarısını aşan aralık (ör. 23:00–02:00): başlangıç günü sayılır
            if (now >= s) return (days & (1 << day)) != 0;
            int prev = (day + 6) % 7;
            return now < e && (days & (1 << prev)) != 0;
        }

        public Moods.Target target(boolean turkish) {
            Moods.Target t = null;
            if ("mood".equals(kind)) t = Moods.target(Moods.mood(value), null, turkish);
            else if ("genre".equals(kind)) {
                Genres.Genre g = Genres.byId(value);
                if (g != null) t = Genres.target(g);
            } else {
                t = Vibe.parse(value, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)).target(turkish);
            }
            if (t != null) t.label = "🗓 " + label();
            return t;
        }

        JSONObject toJson() throws org.json.JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("days", days);
            o.put("sh", sh); o.put("sm", sm); o.put("eh", eh); o.put("em", em);
            o.put("kind", kind); o.put("value", value); o.put("on", on);
            return o;
        }

        static Routine fromJson(JSONObject o) {
            Routine r = new Routine();
            r.id = o.optString("id", r.id);
            r.days = o.optInt("days", r.days);
            r.sh = o.optInt("sh"); r.sm = o.optInt("sm"); r.eh = o.optInt("eh"); r.em = o.optInt("em");
            r.kind = o.optString("kind", "mood");
            r.value = o.optString("value", "odak");
            r.on = o.optBoolean("on", true);
            return r;
        }
    }

    public static String daysText(int d) {
        if (d == 0b1111111) return "her gün";
        if (d == 0b0011111) return "hafta içi";
        if (d == 0b1100000) return "hafta sonu";
        String[] n = {"Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz"};
        List<String> l = new ArrayList<>();
        for (int i = 0; i < 7; i++) if ((d & (1 << i)) != 0) l.add(n[i]);
        return l.isEmpty() ? "gün seçilmedi" : String.join(" ", l);
    }

    // ---------------------------------------------------------------------

    private static List<Routine> cache;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    public static synchronized List<Routine> all(Context c) {
        if (cache == null) {
            cache = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(prefs(c).getString("routines", "[]"));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null) cache.add(Routine.fromJson(o));
                }
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(cache);
    }

    public static synchronized void save(Context c, List<Routine> list) {
        cache = new ArrayList<>(list);
        JSONArray a = new JSONArray();
        try {
            for (Routine r : list) a.put(r.toJson());
        } catch (Exception ignored) {}
        prefs(c).edit().putString("routines", a.toString()).apply();
    }

    public static void add(Context c, Routine r) {
        List<Routine> l = all(c);
        l.add(r);
        save(c, l);
    }

    public static void remove(Context c, String id) {
        List<Routine> l = all(c);
        l.removeIf(r -> r.id.equals(id));
        save(c, l);
    }

    public static void setOn(Context c, String id, boolean on) {
        List<Routine> l = all(c);
        for (Routine r : l) if (r.id.equals(id)) r.on = on;
        save(c, l);
    }

    /** Şu an geçerli rutin (yoksa null). */
    public static Routine active(Context c) {
        Calendar now = Calendar.getInstance();
        for (Routine r : all(c)) if (r.activeAt(now)) return r;
        return null;
    }

    /** Hazır şablonlar. */
    public static Routine template(int i) {
        Routine r = new Routine();
        switch (i) {
            case 0: r.kind = "mood"; r.value = "odak"; r.days = 0b0011111; r.sh = 9; r.eh = 12; break;
            case 1: r.kind = "mood"; r.value = "enerjik"; r.days = 0b0010000; r.sh = 20; r.eh = 0; break;
            case 2: r.kind = "mood"; r.value = "sakin"; r.days = 0b1111111; r.sh = 23; r.eh = 2; break;
            case 3: r.kind = "genre"; r.value = "akustik"; r.days = 0b1000000; r.sh = 10; r.eh = 13; break;
            default: r.kind = "vibe"; r.value = "sabah keyifli uyanış"; r.days = 0b0011111; r.sh = 7; r.eh = 9; break;
        }
        return r;
    }

    public static final String[] TEMPLATE_NAMES = {
        "🎧 Hafta içi 09–12 Odak", "⚡ Cuma 20–24 Enerjik", "🌙 Her gece 23–02 Sakin",
        "🪵 Pazar 10–13 Akustik", "☀️ Hafta içi 07–09 Sabah keyfi"
    };
}
