package com.akillisira.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Ahenk Blend: zevkini kısa bir koda çevirir (sevdiğin sanatçılar, beğendiğin şarkılar, tarz etiketleri).
 * Arkadaşının kodunu girince ikinizin zevkini harmanlayan ortak bir liste hazırlanır.
 * Kod sadece Deezer kimlikleri ve adlar içerir; kişisel bilgi yoktur.
 */
public final class Blend {
    private Blend() {}

    static final String PREFIX = "AHENK-";

    public static final class Taste {
        public String name = "";
        public final List<long[]> artistIds = new ArrayList<>();     // [id]
        public final List<String> artistNames = new ArrayList<>();
        public final List<Long> tracks = new ArrayList<>();
        public final List<String> tags = new ArrayList<>();
    }

    /** Benim zevkim. */
    static Taste mine(Engine e, String name) {
        Taste t = new Taste();
        t.name = name;
        for (Map.Entry<Long, String> a : NewReleases.likedArtists(e.store, 15).entrySet()) {
            t.artistIds.add(new long[]{a.getKey()});
            t.artistNames.add(a.getValue());
        }
        List<Store.Liked> liked = new ArrayList<>(e.store.liked());
        Collections.shuffle(liked);
        for (Store.Liked l : liked) {
            if (l.deezerId > 0) t.tracks.add(l.deezerId);
            if (t.tracks.size() >= 25) break;
        }
        for (String tag : e.profile().keySet()) {
            t.tags.add(tag);
            if (t.tags.size() >= 8) break;
        }
        return t;
    }

    static String encode(Taste t) {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("n", t.name);
            JSONArray a = new JSONArray();
            for (int i = 0; i < t.artistIds.size(); i++) a.put(new JSONArray().put(t.artistIds.get(i)[0]).put(t.artistNames.get(i)));
            o.put("a", a);
            JSONArray tr = new JSONArray();
            for (Long id : t.tracks) tr.put(id);
            o.put("t", tr);
            JSONArray g = new JSONArray();
            for (String s : t.tags) g.put(s);
            o.put("g", g);
            byte[] raw = o.toString().getBytes("UTF-8");
            Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
            d.setInput(raw);
            d.finish();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!d.finished()) bo.write(buf, 0, d.deflate(buf));
            d.end();
            return PREFIX + Base64.encodeToString(bo.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            return null;
        }
    }

    /** Yapıştırılan metnin içinden kodu bulup çözer (bozuksa null). */
    static Taste decode(String text) {
        if (text == null) return null;
        int i = text.indexOf(PREFIX);
        if (i < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int k = i + PREFIX.length(); k < text.length(); k++) {
            char ch = text.charAt(k);
            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') sb.append(ch);
            else break;
        }
        try {
            byte[] z = Base64.decode(sb.toString(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            Inflater inf = new Inflater();
            inf.setInput(z);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int guard = 0;
            while (!inf.finished() && guard++ < 1000) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                bo.write(buf, 0, n);
            }
            inf.end();
            JSONObject o = new JSONObject(bo.toString("UTF-8"));
            Taste t = new Taste();
            t.name = o.optString("n", "");
            JSONArray a = o.optJSONArray("a");
            for (int k = 0; a != null && k < a.length() && k < 30; k++) {
                JSONArray x = a.optJSONArray(k);
                if (x == null || x.optLong(0) <= 0) continue;
                t.artistIds.add(new long[]{x.optLong(0)});
                t.artistNames.add(x.optString(1, ""));
            }
            JSONArray tr = o.optJSONArray("t");
            for (int k = 0; tr != null && k < tr.length() && k < 40; k++) if (tr.optLong(k) > 0) t.tracks.add(tr.optLong(k));
            JSONArray g = o.optJSONArray("g");
            for (int k = 0; g != null && k < g.length() && k < 12; k++) t.tags.add(g.optString(k));
            if (t.artistIds.isEmpty() && t.tracks.isEmpty()) return null;
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Kayıtlı arkadaşlar ----

    public static final class Friend {
        public String name, code;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    static List<Friend> friends(Context c) {
        List<Friend> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("blends", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Friend f = new Friend();
                f.name = o.optString("name");
                f.code = o.optString("code");
                out.add(f);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void saveFriend(Context c, String name, String code) {
        List<Friend> l = friends(c);
        l.removeIf(f -> f.name.equalsIgnoreCase(name));
        Friend f = new Friend();
        f.name = name;
        f.code = code;
        l.add(0, f);
        JSONArray a = new JSONArray();
        for (int i = 0; i < l.size() && i < 10; i++) {
            try { a.put(new JSONObject().put("name", l.get(i).name).put("code", l.get(i).code)); } catch (Exception ignored) {}
        }
        prefs(c).edit().putString("blends", a.toString()).apply();
    }

    static void removeFriend(Context c, String name) {
        List<Friend> l = friends(c);
        l.removeIf(f -> f.name.equalsIgnoreCase(name));
        JSONArray a = new JSONArray();
        for (Friend f : l) {
            try { a.put(new JSONObject().put("name", f.name).put("code", f.code)); } catch (Exception ignored) {}
        }
        prefs(c).edit().putString("blends", a.toString()).apply();
    }
}
