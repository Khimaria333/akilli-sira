package com.akillisira.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Aday şarkıları puanlar.
 *
 *  BPM uyumu     en fazla 40  (çalan şarkıya ve/veya ruh hali aralığına; yarı/çift tempo da sayılır)
 *  Enerji uyumu  en fazla 25  (ses yüksekliğine göre yaklaşık)
 *  Benzerlik     en fazla 23  (benzer sanatçı, radyo, ruh hali listesi, Keşfet listesi)
 *  Popülerlik    en fazla 8
 *  Çeşitlilik    Global modda dil dengesi +6
 *  Cezalar       aynı sanatçı arka arkaya, yakında çalınan sanatçı, çok kısa/uzun
 */
public final class Scorer {
    private Scorer() {}

    public static final class Ctx {
        public Track seed;                 // çalan şarkı (Keşfet'te null)
        public int mode;                   // -1 sakinleştir, 0 aynı, +1 hareketlendir
        public Moods.Target target;        // ruh hali hedefi (yoksa null)
        public List<String> recentArtists; // normalize edilmiş son sanatçılar
        public int preferLang = Lang.UNKNOWN; // Global modda dengelemek için tercih edilen dil
        public boolean strictArtist = true;   // aynı sanatçıyı arka arkaya hiç koyma
    }

    public static double preScore(Track c) {
        return sourceBonus(c) + popularity(c);
    }

    public static List<Track> rank(Ctx x, Collection<Track> candidates) {
        Track seed = x.seed;
        double seedBpm = seed != null && seed.hasBpm() ? seed.bpm * (1 + 0.04 * x.mode) : 0;
        double seedGain = seed != null && seed.hasGain() ? seed.gain + 1.5 * x.mode : Double.NaN;
        double tGain = x.target != null ? x.target.gain + 1.0 * x.mode : Double.NaN;
        double gainTarget = !Double.isNaN(seedGain) && !Double.isNaN(tGain) ? (seedGain + tGain) / 2
                : !Double.isNaN(seedGain) ? seedGain : tGain;
        String seedArtist = seed != null ? Text.norm(Text.mainArtist(seed.artist)) : "";
        String seedTitle = seed != null ? Text.norm(seed.title) : "";

        List<Track> out = new ArrayList<>();
        for (Track c : candidates) {
            if (seed != null && Text.norm(c.title).equals(seedTitle)) continue;
            if (c.durationSec > 0 && c.durationSec < 60) continue;
            String a = Text.norm(Text.mainArtist(c.artist));
            if (x.strictArtist && seed != null && a.equals(seedArtist)) continue;

            double s = 0;
            List<String> why = new ArrayList<>();

            // ---- BPM ----
            double pSeed = Double.NaN, pRange = Double.NaN;
            if (seedBpm > 0 && c.hasBpm()) pSeed = closeness(bpmDistance(c.bpm, seedBpm));
            if (x.target != null && c.hasBpm()) pRange = rangeCloseness(c.bpm, x.target.bpmLo, x.target.bpmHi);
            double pBpm;
            if (!Double.isNaN(pSeed) && !Double.isNaN(pRange)) pBpm = 0.55 * pSeed + 0.45 * pRange;
            else if (!Double.isNaN(pSeed)) pBpm = pSeed;
            else if (!Double.isNaN(pRange)) pBpm = pRange;
            else pBpm = 0.35;
            s += 40 * pBpm;
            if (c.hasBpm() && pBpm >= 0.55) {
                if (seed != null && seed.hasBpm()) {
                    String extra = "";
                    if (Math.abs(c.bpm * 2 - seedBpm) < Math.abs(c.bpm - seedBpm)) extra = " (yarı tempo)";
                    else if (Math.abs(c.bpm / 2 - seedBpm) < Math.abs(c.bpm - seedBpm)) extra = " (çift tempo)";
                    why.add(String.format(Locale.ROOT, "%d → %d BPM%s", Math.round(seed.bpm), Math.round(c.bpm), extra));
                } else {
                    why.add(String.format(Locale.ROOT, "%d BPM", Math.round(c.bpm)));
                }
            }

            // ---- Enerji ----
            if (!Double.isNaN(gainTarget) && c.hasGain()) {
                double p = Math.max(0, 1 - Math.abs(c.gain - gainTarget) / 6.0);
                s += 25 * p;
                if (p >= 0.6) {
                    if (x.mode > 0) why.add("daha enerjik");
                    else if (x.mode < 0) why.add("daha sakin");
                    else why.add("enerjisi uyumlu");
                }
            } else {
                s += 25 * 0.4;
            }

            // ---- Benzerlik / kaynak ----
            s += sourceBonus(c);
            if (c.sources.contains("plan")) why.add("senin listenden");
            else if (c.sources.contains("related")) why.add("benzer sanatçı");
            else if (c.sources.contains("mood") && x.target != null) why.add("ruh haline uygun");
            else if (c.sources.contains("radio")) why.add("aynı tarz");

            // ---- Popülerlik ----
            s += popularity(c);

            // ---- Global modda dil dengesi ----
            if (x.preferLang != Lang.UNKNOWN && c.lang == x.preferLang) {
                s += 6;
                why.add(c.lang == Lang.TR ? "Türkçe ile dengele" : "yabancı ile dengele");
            }

            // ---- Cezalar ----
            if (seed != null && a.equals(seedArtist)) s -= 25;
            else if (x.recentArtists != null && x.recentArtists.contains(a)) s -= 12;
            if (c.durationSec > 0 && (c.durationSec < 90 || c.durationSec > 480)) s -= 15;

            c.score = s;
            c.reason = why.isEmpty() ? "tarzına yakın" : String.join(" · ", why);
            out.add(c);
        }
        out.sort((p, q) -> Double.compare(q.score, p.score));
        return out;
    }

    /** Oransal fark → 0..1 yakınlık. %3 içi tam puan, %15'te sıfır. */
    static double closeness(double d) {
        return d <= 0.03 ? 1 : Math.max(0, 1 - (d - 0.03) / 0.12);
    }

    /** BPM bir aralığa ne kadar uyuyor (yarı/çift tempo da denenir). */
    public static double rangeCloseness(double bpm, double lo, double hi) {
        double best = 0;
        double[] ratios = {1.0, 2.0, 0.5};
        for (double r : ratios) {
            double b = bpm * r;
            double d = b < lo ? (lo - b) / lo : b > hi ? (b - hi) / hi : 0;
            double p = d == 0 ? 1 : Math.max(0, 1 - d / 0.15);
            if (r != 1.0) p *= 0.9; // yarı/çift tempo biraz daha az değerli
            if (p > best) best = p;
        }
        return best;
    }

    /** İki tempo arasındaki oransal fark; yarı ve çift tempoyu da dener. */
    public static double bpmDistance(double candidate, double target) {
        double best = Double.MAX_VALUE;
        double[] ratios = {1.0, 2.0, 0.5};
        for (double r : ratios) {
            double d = Math.abs(candidate * r - target) / target;
            if (d < best) best = d;
        }
        return best;
    }

    static double sourceBonus(Track c) {
        double b = 0;
        if (c.sources.contains("plan")) b = Math.max(b, 20);
        if (c.sources.contains("related")) b = Math.max(b, 18);
        if (c.sources.contains("mood")) b = Math.max(b, 15);
        if (c.sources.contains("radio")) b = Math.max(b, 14);
        if (c.sources.contains("same")) b = Math.max(b, 6);
        if (c.sources.size() > 1) b += 5;
        return b;
    }

    static double popularity(Track c) {
        return 8 * Math.min(1.0, c.rank / 800000.0);
    }
}
