package com.akillisira.app;

import java.util.LinkedHashSet;
import java.util.Set;

/** Bir şarkı ve hakkında bildiklerimiz. */
public class Track {
    public long deezerId;
    public String title = "";
    public String artist = "";
    public long artistId;
    public int durationSec;
    public int rank;                   // Deezer popülerliği (0 - ~1.000.000)
    public double bpm = 0;             // 0 = bilinmiyor
    public double gain = Double.NaN;   // ses yüksekliği (dB), enerji için yaklaşık ölçü
    public boolean detailsLoaded;
    public String coverUrl;            // albüm kapağı (küçük)
    public String coverBigUrl;         // albüm kapağı (büyük)
    public int lang = Lang.UNKNOWN;
    public long albumId;
    public int year;                   // çıkış yılı (0 = bilinmiyor)
    public int genreId;                // Deezer türü (0 = bilinmiyor)
    public String genre;               // "Rap/Hip Hop", "Pop" ...
    public java.util.Map<String, Integer> tags;   // Last.fm sanatçı etiketleri (yoksa null)
    public java.util.Map<String, Integer> trackTags;   // Last.fm şarkı etiketleri (yoksa null)
    public volatile String videoId;    // YT Music'teki karşılığı
    public volatile String spotifyUri; // spotify:track:… (Spotify bağlıysa)

    /** Kaynaklar: "same" aynı sanatçı, "related" benzer sanatçı, "radio" sanatçı radyosu,
     *  "mood" ruh hali / dil çalma listesi, "plan" Keşfet'te seçilen liste */
    public final Set<String> sources = new LinkedHashSet<>();
    public boolean fromTurkishList;    // Türkçe sorgudan gelen çalma listesinde bulundu

    public double score;
    public java.util.Map<String, Double> breakdown;   // kategori → puan ("Neden bu şarkı?")
    public String reason = "";

    public String key() {
        return Text.key(artist, title);
    }

    public boolean hasBpm() {
        return bpm > 1;
    }

    public boolean hasGain() {
        return !Double.isNaN(gain) && gain != 0;
    }

    /** Enerji 0..1 (ses yüksekliğinden kabaca): -14 dB → 0, -4 dB → 1 */
    public double energy() {
        if (!hasGain()) return -1;
        return Math.max(0, Math.min(1, (gain + 14) / 10.0));
    }

    @Override
    public String toString() {
        return artist + " – " + title;
    }
}
