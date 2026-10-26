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
                a("yaz şarkıları", "türkçe chill", "sahil"), a("beach vibes", "summer chill")),
        new Scene("gunbatimi", "Gün batımı", "🌅", "sakin", -0.03, -0.5,
                a("gün batımı", "günbatımı", "akşamüstü", "akşam üstü", "akşamüzeri", "akşam üzeri", "gurup"),
                a("türkçe chill", "türkçe akustik", "gün batımı"), a("sunset chill", "golden hour")),
        new Scene("aksam", "Akşam", "🌆", "sakin", -0.02, -0.5,
                a("akşam"),
                a("türkçe chill", "türkçe akustik"), a("evening chill")),
        new Scene("gece", "Gece", "🌃", "sakin", -0.03, -1.0,
                a("gece", "geceyarısı", "gece yarısı", "geç saat", "uykusuz"),
                a("türkçe slow", "gece", "türkçe akustik"), a("late night vibes", "night drive")),
        new Scene("sabah", "Sabah", "☀️", "mutlu", 0.02, 0.0,
                a("sabah", "kahvaltı", "uyandım", "güne başla", "günaydın"),
                a("günaydın", "türkçe pop hit", "türkçe akustik"), a("morning acoustic", "good morning")),
        new Scene("yagmur", "Yağmur", "🌧️", "huzunlu", -0.03, -1.0,
                a("yağmur", "kapalı hava", "bulutlu", "fırtına", "gri hava"),
                a("türkçe slow", "hüzünlü türkçe", "yağmur"), a("rainy day", "rainy jazz")),
        new Scene("kahve", "Kahve", "☕", "sakin", -0.02, -1.0,
                a("kahve", "kafe", "cafe", "çay", "kitapçı", "kitap okurken"),
                a("türkçe akustik", "kahve", "türkçe chill"), a("coffee shop acoustic", "cafe music")),
        new Scene("yaz", "Yaz", "🌞", "mutlu", 0.03, 0.5,
                a("yaz ", "yazın", "yaz akşam", "yaz gece", "tatil", "havuz", "güneşli", "sıcak"),
                a("yaz şarkıları", "yaz hitleri", "türkçe pop hit"), a("summer hits", "summer vibes")),
        new Scene("kis", "Kış", "❄️", "sakin", -0.02, -0.5,
                a("kış", "kar ", "karlı", "soğuk", "şömine", "battaniye"),
                a("türkçe akustik", "türkçe slow"), a("winter chill", "cozy acoustic")),
        new Scene("doga", "Doğa", "🌲", "sakin", -0.02, -0.5,
                a("doğa", "orman", "kamp", "dağ", "göl", "piknik", "bahçe", "ağaç"),
                a("türkçe akustik", "kamp", "türkçe alternatif"), a("camping acoustic", "indie folk")),
        new Scene("meyhane", "Rakı masası", "🥂", "nostaljik", -0.02, -0.5,
                a("rakı", "meyhane", "fasıl", "rakı masası"),
                a("rakı masası şarkıları", "türk sanat müziği", "fasıl", "arabesk"),
                a("rakı masası şarkıları", "türk sanat müziği")),
        new Scene("arkadas", "Arkadaşlar", "🍻", "mutlu", 0.02, 0.5,
                a("arkadaş", "kanka", "mangal", "muhabbet", "dostlar", "ekiple"),
                a("türkçe pop hit", "eğlenceli türkçe", "parti"), a("friends hangout", "feel good hits")),
        new Scene("yemek", "Akşam yemeği", "🍷", "romantik", -0.03, -1.0,
                a("yemek", "akşam yemeği", "şarap", "restoran", "mum ışığı"),
                a("türkçe caz", "türkçe akustik", "romantik türkçe"), a("dinner jazz", "dinner party")),
    };

    /** Sahnelerin Last.fm etiketleri. */
    private static final Map<String, String[]> SCENE_TAGS = new LinkedHashMap<>();
    /** Kendi başına tarif edici sahneler: tahmin edilen ruh halinin listeleri karışmasın. */
    private static final java.util.Set<String> STRONG = new java.util.HashSet<>(java.util.Arrays.asList("meyhane"));
    static {
        SCENE_TAGS.put("sahil", a("summer", "chill", "beach", "acoustic"));
        SCENE_TAGS.put("gunbatimi", a("chill", "sunset", "mellow", "acoustic"));
        SCENE_TAGS.put("aksam", a("chill", "mellow"));
        SCENE_TAGS.put("gece", a("night", "chill", "melancholic"));
        SCENE_TAGS.put("sabah", a("happy", "morning", "acoustic"));
        SCENE_TAGS.put("yagmur", a("melancholic", "sad", "rain", "acoustic"));
        SCENE_TAGS.put("kahve", a("acoustic", "coffee", "chill", "jazz"));
        SCENE_TAGS.put("yaz", a("summer", "dance", "party", "happy"));
        SCENE_TAGS.put("kis", a("winter", "melancholic", "acoustic"));
        SCENE_TAGS.put("doga", a("acoustic", "folk", "indie"));
        SCENE_TAGS.put("meyhane", a("arabesk", "türk sanat müziği", "turkish classical", "fasil", "fantezi", "arabesque"));
        SCENE_TAGS.put("arkadas", a("party", "happy", "dance"));
        SCENE_TAGS.put("yemek", a("jazz", "lounge", "romantic", "chill"));
    }

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

    // Türler: {Türkçe tür, Global tür, anahtar kelimeler...}. Sıra önemli (anadolu rock, rock'tan önce).
    private static final String[][] GENRE_KEYS = {
        {"anadolurock", "anadolurock", "anadolu rock", "anadolu rok"},
        {"rap", "g_hiphop", "rap ", "rapçı ", "rapçi", "rapler", "rapı ", "rapli ", "rapte ", "hip hop", "hiphop", "trap", "drill"},
        {"rock", "g_rock", "rock", "rok ", "metal", "punk", "grunge"},
        {"arabesk", "arabesk", "arabesk", "damar"},
        {"fantezi", "fantezi", "fantezi"},
        {"tsm", "tsm", "sanat müziği", "tsm", "klasik türk"},
        {"turku", "turku", "türkü", "halk müziği", "bağlama"},
        {"elektronik", "g_electronic", "elektronik", "edm", "house", "techno", "tekno", "electro"},
        {"caz", "g_jazz", "caz ", "cazlı", "jazz"},
        {"alternatif", "g_indie", "alternatif", "indie"},
        {"akustik", "g_acoustic", "akustik", "unplugged"},
        {"slow", "slow", "slow"},
        {"g_lofi", "g_lofi", "lofi", "lo fi"},
        {"g_rnb", "g_rnb", "rnb", "soul"},
        {"g_latin", "g_latin", "latin", "reggaeton"},
        {"g_classical", "g_classical", "klasik müzik"},
        {"pop", "g_pop", "pop"},
    };

    // "sert rap", "agresif bir şeyler"
    private static final String[] HARD = a("sert", "agresif", "hard", "hardcore", "vurucu", "karanlık", "dark");

    /** Tür + "sert" birlikteyken aranacak Last.fm etiketleri. */
    private static String[] hardTags(String genreId) {
        if (genreId == null) return a("aggressive", "heavy", "dark");
        switch (genreId) {
            case "rap": case "g_hiphop": return a("trap", "drill", "aggressive", "hardcore", "dark");
            case "rock": case "g_rock": case "anadolurock": return a("hard rock", "metal", "heavy", "aggressive");
            case "elektronik": case "g_electronic": return a("techno", "hard", "dark");
            default: return a("aggressive", "heavy", "dark");
        }
    }

    // Tür + mekan/ruh hali birleşik arama ekleri ("türkçe rap spor")
    private static String placeWord(String placeId, boolean turkish) {
        if (placeId == null) return null;
        if (!turkish) {
            switch (placeId) {
                case "spor": return "workout";
                case "araba": return "road trip";
                case "parti": return "party";
                case "yuruyus": return "walking";
                case "uyku": return "chill";
                case "is": return "focus";
                default: return null;
            }
        }
        switch (placeId) {
            case "spor": return "spor";
            case "araba": return "yolculuk";
            case "parti": return "parti";
            case "yuruyus": return "yürüyüş";
            case "uyku": return "sakin";
            case "is": return "çalışırken";
            default: return null;
        }
    }

    private static String moodWord(String moodId, boolean turkish) {
        if (moodId == null) return null;
        if (!turkish) {
            switch (moodId) {
                case "huzunlu": return "sad";
                case "romantik": return "love";
                case "sakin": return "chill";
                case "enerjik": return "energy";
                case "nostaljik": return "classics";
                case "mutlu": return "feel good";
                default: return null;
            }
        }
        switch (moodId) {
            case "huzunlu": return "duygusal";
            case "romantik": return "aşk";
            case "sakin": return "sakin";
            case "enerjik": return "hareketli";
            case "nostaljik": return "eski";
            case "mutlu": return "eğlenceli";
            default: return null;
        }
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
        public String genreTr, genreGl;  // cümlede geçen tür (Türkçe / Global karşılığı)
        public boolean hard;             // "sert", "agresif"

        public Genres.Genre genre(boolean turkish) {
            return Genres.byId(turkish ? genreTr : genreGl);
        }

        /** Okunabilir özet: "🌙 Sakin · 🏖️ Sahil · 🌅 Gün batımı" */
        public String summary() {
            return String.join("  ·  ", understood);
        }

        String summaryScenes() {
            List<String> l = new ArrayList<>();
            for (Scene sc : scenes) l.add(sc.emoji + " " + sc.label);
            Moods.Place p = Moods.place(placeId);
            if (p != null) l.add(0, p.emoji + " " + p.label);
            return String.join("  ·  ", l);
        }

        /** Puanlamada kullanılacak hedef. */
        public Moods.Target target(boolean turkish) {
            Moods.Target t = Moods.target(Moods.mood(moodId), Moods.place(placeId), turkish);
            if (t == null) return null;
            // Güçlü sahne (ör. rakı masası) + ruh hali açıkça söylenmemiş → sadece sahne belirler
            boolean strong = false;
            for (Scene sc : scenes) if (STRONG.contains(sc.id)) strong = true;
            boolean sceneOnly = strong && guessedMood;

            double f = 1 + bpmShift;
            t.bpmLo *= f;
            t.bpmHi *= f;
            t.gain += gainShift;
            if (sceneOnly) {   // fasıl/arabesk her tempoda olabilir
                t.bpmLo = 60;
                t.bpmHi = 135;
            }

            List<String> q = new ArrayList<>();
            List<String> tg = new ArrayList<>();
            for (int i = 0; i < scenes.size() && i < 2; i++) {
                Scene sc = scenes.get(i);
                String[] sq = turkish ? sc.qTr : sc.qGl;
                for (int j = 0; j < sq.length && j < (sceneOnly ? 4 : 2); j++) if (!q.contains(sq[j])) q.add(sq[j]);
                String[] st = SCENE_TAGS.get(sc.id);
                if (st != null) for (String x : st) if (!tg.contains(x)) tg.add(x);
            }
            if (!sceneOnly) {
                for (String s : t.queries) if (!q.contains(s)) q.add(s);
                for (String s : t.tags) if (!tg.contains(s)) tg.add(s);
            }
            t.queries.clear();
            t.queries.addAll(q);
            t.tags.clear();
            t.tags.addAll(tg);
            t.label = sceneOnly ? summaryScenes() : summary();

            // Cümlede tür geçiyorsa listeyi tür belirler; ruh hali/mekan sadece tempo ve enerjiyi ayarlar
            Genres.Genre g = genre(turkish);
            if (g != null) {
                List<String> gq = new ArrayList<>();
                // Türkçe: "türkçe rap spor"; Global: "rap workout"
                String base = turkish ? g.queries[0] : Genres.styleTags(g)[0];
                String pw = placeWord(placeId, turkish);
                if (pw != null) gq.add(base + " " + pw);
                if (hard) gq.add(turkish ? base + " sert" : "aggressive " + base);
                if (!guessedMood) {
                    String mw = moodWord(moodId, turkish);
                    if (mw != null) gq.add(base + " " + mw);
                }
                for (String x : g.queries) if (!gq.contains(x)) gq.add(x);
                if (sceneOnly) for (String x : q) if (!gq.contains(x)) gq.add(x);
                t.queries.clear();
                t.queries.addAll(gq);

                // Tahmin edilen ruh halinin etiketleri ("dance", "party") türü bozmasın
                if (guessedMood) {
                    t.tags.clear();
                    String[] pt = Moods.placeTags(placeId);
                    if (pt != null) for (String x : pt) if (!t.tags.contains(x)) t.tags.add(x);
                }
                if (hard) for (String x : hardTags(g.id)) if (!t.tags.contains(x)) t.tags.add(x);
                Genres.applyStyle(t, g);

                // Rap/hip-hop yarı tempoda da gaz olabilir (90 BPM = 180): tempo aralığını aç
                switch (g.id) {
                    case "rap": case "g_hiphop": case "g_rnb": case "g_lofi":
                        t.bpmLo = Math.min(t.bpmLo, 75); t.bpmHi = Math.max(t.bpmHi, 175); break;
                    case "rock": case "g_rock": case "anadolurock":
                        t.bpmLo = Math.min(t.bpmLo, 90); t.bpmHi = Math.max(t.bpmHi, 185); break;
                    default:
                        t.bpmLo *= 0.85; t.bpmHi *= 1.15; break;
                }
            } else if (hard) {
                for (String x : hardTags(null)) if (!t.tags.contains(x)) t.tags.add(x);
            }
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

        // Tür
        for (String[] gk : GENRE_KEYS) {
            if (matchAny(s, java.util.Arrays.copyOfRange(gk, 2, gk.length))) {
                r.genreTr = gk[0];
                r.genreGl = gk[1];
                break;
            }
        }
        if (matchAny(s, HARD)) {
            r.hard = true;
            r.gainShift += 1.5;
            r.bpmShift += 0.02;
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
            else if (r.hard) r.moodId = r.genreTr != null ? "enerjik" : "asi";
            else if ("is".equals(r.placeId)) r.moodId = "odak";
            else if (hour >= 22 || hour < 6) r.moodId = "sakin";
            else if (hour < 11) r.moodId = "mutlu";
            else r.moodId = "mutlu";
        }

        Genres.Genre g = Genres.byId(r.genreTr);
        if (g != null) r.understood.add(g.emoji + " " + g.label.replace("Türkçe ", ""));
        if (r.hard) r.understood.add("🔥 Sert");
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
