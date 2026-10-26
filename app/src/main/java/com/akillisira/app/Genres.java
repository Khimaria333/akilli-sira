package com.akillisira.app;

/** Keşfet'teki "Tür" sekmesi: tek dokunuşla o türün en sevilen şarkılarından liste. */
public final class Genres {
    private Genres() {}

    public static final class Genre {
        public final String id, label, emoji;
        public final int c1, c2;
        public final String[] tags, queries;

        Genre(String id, String label, String emoji, int c1, int c2, String[] tags, String[] queries) {
            this.id = id; this.label = label; this.emoji = emoji;
            this.c1 = c1; this.c2 = c2; this.tags = tags; this.queries = queries;
        }
    }

    private static String[] a(String... s) { return s; }

    public static final Genre[] TR = {
        new Genre("arabesk", "Arabesk", "🥀", 0xFF8E2DE2, 0xFF4A00E0, a("arabesk", "arabesque"), a("arabesk", "arabesk damar")),
        new Genre("fantezi", "Fantezi", "💫", 0xFFDA4453, 0xFF89216B, a("fantezi", "arabesk"), a("fantezi müzik", "fantezi şarkılar")),
        new Genre("tsm", "Türk Sanat Müziği", "🎻", 0xFFB79891, 0xFF94716B, a("türk sanat müziği", "turkish classical", "tsm"), a("türk sanat müziği", "tsm şarkılar")),
        new Genre("turku", "Türkü", "🪕", 0xFF56AB2F, 0xFF2E7D32, a("türkü", "turkish folk", "türk halk müziği"), a("türküler", "türk halk müziği")),
        new Genre("anadolurock", "Anadolu Rock", "🎸", 0xFFF7971E, 0xFFB2470A, a("anatolian rock", "anadolu rock", "turkish psychedelic"), a("anadolu rock")),
        new Genre("rock", "Türkçe Rock", "🤘", 0xFFE53935, 0xFF6D0F0F, a("turkish rock", "rock"), a("türkçe rock")),
        new Genre("rap", "Türkçe Rap", "🎤", 0xFF232526, 0xFF5B5F66, a("turkish rap", "turkish hip-hop", "turkish hip hop"), a("türkçe rap")),
        new Genre("pop", "Türkçe Pop", "🎶", 0xFFFF4D6D, 0xFFFF9F1C, a("turkish pop"), a("türkçe pop hit")),
        new Genre("90lar", "90'lar Pop", "📼", 0xFF00B4DB, 0xFF0083B0, a("90s", "turkish pop"), a("90lar türkçe pop")),
        new Genre("slow", "Slow", "🌙", 0xFF41295A, 0xFF2F0743, a("slow", "ballad", "sad"), a("türkçe slow")),
        new Genre("akustik", "Akustik", "🪵", 0xFFA1887F, 0xFF5D4037, a("acoustic", "unplugged"), a("türkçe akustik")),
        new Genre("alternatif", "Alternatif", "🌀", 0xFF11998E, 0xFF38EF7D, a("indie", "alternative", "turkish indie"), a("türkçe alternatif", "türkçe indie")),
        new Genre("elektronik", "Elektronik", "🎛️", 0xFF7F00FF, 0xFFE100FF, a("electronic", "deep house", "dance"), a("türkçe elektronik", "türkçe remix")),
        new Genre("caz", "Caz", "🎷", 0xFF3E5151, 0xFFDECBA4, a("jazz", "turkish jazz"), a("türkçe caz")),
    };

