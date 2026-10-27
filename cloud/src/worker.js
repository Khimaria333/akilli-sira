// Ahenk bulut: Spotify için her cihazda çalışan "sıradaki şarkı" servisi.
// Her dakika her kullanıcının Spotify'ına ne çaldığını sorar; yeni şarkıda sıradakini seçer,
// şarkının son dakikasında Spotify'ın sırasına ekler. Web uygulaması (public/) ayarları ve durumu gösterir.
import { Ctx } from "./sources.js";
import { choose } from "./engine.js";
import { exchangeCode, nowPlaying, findTrack, addToQueue, playUris } from "./spotify.js";
import { key, norm, mainArtist, titlesMatch, shuffle, randomString } from "./util.js";
import { MOODS, GENRES_TR, GENRES_GL, PLACES } from "./data.js";

const QUEUE_WINDOW_MS = 75000;   // şarkının bitmesine bu kadar kala sıraya ekle

const json = (o, status = 200) => new Response(JSON.stringify(o), {
  status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
});

const parse = (s, d = {}) => { try { return s ? JSON.parse(s) : d; } catch { return d; } };

async function saveState(env, user, st) {
  user.state = JSON.stringify(st);
  user.last_tick = Date.now();
  await env.DB.prepare("UPDATE users SET state = ?, last_tick = ?, updated = ? WHERE id = ?")
    .bind(user.state, user.last_tick, Date.now(), user.id).run();
}

function pushLimited(arr, v, n) {
  const a = (arr || []).filter((x) => x !== v);
  a.push(v);
  return a.slice(-n);
}

/** Spotify'da karşılığını bul (en fazla birkaç aday dener). */
async function resolveSpotify(env, user, ranked, ctx) {
  for (const c of ranked.slice(0, 3)) {
    if (ctx.left() < 2) break;
    const items = await findTrack(env, user, mainArtist(c.a), c.t, ctx).catch(() => []);
    const want = norm(mainArtist(c.a));
    let hit = items.find((it) => titlesMatch(it.name, c.t) && (it.artists || []).some((a) => norm(a.name).includes(want) || want.includes(norm(a.name))));
    if (!hit) hit = items.find((it) => (it.artists || []).some((a) => norm(a.name) === want) && titlesMatch(it.name, c.t));
    if (hit) return { ...c, uri: hit.uri, sc: hit.album?.images?.[0]?.url || null };
  }
  return null;
}

/** Bir kullanıcı için: çalanı oku, gerekirse sıradakini seç, zamanı gelince sıraya ekle. */
async function tick(env, user, ctx, opts = {}) {
  const st = parse(user.state);
  const set = parse(user.settings);
  let np;
  try {
    np = await nowPlaying(env, user, ctx);
  } catch (e) {
    st.error = e.message === "REVOKED" ? "Spotify bağlantısı sona erdi; yeniden bağlan." : e.message;
    await saveState(env, user, st);
    return st;
  }
  st.lastCheck = Date.now();
  st.now = np;
  st.error = null;
  if (!np) { await saveState(env, user, st); return st; }

  // ---- Yeni şarkı ----
  if (np.id !== st.curId) {
    const k = key(np.artist, np.title);
    st.recentArtists = pushLimited(st.recentArtists, norm(mainArtist(np.artist)), 8);
    st.recentKeys = pushLimited(st.recentKeys, k, 40);
    st.plays = (st.plays || []).concat([{ t: np.title, a: np.artist, ts: Date.now(), ahenk: !!(st.pick && titlesMatch(np.title, st.pick.t)) }]).slice(-30);
    st.curId = np.id;
    st.pick = null;
    st.alts = null;
    st.queueError = null;
    if (user.queued_for) {
      user.queued_for = null;
      await env.DB.prepare("UPDATE users SET queued_for = NULL WHERE id = ?").bind(user.id).run();
    }
  }
  if (!user.enabled) { await saveState(env, user, st); return st; }

  // ---- Sıradakini seç ----
  if ((!st.pick || opts.force) && ctx.left() > 14) {
    try {
      const res = await choose(ctx, np, set, st, set.lastfm || env.LASTFM_KEY || null);
      if (res) {
        const sp = await resolveSpotify(env, user, res.ranked, ctx);
        if (sp) {
          st.pick = { t: sp.t, a: sp.a, c: sp.sc || sp.cb || sp.c, bpm: sp.bpm || 0, y: sp.y || 0, reason: sp.reason, score: sp.score, uri: sp.uri, k: sp.k };
          st.alts = res.ranked.filter((x) => x.k !== sp.k).slice(0, 4).map((x) => ({ t: x.t, a: x.a, c: x.c, reason: x.reason, k: x.k }));
          st.target = res.target;
          const pairs = st.pairs || {};
          pairs[res.seedKey] = pushLimited(pairs[res.seedKey], sp.k, 10);
          const ks = Object.keys(pairs);
          if (ks.length > 80) for (const old of ks.slice(0, ks.length - 80)) delete pairs[old];
          st.pairs = pairs;
        } else {
          st.queueError = "Spotify'da uygun karşılık bulunamadı; bir sonraki şarkıda tekrar denenecek.";
        }
      }
    } catch (e) {
      if (e.message !== "BUDGET") st.error = "Seçim yapılamadı: " + e.message;
    }
  }

  // ---- Zamanı gelince Spotify sırasına ekle ----
  const elapsed = np.playing ? Date.now() - np.at : 0;
  const remaining = np.dur - (np.prog + elapsed);
  if (st.pick?.uri && np.playing && remaining <= QUEUE_WINDOW_MS && user.queued_for !== np.id && ctx.left() > 0) {
    const claim = await env.DB.prepare("UPDATE users SET queued_for = ? WHERE id = ? AND (queued_for IS NULL OR queued_for != ?)")
      .bind(np.id, user.id, np.id).run();
    if (claim.meta?.changes === 1) {
      user.queued_for = np.id;
      const r = await addToQueue(env, user, st.pick.uri, ctx).catch(() => 3);
      if (r === 0) { st.queuedAt = Date.now(); st.queueError = null; }
      else {
        st.queueError = r === 1 ? "Spotify sıraya ekleme için Premium istiyor."
          : r === 2 ? "Açık bir Spotify cihazı bulunamadı." : "Spotify sıraya eklemedi; bir sonraki şarkıda tekrar denenecek.";
        if (r !== 1) { user.queued_for = null; await env.DB.prepare("UPDATE users SET queued_for = NULL WHERE id = ?").bind(user.id).run(); }
      }
    }
  }
  await saveState(env, user, st);
  return st;
}

