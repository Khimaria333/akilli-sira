package com.akillisira.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Aday şarkıları çalan şarkıya göre puanlar.
 *
 *  BPM uyumu        en fazla 40 puan (yarı/çift tempo da uyumlu sayılır)
 *  Enerji uyumu     en fazla 25 puan (ses yüksekliğine göre yaklaşık)
 *  Benzerlik        benzer sanatçı 18, sanatçı radyosu 14, aynı sanatçı 6, birden fazla kaynak +5
 *  Popülerlik       en fazla 8 puan
 *  Cezalar          aynı sanatçı arka arkaya -25, son çalınan sanatçılardan -10, çok kısa/uzun -15
 *
 * mode: -1 sakinleştir, 0 aynı havada kal, +1 hareketlendir
 */
public final class Scorer {
    private Scorer() {}

    public static final double BPM_MAX = 40, ENERGY_MAX = 25, POP_MAX = 8;

    /** Detay çekmeden önce kabaca sıralamak için. */
    public static double preScore(Track c) {
        return sourceBonus(c) + popularity(c);
    }

    public static List<Track> rank(Track seed, Collection<Track> candidates,
                                   List<String> recentArtistsNorm, int mode) {
        double targetBpm = seed.hasBpm() ? seed.bpm * (1 + 0.04 * mode) : 0;
        double targetGain = seed.hasGain() ? seed.gain + 1.5 * mode : Double.NaN;
        String seedArtist = Text.norm(Text.mainArtist(seed.artist));
        String seedTitle = Text.norm(seed.title);

        List<Track> out = new ArrayList<>();
        for (Track c : candidates) {
            if (Text.norm(c.title).equals(seedTitle)) continue;
            if (c.durationSec > 0 && c.durationSec < 60) continue;

            double s = 0;
            List<String> why = new ArrayList<>();

            // BPM
            if (targetBpm > 0 && c.hasBpm()) {
                double d = bpmDistance(c.bpm, targetBpm);
                double p = d <= 0.03 ? 1 : Math.max(0, 1 - (d - 0.03) / 0.12);
                s += BPM_MAX * p;
                if (p >= 0.5) {
                    String extra = "";
                    if (Math.abs(c.bpm * 2 - targetBpm) < Math.abs(c.bpm - targetBpm)) extra = " (yarı tempo)";
                    else if (Math.abs(c.bpm / 2 - targetBpm) < Math.abs(c.bpm - targetBpm)) extra = " (çift tempo)";
                    why.add(String.format(Locale.ROOT, "BPM %d → %d%s",
                            Math.round(seed.bpm), Math.round(c.bpm), extra));
                }
            } else {
                s += BPM_MAX * 0.35; // bilinmiyor: ne ödül ne ceza
            }

            // Enerji
            if (!Double.isNaN(targetGain) && c.hasGain()) {
                double d = Math.abs(c.gain - targetGain);
                double p = Math.max(0, 1 - d / 6.0);
                s += ENERGY_MAX * p;
                if (p >= 0.6) {
                    if (mode > 0) why.add("biraz daha enerjik");
                    else if (mode < 0) why.add("biraz daha sakin");
                    else why.add("enerji uyumlu");
                }
            } else {
                s += ENERGY_MAX * 0.4;
            }

            // Benzerlik
            s += sourceBonus(c);
            if (c.sources.contains("related")) why.add("benzer sanatçı");
            else if (c.sources.contains("radio")) why.add("aynı tarz");

            // Popülerlik
            s += popularity(c);

            // Cezalar
            String a = Text.norm(Text.mainArtist(c.artist));
            if (a.equals(seedArtist)) s -= 25;
            else if (recentArtistsNorm != null && recentArtistsNorm.contains(a)) s -= 10;
            if (c.durationSec > 0 && (c.durationSec < 90 || c.durationSec > 480)) s -= 15;

            c.score = s;
            c.reason = why.isEmpty() ? "tarzına yakın" : String.join(" · ", why);
            out.add(c);
        }
        out.sort((x, y) -> Double.compare(y.score, x.score));
        return out;
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
        if (c.sources.contains("related")) b = Math.max(b, 18);
        if (c.sources.contains("radio")) b = Math.max(b, 14);
        if (c.sources.contains("same")) b = Math.max(b, 6);
        if (c.sources.size() > 1) b += 5;
        return b;
    }

    static double popularity(Track c) {
        return POP_MAX * Math.min(1.0, c.rank / 800000.0);
    }
}