    public static final Genre[] GLOBAL = {
        new Genre("g_pop", "Pop", "🎶", 0xFFFF4D6D, 0xFFFF9F1C, a("pop"), a("pop hits")),
        new Genre("g_rock", "Rock", "🎸", 0xFFE53935, 0xFF6D0F0F, a("rock", "classic rock"), a("rock classics")),
        new Genre("g_hiphop", "Hip-Hop", "🎤", 0xFF232526, 0xFF5B5F66, a("hip-hop", "rap"), a("hip hop hits")),
        new Genre("g_rnb", "R&B", "💜", 0xFF8E2DE2, 0xFF4A00E0, a("rnb", "r&b", "soul"), a("r&b hits")),
        new Genre("g_electronic", "Elektronik", "🎛️", 0xFF7F00FF, 0xFFE100FF, a("electronic", "house", "edm"), a("electronic hits")),
        new Genre("g_indie", "Indie", "🌀", 0xFF11998E, 0xFF38EF7D, a("indie", "indie pop", "indie rock"), a("indie hits")),
        new Genre("g_jazz", "Caz", "🎷", 0xFF3E5151, 0xFFDECBA4, a("jazz"), a("jazz classics")),
        new Genre("g_lofi", "Lo-fi", "🎧", 0xFF4FACFE, 0xFF7B5CFF, a("lofi", "chillhop", "lo-fi"), a("lofi beats")),
        new Genre("g_latin", "Latin", "💃", 0xFFF7971E, 0xFFFFD200, a("latin", "reggaeton"), a("latin hits")),
        new Genre("g_80s", "80'ler", "📼", 0xFF00B4DB, 0xFF0083B0, a("80s", "new wave"), a("80s hits")),
        new Genre("g_classical", "Klasik", "🎼", 0xFFB79891, 0xFF94716B, a("classical"), a("classical essentials")),
        new Genre("g_acoustic", "Akustik", "🪵", 0xFFA1887F, 0xFF5D4037, a("acoustic", "singer-songwriter"), a("acoustic hits")),
    };

    public static Genre byId(String id) {
        if (id == null) return null;
        for (Genre g : TR) if (g.id.equals(id)) return g;
        for (Genre g : GLOBAL) if (g.id.equals(id)) return g;
        return null;
    }

    /** Türün Last.fm'de geniş eşleşen etiketleri (dil süzgeci ayrıca uygulanır). */
    public static String[] styleTags(Genre g) {
        switch (g.id) {
            case "rap": case "g_hiphop": return a("rap", "hip-hop", "hip hop", "trap", "drill");
            case "rock": case "g_rock": return a("rock", "metal", "punk", "grunge");
            case "anadolurock": return a("anatolian rock", "anadolu rock", "turkish psychedelic", "psychedelic rock");
            case "elektronik": case "g_electronic": return a("electronic", "house", "edm", "techno", "electro", "dance");
            case "arabesk": return a("arabesk", "arabesque", "fantezi");
            default: return g.tags;
        }
    }

    /** Last.fm etiketi bilinmezken kullanılacak Deezer tür kimlikleri. */
    public static int[] deezerIds(Genre g) {
        switch (g.id) {
            case "rap": case "g_hiphop": return new int[]{116};
            case "rock": case "g_rock": case "anadolurock": return new int[]{152, 464};
            case "pop": case "g_pop": case "90lar": return new int[]{132};
            case "elektronik": case "g_electronic": return new int[]{106, 113};
            case "caz": case "g_jazz": return new int[]{129};
            case "alternatif": case "g_indie": return new int[]{85};
            case "g_rnb": return new int[]{165};
            case "g_latin": return new int[]{197};
            case "g_classical": return new int[]{98};
            default: return new int[0];
        }
    }

    /** Tür değil daha çok "hava" olanlar: zorunlu tutulmaz, sadece etiketle puan kazandırır. */
    private static final java.util.Set<String> SOFT = new java.util.HashSet<>(java.util.Arrays.asList(
            "slow", "akustik", "90lar", "pop", "g_pop", "g_acoustic", "g_80s", "g_lofi"));

    /** Tür hedefine türü yerleştirir. */
    public static void applyStyle(Moods.Target t, Genre g) {
        for (String q : g.queries) if (!t.styleQueries.contains(q)) t.styleQueries.add(q);
        if (SOFT.contains(g.id)) {
            for (String s : g.tags) if (!t.tags.contains(s)) t.tags.add(s);
            return;
        }
        for (String s : styleTags(g)) if (!t.style.contains(s)) t.style.add(s);
        for (int id : deezerIds(g)) t.styleDeezer.add(id);
        t.styleLabel = g.emoji + " " + g.label;
    }

    /** Tür hedefi: tempo/enerji serbest, tür etiketleri tarzı belirler. */
    public static Moods.Target target(Genre g) {
        Moods.Target t = new Moods.Target();
        t.bpmLo = 40;
        t.bpmHi = 220;
        t.gain = Double.NaN;
        for (String q : g.queries) t.queries.add(q);
        applyStyle(t, g);
        t.label = g.emoji + " " + g.label;
        return t;
    }
}