// ---------------- API ----------------

async function auth(env, req) {
  const h = req.headers.get("Authorization") || "";
  const tok = h.startsWith("Bearer ") ? h.slice(7) : null;
  if (!tok) return null;
  return env.DB.prepare("SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?").bind(tok).first();
}

function view(user, st) {
  const set = parse(user.settings);
  return {
    name: user.name,
    enabled: !!user.enabled,
    connected: !!user.refresh_token,
    settings: { lang: set.lang || "tr", mood: set.mood || null, genre: set.genre || null, text: set.text || null, explore: set.explore ?? 1, hasLastfm: !!set.lastfm },
    now: st.now || null,
    pick: st.pick || null,
    alts: st.alts || [],
    queued: !!(st.now && user.queued_for === st.now.id && !st.queueError),
    queueError: st.queueError || null,
    error: st.error || null,
    target: st.target || null,
    lastCheck: st.lastCheck || 0,
    plays: (st.plays || []).slice(-12).reverse(),
  };
}

async function api(req, env, path) {
  const now = Date.now();

  if (path === "/api/meta") {
    return json({
      moods: Object.entries(MOODS).map(([id, m]) => ({ id, label: m.label, emoji: m.emoji })),
      genresTr: GENRES_TR.map((g) => ({ id: g.id, label: g.label, emoji: g.emoji })),
      genresGl: GENRES_GL.map((g) => ({ id: g.id, label: g.label, emoji: g.emoji })),
      places: Object.entries(PLACES).map(([id, p]) => ({ id, label: p.label, emoji: p.emoji })),
      serverLastfm: !!env.LASTFM_KEY,
    });
  }

  if (path === "/api/connect" && req.method === "POST") {
    const b = await req.json().catch(() => ({}));
    if (!/^[0-9a-fA-F]{32}$/.test(b.clientId || "") || !b.code || !b.verifier || !b.redirectUri) return json({ error: "Eksik bilgi" }, 400);
    let tok;
    try { tok = await exchangeCode(b.clientId, b.code, b.verifier, b.redirectUri); }
    catch (e) { return json({ error: e.message }, 400); }
    const meR = await fetch("https://api.spotify.com/v1/me", { headers: { Authorization: "Bearer " + tok.access_token } });
    const meJ = await meR.json().catch(() => ({}));
    if (!meR.ok || !meJ.id) return json({ error: "Spotify hesabı okunamadı (" + meR.status + "). Geliştirici panelinde User Management'a e-postan ekli mi?" }, 400);
    const id = "sp:" + meJ.id;
    const name = meJ.display_name || meJ.id;
    await env.DB.prepare(`INSERT INTO users (id, name, client_id, refresh_token, access_token, access_exp, enabled, created, updated)
      VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
      ON CONFLICT(id) DO UPDATE SET name = excluded.name, client_id = excluded.client_id, refresh_token = excluded.refresh_token,
        access_token = excluded.access_token, access_exp = excluded.access_exp, updated = excluded.updated`)
      .bind(id, name, b.clientId, tok.refresh_token, tok.access_token, now + (tok.expires_in || 3600) * 1000, now, now).run();
    const session = randomString(40);
    await env.DB.prepare("INSERT INTO sessions (token, user_id, created) VALUES (?, ?, ?)").bind(session, id, now).run();
    return json({ session, name });
  }

  const user = await auth(env, req);
  if (!user) return json({ error: "Oturum yok" }, 401);

  if (path === "/api/state") {
    let st = parse(user.state);
    const url = new URL(req.url);
    if (user.refresh_token && (now - (user.last_tick || 0) > 12000 || url.searchParams.get("fresh"))) {
      const ctx = new Ctx(env, 40);
      st = await tick(env, user, ctx).catch(() => st);
      await ctx.flush().catch(() => {});
    }
    return json(view(user, st));
  }

  if (req.method !== "POST") return json({ error: "Yöntem desteklenmiyor" }, 405);
  const b = await req.json().catch(() => ({}));
  let st = parse(user.state);
  const recompute = async () => {
    st.pick = null;
    user.state = JSON.stringify(st);
    const ctx = new Ctx(env, 40);
    st = await tick(env, user, ctx, { force: true }).catch(() => st);
    await ctx.flush().catch(() => {});
  };

  if (path === "/api/settings") {
    const set = parse(user.settings);
    const s = b.settings || {};
    for (const k of ["lang", "mood", "genre", "text", "explore", "tz", "place"]) if (k in s) set[k] = s[k] === "" ? null : s[k];
    if ("lastfm" in s) set.lastfm = s.lastfm && /^[0-9a-f]{32}$/i.test(s.lastfm) ? s.lastfm : null;
    // ruh hali / tür / cümle birbirini dışlar
    if (s.mood) { set.genre = null; set.text = null; }
    if (s.genre) { set.mood = null; set.text = null; }
    if (s.text) { set.mood = null; set.genre = null; }
    user.settings = JSON.stringify(set);
    let enabled = user.enabled;
    if ("enabled" in b) enabled = b.enabled ? 1 : 0;
    user.enabled = enabled;
    await env.DB.prepare("UPDATE users SET settings = ?, enabled = ?, updated = ? WHERE id = ?").bind(user.settings, enabled, now, user.id).run();
    if (enabled && user.queued_for !== st.curId) await recompute();
    return json(view(user, st));
  }

  if (path === "/api/other") {   // başka bir şarkı öner
    if (st.pick) st.recentKeys = pushLimited(st.recentKeys, st.pick.k, 40);
    await recompute();
    return json(view(user, st));
  }

  if (path === "/api/dislike") {  // bu şarkıyı bir daha önerme
    if (st.pick) st.avoid = pushLimited(st.avoid, st.pick.k, 300);
    await recompute();
    return json(view(user, st));
  }

  if (path === "/api/use") {      // alternatiflerden birini seç
    const alt = (st.alts || [])[b.index | 0];
    if (alt) {
      const ctx = new Ctx(env, 6);
      const sp = await resolveSpotify(env, user, [alt], ctx).catch(() => null);
      if (sp) {
        st.alts = [st.pick, ...(st.alts || []).filter((x) => x.k !== alt.k)].filter(Boolean).slice(0, 4)
          .map((x) => ({ t: x.t, a: x.a, c: x.c, reason: x.reason, k: x.k }));
        st.pick = { t: sp.t, a: sp.a, c: sp.sc || alt.c, reason: alt.reason, uri: sp.uri, k: alt.k };
        await saveState(env, user, st);
      }
    }
    return json(view(user, st));
  }

  if (path === "/api/playnow") {
    if (!st.pick?.uri) return json({ ...view(user, st), message: "Henüz seçili şarkı yok" });
    const r = await playUris(env, user, [st.pick.uri]).catch(() => 3);
    const msg = r === 0 ? "▶ Çalıyor" : r === 1 ? "Spotify bunun için Premium istiyor" : r === 2 ? "Açık bir Spotify cihazı yok; önce Spotify'da bir şarkı aç" : "Spotify başlatmadı";
    return json({ ...view(user, st), message: msg });
  }

  if (path === "/api/logout") {
    const h = req.headers.get("Authorization").slice(7);
    await env.DB.prepare("DELETE FROM sessions WHERE token = ?").bind(h).run();
    return json({ ok: true });
  }

  if (path === "/api/forget") {   // hesabı ve tüm verileri sil
    await env.DB.prepare("DELETE FROM sessions WHERE user_id = ?").bind(user.id).run();
    await env.DB.prepare("DELETE FROM users WHERE id = ?").bind(user.id).run();
    return json({ ok: true });
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
    // Tek sayfalık uygulama: bilinmeyen yollar (ör. /callback) index.html'e gider
    const res = await env.ASSETS.fetch(req);
    if (res.status !== 404) return res;
    // "/" doğrudan index.html'i verir (index.html'e istek atılırsa "/"e yönlendirip ?code= kaybolur)
    const home = await env.ASSETS.fetch(new Request(new URL("/", url), { method: "GET", headers: req.headers }));
    return new Response(home.body, { status: 200, headers: home.headers });
  },

  async scheduled(event, env, ctx) {
    const rs = await env.DB.prepare("SELECT * FROM users WHERE refresh_token IS NOT NULL AND enabled = 1").all();
    const users = shuffle(rs.results || []);
    const c = new Ctx(env, 45);
    for (const u of users) {
      if (c.left() < 3) break;
      if (Date.now() - (u.last_tick || 0) < 20000) continue;   // web uygulaması az önce baktı
      try { await tick(env, u, c); } catch {}
    }
    await c.flush().catch(() => {});
  },
};
