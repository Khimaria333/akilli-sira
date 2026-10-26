package com.akillisira.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serbest cümleden ruh hali çıkarır.
 * "akşamüstü sahilde sakin bir şeyler" → Sakin + Sahil + Gün batımı → hedef tempo/enerji + arama sorguları.
 * Tamamen telefonda çalışır; anahtar kelime kökleriyle eşleştirir (ekleri tolere eder: sahil-de, yağmur-lu).
 */
public final class Vibe {
    private Vibe() {}

    // ----------------------------------------------------------------------
    // Sahneler: mekan/ortam/zaman; kendi arama sorguları ve tempo/enerji ayarı var
    // ----------------------------------------------------------------------
    public static final class Scene {
        public final String id, label, emoji, defaultMood;
        final String[] keys, qTr, qGl;
        final double bpmShift, gainShift;

        Scene(String id, String label, String emoji, String defaultMood, double bpmShift, double gainShift,
              String[] keys, String[] qTr, String[] qGl) {
            this.id = id; this.label = label; this.emoji = emoji; this.defaultMood = defaultMood;
            this.bpmShift = bpmShift; this.gainShift = gainShift;
            this.keys = keys; this.qTr = qTr; this.qGl = qGl;
        }
    }

    private static String[] a(String... s) { return s; }

    static final Scene[] SCENES = {
        new Scene("sahil", "Sahil", "🏖️", "sakin", -0.02, -0.5,
                a("sahil", "deniz", "kumsal", "plaj", "kıyı", "iskele", "dalga", "marina"),
                a("türkçe yaz akşamı", "türkçe deniz şarkıları"), a("beach vibes", "summer chill")),
        new Scene("gunbatimi", "Gün batımı", "🌅", "sakin", -0.03, -0.5,
                a("gün batımı", "günbatımı", "akşamüstü", "akşam üstü", "akşamüzeri", "akşam üzeri", "gurup"),
                a("akşamüstü türkçe", "gün batımı türkçe"), a("sunset chill", "golden hour")),
        new Scene("aksam", "Akşam", "🌆", "sakin", -0.02, -0.5,
                a("akşam"),
                a("akşam türkçe şarkılar"), a("evening chill")),
        new Scene("gece", "Gece", "🌃", "sakin", -0.03, -1.0,
                a("gece", "geceyarısı", "gece yarısı", "geç saat", "uykusuz"),
                a("gece türkçe şarkılar", "gece yarısı türkçe"), a("late night vibes", "night drive")),
        new Scene("sabah", "Sabah", "☀️", "mutlu", 0.02, 0.0,
                a("sabah", "kahvaltı", "uyandım", "güne başla", "günaydın"),
                a("günaydın türkçe", "sabah türkçe şarkılar"), a("morning acoustic", "good morning")),
        new Scene("yagmur", "Yağmur", "🌧️", "huzunlu", -0.03, -1.0,
                a("yağmur", "kapalı hava", "bulutlu", "fırtına", "gri hava"),
                a("yağmurlu gün türkçe", "yağmur türkçe şarkılar"), a("rainy day", "rainy jazz")),
        new Scene("kahve", "Kahve", "☕", "sakin", -0.02, -1.0,
                a("kahve", "kafe", "cafe", "çay", "kitapçı", "kitap okurken"),
                a("kahve eşliğinde türkçe", "türkçe akustik kahve"), a("coffee shop acoustic", "cafe music")),
        new Scene("yaz", "Yaz", "🌞", "mutlu", 0.03, 0.5,
                a("yaz ", "yazın", "yaz akşam", "yaz gece", "tatil", "havuz", "güneşli", "sıcak"),
                a("türkçe yaz şarkıları", "yaz hitleri türkçe"), a("summer hits", "summer vibes")),
        new Scene("kis", "Kış", "❄️", "sakin", -0.02, -0.5,
                a("kış", "kar ", "karlı", "soğuk", "şömine", "battaniye"),
                a("kış türkçe şarkılar"), a("winter chill", "cozy acoustic")),
        new Scene("doga", "Doğa", "🌲", "sakin", -0.02, -0.5,
                a("doğa", "orman", "kamp", "dağ", "göl", "piknik", "bahçe", "ağaç"),
                a("kamp ateşi türkçe", "türkçe akustik"), a("camping acoustic", "indie folk")),
        new Scene("meyhane", "Rakı masası", "🥂", "nostaljik", -0.02, -0.5,
                a("rakı", "meyhane", "fasıl", "rakı masası"),
                a("rakı masası şarkıları", "türk sanat müziği fasıl"), a("rakı masası şarkıları")),
        new Scene("arkadas", "Arkadaşlar", "🍻", "mutlu", 0.02, 0.5,
                a("arkadaş", "kanka", "mangal", "muhabbet", "dostlar", "ekiple"),
                a("arkadaşlarla türkçe şarkılar", "türkçe pop hit"), a("friends hangout", "feel good hits")),
        new Scene("yemek", "Akşam yemeği", "🍷", "romantik", -0.03, -1.0,
                a("yemek", "akşam yemeği", "şarap", "restoran", "mum ışığı"),
                a("akşam yemeği türkçe", "türkçe caz"), a("dinner jazz", "dinner party")),
    };

