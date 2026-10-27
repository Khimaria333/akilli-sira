// Ahenk bulut: ruh hali, mekan, tür tabloları ve cümle ayrıştırıcı (Android'deki Moods / Genres / Vibe ile aynı)

export const MOODS = {
  mutlu:     { label: "Mutlu", emoji: "😊", bpm: [105, 130], gain: -7.0, qTr: ["türkçe pop hit", "neşeli türkçe şarkılar"], qGl: ["happy hits", "feel good pop"], tags: ["happy", "upbeat", "feel good", "dance"] },
  enerjik:   { label: "Enerjik", emoji: "⚡", bpm: [120, 150], gain: -6.0, qTr: ["türkçe hareketli", "türkçe pop hareketli"], qGl: ["energy booster", "dance hits"], tags: ["energetic", "dance", "party", "upbeat"] },
  sakin:     { label: "Sakin", emoji: "🌙", bpm: [70, 100], gain: -10.0, qTr: ["türkçe akustik", "türkçe sakin şarkılar"], qGl: ["chill hits", "acoustic chill"], tags: ["chill", "mellow", "calm", "acoustic"] },
  huzunlu:   { label: "Hüzünlü", emoji: "🌧️", bpm: [65, 95], gain: -10.0, qTr: ["türkçe slow", "hüzünlü türkçe şarkılar"], qGl: ["sad songs", "sad hits"], tags: ["sad", "melancholic", "melancholy", "ballad"] },
  romantik:  { label: "Romantik", emoji: "❤️", bpm: [70, 105], gain: -9.0, qTr: ["türkçe aşk şarkıları", "romantik türkçe şarkılar"], qGl: ["love songs", "romantic hits"], tags: ["romantic", "love songs", "ballad"] },
  odak:      { label: "Odak", emoji: "🎧", bpm: [80, 115], gain: -11.0, qTr: ["türkçe lofi", "türkçe akustik sakin"], qGl: ["lofi beats", "deep focus"], tags: ["instrumental", "lofi", "ambient", "chill"] },
  nostaljik: { label: "Nostaljik", emoji: "📻", bpm: [90, 125], gain: -8.0, qTr: ["90lar türkçe pop", "2000ler türkçe pop"], qGl: ["90s hits", "80s hits"], tags: ["90s", "80s", "70s", "oldies", "nostalgia"] },
  asi:       { label: "Asi", emoji: "🔥", bpm: [110, 150], gain: -5.5, qTr: ["türkçe rock", "türkçe rock hareketli"], qGl: ["rock hits", "rock workout"], tags: ["rock", "alternative", "metal", "anatolian rock"] },
};

export const PLACES = {
  ev:      { label: "Evde", emoji: "🏠", qTr: [], qGl: [], tags: [] },
  araba:   { label: "Arabada", emoji: "🚗", qTr: ["türkçe yol şarkıları"], qGl: ["road trip"], tags: ["road trip", "driving"] },
  spor:    { label: "Sporda", emoji: "🏋️", qTr: ["türkçe spor"], qGl: ["workout hits"], tags: ["workout", "energetic"] },
  is:      { label: "Çalışırken", emoji: "💻", qTr: [], qGl: ["focus music"], tags: ["instrumental", "chill"] },
  parti:   { label: "Partide", emoji: "🎉", qTr: ["türkçe parti"], qGl: ["party hits"], tags: ["party", "dance"] },
  yuruyus: { label: "Yürüyüşte", emoji: "🚶", qTr: ["türkçe yürüyüş"], qGl: ["walking music"], tags: [] },
  uyku:    { label: "Uyumadan önce", emoji: "😴", qTr: ["türkçe uyku öncesi"], qGl: ["sleep songs"], tags: ["sleep", "calm"] },
};

