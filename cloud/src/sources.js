// Ahenk bulut: Deezer + Last.fm istemcileri, istek bütçesi ve toplu önbellek (D1)
// Cloudflare ücretsiz planında çağrı başına ~50 dış istek ve ~50 D1 sorgusu sınırı var:
// önbellek tek sorguda toplu okunur / yazılır, dış istekler sayılır.

export class Ctx {
  constructor(env, maxFetch = 44) {
    this.env = env;
    this.used = 0;
    this.max = maxFetch;
    this.mem = new Map();      // önbellek (okunan + yeni)
    this.dirty = new Map();    // yazılacaklar
  }
  left() { return this.max - this.used; }
  async fetch(url, init) {
    if (this.used >= this.max) throw new Error("BUDGET");
    this.used++;
    return fetch(url, init);
  }
  // ---- önbellek ----
  async load(keys) {
    const need = keys.filter((k) => !this.mem.has(k));
    for (let i = 0; i < need.length; i += 90) {
      const part = need.slice(i, i + 90);
      const rs = await this.env.DB.prepare(`SELECT k, v, ts FROM cache WHERE k IN (${part.map(() => "?").join(",")})`)
        .bind(...part).all();
      for (const r of rs.results || []) {
        try { this.mem.set(r.k, JSON.parse(r.v)); } catch {}
      }
    }
  }
  get(k) { return this.mem.get(k); }
  put(k, v) { this.mem.set(k, v); this.dirty.set(k, v); }
  async flush() {
    if (!this.dirty.size) return;
    const now = Date.now();
    const rows = [...this.dirty.entries()];
    this.dirty.clear();
    for (let i = 0; i < rows.length; i += 30) {
      const part = rows.slice(i, i + 30);
      const sql = `INSERT OR REPLACE INTO cache (k, v, ts) VALUES ${part.map(() => "(?, ?, ?)").join(",")}`;
      const args = [];
      for (const [k, v] of part) args.push(k, JSON.stringify(v), now);
      await this.env.DB.prepare(sql).bind(...args).run();
    }
  }
}

// ---------------- Deezer ----------------

const DZ = "https://api.deezer.com";

async function dz(ctx, path) {
  const r = await ctx.fetch(DZ + path, { headers: { "User-Agent": "Ahenk/1.0" } });
  const j = await r.json();
  if (j && j.error) {
    if (j.error.code === 800) return {};
    throw new Error("Deezer: " + (j.error.message || j.error.code));
  }
  return j;
}

/** Deezer şarkı nesnesini sade hale getir. */
export function slim(o, src) {
  if (!o || !o.id) return null;
  return {
    id: o.id,
    t: o.title_short || o.title || "",
    a: o.artist?.name || "",
    aid: o.artist?.id || 0,
    r: o.rank || 0,
    d: o.duration || 0,
    c: o.album?.cover_medium || null,
    cb: o.album?.cover_xl || o.album?.cover_big || null,
    s: src ? [src] : [],
  };
}

export async function dzFind(ctx, artist, title) {
  const r = await dz(ctx, `/search?limit=3&q=${encodeURIComponent(`artist:"${artist}" track:"${title}"`)}`);
  let it = (r.data || [])[0];
  if (!it) {
    const r2 = await dz(ctx, `/search?limit=3&q=${encodeURIComponent(artist + " " + title)}`);
    it = (r2.data || [])[0];
  }
  return slim(it);
}

export async function dzDetails(ctx, id) {
  const o = await dz(ctx, `/track/${id}`);
  const y = parseInt((o.release_date || o.album?.release_date || "").slice(0, 4), 10);
  return { bpm: o.bpm > 0 ? o.bpm : 0, gain: typeof o.gain === "number" ? o.gain : null, y: y > 1900 ? y : 0 };
}

export const dzRelated = async (ctx, aid, n = 12) => ((await dz(ctx, `/artist/${aid}/related?limit=${n}`)).data || []).map((a) => a.id);
export const dzTop = async (ctx, aid, n = 6, src) => ((await dz(ctx, `/artist/${aid}/top?limit=${n}`)).data || []).map((o) => slim(o, src)).filter(Boolean);
export const dzRadio = async (ctx, aid, n = 25) => ((await dz(ctx, `/artist/${aid}/radio?limit=${n}`)).data || []).map((o) => slim(o, "radio")).filter(Boolean);

export async function dzPlaylistPool(ctx, query, n = 40) {
  const r = await dz(ctx, `/search/playlist?limit=10&q=${encodeURIComponent(query)}`);
  const ok = (r.data || []).filter((p) => p.nb_tracks >= 20);
  if (!ok.length) return [];
  const pl = ok[Math.floor(Math.random() * Math.min(3, ok.length))];
  return ((await dz(ctx, `/playlist/${pl.id}/tracks?limit=${n}`)).data || []).map((o) => slim(o, "mood")).filter(Boolean);
}

// ---------------- Last.fm ----------------

const NOISE = new Set(["seen live", "favorites", "favourites", "favorite", "favourite", "my favorite", "love", "loved", "awesome",
  "beautiful", "amazing", "good", "best", "all", "music", "under 2000 listeners", "albums i own", "spotify", "check out",
  "favorite artists", "male vocalists", "female vocalists", "singer-songwriter", "vocal", "fav", "favs", "great", "cool", "deezer"]);

export async function lfTags(ctx, key, artist) {
  const u = `https://ws.audioscrobbler.com/2.0/?method=artist.gettoptags&autocorrect=1&format=json&api_key=${key}&artist=${encodeURIComponent(artist)}`;
  const r = await ctx.fetch(u);
  const j = await r.json();
  const out = {};
  for (const t of j?.toptags?.tag || []) {
    const n = String(t.name || "").toLocaleLowerCase("tr").trim();
    const c = parseInt(t.count, 10) || 0;
    if (!n || c < 5 || NOISE.has(n)) continue;
    out[n] = c;
    if (Object.keys(out).length >= 15) break;
  }
  return out;
}
