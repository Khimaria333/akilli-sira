// Ahenk bulut: Spotify Web API (kullanıcının kendi Client ID'si, PKCE; jetonlar D1'de)

const ACC = "https://accounts.spotify.com/api/token";
const API = "https://api.spotify.com/v1";

export async function exchangeCode(clientId, code, verifier, redirectUri) {
  const body = new URLSearchParams({ grant_type: "authorization_code", code, redirect_uri: redirectUri, client_id: clientId, code_verifier: verifier });
  const r = await fetch(ACC, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error_description || j.error || "Spotify jeton vermedi (" + r.status + ")");
  return j;
}

/** Geçerli erişim jetonu; gerekirse yeniler ve kullanıcı kaydını günceller. */
export async function accessToken(env, user, ctx) {
  if (user.access_token && user.access_exp > Date.now() + 30000) return user.access_token;
  const body = new URLSearchParams({ grant_type: "refresh_token", refresh_token: user.refresh_token, client_id: user.client_id });
  const r = ctx ? await ctx.fetch(ACC, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body })
                : await fetch(ACC, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) {
    if (r.status === 400 || r.status === 401) {
      await env.DB.prepare("UPDATE users SET refresh_token = NULL, enabled = 0 WHERE id = ?").bind(user.id).run();
      throw new Error("REVOKED");
    }
    throw new Error("Spotify jeton yenilenemedi (" + r.status + ")");
  }
  user.access_token = j.access_token;
  user.access_exp = Date.now() + (j.expires_in || 3600) * 1000;
  if (j.refresh_token) user.refresh_token = j.refresh_token;
  await env.DB.prepare("UPDATE users SET access_token = ?, access_exp = ?, refresh_token = ? WHERE id = ?")
    .bind(user.access_token, user.access_exp, user.refresh_token, user.id).run();
  return user.access_token;
}

export async function sp(env, user, method, path, body, ctx) {
  const tok = await accessToken(env, user, ctx);
  const init = { method, headers: { Authorization: "Bearer " + tok } };
  if (body !== undefined) { init.headers["Content-Type"] = "application/json"; init.body = typeof body === "string" ? body : JSON.stringify(body); }
  const r = ctx ? await ctx.fetch(API + path, init) : await fetch(API + path, init);
  const text = await r.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch {}
  return { status: r.status, json };
}

export async function me(env, user) {
  const r = await sp(env, user, "GET", "/me");
  return r.json || {};
}

/** Çalan şarkı: { playing, id, title, artist, dur, prog, cover } ya da null */
export async function nowPlaying(env, user, ctx) {
  const r = await sp(env, user, "GET", "/me/player/currently-playing?additional_types=track", undefined, ctx);
  if (r.status === 204 || !r.json || !r.json.item || r.json.currently_playing_type !== "track") return null;
  const it = r.json.item;
  return {
    playing: !!r.json.is_playing,
    id: it.id,
    uri: it.uri,
    title: it.name,
    artist: (it.artists || []).map((a) => a.name).join(", "),
    dur: it.duration_ms || 0,
    prog: r.json.progress_ms || 0,
    cover: it.album?.images?.[0]?.url || null,
    at: Date.now(),
  };
}

export async function findTrack(env, user, artist, title, ctx) {
  const q = `track:${title} artist:${artist}`;
  const r = await sp(env, user, "GET", `/search?type=track&limit=5&market=from_token&q=${encodeURIComponent(q)}`, undefined, ctx);
  const items = r.json?.tracks?.items || [];
  return items;
}

/** 0 tamam, 1 Premium/izin yok, 2 açık cihaz yok, 3 diğer */
const code = (s) => (s >= 200 && s < 300 ? 0 : s === 403 ? 1 : s === 404 ? 2 : 3);
export const addToQueue = async (env, user, uri, ctx) => code((await sp(env, user, "POST", `/me/player/queue?uri=${encodeURIComponent(uri)}`, "", ctx)).status);
export const playUris = async (env, user, uris, ctx) => code((await sp(env, user, "PUT", "/me/player/play", { uris }, ctx)).status);
