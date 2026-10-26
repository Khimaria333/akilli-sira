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
    public int rank;               // Deezer popülerliği (0 - ~1.000.000)
    public double bpm = 0;         // 0 = bilinmiyor
    public double gain = Double.NaN; // ses yüksekliği (dB), enerji için yaklaşık ölçü
    public boolean detailsLoaded;
    public volatile String videoId;  // YT Music'teki karşılığı (bulunduysa)

    /** Nereden geldi: "same" (aynı sanatçı), "related" (benzer sanatçı), "radio" (sanatçı radyosu) */
    public final Set<String> sources = new LinkedHashSet<>();

    public double score;
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

    @Override
    public String toString() {
        return artist + " – " + title;
    }
}
