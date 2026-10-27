// Ahenk bulut: sıradaki şarkıyı seçen motor (Android'deki Scorer'ın sadeleştirilmiş eşi)
import { key, norm, mainArtist, cleanTitle, titlesMatch, detectLang, langFromTags, similarity, overlap, sharedTag, sample, TR, FOREIGN, UNKNOWN } from "./util.js";
import { targetFor } from "./data.js";
import { dzFind, dzDetails, dzRelated, dzTop, dzRadio, dzPlaylistPool, lfTags } from "./sources.js";

export function hourIn(tz) {
  try {
    return parseInt(new Intl.DateTimeFormat("en-US", { hour: "numeric", hourCycle: "h23", timeZone: tz || "Europe/Istanbul" }).format(new Date()), 10) % 24;
  } catch { return new Date().getUTCHours(); }
}

function bpmDistance(c, t) {
  let best = 9;
  for (const r of [1, 2, 0.5]) best = Math.min(best, Math.abs(c * r - t) / t + (r === 1 ? 0 : 0.02));
  return best;
}
const closeness = (d) => (d <= 0.03 ? 1 : Math.max(0, 1 - (d - 0.03) / 0.12));
function rangeCloseness(bpm, lo, hi) {
  let best = 0;
  for (const r of [1, 2, 0.5]) {
    const b = bpm * r;
    const d = b < lo ? (lo - b) / lo : b > hi ? (b - hi) / hi : 0;
    let p = d === 0 ? 1 : Math.max(0, 1 - d / 0.15);
    if (r !== 1) p *= 0.9;
    best = Math.max(best, p);
  }
  return best;
}
const energyOf = (gain) => (gain == null ? -1 : Math.max(0, Math.min(1, (gain + 14) / 10)));

/**
 * Sıradakini seç. now: Spotify'daki çalan şarkı. Dönen: { best, ranked[] } ya da null.
 * state: { recentArtists[], recentKeys[], avoid[], pairs{} }
 */