const G = (id, label, emoji, tags, queries, deezer = [], soft = false, style = null) => ({ id, label, emoji, tags, queries, deezer, soft, style: style || tags });
export const GENRES_TR = [
  G("arabesk", "Arabesk", "🥀", ["arabesk", "arabesque"], ["arabesk", "arabesk damar"], [], false, ["arabesk", "arabesque", "fantezi"]),
  G("fantezi", "Fantezi", "💫", ["fantezi", "arabesk"], ["fantezi müzik", "fantezi şarkılar"]),
  G("tsm", "Türk Sanat Müziği", "🎻", ["türk sanat müziği", "turkish classical", "tsm"], ["türk sanat müziği", "tsm şarkılar"]),
  G("turku", "Türkü", "🪕", ["türkü", "turkish folk", "türk halk müziği"], ["türküler", "türk halk müziği"]),
  G("anadolurock", "Anadolu Rock", "🎸", ["anatolian rock", "anadolu rock", "turkish psychedelic"], ["anadolu rock"], [152, 464], false, ["anatolian rock", "anadolu rock", "turkish psychedelic", "psychedelic rock"]),
  G("rock", "Türkçe Rock", "🤘", ["turkish rock", "rock"], ["türkçe rock"], [152, 464], false, ["rock", "metal", "punk", "grunge"]),
  G("rap", "Türkçe Rap", "🎤", ["turkish rap", "turkish hip-hop"], ["türkçe rap"], [116], false, ["rap", "hip-hop", "hip hop", "trap", "drill"]),
  G("pop", "Türkçe Pop", "🎶", ["turkish pop"], ["türkçe pop hit"], [132], true),
  G("90lar", "90'lar Pop", "📼", ["90s", "turkish pop"], ["90lar türkçe pop"], [132], true),
  G("slow", "Slow", "🌙", ["slow", "ballad", "sad"], ["türkçe slow"], [], true),
  G("akustik", "Akustik", "🪵", ["acoustic", "unplugged"], ["türkçe akustik"], [], true),
  G("alternatif", "Alternatif", "🌀", ["indie", "alternative", "turkish indie"], ["türkçe alternatif", "türkçe indie"], [85]),
  G("elektronik", "Elektronik", "🎛️", ["electronic", "deep house", "dance"], ["türkçe elektronik", "türkçe remix"], [106, 113], false, ["electronic", "house", "edm", "techno", "electro", "dance"]),
  G("caz", "Caz", "🎷", ["jazz", "turkish jazz"], ["türkçe caz"], [129]),
];
export const GENRES_GL = [
  G("g_pop", "Pop", "🎶", ["pop"], ["pop hits"], [132], true),
  G("g_rock", "Rock", "🎸", ["rock", "classic rock"], ["rock classics"], [152, 464], false, ["rock", "metal", "punk", "grunge"]),
  G("g_hiphop", "Hip-Hop", "🎤", ["hip-hop", "rap"], ["hip hop hits"], [116], false, ["rap", "hip-hop", "hip hop", "trap", "drill"]),
  G("g_rnb", "R&B", "💜", ["rnb", "r&b", "soul"], ["r&b hits"], [165]),
  G("g_electronic", "Elektronik", "🎛️", ["electronic", "house", "edm"], ["electronic hits"], [106, 113], false, ["electronic", "house", "edm", "techno", "electro", "dance"]),
  G("g_indie", "Indie", "🌀", ["indie", "indie pop", "indie rock"], ["indie hits"], [85]),
  G("g_jazz", "Caz", "🎷", ["jazz"], ["jazz classics"], [129]),
  G("g_lofi", "Lo-fi", "🎧", ["lofi", "chillhop", "lo-fi"], ["lofi beats"], [], true),
  G("g_latin", "Latin", "💃", ["latin", "reggaeton"], ["latin hits"], [197]),
  G("g_80s", "80'ler", "📼", ["80s", "new wave"], ["80s hits"], [], true),
  G("g_classical", "Klasik", "🎼", ["classical"], ["classical essentials"], [98]),
  G("g_acoustic", "Akustik", "🪵", ["acoustic", "singer-songwriter"], ["acoustic hits"], [], true),
];
export const genreById = (id) => GENRES_TR.concat(GENRES_GL).find((g) => g.id === id) || null;

// ---- Hedef (ruh hali / tür / cümle) ----

