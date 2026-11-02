// Ahenk Oynatıcı API sınaması: gerçek SQLite (D1 yerine) + sahte Deezer / YouTube yanıtları.
// Çalıştırma: node --experimental-sqlite oynatici/test/api.test.mjs
import { DatabaseSync } from "node:sqlite";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { _api } from "../src/worker.js";
import { _scoreItem } from "../src/youtube.js";

// ---- D1 benzeri sarmalayıcı ----
const db = new DatabaseSync(":memory:");
db.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
const D1 = {
  prepare(sql) {
    let args = [];
    const st = {
      bind(...a) { args = a; return st; },
      async all() { return { results: db.prepare(sql).all(...args) }; },
      async run() { const r = db.prepare(sql).run(...args); return { meta: { changes: r.changes } }; },
      async first() { return db.prepare(sql).get(...args) || null; },
    };
    return st;
  },
};

// ---- Sahte dış servisler ----
const calls = { yt: 0, dz: 0 };
let ytMode = "ok";
const artists = { 1: "Tarkan", 2: "Sezen Aksu", 3: "Mabel Matiz", 4: "Kenan Doğulu", 5: "Sertab Erener" };
const tracks = [];
let tid = 100;
for (const [aid, name] of Object.entries(artists)) {
  for (const t of ["Gül", "Deniz", "Yıldız", "Rüzgar"]) tracks.push({ id: tid++, title: `${t} ${name.split(" ")[0]}`, title_short: `${t} ${name.split(" ")[0]}`, artist: { id: +aid, name }, rank: 500000, duration: 210, album: { cover_medium: "c.jpg" } });
}
const reply = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { "Content-Type": "application/json" } });
globalThis.fetch = async (u) => {
  u = String(u);
  if (u.startsWith("https://api.deezer.com")) {
    calls.dz++;
    const p = new URL(u);
    if (p.pathname === "/search") return reply({ data: tracks.slice(0, 3) });
    if (/^\/track\/\d+$/.test(p.pathname)) return reply({ bpm: 110, gain: -9, release_date: "2010-01-01" });
    if (/\/related$/.test(p.pathname)) return reply({ data: [2, 3, 4, 5].map((id) => ({ id })) });
    if (/\/top$/.test(p.pathname)) { const aid = +p.pathname.split("/")[2]; return reply({ data: tracks.filter((t) => t.artist.id === aid) }); }
    if (/\/radio$/.test(p.pathname)) return reply({ data: tracks.filter((t) => t.artist.id !== 1).slice(0, 8) });
    if (p.pathname === "/search/playlist") return reply({ data: [{ id: 9, nb_tracks: 30 }] });
    if (/^\/playlist\/9\/tracks$/.test(p.pathname)) return reply({ data: tracks });
    return reply({ error: { code: 800 } });
  }
  if (u.startsWith("https://www.googleapis.com/youtube/v3/search")) {
    calls.yt++;
    if (ytMode === "quota") return reply({ error: { errors: [{ reason: "quotaExceeded" }] } }, 403);
    const q = new URL(u).searchParams.get("q");
    assert.ok(new URL(u).searchParams.get("videoEmbeddable") === "true");
    return reply({ items: [
      { id: { videoId: "COVER000001" }, snippet: { title: q + " cover (karaoke)", channelTitle: "Biri" } },
      { id: { videoId: "TOPIC000001" }, snippet: { title: q.split(" ").slice(-2).join(" "), channelTitle: q.split(" ")[0] + " - Topic" } },
    ] });
  }
  throw new Error("beklenmeyen istek: " + u);
};

const env = { DB: D1, YOUTUBE_API_KEY: "x" };
const call = async (path, body, method = body ? "POST" : "GET") => {
  const req = new Request("https://ahenk-oynatici.test" + path, { method, body: body ? JSON.stringify(body) : undefined });
  const r = await _api(req, env, new URL(req.url).pathname);
  return { status: r.status, body: await r.json() };
};

