package com.akillisira.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Kalıcı veriler: dinleme geçmişi (dosya) ve beğenilmeyen şarkılar. */
public final class Store {

    public static final class Play {
        public long ts;
        public String title = "", artist = "", cover, mood;
        public double bpm, energy = -1;
        public int lang;
        public long durMs;
    }

    public static final class Disliked {
        public String key, artist, title;
        public long ts;
    }

    private static final int MAX_PLAYS = 2000;

    private final File file;
    private final SharedPreferences dislikes;
    private final SharedPreferences likes;
    private final SharedPreferences badTrans;
    private List<Play> plays;          // en yeni başta
    private volatile int version;

    public Store(Context c) {
        file = new File(c.getFilesDir(), "gecmis.jsonl");
        dislikes = c.getSharedPreferences("begenmedim", Context.MODE_PRIVATE);
        likes = c.getSharedPreferences("begendim", Context.MODE_PRIVATE);
        badTrans = c.getSharedPreferences("kotugecis", Context.MODE_PRIVATE);
    }

    /** Ekranın ne zaman yeniden çizileceğini bilmesi için. */
    public int version() { return version; }

    // =====================================================================
    // Dinleme geçmişi
    // =====================================================================

    /** Kopya döner (arka planda eklenirken ekran güvenle okuyabilsin). */
    public synchronized List<Play> plays() {
        if (plays == null) load();
        return new ArrayList<>(plays);
    }

