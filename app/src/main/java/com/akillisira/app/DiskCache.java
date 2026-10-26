package com.akillisira.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Telefona kaydedilen önbellek: Last.fm etiketleri, Deezer tempo/tür bilgileri, YT Music bağlantıları.
 * Uygulama kapanıp açılınca her şeyi baştan sormaz; sıradaki şarkı çok daha hızlı hazırlanır.
 * Her ad alanı (namespace) ayrı bir JSON dosyasıdır; değişiklikler birkaç saniye toplanıp yazılır.
 */
public final class DiskCache {

    public static final long DAY = 24L * 3600 * 1000;

    private static Context app;
    private static final Map<String, DiskCache> SPACES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService SAVER = Executors.newSingleThreadScheduledExecutor();

    public static void init(Context c) {
        if (app == null) app = c.getApplicationContext();
    }

    /** Ad alanını döndürür (ör. "lfm_artist"). init çağrılmadıysa sadece bellekte çalışır. */
    public static DiskCache of(String name, int maxEntries) {
        return SPACES.computeIfAbsent(name, n -> new DiskCache(n, maxEntries));
    }

    private final String name;
    private final int max;
    private final LinkedHashMap<String, Object[]> map = new LinkedHashMap<>(); // key → {ts(Long), value(String)}
    private boolean loaded, dirty, saveScheduled;

    private DiskCache(String name, int max) {
        this.name = name;
        this.max = max;
    }

    /** Süresi dolmamışsa değeri, yoksa null döner. */
    public synchronized String get(String key, long ttlMs) {
        load();
        Object[] e = map.get(key);
        if (e == null) return null;
        if (System.currentTimeMillis() - (Long) e[0] > ttlMs) {
            map.remove(key);
            return null;
        }
        return (String) e[1];
    }

    public synchronized void put(String key, String value) {
        load();
        map.remove(key);
        map.put(key, new Object[]{System.currentTimeMillis(), value == null ? "" : value});
        while (map.size() > max) {
            Iterator<String> it = map.keySet().iterator();
            it.next();
            it.remove();
        }
        dirty = true;
        scheduleSave();
    }

    public synchronized int size() {
        load();
        return map.size();
    }

    public synchronized void clear() {
        map.clear();
        dirty = true;
        scheduleSave();
    }

    // ---------------------------------------------------------------------

    private File file() {
        return app == null ? null : new File(app.getFilesDir(), "cache_" + name + ".json");
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (f == null || !f.exists()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            JSONObject o = new JSONObject(new String(b, 0, off, StandardCharsets.UTF_8));
            JSONArray keys = o.names();
            if (keys == null) return;
            // eski kayıtlar önce gelsin diye zamana göre sırala
            java.util.List<Object[]> rows = new java.util.ArrayList<>();
            for (int i = 0; i < keys.length(); i++) {
                String k = keys.getString(i);
                JSONArray v = o.optJSONArray(k);
                if (v == null || v.length() < 2) continue;
                rows.add(new Object[]{k, v.optLong(0), v.optString(1)});
            }
            rows.sort((x, y) -> Long.compare((Long) x[1], (Long) y[1]));
            for (Object[] r : rows) map.put((String) r[0], new Object[]{r[1], r[2]});
        } catch (Exception ignored) {
            // bozuk dosya: boş başla
        }
    }

    private void scheduleSave() {
        if (saveScheduled || app == null) return;
        saveScheduled = true;
        SAVER.schedule(this::save, 4, TimeUnit.SECONDS);
    }

    private void save() {
        JSONObject o = new JSONObject();
        synchronized (this) {
            saveScheduled = false;
            if (!dirty) return;
            dirty = false;
            try {
                for (Map.Entry<String, Object[]> en : map.entrySet()) {
                    JSONArray v = new JSONArray();
                    v.put(en.getValue()[0]);
                    v.put(en.getValue()[1]);
                    o.put(en.getKey(), v);
                }
            } catch (Exception e) {
                return;
            }
        }
        File f = file();
        if (f == null) return;
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    /** Ayarlar'daki "önbelleği temizle" için: tüm ad alanları. */
    public static void clearAll() {
        for (DiskCache c : SPACES.values()) c.clear();
    }

    public static int totalEntries() {
        int n = 0;
        for (DiskCache c : SPACES.values()) n += c.size();
        return n;
    }

    // ---- Basit kodlama yardımcıları ----

    public static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(parts[i] == null ? "" : parts[i].replace('\t', ' '));
        }
        return sb.toString();
    }

    public static String[] split(String s) {
        return s.split("\t", -1);
    }
}