    // ----------------------------------------------------------------------
    // Ruh hali ve mekan anahtar kelimeleri (kökler)
    // ----------------------------------------------------------------------
    private static final Map<String, String[]> MOOD_KEYS = new LinkedHashMap<>();
    private static final Map<String, String[]> PLACE_KEYS = new LinkedHashMap<>();
    static {
        MOOD_KEYS.put("nostaljik", a("nostalji", "eskiler", "eski şarkı", "90", "2000", "80", "çocukluk", "anılar", "anı ",
                "geçmiş", "eski günler", "retro"));
        MOOD_KEYS.put("huzunlu", a("hüzün", "üzgün", "mutsuz", "kırgın", "ağla", "ayrıl", "yalnız", "melankoli",
                "buruk", "depres", "kalbim kırık", "özle", "dertli", "duygusal", "efkar", "hasret"));
        MOOD_KEYS.put("romantik", a("romantik", "aşk", "sevgili", "aşık", "flört", "randevu", "öp", "sarıl",
                "evlilik", "nişan", "ilk buluşma"));
        MOOD_KEYS.put("enerjik", a("enerji", "hareketli", "coşku", "gaz", "hızlı", "dans", "zıpla", "motivasyon",
                "hareket", "yüksek tempo", "çılgın", "kopmak", "uçmak", "hype"));
        MOOD_KEYS.put("asi", a("sinir", "öfke", "kızgın", "rock", "isyan", "hırs", "stres at", "bağır", "metal"));
        MOOD_KEYS.put("odak", a("odak", "ders", "ödev", "konsantre", "kod", "sınav", "verimli", "üretken",
                "kitap oku", "yazı yaz"));
        MOOD_KEYS.put("sakin", a("sakin", "huzur", "rahat", "dinlen", "chill", "yavaş", "sessiz", "kafa dinle", "kafamı dinle", "kafamı boşalt",
                "hafif", "dingin", "sakinleş", "rahatla", "tatlı", "lounge", "soft"));
        MOOD_KEYS.put("mutlu", a("mutlu", "neşe", "keyif", "sevinç", "güzel bir gün", "havamda", "pozitif",
                "eğlen", "gülümse", "iyi hissed"));

        PLACE_KEYS.put("spor", a("spor", "gym", "koşu", "koş", "antrenman", "fitness", "salon", "idman", "bisiklet"));
        PLACE_KEYS.put("araba", a("araba", "yolculuk", "otoban", "direksiyon", "sürüş", "road trip", "yola"));
        PLACE_KEYS.put("parti", a("parti", "kutlama", "doğum günü", "eğlence", "kulüp", "düğün"));
        PLACE_KEYS.put("is", a("iş", "ofis", "çalışırken", "mesai", "toplantı"));
        PLACE_KEYS.put("yuruyus", a("yürüyüş", "yürü", "park", "sokak", "gezinti", "gezer"));
        PLACE_KEYS.put("uyku", a("uyku", "uyumadan", "yatarken", "yatmadan", "uyuyacağım", "uyumak"));
        PLACE_KEYS.put("ev", a("ev", "evde", "oda", "koltuk", "kanepe"));
    }

    // Yoğunluk sözcükleri
    private static final String[] CALMER = a("çok sakin", "iyice sakin", "fazla sakin", "yumuşak", "usul");
    private static final String[] UPPER = a("çok hareketli", "çok enerjik", "bas bas", "full gaz", "son ses");

    // ----------------------------------------------------------------------
    public static final class Result {
        public String moodId;
        public String placeId;
        public final List<Scene> scenes = new ArrayList<>();
        public double bpmShift, gainShift;
        public final List<String> understood = new ArrayList<>();
        public boolean guessedMood;    // ruh hali açıkça yazılmadı, tahmin edildi

        /** Okunabilir özet: "🌙 Sakin · 🏖️ Sahil · 🌅 Gün batımı" */
        public String summary() {
            return String.join("  ·  ", understood);
        }

