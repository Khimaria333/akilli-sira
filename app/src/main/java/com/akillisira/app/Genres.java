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

    /** Tür hedefi: tempo/enerji serbest, etiketler ve aramalar türü belirler. */
    public static Moods.Target target(Genre g) {
        Moods.Target t = new Moods.Target();
        t.bpmLo = 40;
        t.bpmHi = 220;
        t.gain = Double.NaN;
        for (String q : g.queries) t.queries.add(q);
        for (String tg : g.tags) t.tags.add(tg);
        t.label = g.emoji + " " + g.label;
        return t;
    }
}