/** { bpmLo, bpmHi, gain, queries[], tags[], style[], styleDeezer[], styleLabel, label } */
export function moodTarget(moodId, placeId, turkish) {
  const m = MOODS[moodId];
  if (!m) return null;
  const p = PLACES[placeId];
  const t = { bpmLo: m.bpm[0], bpmHi: m.bpm[1], gain: m.gain, queries: [], tags: [], style: [], styleDeezer: [], styleLabel: null, label: "" };
  if (p) {
    switch (placeId) {
      case "spor": t.bpmLo = Math.max(t.bpmLo, 118); t.bpmHi = Math.max(t.bpmHi, 150); t.gain += 1.5; break;
      case "parti": t.bpmLo = Math.max(t.bpmLo, 112); t.bpmHi = Math.max(t.bpmHi, 140); t.gain += 1.5; break;
      case "araba": t.bpmLo *= 1.03; t.bpmHi *= 1.06; t.gain += 0.5; break;
      case "yuruyus": t.bpmLo = Math.max(t.bpmLo, 95); t.bpmHi = Math.min(Math.max(t.bpmHi, 120), 135); break;
      case "is": t.bpmHi = Math.min(t.bpmHi, 120); t.gain -= 1; break;
      case "uyku": t.bpmHi = Math.min(t.bpmHi, 92); t.bpmLo = Math.min(t.bpmLo, 60); t.gain -= 2; break;
    }
    if (t.bpmHi - t.bpmLo < 15) t.bpmHi = t.bpmLo + 15;
    t.queries.push(...(turkish ? p.qTr : p.qGl));
    t.tags.push(...p.tags);
  }
  t.queries.push(...(turkish ? m.qTr : m.qGl));
  t.tags.push(...m.tags);
  t.label = m.emoji + " " + m.label + (p ? " · " + p.emoji + " " + p.label : "");
  return t;
}

export function applyGenre(t, g) {
  for (const q of g.queries) if (!t.queries.includes(q)) t.queries.push(q);
  if (g.soft) { for (const x of g.tags) if (!t.tags.includes(x)) t.tags.push(x); return; }
  for (const x of g.style) if (!t.style.includes(x)) t.style.push(x);
  for (const d of g.deezer) if (!t.styleDeezer.includes(d)) t.styleDeezer.push(d);
  t.styleLabel = g.emoji + " " + g.label;
}

export function genreTarget(id) {
  const g = genreById(id);
  if (!g) return null;
  const t = { bpmLo: 40, bpmHi: 220, gain: NaN, queries: [...g.queries], tags: [], style: [], styleDeezer: [], styleLabel: null, label: g.emoji + " " + g.label };
  applyGenre(t, g);
  return t;
}

// ---- Cümle ayrıştırıcı ----

