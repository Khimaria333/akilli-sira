package com.akillisira.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Aday şarkıları puanlar. Öncelik sırası: TARZ > DÖNEM > TEMPO/ENERJİ.
 *
 *  Tarz          en fazla 38  benzer sanatçı (20) / aynı çevre (14) + aynı tür (18), alakasız türe -16
 *  Dönem         en fazla 10  çıkış yılları yakınsa; 20+ yıl farka -10
 *  Tempo (BPM)   en fazla 25  çalan şarkıya ve/veya ruh hali aralığına; yarı/çift tempo da sayılır
 *  Enerji        en fazla 15  ses yüksekliğine göre yaklaşık
 *  Kaynak        Keşfet listen +20, ruh hali listesi +8 (sadece ruh hali açıkken)
 *  Popülerlik    en fazla 5
 *  Beğeniler     ❤️ şarkı +12, ❤️ sanatçı +6..15; 👎 sanatçı -12..30
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
        public java.util.Map<String, Integer> artistDislikes; // sanatçı → beğenilmeyen şarkı sayısı
        public java.util.Map<String, Integer> artistLikes;    // sanatçı → beğenilen şarkı sayısı
        public java.util.Set<String> likedKeys;               // beğenilen şarkılar
        public Set<Long> relatedArtists;   // çalan sanatçının benzerleri (Deezer)
        public Set<Long> radioArtists;     // çalan sanatçının radyosunda geçen sanatçılar
        public java.util.Map<String, Integer> seedTags;   // çalan sanatçının Last.fm etiketleri
        public java.util.Map<String, Integer> profile;    // beğenilerden çıkan tarz profili
        public int explore = 1;                           // 0 tanıdık, 1 dengeli, 2 yeni
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

            // ---- TARZ: sanatçı yakınlığı + tür ----
            if (seed != null) {
                boolean rel = x.relatedArtists != null && c.artistId > 0 && x.relatedArtists.contains(c.artistId);
                boolean rad = !rel && x.radioArtists != null && c.artistId > 0 && x.radioArtists.contains(c.artistId);
                if (rel) { s += 20; why.add("benzer sanatçı"); }
                else if (rad) { s += 14; why.add("aynı çevreden"); }

                // Last.fm etiketleri varsa tarzı onlar belirler (Deezer türü kaba kalıyor)
                double tagSim = LastFm.similarity(x.seedTags, c.tags);
                boolean tagsKnown = tagSim >= 0;
                double genreWeight = tagsKnown ? 0.35 : 1.0;
                if (tagsKnown) {
                    s += 30 * Math.min(1, tagSim * 1.5);
                    if (tagSim < 0.08) s -= (rel || rad) ? 6 : 18;
                    else {
                        String sh = LastFm.sharedTag(x.seedTags, c.tags);
                        if (sh != null && tagSim >= 0.25) why.add("ortak tarz: " + sh);
                    }
                }
                if (seed.genreId > 0 && c.genreId > 0) {
                    int g = genreRelation(seed, c);
                    if (g == 2) {
                        s += 18 * genreWeight;
                        if (!tagsKnown) why.add(c.genre != null ? "aynı tür: " + c.genre : "aynı tür");
                    } else if (g == 1) {
                        s += 7 * genreWeight;
                    } else {
                        s -= ((rel || rad) ? 5 : 16) * genreWeight;   // benzer sanatçıysa Deezer türüne az takıl
                    }
                } else if (!tagsKnown) {
                    s += 4;   // tür bilinmiyor: tarafsız
                }

                // ---- DÖNEM ----
                if (seed.year > 0 && c.year > 0) {
                    int d = Math.abs(seed.year - c.year);
                    if (d <= 4) { s += 10; why.add("aynı dönem"); }
                    else if (d <= 9) s += 6;
                    else if (d >= 20) s -= 10;
                } else {
                    s += 3;
                }
            }

            // ---- TEMPO ----
            double pSeed = Double.NaN, pRange = Double.NaN;
            if (seedBpm > 0 && c.hasBpm()) pSeed = closeness(bpmDistance(c.bpm, seedBpm));
            if (x.target != null && c.hasBpm()) pRange = rangeCloseness(c.bpm, x.target.bpmLo, x.target.bpmHi);
            double pBpm;
            if (!Double.isNaN(pSeed) && !Double.isNaN(pRange)) pBpm = 0.55 * pSeed + 0.45 * pRange;
            else if (!Double.isNaN(pSeed)) pBpm = pSeed;
            else if (!Double.isNaN(pRange)) pBpm = pRange;
            else pBpm = 0.35;
            s += 25 * pBpm;
            if (c.hasBpm() && pBpm >= 0.6) {
                if (seed != null && seed.hasBpm()) {
                    String extra = "";
                    if (Math.abs(c.bpm * 2 - seedBpm) < Math.abs(c.bpm - seedBpm)) extra = " (yarı tempo)";
                    else if (Math.abs(c.bpm / 2 - seedBpm) < Math.abs(c.bpm - seedBpm)) extra = " (çift tempo)";
                    why.add(String.format(Locale.ROOT, "%d → %d BPM%s", Math.round(seed.bpm), Math.round(c.bpm), extra));
                } else {
                    why.add(String.format(Locale.ROOT, "%d BPM", Math.round(c.bpm)));
                }
            }

            // ---- ENERJİ ----
            if (!Double.isNaN(gainTarget) && c.hasGain()) {
                double p = Math.max(0, 1 - Math.abs(c.gain - gainTarget) / 6.0);
                s += 15 * p;
                if (p >= 0.7 && x.mode != 0) why.add(x.mode > 0 ? "daha enerjik" : "daha sakin");
            } else {
                s += 15 * 0.4;
            }

            // ---- Ruh hali / sahne etiketleri (Last.fm) ----
            if (x.target != null && !x.target.tags.isEmpty()) {
                double ovA = LastFm.overlap(c.tags, x.target.tags);
                double ovT = LastFm.overlap(c.trackTags, x.target.tags);
                double ov = ovT >= 0 && ovA >= 0 ? 0.6 * ovT + 0.4 * ovA : ovT >= 0 ? ovT : ovA;
                if (ov > 0) {
                    s += 25 * ov;
                    if (ov >= 0.3) why.add("ruh haline uygun");
                } else if (ov == 0) {
                    s -= 14;   // etiketleri biliniyor ama hiçbiri uymuyor
                }
            }

            // ---- Kaynak ----
            if (c.sources.contains("plan")) { s += 20; why.add("senin listenden"); }
            else if (c.sources.contains("mood") && x.target != null) { s += 8; why.add("ruh haline uygun"); }
            else if (c.sources.contains("same")) s += 4;
            if (seed == null) s += sourceBonus(c) * 0.5;   // Keşfet'te (çalan yokken) kaynak daha önemli

            // ---- Popülerlik (keşif seviyesine göre) ----
            double pop = Math.min(1.0, c.rank / 800000.0);
            if (x.explore == 0) s += 12 * pop;
            else if (x.explore == 2) s += 8 * (1 - pop);
            else s += 5 * pop;

            // ---- Tarz profili (❤️ beğenilerden) ----
            if (x.profile != null && !x.profile.isEmpty()) {
                double ps = LastFm.similarity(x.profile, c.tags);
                if (ps > 0) {
                    s += 12 * Math.min(1, ps * 1.5);
                    if (ps >= 0.35) why.add("tarzına uygun");
                }
            }

            // ---- Global modda dil dengesi ----
            if (x.preferLang != Lang.UNKNOWN && c.lang == x.preferLang) {
                s += 6;
                why.add(c.lang == Lang.TR ? "Türkçe ile dengele" : "yabancı ile dengele");
            }

            // ---- Beğeniler ve cezalar ----
            if (seed != null && a.equals(seedArtist)) s -= 25;
            else if (x.recentArtists != null && x.recentArtists.contains(a)) s -= 12;
            if (c.durationSec > 0 && (c.durationSec < 90 || c.durationSec > 480)) s -= 15;
            if (x.likedKeys != null && x.likedKeys.contains(c.key())) {
                s += 12;
                why.add("❤️ beğendiğin");
            } else if (x.artistLikes != null) {
                Integer n = x.artistLikes.get(a);
                if (n != null && n > 0) {
                    s += Math.min(15, 6 * n);
                    why.add("❤️ sevdiğin sanatçı");
                }
            }
            if (x.artistDislikes != null) {
                Integer n = x.artistDislikes.get(a);
                if (n != null && n > 0) s -= Math.min(30, 12 * n);
            }

            c.score = s;
            c.reason = why.isEmpty() ? "tarzına yakın" : String.join(" · ", new java.util.LinkedHashSet<>(why));
            out.add(c);
        }
        out.sort((p, q) -> Double.compare(q.score, p.score));
        return out;
    }

    // =====================================================================
    // Tür yakınlığı
    // =====================================================================

    private static final String[][] GENRE_GROUPS = {
        {"rap", "hip hop", "hip-hop", "trap", "drill"},
        {"pop", "dance", "r&b", "soul", "latin", "electro"},
        {"rock", "alternative", "alternatif", "metal", "indie", "punk", "grunge"},
        {"folk", "halk", "türkü", "world", "arabesk", "fantezi", "sanat", "traditional", "geleneksel"},
        {"jazz", "caz", "blues", "soul", "classical", "klasik"},
        {"electro", "dance", "house", "techno", "edm"},
        {"r&b", "rap", "hip hop"},
    };

    /** 2: aynı tür, 1: yakın tür, 0: alakasız */
    public static int genreRelation(Track a, Track b) {
        if (a.genreId > 0 && a.genreId == b.genreId) return 2;
        Set<Integer> ga = groups(a.genre), gb = groups(b.genre);
        for (Integer g : ga) if (gb.contains(g)) return 1;
        return 0;
    }

    private static Set<Integer> groups(String genre) {
        Set<Integer> out = new HashSet<>();
        if (genre == null) return out;
        String g = genre.toLowerCase(Locale.forLanguageTag("tr"));
        for (int i = 0; i < GENRE_GROUPS.length; i++) {
            for (String k : GENRE_GROUPS[i]) if (g.contains(k)) { out.add(i); break; }
        }
        return out;
    }

    /** Çalan şarkının türüne uygun Türkçe çalma listesi araması. */
    public static List<String> genreQueries(Track seed) {
        List<String> q = new ArrayList<>();
        String g = seed.genre == null ? "" : seed.genre.toLowerCase(Locale.forLanguageTag("tr"));
        if (g.contains("rap") || g.contains("hip")) q.add("türkçe rap");
        else if (g.contains("rock") || g.contains("alternative") || g.contains("metal")) q.add("türkçe rock");
        else if (g.contains("arabesk")) q.add("arabesk");
        else if (g.contains("folk") || g.contains("world") || g.contains("halk")) q.add("türk halk müziği");
        else if (g.contains("jazz") || g.contains("caz")) q.add("türkçe caz");
        else if (g.contains("dance") || g.contains("electro")) q.add("türkçe dance");
        else if (g.contains("r&b")) q.add("türkçe r&b");
        else if (g.contains("pop")) {
            if (seed.year > 0 && seed.year < 1990) q.add("eski türkçe pop");
            else if (seed.year > 0 && seed.year < 2005) q.add("90lar türkçe pop");
            else q.add("türkçe pop hit");
        }
        return q;
    }

    /** Yoğunluk 0..1: tempo + enerji. */
    public static double intensity(Track t) {
        double b = t.hasBpm() ? Math.max(0, Math.min(1, (t.bpm - 65) / 100.0)) : -1;
        double e = t.energy();
        if (b < 0 && e < 0) return 0.5;
        if (b < 0) return e;
        if (e < 0) return b;
        return 0.55 * b + 0.45 * e;
    }

    /**
     * DJ seti gibi sıralar: ısınma → zirve → yavaş iniş.
     * Sıralanmış adaylardan en iyi 40'ını alır, her pozisyon için hedef yoğunluğa en yakın olanı seçer.
     * curveOut: seçilen şarkıların yoğunlukları (grafik için).
     */
    public static List<Track> djOrder(List<Track> ranked, int n, double[] curveOut) {
        List<Track> cand = new ArrayList<>();
        java.util.Map<String, Integer> perArtist = new java.util.HashMap<>();
        for (Track t : ranked) {
            String a = Text.norm(Text.mainArtist(t.artist));
            int k = perArtist.getOrDefault(a, 0);
            if (k >= 2) continue;
            perArtist.put(a, k + 1);
            cand.add(t);
            if (cand.size() >= 40) break;
        }
        if (cand.isEmpty()) return cand;
        double lo = 1, hi = 0;
        for (Track t : cand) { double v = intensity(t); lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (hi - lo < 0.15) { lo = Math.max(0, lo - 0.1); hi = Math.min(1, hi + 0.1); }

        List<Track> out = new ArrayList<>();
        int count = Math.min(n, cand.size());
        String prevArtist = "";
        for (int i = 0; i < count; i++) {
            double x = count == 1 ? 0.5 : i / (double) (count - 1);
            double d = x < 0.6 ? 0.3 + 0.7 * (x / 0.6) : 1.0 - 0.65 * ((x - 0.6) / 0.4);
            double want = lo + d * (hi - lo);
            Track best = null;
            double bestCost = Double.MAX_VALUE;
            for (int j = 0; j < cand.size(); j++) {
                Track t = cand.get(j);
                double cost = Math.abs(intensity(t) - want) + 0.12 * j / cand.size();
                if (Text.norm(Text.mainArtist(t.artist)).equals(prevArtist)) cost += 0.3;
                if (cost < bestCost) { bestCost = cost; best = t; }
            }
            out.add(best);
            cand.remove(best);
            prevArtist = Text.norm(Text.mainArtist(best.artist));
            if (curveOut != null && i < curveOut.length) curveOut[i] = intensity(best);
        }
        return out;
    }

    /** Bir listeyi tempo ve enerjiye göre akıcı sıraya dizer (en yakın komşu). */
    public static List<Track> flowOrder(Track start, List<Track> rest) {
        List<Track> left = new ArrayList<>(rest);
        List<Track> out = new ArrayList<>();
        Track cur = start;
        while (!left.isEmpty()) {
            Track best = null;
            double bestD = Double.MAX_VALUE;
            for (Track t : left) {
                double d = (cur.hasBpm() && t.hasBpm()) ? bpmDistance(t.bpm, cur.bpm) : 0.12;
                double e1 = cur.energy(), e2 = t.energy();
                d += (e1 >= 0 && e2 >= 0) ? Math.abs(e1 - e2) * 0.3 : 0.06;
                if (Text.norm(Text.mainArtist(t.artist)).equals(Text.norm(Text.mainArtist(cur.artist)))) d += 0.2;
                if (d < bestD) { bestD = d; best = t; }
            }
            out.add(best);
            left.remove(best);
            cur = best;
        }
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
        if (c.sources.contains("mood")) b = Math.max(b, 10);
        if (c.sources.contains("radio")) b = Math.max(b, 14);
        if (c.sources.contains("same")) b = Math.max(b, 6);
        if (c.sources.size() > 1) b += 5;
        return b;
    }

    static double popularity(Track c) {
        return 5 * Math.min(1.0, c.rank / 800000.0);
    }
}