    public synchronized void addPlay(Play p) {
        if (plays == null) load();
        plays.add(0, p);
        if (plays.size() > MAX_PLAYS) {
            plays = new ArrayList<>(plays.subList(0, MAX_PLAYS));
            rewrite();
        } else {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                w.write(toJson(p).toString());
                w.write('\n');
            } catch (Exception ignored) {}
        }
        version++;
    }

    public synchronized void clearHistory() {
        plays = new ArrayList<>();
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        version++;
    }

    private void load() {
        plays = new ArrayList<>();
        if (!file.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                try {
                    plays.add(0, fromJson(new JSONObject(line)));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        if (plays.size() > MAX_PLAYS) {
            plays = new ArrayList<>(plays.subList(0, MAX_PLAYS));
            rewrite();
        }
    }

    private void rewrite() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            for (int i = plays.size() - 1; i >= 0; i--) {
                w.write(toJson(plays.get(i)).toString());
                w.write('\n');
            }
        } catch (Exception ignored) {}
    }

    // =====================================================================
    // Yedekleme: dışa / içe aktarma
    // =====================================================================

    /** Beğeniler, beğenilmeyenler ve geçmiş tek JSON nesnesinde. */
    public synchronized JSONObject exportJson() throws org.json.JSONException {
        JSONObject o = new JSONObject();
        JSONObject lk = new JSONObject();
        for (Map.Entry<String, ?> en : likes.getAll().entrySet()) lk.put(en.getKey(), String.valueOf(en.getValue()));
        o.put("likes", lk);
        JSONObject dk = new JSONObject();
        for (Map.Entry<String, ?> en : dislikes.getAll().entrySet()) dk.put(en.getKey(), String.valueOf(en.getValue()));
        o.put("dislikes", dk);
        org.json.JSONArray pl = new org.json.JSONArray();
        List<Play> all = plays();
        for (int i = all.size() - 1; i >= 0; i--) pl.put(toJson(all.get(i)));
        o.put("plays", pl);
        return o;
    }

    /** Var olanlarla birleştirir; {beğeni, beğenmeme, geçmiş} eklenen sayıları döner. */
    public synchronized int[] importJson(JSONObject o) {
        int nl = 0, nd = 0, np = 0;
        JSONObject lk = o.optJSONObject("likes");
        if (lk != null) {
            SharedPreferences.Editor ed = likes.edit();
            org.json.JSONArray names = lk.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String k = names.optString(i);
                if (!likes.contains(k)) { ed.putString(k, lk.optString(k)); nl++; }
            }
            ed.apply();
        }
        JSONObject dk = o.optJSONObject("dislikes");
        if (dk != null) {
            SharedPreferences.Editor ed = dislikes.edit();
            org.json.JSONArray names = dk.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String k = names.optString(i);
                if (!dislikes.contains(k) && !likes.contains(k)) { ed.putString(k, dk.optString(k)); nd++; }
            }
            ed.apply();
        }
        org.json.JSONArray pl = o.optJSONArray("plays");
        if (pl != null) {
            if (plays == null) load();
            java.util.Set<String> have = new java.util.HashSet<>();
            for (Play p : plays) have.add(p.ts + "|" + p.title);
            for (int i = 0; i < pl.length(); i++) {
                JSONObject j = pl.optJSONObject(i);
                if (j == null) continue;
                Play p = fromJson(j);
                if (have.add(p.ts + "|" + p.title)) { plays.add(p); np++; }
            }
            plays.sort((x, y) -> Long.compare(y.ts, x.ts));
            if (plays.size() > MAX_PLAYS) plays = new ArrayList<>(plays.subList(0, MAX_PLAYS));
            rewrite();
        }
        version++;
        return new int[]{nl, nd, np};
    }

    private static JSONObject toJson(Play p) {
        JSONObject o = new JSONObject();
        try {
            o.put("ts", p.ts);
            o.put("t", p.title);
            o.put("a", p.artist);
            if (p.cover != null) o.put("c", p.cover);
            if (p.mood != null) o.put("m", p.mood);
            o.put("b", p.bpm);
            o.put("e", p.energy);
            o.put("l", p.lang);
            o.put("d", p.durMs);
        } catch (Exception ignored) {}
        return o;
    }

    private static Play fromJson(JSONObject o) {
        Play p = new Play();
        p.ts = o.optLong("ts");
        p.title = o.optString("t");
        p.artist = o.optString("a");
        p.cover = o.has("c") ? o.optString("c") : null;
        p.mood = o.has("m") ? o.optString("m") : null;
        p.bpm = o.optDouble("b", 0);
        p.energy = o.optDouble("e", -1);
        p.lang = o.optInt("l");
        p.durMs = o.optLong("d");
        return p;
    }

    // =====================================================================
    // Beğenilenler ❤️
    // =====================================================================

    public static final class Liked {
        public String key, artist, title, cover;
        public long deezerId, artistId, ts;

        public Track toTrack() {
            Track t = new Track();
            t.title = title;
            t.artist = artist;
            t.deezerId = deezerId;
            t.artistId = artistId;
            t.coverUrl = cover;
            t.lang = Lang.detect(title, artist);
            t.sources.add("plan");
            return t;
        }
    }

    public synchronized void like(String key, String artist, String title, long deezerId, long artistId, String cover) {
        likes.edit().putString(key, artist + "\t" + title + "\t" + System.currentTimeMillis() + "\t"
                + deezerId + "\t" + artistId + "\t" + (cover == null ? "" : cover)).apply();
        undislikeQuiet(key);
        version++;
    }

    public synchronized void unlike(String key) {
        likes.edit().remove(key).apply();
        version++;
    }

    public synchronized boolean isLiked(String key) {
        return key != null && likes.contains(key);
    }

    public synchronized java.util.Set<String> likedKeys() {
        return new java.util.HashSet<>(likes.getAll().keySet());
    }

    public synchronized List<Liked> liked() {
        List<Liked> out = new ArrayList<>();
        for (Map.Entry<String, ?> en : likes.getAll().entrySet()) {
            String[] p = String.valueOf(en.getValue()).split("\t", -1);
            Liked l = new Liked();
            l.key = en.getKey();
            l.artist = p.length > 0 ? p[0] : "";
            l.title = p.length > 1 ? p[1] : "";
            l.ts = parseLong(p, 2);
            l.deezerId = parseLong(p, 3);
            l.artistId = parseLong(p, 4);
            l.cover = p.length > 5 && !p[5].isEmpty() ? p[5] : null;
            out.add(l);
        }
        out.sort((x, y) -> Long.compare(y.ts, x.ts));
        return out;
    }

    /** Sanatçı (normalize) → beğenilen şarkı sayısı */
    public synchronized Map<String, Integer> likedArtists() {
        Map<String, Integer> out = new HashMap<>();
        for (Liked l : liked()) {
            String a = Text.norm(Text.mainArtist(l.artist));
            out.put(a, out.getOrDefault(a, 0) + 1);
        }
        return out;
    }

    private static long parseLong(String[] p, int i) {
        try { return p.length > i && !p[i].isEmpty() ? Long.parseLong(p[i]) : 0; } catch (Exception e) { return 0; }
    }

    private void undislikeQuiet(String key) {
        if (dislikes.contains(key)) dislikes.edit().remove(key).apply();
    }

    // =====================================================================
    // 👎 Kötü geçişler: "tarzA→tarzB" sayaçları ve "sanatçıA→sanatçıB" çiftleri
    // =====================================================================

    public synchronized void addBadTransition(String sigA, String sigB, String artistA, String artistB) {
        SharedPreferences.Editor ed = badTrans.edit();
        if (sigA != null && sigB != null && !sigA.equals(sigB)) {
            String k = "s:" + sigA + "→" + sigB;
            ed.putInt(k, badTrans.getInt(k, 0) + 1);
        }
        ed.putInt("a:" + artistA + "→" + artistB, 1);
        ed.apply();
        version++;
    }

    public synchronized Map<String, Integer> badPairs() {
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, ?> en : badTrans.getAll().entrySet()) {
            if (en.getKey().startsWith("s:") && en.getValue() instanceof Integer) {
                out.put(en.getKey().substring(2), (Integer) en.getValue());
            }
        }
        return out;
    }

    public synchronized java.util.Set<String> badArtistPairs() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String k : badTrans.getAll().keySet()) if (k.startsWith("a:")) out.add(k.substring(2));
        return out;
    }

    public synchronized int badTransitionCount() {
        return badTrans.getAll().size();
    }

    public synchronized void clearBadTransitions() {
        badTrans.edit().clear().apply();
        version++;
    }

    // =====================================================================
    // Beğenilmeyenler
    // =====================================================================

    public synchronized void dislike(String key, String artist, String title) {
        if (likes.contains(key)) likes.edit().remove(key).apply();
        dislikes.edit().putString(key, artist + "\t" + title + "\t" + System.currentTimeMillis()).apply();
        version++;
    }

    public synchronized void undislike(String key) {
        dislikes.edit().remove(key).apply();
        version++;
    }

    public synchronized boolean isDisliked(String key) {
        return dislikes.contains(key);
    }

    public synchronized List<String> dislikedKeys() {
        return new ArrayList<>(dislikes.getAll().keySet());
    }

    /** Sanatçı (normalize) → beğenilmeyen şarkı sayısı */
    public synchronized Map<String, Integer> dislikedArtists() {
        Map<String, Integer> out = new HashMap<>();
        for (Disliked d : disliked()) {
            String a = Text.norm(Text.mainArtist(d.artist));
            out.put(a, out.getOrDefault(a, 0) + 1);
        }
        return out;
    }

    public synchronized List<Disliked> disliked() {
        List<Disliked> out = new ArrayList<>();
        for (Map.Entry<String, ?> en : dislikes.getAll().entrySet()) {
            String[] parts = String.valueOf(en.getValue()).split("\t");
            Disliked d = new Disliked();
            d.key = en.getKey();
            d.artist = parts.length > 0 ? parts[0] : "";
            d.title = parts.length > 1 ? parts[1] : "";
            try { d.ts = parts.length > 2 ? Long.parseLong(parts[2]) : 0; } catch (Exception e) { d.ts = 0; }
            out.add(d);
        }
        out.sort((x, y) -> Long.compare(y.ts, x.ts));
        return out;
    }
}