const S = (id, label, emoji, mood, bpm, gain, keys, qTr, qGl, tags) => ({ id, label, emoji, mood, bpm, gain, keys, qTr, qGl, tags });
const SCENES = [
  S("sahil", "Sahil", "🏖️", "sakin", -0.02, -0.5, ["sahil", "deniz", "kumsal", "plaj", "kıyı", "iskele", "dalga", "marina"], ["yaz şarkıları", "türkçe chill", "sahil"], ["beach vibes", "summer chill"], ["summer", "chill", "beach", "acoustic"]),
  S("gunbatimi", "Gün batımı", "🌅", "sakin", -0.03, -0.5, ["gün batımı", "günbatımı", "akşamüstü", "akşam üstü", "akşamüzeri", "akşam üzeri", "gurup"], ["türkçe chill", "türkçe akustik", "gün batımı"], ["sunset chill", "golden hour"], ["chill", "sunset", "mellow", "acoustic"]),
  S("aksam", "Akşam", "🌆", "sakin", -0.02, -0.5, ["akşam"], ["türkçe chill", "türkçe akustik"], ["evening chill"], ["chill", "mellow"]),
  S("gece", "Gece", "🌃", "sakin", -0.03, -1.0, ["gece", "geceyarısı", "gece yarısı", "geç saat", "uykusuz"], ["türkçe slow", "gece", "türkçe akustik"], ["late night vibes", "night drive"], ["night", "chill", "melancholic"]),
  S("sabah", "Sabah", "☀️", "mutlu", 0.02, 0, ["sabah", "kahvaltı", "uyandım", "güne başla", "günaydın"], ["günaydın", "türkçe pop hit", "türkçe akustik"], ["morning acoustic", "good morning"], ["happy", "morning", "acoustic"]),
  S("yagmur", "Yağmur", "🌧️", "huzunlu", -0.03, -1.0, ["yağmur", "kapalı hava", "bulutlu", "fırtına", "gri hava"], ["türkçe slow", "hüzünlü türkçe", "yağmur"], ["rainy day", "rainy jazz"], ["melancholic", "sad", "rain", "acoustic"]),
  S("kahve", "Kahve", "☕", "sakin", -0.02, -1.0, ["kahve", "kafe", "cafe", "çay", "kitapçı", "kitap okurken"], ["türkçe akustik", "kahve", "türkçe chill"], ["coffee shop acoustic", "cafe music"], ["acoustic", "coffee", "chill", "jazz"]),
  S("yaz", "Yaz", "🌞", "mutlu", 0.03, 0.5, ["yaz ", "yazın", "yaz akşam", "yaz gece", "tatil", "havuz", "güneşli", "sıcak"], ["yaz şarkıları", "yaz hitleri", "türkçe pop hit"], ["summer hits", "summer vibes"], ["summer", "dance", "party", "happy"]),
  S("kis", "Kış", "❄️", "sakin", -0.02, -0.5, ["kış", "kar ", "karlı", "soğuk", "şömine", "battaniye"], ["türkçe akustik", "türkçe slow"], ["winter chill", "cozy acoustic"], ["winter", "melancholic", "acoustic"]),
  S("doga", "Doğa", "🌲", "sakin", -0.02, -0.5, ["doğa", "orman", "kamp", "dağ", "göl", "piknik", "bahçe", "ağaç"], ["türkçe akustik", "kamp", "türkçe alternatif"], ["camping acoustic", "indie folk"], ["acoustic", "folk", "indie"]),
  S("meyhane", "Rakı masası", "🥂", "nostaljik", -0.02, -0.5, ["rakı", "meyhane", "fasıl", "rakı masası"], ["rakı masası şarkıları", "türk sanat müziği", "fasıl", "arabesk"], ["rakı masası şarkıları", "türk sanat müziği"], ["arabesk", "türk sanat müziği", "turkish classical", "fasil", "fantezi", "arabesque"]),
  S("arkadas", "Arkadaşlar", "🍻", "mutlu", 0.02, 0.5, ["arkadaş", "kanka", "mangal", "muhabbet", "dostlar", "ekiple"], ["türkçe pop hit", "eğlenceli türkçe", "parti"], ["friends hangout", "feel good hits"], ["party", "happy", "dance"]),
  S("yemek", "Akşam yemeği", "🍷", "romantik", -0.03, -1.0, ["yemek", "akşam yemeği", "şarap", "restoran", "mum ışığı"], ["türkçe caz", "türkçe akustik", "romantik türkçe"], ["dinner jazz", "dinner party"], ["jazz", "lounge", "romantic", "chill"]),
];
const STRONG = new Set(["meyhane"]);

