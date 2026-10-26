package com.akillisira.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Ruh hali ve mekan seçenekleri. Her biri bir BPM aralığına, bir enerji (ses yüksekliği)
 * hedefine ve Deezer'de aranacak çalma listesi sorgularına karşılık gelir.
 */
public final class Moods {
    private Moods() {}

    public static final class Mood {
        public final String id, label, emoji;
        public final int color1, color2;
        public final double bpmLo, bpmHi, gain;
        public final String[] qTr, qGlobal;

        Mood(String id, String label, String emoji, int c1, int c2,
             double bpmLo, double bpmHi, double gain, String[] qTr, String[] qGlobal) {
            this.id = id; this.label = label; this.emoji = emoji;
            this.color1 = c1; this.color2 = c2;
            this.bpmLo = bpmLo; this.bpmHi = bpmHi; this.gain = gain;
            this.qTr = qTr; this.qGlobal = qGlobal;
        }
    }

    public static final class Place {
        public final String id, label, emoji;
        public final String[] qTr, qGlobal;

        Place(String id, String label, String emoji, String[] qTr, String[] qGlobal) {
            this.id = id; this.label = label; this.emoji = emoji;
            this.qTr = qTr; this.qGlobal = qGlobal;
        }
    }

    /** Puanlamada kullanılan hedef. */
    public static final class Target {
        public double bpmLo, bpmHi, gain;
        public final List<String> queries = new ArrayList<>();
        public String label = "";
    }

    private static String[] a(String... s) { return s; }

    public static final Mood[] MOODS = {
        new Mood("mutlu", "Mutlu", "😊", 0xFFFFB347, 0xFFFF6F61, 105, 130, -7.0,
                a("türkçe pop hit", "neşeli türkçe şarkılar"), a("happy hits", "feel good pop")),
        new Mood("enerjik", "Enerjik", "⚡", 0xFFFF4D6D, 0xFFFF9F1C, 120, 150, -6.0,
                a("türkçe hareketli", "türkçe pop hareketli"), a("energy booster", "dance hits")),
        new Mood("sakin", "Sakin", "🌙", 0xFF4FACFE, 0xFF7B5CFF, 70, 100, -10.0,
                a("türkçe akustik", "türkçe sakin şarkılar"), a("chill hits", "acoustic chill")),
        new Mood("huzunlu", "Hüzünlü", "🌧️", 0xFF5B6B8C, 0xFF2E3A59, 65, 95, -10.0,
                a("türkçe slow", "hüzünlü türkçe şarkılar"), a("sad songs", "sad hits")),
        new Mood("romantik", "Romantik", "❤️", 0xFFFF5E8E, 0xFFB24592, 70, 105, -9.0,
                a("türkçe aşk şarkıları", "romantik türkçe şarkılar"), a("love songs", "romantic hits")),
        new Mood("odak", "Odak", "🎧", 0xFF3DDC97, 0xFF1A8F7A, 80, 115, -11.0,
                a("türkçe lofi", "türkçe akustik sakin"), a("lofi beats", "deep focus")),
        new Mood("nostaljik", "Nostaljik", "📻", 0xFFD4A373, 0xFF8D5B4C, 90, 125, -8.0,
                a("90lar türkçe pop", "2000ler türkçe pop"), a("90s hits", "80s hits")),
        new Mood("asi", "Asi", "🔥", 0xFFE63946, 0xFF6A040F, 110, 150, -5.5,
                a("türkçe rock", "türkçe rock hareketli"), a("rock hits", "rock workout")),
    };

    public static final Place[] PLACES = {
        new Place("ev", "Evde", "🏠", a(), a()),
        new Place("araba", "Arabada", "🚗", a("türkçe yol şarkıları"), a("road trip")),
        new Place("spor", "Sporda", "🏋️", a("türkçe spor"), a("workout hits")),
        new Place("is", "Çalışırken", "💻", a(), a("focus music")),
        new Place("parti", "Partide", "🎉", a("türkçe parti"), a("party hits")),
        new Place("yuruyus", "Yürüyüşte", "🚶", a("türkçe yürüyüş"), a("walking music")),
        new Place("uyku", "Uyumadan önce", "😴", a("türkçe uyku öncesi"), a("sleep songs")),
    };

    public static Mood mood(String id) {
        if (id == null) return null;
        for (Mood m : MOODS) if (m.id.equals(id)) return m;
        return null;
    }

    public static Place place(String id) {
        if (id == null) return null;
        for (Place p : PLACES) if (p.id.equals(id)) return p;
        return null;
    }

    /** Ruh hali + mekan + dil → hedef BPM aralığı, enerji ve arama sorguları. */
    public static Target target(Mood m, Place p, boolean turkish) {
        if (m == null) return null;
        Target t = new Target();
        t.bpmLo = m.bpmLo;
        t.bpmHi = m.bpmHi;
        t.gain = m.gain;
        if (p != null) {
            switch (p.id) {
                case "spor":    t.bpmLo = Math.max(t.bpmLo, 118); t.bpmHi = Math.max(t.bpmHi, 150); t.gain += 1.5; break;
                case "parti":   t.bpmLo = Math.max(t.bpmLo, 112); t.bpmHi = Math.max(t.bpmHi, 140); t.gain += 1.5; break;
                case "araba":   t.bpmLo *= 1.03; t.bpmHi *= 1.06; t.gain += 0.5; break;
                case "yuruyus": t.bpmLo = Math.max(t.bpmLo, 95); t.bpmHi = Math.min(Math.max(t.bpmHi, 120), 135); break;
                case "is":      t.bpmHi = Math.min(t.bpmHi, 120); t.gain -= 1.0; break;
                case "uyku":    t.bpmHi = Math.min(t.bpmHi, 92); t.bpmLo = Math.min(t.bpmLo, 60); t.gain -= 2.0; break;
                default: break;
            }
            if (t.bpmHi - t.bpmLo < 15) t.bpmHi = t.bpmLo + 15;
            for (String q : turkish ? p.qTr : p.qGlobal) t.queries.add(q);
        }
        for (String q : turkish ? m.qTr : m.qGlobal) t.queries.add(q);
        t.label = m.emoji + " " + m.label + (p != null ? "  ·  " + p.emoji + " " + p.label : "");
        return t;
    }

    /** Ruh hali seçilmemişken Türkçe modda çalan şarkının havasına göre ek kaynak. */
    public static List<String> defaultTurkishQueries(Track seed) {
        List<String> q = new ArrayList<>();
        boolean energetic = seed != null && ((seed.hasBpm() && seed.bpm >= 112 && seed.bpm <= 175)
                || (seed.hasGain() && seed.gain > -7.5));
        if (energetic) { q.add("türkçe pop hareketli"); q.add("türkçe pop hit"); }
        else { q.add("türkçe pop hit"); q.add("türkçe slow"); }
        return q;
    }
}
