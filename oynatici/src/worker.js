// Ahenk Oynatıcı: Ahenk'in seçim motoruyla çalışan, YouTube'un resmi gömülü oynatıcısını kullanan sürüm.
// Sunucu durumsuzdur: dinleme geçmişi, beğenmediklerin ve ayarların telefonda (localStorage) durur ve her
// istekte gönderilir. Sunucuda yalnızca ortak önbellek (Deezer ayrıntıları, YouTube eşleşmeleri) tutulur.
// Seçim motoru mevcut Ahenk bulutuyla ortaktır (../../cloud/src).
import { Ctx, slim } from "../../cloud/src/sources.js";
import { choose } from "../../cloud/src/engine.js";
import { key, norm, mainArtist } from "../../cloud/src/util.js";
import { MOODS, GENRES_TR, GENRES_GL, PLACES } from "../../cloud/src/data.js";
import { ytFind, ytBad } from "./youtube.js";

const json = (o, status = 200) => new Response(JSON.stringify(o), {
  status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
});

const str = (s, n = 200) => (typeof s === "string" ? s.slice(0, n) : "");
const strArr = (a, n, m = 200) => (Array.isArray(a) ? a.filter((x) => typeof x === "string").slice(-n).map((x) => x.slice(0, m)) : []);

/** İstemciden gelen durumu temizle (boyut sınırı, tip kontrolü). */
function cleanState(s = {}) {
  const pairs = {};
  if (s.pairs && typeof s.pairs === "object") {
    for (const [k, v] of Object.entries(s.pairs).slice(-80)) pairs[str(k)] = strArr(v, 10);
  }
  return {
    recentArtists: [...new Set(strArr(s.recentArtists, 8).map((a) => norm(mainArtist(a))).filter(Boolean))],
    recentKeys: strArr(s.recentKeys, 60),
    avoid: strArr(s.avoid, 300),
    pairs,
  };
}

function cleanSettings(s = {}) {
  const out = {};
  out.lang = s.lang === "global" ? "global" : "tr";
  for (const k of ["mood", "genre", "place"]) out[k] = typeof s[k] === "string" && s[k] ? s[k].slice(0, 40) : null;
  out.text = typeof s.text === "string" && s.text.trim() ? s.text.trim().slice(0, 200) : null;
  out.explore = [0, 1, 2].includes(s.explore) ? s.explore : 1;
  out.tz = typeof s.tz === "string" ? s.tz.slice(0, 60) : "Europe/Istanbul";
  return out;
}

const ytError = (e) => e.message === "QUOTA" ? "Bugünlük YouTube arama sınırı doldu. Daha önce çalınan şarkılar çalmaya devam eder; yarın sabah sıfırlanır."
  : e.message === "NOKEY" ? "YouTube anahtarı henüz eklenmemiş (YOUTUBE_API_KEY)."
  : "YouTube'a ulaşılamadı: " + e.message;

const card = (x, v) => ({ t: x.t, a: x.a, c: x.cb || x.c || null, bpm: x.bpm || 0, y: x.y || 0, reason: x.reason || "", k: x.k || key(x.a, x.t), v: v || null });

