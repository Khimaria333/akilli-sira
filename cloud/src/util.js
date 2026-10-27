// Ahenk bulut: ortak yardımcılar (Android'deki Text / Lang ile aynı mantık)

export function cleanTitle(s) {
  if (!s) return "";
  let t = s.replace(/\([^)]*\)/g, " ").replace(/\[[^\]]*\]/g, " ");
  t = t.replace(/\s(feat\.?|ft\.?|featuring)\s.*$/i, " ");
  t = t.replace(/\s-\s.*(remaster|version|edit|mix|live|mono|stereo).*$/i, " ");
  t = t.replace(/\s+/g, " ").trim();
  return t || s.trim();
}

export function mainArtist(s) {
  if (!s) return "";
  return s.split(/\s(?:&|x|feat\.?|ft\.?)\s|,|•|·/i)[0].trim();
}

export function norm(s) {
  if (!s) return "";
  let t = cleanTitle(s).toLocaleLowerCase("tr");
  t = t.replace(/ı/g, "i").replace(/ş/g, "s").replace(/ğ/g, "g").replace(/ü/g, "u").replace(/ö/g, "o").replace(/ç/g, "c");
  return t.replace(/[^\p{L}\p{N}]+/gu, " ").trim();
}

export const key = (artist, title) => norm(mainArtist(artist)) + "|" + norm(title);

export function titlesMatch(a, b) {
  const x = norm(a), y = norm(b);
  if (!x || !y) return false;
  return x === y || x.startsWith(y) || y.startsWith(x);
}

// ---- Dil ----
export const UNKNOWN = 0, TR = 1, FOREIGN = 2;
const TR_WORDS = new Set(("ve bir ben sen beni seni bana sana benim senin bizim aşk aşkım yok var gel git gibi için icin kalp kalbim " +
  "gece gün yine artık olmaz olsun değil deli güzel sevgilim canım hayat dünya dünyam neden nasıl kadar daha hep hiç şey bu şu aman " +
  "yar yarim yalan kader özledim seviyorum sevda sevdim gözler gözlerin yüreğim dön kal oldu olur geceler kız yanarım kadın kalpsizsin " +
  "ağla ağlama sevgili hasret ayrılık unut unutma bitti biter gitme gidiyorum sevmek sevme aşkın yüzün yolu yol yollar sabah akşam bugün " +
  "yarın dün gönül derdim dert kara sevdalı vay başka biri birisi kimse hadi haydi söyle sor bak ister istemem affet bilmem olmuyor yazık " +
  "kalbe kalbin canın ölürüm aşka sensiz seninle benimle bende sende aklım kafam deliler deniz rüzgar yağmur çiçek gül bahar yaz kış " +
  "yıldız ışık şarkı türkü").split(" "));
const FOREIGN_WORDS = new Set(("the you me my i love in on to your it is we be baby night heart don't dont it's im i'm with all what when " +
  "like girl boy good bad time life stay go never one up down new home this that no yes oh just more dance world forever tonight without " +
  "everything nothing feel lights rain sun fire song away back again people dream dreams kiss want need know let can't cant won't are for " +
  "from out about after before only still where why who how la los las que amor corazón bonita les je pas moi toi die der und ich nicht " +
  "mein dein liebe amore cuore sei che").split(" "));

export function detectLang(title, artist) {
  const t = title || "", a = artist || "";
  if (/[ğĞşŞıİ]/.test(t + a)) return TR;
  if (/[Ͱ-ϿЀ-ӿ֐-ۿऀ-෿぀-ヿ一-鿿가-힯]/.test(t + a)) return FOREIGN;
  let tr = 0, fo = 0;
  for (const w of cleanTitle(t).toLocaleLowerCase("tr").split(/[^\p{L}']+/u)) {
    if (!w) continue;
    if (TR_WORDS.has(w)) tr++;
    if (FOREIGN_WORDS.has(w)) fo++;
  }
  const weak = /[çÇöÖüÜ]/.test(t + a);
  if (tr > fo) return TR;
  if (fo > tr) return FOREIGN;
  if (weak && fo === 0) return TR;
  return UNKNOWN;
}

const TR_MARKS = ["turkish", "türk", "turk", "türkçe", "turkce", "arabesk", "arabesque", "anatolian", "anadolu", "fasıl", "fasil",
  "istanbul", "turkey", "türkiye", "türkü", "turku"];
const FOREIGN_MARKS = ["greek", "english", "american", "british", "german", "french", "spanish", "italian", "k-pop", "kpop", "korean",
  "japanese", "j-pop", "russian", "arabic", "persian", "iranian", "latin", "laiko", "rebetiko", "bulgarian", "serbian", "balkan",
  "azeri", "azerbaijani", "bollywood", "hindi", "brazilian", "uk ", "usa"];

export function langFromTags(tags) {
  if (!tags) return UNKNOWN;
  let tr = 0, fo = 0;
  for (const [k, v] of Object.entries(tags)) {
    const t = k + " ";
    if (TR_MARKS.some((m) => t.includes(m))) tr += v;
    if (FOREIGN_MARKS.some((m) => t.includes(m))) fo += v;
  }
  if (!tr && !fo) return UNKNOWN;
  if (tr >= fo) return TR;
  return fo >= 30 ? FOREIGN : UNKNOWN;
}

const LANG_TAGS = new Set(["turkish", "türkçe", "turkce", "türk", "turkey", "greek", "english", "american", "british"]);
export const isLangTag = (t) => LANG_TAGS.has(t);

/** Kosinüs benzerliği (dil etiketleri hariç); bilinmiyorsa -1. */
export function similarity(a, b) {
  if (!a || !b) return -1;
  let dot = 0, na = 0, nb = 0;
  for (const [k, v] of Object.entries(a)) {
    if (isLangTag(k)) continue;
    na += v * v;
    if (b[k] != null) dot += v * b[k];
  }
  for (const [k, v] of Object.entries(b)) if (!isLangTag(k)) nb += v * v;
  if (!na || !nb) return -1;
  return dot / Math.sqrt(na * nb);
}

/** İstenen etiketlerle örtüşme 0..1; bilinmiyorsa -1. */
export function overlap(tags, want) {
  if (!tags || !Object.keys(tags).length || !want || !want.length) return -1;
  let best = 0, sum = 0;
  for (const w of want) {
    for (const [k, v] of Object.entries(tags)) {
      if (k.includes(w)) { sum += v / 100; best = Math.max(best, v / 100); break; }
    }
  }
  return Math.min(1, best * 0.7 + sum * 0.3);
}

export function sharedTag(a, b) {
  if (!a || !b) return null;
  let best = null, bv = 0;
  for (const [k, v] of Object.entries(a)) {
    if (isLangTag(k) && !k.includes("arabesk") && !k.includes(" ")) continue;
    if (b[k] == null) continue;
    const m = Math.min(v, b[k]);
    if (m > bv) { bv = m; best = k; }
  }
  return best;
}

export function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

export const sample = (arr, n) => shuffle([...arr]).slice(0, n);

export function randomString(n) {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  const b = new Uint8Array(n);
  crypto.getRandomValues(b);
  return Array.from(b, (x) => chars[x % chars.length]).join("");
}
