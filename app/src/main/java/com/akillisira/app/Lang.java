package com.akillisira.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Şarkının Türkçe olup olmadığını tahmin eder (başlık, sanatçı, sanatçının diğer şarkıları). */
public final class Lang {
    private Lang() {}

    public static final int UNKNOWN = 0, TR = 1, FOREIGN = 2;

    private static final Set<String> TR_WORDS = new HashSet<>(Arrays.asList(
        "ve", "bir", "ben", "sen", "beni", "seni", "bana", "sana", "benim", "senin", "bizim", "aşk", "aşkım",
        "yok", "var", "gel", "git", "gibi", "için", "icin", "kalp", "kalbim", "gece", "gün", "yine", "artık",
        "olmaz", "olsun", "değil", "deli", "güzel", "sevgilim", "canım", "hayat", "dünya", "dünyam", "neden",
        "nasıl", "kadar", "daha", "hep", "hiç", "şey", "bu", "şu", "aman", "yar", "yarim", "yalan",
        "kader", "özledim", "seviyorum", "sevda", "sevdim", "gözler", "gözlerin", "yüreğim", "dön", "kal",
        "oldu", "olur", "geceler", "kız", "yanarım", "ezberbozan", "manifesto", "kadın",
        "kalpsizsin", "ağla", "ağlama", "sevgili", "hasret", "ayrılık", "unut", "unutma", "bitti", "biter",
        "gitme", "gidiyorum", "sevmek", "sevme", "aşkın", "yüzün", "yolu", "yol", "yollar", "sabah", "akşam",
        "bugün", "yarın", "dün", "gel", "gönül", "derdim", "dert", "kara", "sevdalı", "vay",
        "başka", "biri", "birisi", "kimse", "hadi", "haydi", "söyle", "sor", "bak", "ister", "istemem",
        "affet", "bilmem", "olmuyor", "yazık", "kalbe", "kalbin", "canın", "ölürüm", "aşka", "sensiz",
        "seninle", "benimle", "bende", "sende", "aklım", "kafam", "deliler", "deniz", "rüzgar", "yağmur",
        "çiçek", "gül", "bahar", "yaz", "kış", "yıldız", "ışık", "şarkı", "türkü", "sevişmek"));

    private static final Set<String> FOREIGN_WORDS = new HashSet<>(Arrays.asList(
        "the", "you", "me", "my", "i", "love", "in", "on", "to", "your", "it", "is", "we", "be", "baby",
        "night", "heart", "don't", "dont", "it's", "im", "i'm", "with", "all", "what", "when", "like", "girl",
        "boy", "good", "bad", "time", "life", "stay", "go", "never", "one", "up", "down", "new", "home",
        "this", "that", "no", "yes", "oh", "just", "more", "dance", "world", "forever", "tonight", "without",
        "everything", "nothing", "feel", "lights", "rain", "sun", "fire", "song", "away", "back", "again",
        "people", "dream", "dreams", "kiss", "want", "need", "know", "let", "can't", "cant", "won't", "are",
        "for", "from", "out", "about", "after", "before", "only", "still", "where", "why", "who", "how",
        "la", "los", "las", "que", "amor", "corazón", "bonita", "les", "je", "pas", "moi", "toi",
        "die", "der", "und", "ich", "nicht", "mein", "dein", "liebe", "amore", "cuore", "sei", "che"));

    private static final String STRONG_TR = "ğĞşŞıİ";
    private static final String WEAK_TR = "çÇöÖüÜ";

    /** Tek bir şarkının dilini başlık ve sanatçı adından tahmin eder. */
    public static int detect(String title, String artist) {
        String t = title == null ? "" : title;
        String a = artist == null ? "" : artist;
        if (hasAny(t, STRONG_TR) || hasAny(a, STRONG_TR)) return TR;
        if (nonLatin(t) || nonLatin(a)) return FOREIGN;   // Yunan, Kiril, Arap, Asya alfabeleri

        int tr = 0, fo = 0;
        for (String w : Text.cleanTitle(t).toLowerCase(Locale.ROOT).split("[^\\p{L}']+")) {
            if (w.isEmpty()) continue;
            if (TR_WORDS.contains(w)) tr++;
            if (FOREIGN_WORDS.contains(w)) fo++;
        }
        boolean weak = hasAny(t, WEAK_TR) || hasAny(a, WEAK_TR);
        if (tr > fo) return TR;
        if (fo > tr) return FOREIGN;
        if (weak && fo == 0) return TR;
        return UNKNOWN;
    }

    /** Sanatçının birkaç şarkı adına bakarak çoğunluk oyu. */
    public static int vote(List<String> titles, String artist) {
        int tr = 0, fo = 0;
        for (String s : titles) {
            int l = detect(s, artist);
            if (l == TR) tr++;
            else if (l == FOREIGN) fo++;
        }
        if (tr == 0 && fo == 0) return UNKNOWN;
        return tr >= fo ? TR : FOREIGN;
    }

    public static String label(int l) {
        return l == TR ? "Türkçe" : l == FOREIGN ? "Yabancı" : "—";
    }

    private static boolean nonLatin(String s) {
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeBlock b = Character.UnicodeBlock.of(s.charAt(i));
            if (b == Character.UnicodeBlock.GREEK || b == Character.UnicodeBlock.CYRILLIC
                    || b == Character.UnicodeBlock.ARABIC || b == Character.UnicodeBlock.HEBREW
                    || b == Character.UnicodeBlock.HANGUL_SYLLABLES || b == Character.UnicodeBlock.HIRAGANA
                    || b == Character.UnicodeBlock.KATAKANA || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || b == Character.UnicodeBlock.DEVANAGARI || b == Character.UnicodeBlock.THAI) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) if (chars.indexOf(s.charAt(i)) >= 0) return true;
        return false;
    }
}