async function api(req, env, path) {
  const url = new URL(req.url);

  if (path === "/api/meta") {
    return json({
      moods: Object.entries(MOODS).map(([id, m]) => ({ id, label: m.label, emoji: m.emoji })),
      genresTr: GENRES_TR.map((g) => ({ id: g.id, label: g.label, emoji: g.emoji })),
      genresGl: GENRES_GL.map((g) => ({ id: g.id, label: g.label, emoji: g.emoji })),
      places: Object.entries(PLACES).map(([id, p]) => ({ id, label: p.label, emoji: p.emoji })),
      youtube: !!env.YOUTUBE_API_KEY,
    });
  }

  // Başlangıç şarkısı aramak için (Deezer, anahtarsız)
  if (path === "/api/search") {
    const q = str(url.searchParams.get("q") || "", 100).trim();
    if (q.length < 2) return json({ items: [] });
    const ctx = new Ctx(env, 2);
    const r = await ctx.fetch(`https://api.deezer.com/search?limit=10&q=${encodeURIComponent(q)}`, { headers: { "User-Agent": "Ahenk/1.0" } });
    const j = await r.json().catch(() => ({}));
    const items = (j.data || []).map((o) => slim(o)).filter(Boolean).map((x) => card(x));
    return json({ items });
  }

  if (req.method !== "POST") return json({ error: "Yöntem desteklenmiyor" }, 405);
  const b = await req.json().catch(() => ({}));

  // Bir şarkının YouTube videosu
  if (path === "/api/yt") {
    const a = str(b.a), t = str(b.t);
    if (!a || !t) return json({ error: "Eksik bilgi" }, 400);
    const ctx = new Ctx(env, 3);
    try {
      if (b.bad) await ytBad(ctx, a, t, str(b.bad, 20));
      const hit = await ytFind(ctx, env, a, t);
      await ctx.flush().catch(() => {});
      return json({ v: hit?.v || null });
    } catch (e) {
      await ctx.flush().catch(() => {});
      return json({ v: null, error: ytError(e) });
    }
  }

  // Sıradakini seç ve YouTube karşılığını bul
  if (path === "/api/next") {
    const settings = cleanSettings(b.settings);
    const state = cleanState(b.state);
    const now = b.now && str(b.now.t) ? { artist: str(b.now.a), title: str(b.now.t) } : { artist: "", title: "" };
    if (!now.title && !settings.mood && !settings.genre && !settings.text) return json({ error: "Bir şarkıyla, ruh haliyle ya da türle başla." }, 400);
    const ctx = new Ctx(env, 42);
    if (!now.title) {   // tohum şarkı yok: sadece ruh hali / tür
      ctx.mem.set("seed:" + key("", ""), { id: 0, aid: 0 });
      ctx.mem.set("lf:", {});
    }
    let res;
    try {
      res = await choose(ctx, now, settings, state, env.LASTFM_KEY || null);
    } catch (e) {
      await ctx.flush().catch(() => {});
      return json({ error: e.message === "BUDGET" ? "Seçim zaman aşımına uğradı, tekrar dene." : "Seçim yapılamadı: " + e.message }, 502);
    }
    if (!res) { await ctx.flush().catch(() => {}); return json({ error: "Uygun şarkı bulunamadı. Başka bir ruh hali ya da şarkı dene." }, 404); }

    // Sıralamada YouTube'da bulunan ilk aday sıradaki olur
    let pick = null, ytErr = null;
    const tried = new Set();
    for (const c of res.ranked.slice(0, 4)) {
      if (ctx.left() < 1) break;
      tried.add(c.k);
      try {
        const hit = await ytFind(ctx, env, c.a, c.t);
        if (hit) { pick = card(c, hit.v); break; }
      } catch (e) { ytErr = ytError(e); break; }
    }
    await ctx.flush().catch(() => {});
    if (!pick) return json({ error: ytErr || "Seçilen şarkılar YouTube'da bulunamadı, tekrar dene." }, ytErr ? 503 : 404);
    const alts = res.ranked.filter((x) => x.k !== pick.k).slice(0, 4).map((x) => card(x));
    return json({ pick, alts, seedKey: res.seedKey, target: res.target || null });
  }

  return json({ error: "Bulunamadı" }, 404);
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname.startsWith("/api/")) {
      try { return await api(req, env, url.pathname); }
      catch (e) { return json({ error: "Sunucu hatası: " + e.message }, 500); }
    }
    const res = await env.ASSETS.fetch(req);
    if (res.status !== 404) return res;
    const home = await env.ASSETS.fetch(new Request(new URL("/", url), { method: "GET", headers: req.headers }));
    return new Response(home.body, { status: 200, headers: home.headers });
  },
};

// Sınama için
export const _api = api;
