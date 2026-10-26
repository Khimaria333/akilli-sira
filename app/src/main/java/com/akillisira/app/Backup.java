package com.akillisira.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Yedek dosyası: beğeniler, beğenilmeyenler, geçmiş ve ayarlar (Last.fm anahtarı hariç). */
public final class Backup {
    private Backup() {}

    private static final String[] BOOL_KEYS = {"auto", "turkish", "fade", "returnToApp", "notif", "nightCalm",
            "carMode", "dj", "wakeOn"};
    private static final String[] INT_KEYS = {"mode", "fadeSec", "explore", "wakeH", "wakeM", "wakeDays", "wakeVol"};

    public static void write(Context c, Store store, Uri uri) throws Exception {
        JSONObject o = store.exportJson();
        o.put("app", "Akıllı Sıra");
        o.put("version", 1);
        o.put("exportedAt", System.currentTimeMillis());
        SharedPreferences p = c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
        JSONObject st = new JSONObject();
        for (String k : BOOL_KEYS) if (p.contains(k)) st.put(k, p.getBoolean(k, false));
        for (String k : INT_KEYS) if (p.contains(k)) st.put(k, p.getInt(k, 0));
        o.put("settings", st);
        try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new Exception("Dosya açılamadı");
            out.write(o.toString(1).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** {beğeni, beğenmeme, geçmiş} eklenen sayıları döner. */
    public static int[] read(Context c, Store store, Uri uri) throws Exception {
        String text;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Dosya açılamadı");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            text = bo.toString(StandardCharsets.UTF_8.name());
        }
        JSONObject o = new JSONObject(text);
        if (!o.has("likes") && !o.has("plays")) throw new Exception("Bu bir Akıllı Sıra yedeği değil");
        int[] r = store.importJson(o);
        JSONObject st = o.optJSONObject("settings");
        if (st != null) {
            SharedPreferences.Editor ed = c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE).edit();
            for (String k : BOOL_KEYS) if (st.has(k)) ed.putBoolean(k, st.optBoolean(k));
            for (String k : INT_KEYS) if (st.has(k)) ed.putInt(k, st.optInt(k));
            ed.apply();
        }
        return r;
    }
}