// 1) meta
let r = await call("/api/meta");
assert.equal(r.status, 200);
assert.ok(r.body.moods.length > 3 && r.body.youtube === true);

// 2) arama
r = await call("/api/search?q=tarkan");
assert.equal(r.body.items.length, 3);
assert.ok(r.body.items[0].t && r.body.items[0].a && r.body.items[0].k);

// 3) başlangıç bilgisi yoksa hata
r = await call("/api/next", { settings: {}, state: {} });
assert.equal(r.status, 400);

// 4) bir şarkıdan sonra sıradaki: YouTube'dan "Topic" videosu, cover değil
r = await call("/api/next", { now: { a: "Tarkan", t: "Gül Tarkan" }, settings: { lang: "tr" }, state: { recentKeys: [] } });
assert.equal(r.status, 200, JSON.stringify(r.body));
assert.ok(r.body.pick.v === "TOPIC000001", "Topic videosu seçilmeli: " + r.body.pick.v);
assert.notEqual(r.body.pick.a, "Tarkan", "aynı sanatçı üst üste gelmemeli");
assert.ok(r.body.alts.length >= 1 && r.body.seedKey);
const first = r.body.pick;

// 5) önbellek: aynı şarkı ikinci kez YouTube'da aranmaz
const before = calls.yt;
r = await call("/api/yt", { a: first.a, t: first.t });
assert.equal(r.body.v, "TOPIC000001");
assert.equal(calls.yt, before, "önbellekten gelmeliydi");

// 6) gömülemeyen video işaretlenince başka video aranır, aynısı tekrar verilmez
r = await call("/api/yt", { a: first.a, t: first.t, bad: "TOPIC000001" });
assert.equal(calls.yt, before + 1);
assert.notEqual(r.body.v, "TOPIC000001");

// 7) sadece ruh haliyle başlama (tohum şarkı yok)
const mood = (await call("/api/meta")).body.moods[0].id;
r = await call("/api/next", { settings: { mood }, state: {} });
assert.equal(r.status, 200, JSON.stringify(r.body));
assert.ok(r.body.pick.v);

// 8) beğenilmeyen ve yakın zamanda çalınan şarkı önerilmez
const allKeys = (await import("../../cloud/src/util.js")).key;
const banned = tracks.filter((t) => t.artist.id !== 1).map((t) => allKeys(t.artist.name, t.title)).slice(0, 12);
for (let i = 0; i < 5; i++) {
  r = await call("/api/next", { now: { a: "Tarkan", t: "Gül Tarkan" }, settings: {}, state: { avoid: banned } });
  if (r.status === 200) assert.ok(!banned.includes(r.body.pick.k), "yasaklı şarkı önerildi: " + r.body.pick.k);
}

// 9) kota dolunca anlaşılır mesaj
ytMode = "quota";
r = await call("/api/yt", { a: "Yeni", t: "Hiç Aranmamış" });
assert.equal(r.body.v, null);
assert.match(r.body.error, /sınırı doldu/);

// 10) eşleşme puanı
assert.ok(_scoreItem({ snippet: { title: "Gül Tarkan", channelTitle: "Tarkan - Topic" } }, "Tarkan", "Gül Tarkan") >
  _scoreItem({ snippet: { title: "Gül Tarkan (Cover)", channelTitle: "Biri" } }, "Tarkan", "Gül Tarkan"));
assert.equal(_scoreItem({ snippet: { title: "Başka şarkı", channelTitle: "Tarkan" } }, "Tarkan", "Gül"), -1);

// 11) kötü giriş: büyük / bozuk durum sunucuyu düşürmez
r = await call("/api/next", { now: { a: "Tarkan", t: "Gül Tarkan" }, settings: { explore: "x", lang: 5 }, state: { avoid: "x", pairs: [1, 2], recentKeys: [1, {}, "a"] } });
assert.ok([200, 503].includes(r.status));

console.log("Tüm sınamalar geçti. Deezer:", calls.dz, "YouTube:", calls.yt);