const MOOD_KEYS = {
  nostaljik: ["nostalji", "eskiler", "eski şarkı", "90", "2000", "80", "çocukluk", "anılar", "anı ", "geçmiş", "eski günler", "retro"],
  huzunlu: ["hüzün", "üzgün", "mutsuz", "kırgın", "ağla", "ayrıl", "yalnız", "melankoli", "buruk", "depres", "kalbim kırık", "özle", "dertli", "duygusal", "efkar", "hasret"],
  romantik: ["romantik", "aşk", "sevgili", "aşık", "flört", "randevu", "öp", "sarıl", "evlilik", "nişan", "ilk buluşma"],
  enerjik: ["enerji", "hareketli", "coşku", "gaz", "hızlı", "dans", "zıpla", "motivasyon", "hareket", "yüksek tempo", "çılgın", "kopmak", "uçmak", "hype"],
  asi: ["sinir", "öfke", "kızgın", "rock", "isyan", "hırs", "stres at", "bağır", "metal"],
  odak: ["odak", "ders", "ödev", "konsantre", "kod", "sınav", "verimli", "üretken", "kitap oku", "yazı yaz"],
  sakin: ["sakin", "huzur", "rahat", "dinlen", "chill", "yavaş", "sessiz", "kafa dinle", "kafamı dinle", "kafamı boşalt", "hafif", "dingin", "sakinleş", "rahatla", "tatlı", "lounge", "soft"],
  mutlu: ["mutlu", "neşe", "keyif", "sevinç", "güzel bir gün", "havamda", "pozitif", "eğlen", "gülümse", "iyi hissed"],
};
const PLACE_KEYS = {
  spor: ["spor", "gym", "koşu", "koş", "antrenman", "fitness", "salon", "idman", "bisiklet"],
  araba: ["araba", "yolculuk", "otoban", "direksiyon", "sürüş", "road trip", "yola"],
  parti: ["parti", "kutlama", "doğum günü", "eğlence", "kulüp", "düğün"],
  is: ["iş", "ofis", "çalışırken", "mesai", "toplantı"],
  yuruyus: ["yürüyüş", "yürü", "park", "sokak", "gezinti", "gezer"],
  uyku: ["uyku", "uyumadan", "yatarken", "yatmadan", "uyuyacağım", "uyumak"],
  ev: ["ev", "evde", "oda", "koltuk", "kanepe"],
};
const GENRE_KEYS = [
  ["anadolurock", "g_rock", "anadolu rock", "anadolu rok"],
  ["rap", "g_hiphop", "rap ", "rapçı ", "rapçi", "rapler", "rapı ", "rapli ", "rapte ", "hip hop", "hiphop", "trap", "drill"],
  ["rock", "g_rock", "rock", "rok ", "metal", "punk", "grunge"],
  ["arabesk", "arabesk", "arabesk", "damar"],
  ["fantezi", "fantezi", "fantezi"],
  ["tsm", "tsm", "sanat müziği", "tsm", "klasik türk"],
  ["turku", "turku", "türkü", "halk müziği", "bağlama"],
  ["elektronik", "g_electronic", "elektronik", "edm", "house", "techno", "tekno", "electro"],
  ["caz", "g_jazz", "caz ", "cazlı", "jazz"],
  ["alternatif", "g_indie", "alternatif", "indie"],
  ["akustik", "g_acoustic", "akustik", "unplugged"],
  ["slow", "slow", "slow"],
  ["g_lofi", "g_lofi", "lofi", "lo fi"],
  ["g_rnb", "g_rnb", "rnb", "soul"],
  ["g_latin", "g_latin", "latin", "reggaeton"],
  ["g_classical", "g_classical", "klasik müzik"],
  ["pop", "g_pop", "pop"],
];
const HARD = ["sert", "agresif", "hard", "hardcore", "vurucu", "karanlık", "dark"];
const CALMER = ["çok sakin", "iyice sakin", "fazla sakin", "yumuşak", "usul"];
const UPPER = ["çok hareketli", "çok enerjik", "bas bas", "full gaz", "son ses"];