export async function choose(ctx, now, settings, state, lastfmKey) {
  const turkish = settings.lang !== "global";
  const target = targetFor(settings, hourIn(settings.tz));
  const explore = settings.explore ?? 1;
  const mainA = mainArtist(now.artist);
  const sk = key(now.artist, now.title);

  // ---- Çalan şarkı (tohum) ----
  await ctx.load(["seed:" + sk, "lf:" + norm(mainA)]);
  let seed = ctx.get("seed:" + sk);
  if (!seed) {
    seed = (await dzFind(ctx, mainA, cleanTitle(now.title)).catch(() => null)) || { id: 0, aid: 0 };
    if (seed.id) Object.assign(seed, await dzDetails(ctx, seed.id).catch(() => ({})));
    ctx.put("seed:" + sk, seed);
  }
  let seedTags = null;
  if (lastfmKey) {
    seedTags = ctx.get("lf:" + norm(mainA));
    if (!seedTags) { seedTags = await lfTags(ctx, lastfmKey, mainA).catch(() => ({})); ctx.put("lf:" + norm(mainA), seedTags); }
  }

  // ---- Aday havuzu ----
  const pool = [];
  const relSet = new Set(), radSet = new Set();
  if (seed.aid) {
    await ctx.load(["rel:" + seed.aid, "radio:" + seed.aid]);
    let rel = ctx.get("rel:" + seed.aid);
    if (!rel) { rel = await dzRelated(ctx, seed.aid).catch(() => []); ctx.put("rel:" + seed.aid, rel); }
    rel.forEach((id) => relSet.add(id));
    let radio = ctx.get("radio:" + seed.aid);
    if (!radio) { radio = await dzRadio(ctx, seed.aid).catch(() => []); ctx.put("radio:" + seed.aid, radio); }
    for (const t of radio) { if (t.aid) radSet.add(t.aid); pool.push({ ...t, s: ["radio"] }); }
    const picks = sample(rel, 5);
    await ctx.load(picks.map((id) => "top:" + id));
    for (const rid of picks) {
      let top = ctx.get("top:" + rid);
      if (!top) {
        if (ctx.left() < 22) break;
        top = await dzTop(ctx, rid, 6, "related").catch(() => []);
        ctx.put("top:" + rid, top);
      }
      for (const t of sample(top, 3)) pool.push({ ...t, s: ["related"] });
    }
  }
  if (target && target.queries.length && ctx.left() > 18) {
    const q = target.queries[Math.floor(Math.random() * Math.min(2, target.queries.length))];
    const ck = "pl:" + norm(q);
    await ctx.load([ck]);
    let pl = ctx.get(ck);
    if (!pl) { pl = await dzPlaylistPool(ctx, q).catch(() => []); ctx.put(ck, pl); }
    for (const t of sample(pl, seed.aid ? 15 : 30)) pool.push({ ...t, s: ["mood"] });
  }
  if (!pool.length) return null;

  // ---- Eleme ----
  const avoid = new Set(state.avoid || []);
  const recentKeys = new Set(state.recentKeys || []);
  const recentArtists = state.recentArtists || [];
  const seedArtist = norm(mainA);
  const uniq = new Map();
  for (const t of pool) {
    const k = key(t.a, t.t);
    if (!t.t || titlesMatch(t.t, now.title) || avoid.has(k) || recentKeys.has(k)) continue;
    if (t.d && t.d < 60) continue;
    if (turkish && detectLang(t.t, t.a) === FOREIGN) continue;
    const ex = uniq.get(k);
    if (ex) { for (const s of t.s) if (!ex.s.includes(s)) ex.s.push(s); continue; }
    uniq.set(k, { ...t, k });
  }
  let list = [...uniq.values()];
  // Ön seçim: kaynak + popülerlik + rastgele pay (hep aynı hitler öne çıkmasın)
  const pre = (t) => (t.s.includes("related") ? 10 : 0) + (t.s.includes("radio") ? 8 : 0) + (t.s.includes("mood") ? 6 : 0)
    + Math.min(1, t.r / 800000) * 6 + Math.random() * 14;
  list.forEach((t) => (t._pre = pre(t)));
  list.sort((a, b) => b._pre - a._pre);
  list = list.slice(0, 14);

  // ---- Ayrıntılar (BPM, enerji, yıl) ve tarz etiketleri ----
  await ctx.load(list.map((t) => "dt:" + t.id).concat(lastfmKey ? list.map((t) => "lf:" + norm(mainArtist(t.a))) : []));
  for (const t of list) {
    let d = ctx.get("dt:" + t.id);
    if (!d && ctx.left() > 4) { d = await dzDetails(ctx, t.id).catch(() => null); if (d) ctx.put("dt:" + t.id, d); }
    if (d) Object.assign(t, d);
  }
  if (lastfmKey) {
    for (const t of list) {
      const ak = "lf:" + norm(mainArtist(t.a));
      let tg = ctx.get(ak);
      if (!tg && ctx.left() > 4) { tg = await lfTags(ctx, lastfmKey, mainArtist(t.a)).catch(() => null); if (tg) ctx.put(ak, tg); }
      t.tags = tg || null;
    }
  }

  // ---- Puanlama ----
  const mode = 0;
  const seedBpm = seed.bpm > 0 ? seed.bpm : 0;
  const tGain = target && !Number.isNaN(target.gain) ? target.gain : null;
  const sGain = seed.gain != null ? seed.gain : null;
  const gainTarget = sGain != null && tGain != null ? (sGain + tGain) / 2 : sGain ?? tGain;
  const before = (state.pairs || {})[sk] || [];
  const ranked = [];
  for (const c of list) {
    let sc = 0;
    const why = [];
    const a = norm(mainArtist(c.a));
    // dil (Türkçe modda etiketlere de bak)
    if (turkish) {
      const lt = langFromTags(c.tags);
      const ld = detectLang(c.t, c.a);
      if (lt === FOREIGN && ld !== TR) continue;
      if (lt === UNKNOWN && ld === UNKNOWN && !c.s.includes("mood")) sc -= 6;
    }
    // tarz
    const rel = c.aid && relSet.has(c.aid), rad = !rel && c.aid && radSet.has(c.aid);
    if (rel) { sc += 20; why.push("benzer sanatçı"); } else if (rad) { sc += 14; why.push("aynı çevreden"); }
    const ts = similarity(seedTags, c.tags);
    if (ts >= 0) {
      sc += 30 * Math.min(1, ts * 1.5);
      if (ts < 0.08) sc -= rel || rad ? 6 : 18;
      else { const sh = sharedTag(seedTags, c.tags); if (sh && ts >= 0.25) why.push("ortak tarz: " + sh); }
    } else sc += 4;
    // dönem
    if (seed.y && c.y) {
      const d = Math.abs(seed.y - c.y);
      if (d <= 4) { sc += 10; why.push("aynı dönem"); } else if (d <= 9) sc += 6; else if (d >= 20) sc -= 10;
    } else sc += 3;
    // tempo
    let pSeed = NaN, pRange = NaN;
    if (seedBpm && c.bpm) pSeed = closeness(bpmDistance(c.bpm, seedBpm * (1 + 0.04 * mode)));
    if (target && c.bpm) pRange = rangeCloseness(c.bpm, target.bpmLo, target.bpmHi);
    let p = !isNaN(pSeed) && !isNaN(pRange) ? 0.55 * pSeed + 0.45 * pRange : !isNaN(pSeed) ? pSeed : !isNaN(pRange) ? pRange : 0.35;
    sc += 25 * p;
    if (c.bpm && p >= 0.6) why.push(seedBpm ? `${Math.round(seedBpm)} → ${Math.round(c.bpm)} BPM` : `${Math.round(c.bpm)} BPM`);
    // enerji
    if (gainTarget != null && c.gain != null) sc += 15 * Math.max(0, 1 - Math.abs(c.gain - gainTarget) / 6);
    else sc += 6;
    // ruh hali etiketleri
    if (target && target.tags.length) {
      const ov = overlap(c.tags, target.tags);
      if (ov > 0) { sc += 25 * ov; if (ov >= 0.3) why.push("ruh haline uygun"); }
      else if (ov === 0) sc -= target.style.length ? 5 : 14;
    }
    if (c.s.includes("mood") && target) { sc += 8; if (!why.includes("ruh haline uygun")) why.push("ruh haline uygun"); }
    // istenen tür
    if (target && target.style.length) {
      const ov = overlap(c.tags, target.style);
      if (ov >= 0.15) { sc += 18 + 22 * Math.min(1, ov); why.push(target.styleLabel ? target.styleLabel.replace(/^\S+\s/, "").toLocaleLowerCase("tr") : "istenen tür"); }
      else if (ov >= 0) sc -= ov > 0 ? 22 : 40;
      else sc -= 4;
    }
    // popülerlik / keşif
    const pop = Math.min(1, c.r / 800000);
    sc += explore === 0 ? 12 * pop : explore === 2 ? 8 * (1 - pop) : 5 * pop;
    // cezalar
    if (a === seedArtist) sc -= 25;
    else if (recentArtists.includes(a)) sc -= 12;
    if (c.d && (c.d < 90 || c.d > 480)) sc -= 15;
    // çeşitlilik: bu şarkıdan sonra daha önce seçilenler
    const bi = before.indexOf(c.k);
    if (bi >= 0) sc -= Math.max(8, 22 - bi * 3);
    sc += Math.random() * 6 - 3;
    ranked.push({ ...c, score: Math.round(sc * 10) / 10, reason: why.slice(0, 2).join(" · ") });
  }
  if (!ranked.length) return null;
  ranked.sort((x, y) => y.score - x.score);
  // En iyiye yakın (10 puan içinde) en fazla 5 aday arasından ağırlıklı rastgele
  const top = ranked[0].score;
  const win = ranked.filter((t, i) => i < 5 && t.score >= top - 10);
  const w = win.map((t) => Math.exp((t.score - top) / 4));
  let r = Math.random() * w.reduce((s, x) => s + x, 0);
  let chosen = 0;
  for (let i = 0; i < win.length; i++) { r -= w[i]; if (r <= 0) { chosen = i; break; } }
  if (chosen > 0) ranked.unshift(ranked.splice(chosen, 1)[0]);
  return { best: ranked[0], ranked: ranked.slice(0, 6), seedKey: sk, target: target ? target.label : null };
}
