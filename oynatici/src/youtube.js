// Ahenk Oynatıcı: şarkının YouTube karşılığını resmi YouTube Data API ile bulur.
// search.list pahalıdır (günlük varsayılan kota ~100 arama), bu yüzden her sonuç D1'de saklanır:
// bir şarkı tüm kullanıcılar için yalnızca bir kez aranır.
import { norm, cleanTitle, mainArtist, key } from "../../cloud/src/util.js";

const API = "https://www.googleapis.com/youtube/v3/search";
const DAY = 86400000;

// İstenmedikçe kaçınılan sürümler
const BAD = [
  [/\b(cover|karaoke|instrumental|enstrumantal|altyapi|tutorial|ders|nasil calinir|reaction|tepki)\b/, 40],
  [/\b(slowed|sped up|speed up|8d|nightcore|reverb|bass boosted)\b/, 35],
  [/\b(remix|mix|mashup)\b/, 18],
  [/\b(live|canli|konser|concert|akustik|acoustic)\b/, 14],
  [/\b(full album|playlist|en iyi sarkilar|best of|1 saat|1 hour)\b/, 50],
];

function scoreItem(it, artist, title) {
  const vt = norm(it.snippet?.title || "");
  const ch = norm(it.snippet?.channelTitle || "");
  const a = norm(mainArtist(artist));
  const t = norm(cleanTitle(title));
  const want = norm(artist + " " + title);
  if (!t || !vt.includes(t)) return -1;             // şarkı adı başlıkta yoksa alma
  let s = 30;
  const chName = ch.replace(/ topic$/, "").trim();
  if (a && chName && (ch.includes(a) || a.includes(chName))) s += 30;
  else if (a && vt.includes(a)) s += 18;
  if (/ topic$/.test(ch)) s += 14;                 // YouTube'un otomatik "Sanatçı - Topic" kanalı: albüm sesi
  if (/\bvevo$/.test(ch)) s += 8;
  if (/\b(official|resmi)\b/.test(vt)) s += 8;
  if (/\b(lyrics?|sozleri)\b/.test(vt)) s -= 4;
  for (const [re, p] of BAD) if (re.test(vt) && !re.test(want)) s -= p;
  return s;
}

/**
 * { v, ti, ch } ya da null. Kota biterse "QUOTA" hatası atar.
 * bad: bu şarkı için daha önce gömülemeyen video kimlikleri.
 */
export async function ytFind(ctx, env, artist, title, bad = []) {
  const ck = "yt:" + key(artist, title);
  await ctx.load([ck]);
  const c = ctx.get(ck);
  const badSet = new Set([...(c?.bad || []), ...bad]);
  if (c && c.v && !badSet.has(c.v)) return c;
  if (c && !c.v && c.at && Date.now() - c.at < 7 * DAY && !bad.length) return null;   // yakın zamanda bulunamadı
  if (!env.YOUTUBE_API_KEY) throw new Error("NOKEY");

  const q = `${mainArtist(artist)} ${cleanTitle(title)}`;
  const u = `${API}?part=snippet&type=video&videoEmbeddable=true&videoSyndicated=true&maxResults=8&regionCode=TR` +
    `&q=${encodeURIComponent(q)}&key=${encodeURIComponent(env.YOUTUBE_API_KEY)}`;
  const r = await ctx.fetch(u);
  const j = await r.json().catch(() => ({}));
  if (!r.ok) {
    const reason = j?.error?.errors?.[0]?.reason || "";
    if (r.status === 403 && /quota|rateLimit|dailyLimit/i.test(reason)) throw new Error("QUOTA");
    throw new Error("YouTube " + r.status + (reason ? " " + reason : ""));
  }
  let best = null, bs = 0;
  for (const it of j.items || []) {
    const v = it.id?.videoId;
    if (!v || badSet.has(v)) continue;
    const s = scoreItem(it, artist, title);
    if (s > bs) { bs = s; best = { v, ti: it.snippet.title, ch: it.snippet.channelTitle }; }
  }
  const out = best && bs >= 30 ? { ...best, bad: [...badSet].slice(-8), at: Date.now() } : { v: null, bad: [...badSet].slice(-8), at: Date.now() };
  ctx.put(ck, out);
  return out.v ? out : null;
}

/** Gömülemeyen videoyu işaretle: bir dahaki istekte başka bir video aranır. */
export async function ytBad(ctx, artist, title, v) {
  const ck = "yt:" + key(artist, title);
  await ctx.load([ck]);
  const c = ctx.get(ck) || {};
  const bad = [...new Set([...(c.bad || []), v])].slice(-8);
  ctx.put(ck, { ...c, v: c.v === v ? null : c.v, bad, at: 0 });
}

// Sınama için
export const _scoreItem = scoreItem;