        /** Puanlamada kullanılacak hedef. */
        public Moods.Target target(boolean turkish) {
            Moods.Target t = Moods.target(Moods.mood(moodId), Moods.place(placeId), turkish);
            if (t == null) return null;
            double f = 1 + bpmShift;
            t.bpmLo *= f;
            t.bpmHi *= f;
            t.gain += gainShift;
            List<String> q = new ArrayList<>();
            for (int i = 0; i < scenes.size() && i < 2; i++) {
                String[] sq = turkish ? scenes.get(i).qTr : scenes.get(i).qGl;
                if (sq.length > 0) q.add(sq[0]);
            }
            // sahnenin ilk sorgusu + ruh halinin/mekanın sorguları
            for (String s : t.queries) if (!q.contains(s)) q.add(s);
            t.queries.clear();
            t.queries.addAll(q);
            t.label = summary();
            return t;
        }
    }

    /** hour: 0-23, cümlede zaman geçmiyorsa ruh hali tahmininde kullanılır. */
    public static Result parse(String text, int hour) {
        Result r = new Result();
        String s = " " + fold(text) + " ";

        // Sahneler
        for (Scene sc : SCENES) {
            if (matchAny(s, sc.keys)) {
                // "akşamüstü" eşleşince düz "akşam"ı tekrar ekleme
                if (sc.id.equals("aksam") && hasScene(r, "gunbatimi")) continue;
                if (sc.id.equals("aksam") && matchAny(s, a("akşam yemeği"))) continue;
                if (sc.id.equals("gece") && hasScene(r, "aksam")) continue;
                r.scenes.add(sc);
                r.bpmShift += sc.bpmShift;
                r.gainShift += sc.gainShift;
            }
        }

        // Ruh hali: en çok eşleşen
        int best = 0;
        for (Map.Entry<String, String[]> en : MOOD_KEYS.entrySet()) {
            int n = countAny(s, en.getValue());
            if (n > best) { best = n; r.moodId = en.getKey(); }
        }
        // Mekan
        best = 0;
        for (Map.Entry<String, String[]> en : PLACE_KEYS.entrySet()) {
            int n = countAny(s, en.getValue());
            if (n > best) { best = n; r.placeId = en.getKey(); }
        }

        // Yoğunluk
        if (matchAny(s, CALMER)) { r.bpmShift -= 0.04; r.gainShift -= 1.0; }
        if (matchAny(s, UPPER)) { r.bpmShift += 0.04; r.gainShift += 1.0; }

        // Ruh hali yoksa: sahneden, mekandan ya da saatten tahmin et
        if (r.moodId == null) {
            r.guessedMood = true;
            if (!r.scenes.isEmpty()) r.moodId = r.scenes.get(0).defaultMood;
            else if ("spor".equals(r.placeId) || "parti".equals(r.placeId)) r.moodId = "enerjik";
            else if ("uyku".equals(r.placeId)) r.moodId = "sakin";
            else if ("is".equals(r.placeId)) r.moodId = "odak";
            else if (hour >= 22 || hour < 6) r.moodId = "sakin";
            else if (hour < 11) r.moodId = "mutlu";
            else r.moodId = "mutlu";
        }

        Moods.Mood m = Moods.mood(r.moodId);
        if (m != null) r.understood.add(m.emoji + " " + m.label);
        Moods.Place p = Moods.place(r.placeId);
        if (p != null) r.understood.add(p.emoji + " " + p.label);
        for (Scene sc : r.scenes) r.understood.add(sc.emoji + " " + sc.label);
        return r;
    }

    // ----------------------------------------------------------------------

    private static boolean hasScene(Result r, String id) {
        for (Scene s : r.scenes) if (s.id.equals(id)) return true;
        return false;
    }

    private static boolean matchAny(String s, String[] keys) {
        return countAny(s, keys) > 0;
    }

    /** Anahtar kelime bir sözcüğün başında geçiyorsa sayar (ekleri tolere eder). */
    private static int countAny(String s, String[] keys) {
        int n = 0;
        for (String k : keys) {
            String fk = fold(k);
            if (fk.isEmpty()) continue;
            if (k.endsWith(" ")) {               // tam sözcük isteniyor ("kar ")
                if (s.contains(" " + fk + " ")) n++;
            } else if (fk.length() <= 3) {       // kısa kökler tam sözcük ya da 1-3 harf ekle
                if (s.matches("(?s).*\\s" + java.util.regex.Pattern.quote(fk) + "\\p{L}{0,3}\\s.*")) n++;
            } else if (s.contains(" " + fk)) {
                n++;
            }
        }
        return n;
    }

    /** Küçük harf + Türkçe karakterleri sadeleştir + noktalama → boşluk. */
    static String fold(String in) {
        if (in == null) return "";
        String t = in.replace('I', 'ı').replace('İ', 'i').toLowerCase(Locale.ROOT);
        t = t.replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
             .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c').replace('â', 'a');
        t = t.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return t;
    }
}