function fold(s) {
  return (s || "").replace(/I/g, "ı").replace(/İ/g, "i").toLowerCase()
    .replace(/ı/g, "i").replace(/ş/g, "s").replace(/ğ/g, "g").replace(/ü/g, "u").replace(/ö/g, "o").replace(/ç/g, "c").replace(/â/g, "a")
    .replace(/[^\p{L}\p{N}]+/gu, " ").trim();
}
function countAny(s, keys) {
  let n = 0;
  for (const k of keys) {
    const fk = fold(k);
    if (!fk) continue;
    if (k.endsWith(" ")) { if (s.includes(" " + fk + " ")) n++; }
    else if (fk.length <= 3) { if (new RegExp("\\s" + fk.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\p{L}{0,3}\\s", "u").test(s)) n++; }
    else if (s.includes(" " + fk)) n++;
  }
  return n;
}

function hardTags(gid) {
  if (["rap", "g_hiphop"].includes(gid)) return ["trap", "drill", "aggressive", "hardcore", "dark"];
  if (["rock", "g_rock", "anadolurock"].includes(gid)) return ["hard rock", "metal", "heavy", "aggressive"];
  if (["elektronik", "g_electronic"].includes(gid)) return ["techno", "hard", "dark"];
  return ["aggressive", "heavy", "dark"];
}

/** Serbest cümleden hedef. Dönen: { target, summary } */
export function parseVibe(text, hour, turkish) {
  const s = " " + fold(text) + " ";
  const scenes = [];
  let bpmShift = 0, gainShift = 0;
  for (const sc of SCENES) {
    if (!countAny(s, sc.keys)) continue;
    if (sc.id === "aksam" && (scenes.some((x) => x.id === "gunbatimi") || countAny(s, ["akşam yemeği"]))) continue;
    if (sc.id === "gece" && scenes.some((x) => x.id === "aksam")) continue;
    scenes.push(sc);
    bpmShift += sc.bpm;
    gainShift += sc.gain;
  }
  let moodId = null, best = 0;
  for (const [id, keys] of Object.entries(MOOD_KEYS)) { const n = countAny(s, keys); if (n > best) { best = n; moodId = id; } }
  let placeId = null; best = 0;
  for (const [id, keys] of Object.entries(PLACE_KEYS)) { const n = countAny(s, keys); if (n > best) { best = n; placeId = id; } }
  let genre = null;
  for (const gk of GENRE_KEYS) if (countAny(s, gk.slice(2))) { genre = genreById(turkish ? gk[0] : gk[1]); break; }
  const hard = countAny(s, HARD) > 0;
  if (hard) { gainShift += 1.5; bpmShift += 0.02; }
  if (countAny(s, CALMER)) { bpmShift -= 0.04; gainShift -= 1; }
  if (countAny(s, UPPER)) { bpmShift += 0.04; gainShift += 1; }
  let guessed = false;
  if (!moodId) {
    guessed = true;
    if (scenes.length) moodId = scenes[0].mood;
    else if (placeId === "spor" || placeId === "parti") moodId = "enerjik";
    else if (placeId === "uyku") moodId = "sakin";
    else if (hard) moodId = genre ? "enerjik" : "asi";
    else if (placeId === "is") moodId = "odak";
    else if (hour >= 22 || hour < 6) moodId = "sakin";
    else moodId = "mutlu";
  }
  const parts = [];
  if (genre) parts.push(genre.emoji + " " + genre.label.replace("Türkçe ", ""));
  if (hard) parts.push("🔥 Sert");
  parts.push(MOODS[moodId].emoji + " " + MOODS[moodId].label);
  if (PLACES[placeId]) parts.push(PLACES[placeId].emoji + " " + PLACES[placeId].label);
  for (const sc of scenes) parts.push(sc.emoji + " " + sc.label);
  const summary = parts.join(" · ");

  const t = moodTarget(moodId, placeId, turkish);
  const strong = scenes.some((x) => STRONG.has(x.id)) && guessed;
  const f = 1 + bpmShift;
  t.bpmLo *= f; t.bpmHi *= f; t.gain += gainShift;
  if (strong) { t.bpmLo = 60; t.bpmHi = 135; }
  const q = [], tg = [];
  for (const sc of scenes.slice(0, 2)) {
    for (const x of (turkish ? sc.qTr : sc.qGl).slice(0, strong ? 4 : 2)) if (!q.includes(x)) q.push(x);
    for (const x of sc.tags) if (!tg.includes(x)) tg.push(x);
  }
  if (!strong) { for (const x of t.queries) if (!q.includes(x)) q.push(x); for (const x of t.tags) if (!tg.includes(x)) tg.push(x); }
  t.queries = q; t.tags = tg;
  if (genre) {
    const base = turkish ? genre.queries[0] : genre.style[0];
    const gq = [];
    if (placeId === "spor") gq.push(base + (turkish ? " spor" : " workout"));
    if (hard) gq.push(turkish ? base + " sert" : "aggressive " + base);
    for (const x of genre.queries) if (!gq.includes(x)) gq.push(x);
    t.queries = gq;
    if (guessed) t.tags = [...(PLACES[placeId]?.tags || [])];
    if (hard) for (const x of hardTags(genre.id)) if (!t.tags.includes(x)) t.tags.push(x);
    applyGenre(t, genre);
    if (["rap", "g_hiphop", "g_rnb", "g_lofi"].includes(genre.id)) { t.bpmLo = Math.min(t.bpmLo, 75); t.bpmHi = Math.max(t.bpmHi, 175); }
    else if (["rock", "g_rock", "anadolurock"].includes(genre.id)) { t.bpmLo = Math.min(t.bpmLo, 90); t.bpmHi = Math.max(t.bpmHi, 185); }
    else { t.bpmLo *= 0.85; t.bpmHi *= 1.15; }
  } else if (hard) {
    for (const x of hardTags(null)) if (!t.tags.includes(x)) t.tags.push(x);
  }
  t.label = summary;
  return { target: t, summary };
}

/** Ayarlardan geçerli hedef (yoksa null). */
export function targetFor(settings, hour) {
  const tr = settings.lang !== "global";
  if (settings.genre) return genreTarget(settings.genre);
  if (settings.text) return parseVibe(settings.text, hour, tr).target;
  if (settings.mood) return moodTarget(settings.mood, settings.place, tr);
  return null;
}
