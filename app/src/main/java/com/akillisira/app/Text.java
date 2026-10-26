package com.akillisira.app;

import java.util.Locale;

/** Şarkı adı / sanatçı metinlerini karşılaştırmak ve aramak için temizleme. */
public final class Text {
    private Text() {}

    /** Arama için: "(Official Video)", "[Live]", "feat. X", "- Remastered 2011" gibi ekleri atar. */
    public static String cleanTitle(String s) {
        if (s == null) return "";
        String t = s;
        t = t.replaceAll("\\([^)]*\\)", " ");
        t = t.replaceAll("\\[[^\\]]*\\]", " ");
        t = t.replaceAll("(?i)\\s(feat\\.?|ft\\.?|featuring)\\s.*$", " ");
        t = t.replaceAll("(?i)\\s-\\s.*(remaster|version|edit|mix|live|mono|stereo).*$", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? s.trim() : t;
    }

    /** Ana sanatçı: "A & B", "A, B", "A x B", "A feat. B" -> "A". */
    public static String mainArtist(String s) {
        if (s == null) return "";
        String t = s.split("(?i)\\s(&|x|feat\\.?|ft\\.?)\\s|,|•|·")[0];
        return t.trim();
    }

    /** Karşılaştırma anahtarı: küçük harf, eksiz, sadece harf/rakam. */
    public static String norm(String s) {
        if (s == null) return "";
        String t = cleanTitle(s).toLowerCase(Locale.ROOT);
        t = t.replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
             .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        t = t.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return t;
    }

    public static String key(String artist, String title) {
        return norm(mainArtist(artist)) + "|" + norm(title);
    }
}
