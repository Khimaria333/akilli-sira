// Ahenk Oynatıcı: YouTube'un resmi gömülü oynatıcısı + Ahenk'in seçim motoru.
// Dinleme geçmişi ve ayarlar yalnızca bu cihazda (localStorage) tutulur.
"use strict";

const $ = (id) => document.getElementById(id);
const LS = "ahenk-oynatici-v1";

// ---------------- Durum ----------------
function defaults() {
  let tz = "Europe/Istanbul";
  try { tz = Intl.DateTimeFormat().resolvedOptions().timeZone || tz; } catch {}
  return {
    settings: { lang: "tr", explore: 1, mood: null, genre: null, text: null, tz },
    recentArtists: [], recentKeys: [], avoid: [], pairs: {},
    plays: [], back: [], cur: null, next: null, alts: [], target: null, awake: true,
  };
}
function loadState() {
  try {
    const s = JSON.parse(localStorage.getItem(LS) || "{}");
    const d = defaults();
    return { ...d, ...s, settings: { ...d.settings, ...(s.settings || {}) } };
  } catch { return defaults(); }
}
const S = loadState();
function save() { try { localStorage.setItem(LS, JSON.stringify(S)); } catch {} }
const push = (arr, v, n) => [...(arr || []).filter((x) => x !== v), v].slice(-n);

// ---------------- Yardımcılar ----------------
let toastT = 0;
function toast(msg, ms = 2600) {
  const t = $("toast");
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(toastT);
  toastT = setTimeout(() => t.classList.remove("show"), ms);
}
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const fmt = (s) => { s = Math.max(0, Math.floor(s || 0)); return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0"); };

async function api(path, body) {
  try {
    const r = await fetch(path, body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {});
    const j = await r.json().catch(() => ({ error: "Sunucu yanıt vermedi" }));
    if (!r.ok && !j.error) j.error = "Hata " + r.status;
    return j;
  } catch {
    return { error: "İnternet bağlantısı yok gibi görünüyor." };
  }
}

// ---------------- YouTube oynatıcısı ----------------
let player = null, ready = false, pending = null, tapT = 0, startedFor = null, errRow = 0, wasPlaying = false;

window.onYouTubeIframeAPIReady = () => {
  player = new YT.Player("yt", {
    width: "100%", height: "100%",
    playerVars: { playsinline: 1, rel: 0, origin: location.origin },
    events: { onReady, onStateChange, onError },
  });
};
(function loadYT() {
  const s = document.createElement("script");
  s.src = "https://www.youtube.com/iframe_api";
  s.async = true;
  s.onerror = () => toast("YouTube oynatıcısı yüklenemedi. İnternetini kontrol et.");
  document.head.appendChild(s);
})();

function onReady() {
  ready = true;
  if (pending) { const p = pending; pending = null; p(); }
  else if (S.cur && S.cur.v) { player.cueVideoById(S.cur.v); $("idle").classList.add("hidden"); }
}

function playVideo(v) {
  $("idle").classList.add("hidden");
  const go = () => { player.loadVideoById(v); armTap(); };
  if (!ready) { pending = go; return; }
  go();
}

// iOS ilk çalmada dokunma isteyebilir: birkaç saniye içinde başlamazsa ipucu göster
function armTap() {
  clearTimeout(tapT);
  $("tapHint").classList.add("hidden");
  tapT = setTimeout(() => {
    const st = player && player.getPlayerState ? player.getPlayerState() : -1;
    if (st !== 1 && st !== 3 && document.visibilityState === "visible") $("tapHint").classList.remove("hidden");
  }, 2800);
}

function onStateChange(e) {
  const st = e.data;
  setPlayIcon(st === 1 || st === 3);
  if (st === 1) {
    $("tapHint").classList.add("hidden");
    errRow = 0;
    wake(true);
    if (S.cur && startedFor !== S.cur.k) {
      startedFor = S.cur.k;
      recordPlay(S.cur);
      if (!S.next || S.next.after !== S.cur.k) fetchNext();
    }
  } else if (st === 2) {
    wake(false);
  } else if (st === 0) {
    advance();
  }
}

function onError(e) {
  const c = S.cur;
  if (c && c.v && [100, 101, 150].includes(e.data)) api("/api/yt", { a: c.a, t: c.t, bad: c.v });   // başka bir video bulunsun
  errRow++;
  if (errRow > 4) { toast("Üst üste birkaç video açılamadı. Biraz sonra tekrar dene."); return; }
  toast(e.data === 101 || e.data === 150 ? "Bu video başka uygulamalarda çalınamıyor, sıradakine geçiliyor." : "Video açılamadı, sıradakine geçiliyor.");
  advance();
}

function setPlayIcon(playing) {
  $("playIcon").innerHTML = playing ? '<path d="M7 5h4v14H7zM13 5h4v14h-4z"/>' : '<path d="M8 5v14l11-7z"/>';
}

// Ekranı açık tut (Wake Lock). Ekran kapanınca YouTube kuralı gereği müzik durur.
let lock = null;
async function wake(on) {
  try {
    if (on && S.awake && "wakeLock" in navigator && document.visibilityState === "visible") {
      if (!lock) { lock = await navigator.wakeLock.request("screen"); lock.addEventListener("release", () => { lock = null; }); }
    } else if (lock) { await lock.release(); lock = null; }
  } catch {}
}

document.addEventListener("visibilitychange", () => {
  if (!ready) return;
  const st = player.getPlayerState();
  if (document.visibilityState === "hidden") {
    // Arka planda çalmak YouTube API kurallarına aykırı: uygulamadan çıkınca duraklat
    wasPlaying = st === 1 || st === 3;
    if (wasPlaying) player.pauseVideo();
  } else if (wasPlaying) {
    wasPlaying = false;
    player.playVideo();
    armTap();
  }
});

// ---------------- Akış ----------------
function recordPlay(c) {
  S.recentKeys = push(S.recentKeys, c.k, 60);
  S.recentArtists = push(S.recentArtists, c.a, 8);
  S.plays = [{ t: c.t, a: c.a, c: c.c, k: c.k, v: c.v, ahenk: !!c.ahenk, ts: Date.now() }, ...S.plays.filter((p) => p.k !== c.k)].slice(0, 30);
  save();
  renderPlays();
}

async function ensureVideo(c) {
  if (c.v) return true;
  const j = await api("/api/yt", { a: c.a, t: c.t });
  if (j.v) { c.v = j.v; return true; }
  toast(j.error || `"${c.t}" YouTube'da bulunamadı.`, 3600);
  return false;
}

// Aynı şarkı için süren bir seçim varsa onu bekle (atlama ile şarkı başlangıcı çakışmasın).
// force: ayar değişti / başka öner gibi durumlarda eskisini bırakıp yeniden seç.
let nextReq = 0, nextP = null, nextFor;
function fetchNext(force) {
  const seedK = S.cur ? S.cur.k : null;
  if (!force && nextP && nextFor === seedK) return nextP;
  const my = ++nextReq;
  nextFor = seedK;
  S.next = null; S.alts = [];
  renderNext("loading");
  const p = (async () => {
    const j = await api("/api/next", {
      now: S.cur ? { a: S.cur.a, t: S.cur.t } : null,
      settings: S.settings,
      state: { recentArtists: S.recentArtists, recentKeys: S.recentKeys, avoid: S.avoid, pairs: S.pairs },
    });
    if (my !== nextReq) return nextP;            // yerini daha yeni bir seçim aldı: onun sonucunu ver
    nextP = null;
    if (j.error || !j.pick) { renderNext("error", j.error || "Seçim yapılamadı."); return null; }
    j.pick.after = seedK;
    j.pick.ahenk = true;
    S.next = j.pick;
    S.alts = j.alts || [];
    S.target = j.target || null;
    if (j.seedKey) {
      S.pairs[j.seedKey] = push(S.pairs[j.seedKey], j.pick.k, 10);
      const ks = Object.keys(S.pairs);
      for (const k of ks.slice(0, Math.max(0, ks.length - 80))) delete S.pairs[k];
    }
    save();
    renderNext();
    renderTarget();
    return j.pick;
  })();
  nextP = p;
  return p;
}

let advancing = false;
async function advance() {
  if (advancing) return;
  advancing = true;
  try {
    for (let tries = 0; tries < 4; tries++) {
      let n = S.next;
      if (!n) n = await fetchNext();
      if (!n) { setPlayIcon(false); return; }
      if (S.cur) S.back = [...S.back, S.cur].slice(-20);
      S.cur = { ...n, after: undefined };
      S.next = null; S.alts = [];
      save();
      renderCur();
      renderNext("loading");
      if (await ensureVideo(S.cur)) { playVideo(S.cur.v); save(); return; }
      S.avoid = push(S.avoid, S.cur.k, 300);    // YouTube'da yoksa bir daha önerme
    }
  } finally { advancing = false; }
}

/** Belirli bir şarkıyla başla (arama, geçmiş). */
async function startWith(track) {
  if (S.cur) S.back = [...S.back, S.cur].slice(-20);
  S.cur = { t: track.t, a: track.a, c: track.c, k: track.k, v: track.v || null, reason: "" };
  S.next = null; S.alts = [];
  startedFor = null;
  save();
  renderCur();
  renderNext("loading");
  if (!(await ensureVideo(S.cur))) { renderNext("error", "Bu şarkı YouTube'da bulunamadı, başka bir şarkı dene."); return; }
  playVideo(S.cur.v);
  save();
}

/** Seçili ruh hali / türle başla (tohum şarkı olmadan). */
async function startMood() {
  if (!S.settings.mood && !S.settings.genre && !S.settings.text) { toast("Önce bir ruh hali ya da tür seç."); return; }
  if (S.cur) S.back = [...S.back, S.cur].slice(-20);
  S.cur = null;
  startedFor = null;
  renderCur();
  const pick = await fetchNext(true);
  if (!pick) return;
  S.cur = { ...pick, after: undefined };
  S.next = null; S.alts = [];
  save();
  renderCur();
  renderNext("loading");
  playVideo(S.cur.v);
}

// ---------------- Ekran ----------------
function renderCur() {
  const c = S.cur;
  $("curTitle").textContent = c ? c.t : "—";
  $("curArtist").textContent = c ? c.a : "";
  $("curReason").textContent = c && c.reason ? "Ahenk seçti · " + c.reason : c && c.ahenk ? "Ahenk seçti" : "";
  if (!c) { $("idle").classList.remove("hidden"); $("bar").style.width = "0"; $("tNow").textContent = "0:00"; $("tDur").textContent = "0:00"; }
}

function renderNext(mode, msg) {
  const box = $("nextBox");
  if (!S.cur && mode !== "loading" && mode !== "error") { box.classList.add("hidden"); return; }
  box.classList.remove("hidden");
  const n = S.next;
  $("nextErr").classList.toggle("hidden", mode !== "error");
  $("nextErr").textContent = mode === "error" ? msg : "";
  const st = $("nextStatus");
  if (mode === "loading") { st.className = "pill"; st.innerHTML = '<span class="spin"></span> Seçiliyor'; }
  else if (mode === "error") { st.className = "pill warn"; st.textContent = "Seçilemedi"; }
  else { st.className = "pill good"; st.textContent = "Hazır"; }
  $("nextTitle").textContent = n ? n.t : mode === "loading" ? "Sıradaki seçiliyor…" : "—";
  $("nextArtist").textContent = n ? n.a : "";
  $("nextReason").textContent = n ? n.reason || "" : "";
  const img = $("nextCover");
  if (n && n.c) { img.src = n.c; img.style.visibility = ""; } else { img.removeAttribute("src"); img.style.visibility = "hidden"; }
  $("nOther").disabled = $("nDislike").disabled = $("nPlay").disabled = mode === "loading";
  $("nOther").textContent = mode === "error" ? "↻ Tekrar dene" : "↻ Başka öner";
  $("alts").innerHTML = (S.alts || []).map((a, i) => `
    <button class="alt res" data-i="${i}">
      <img src="${esc(a.c || "")}" alt="" ${a.c ? "" : 'style="visibility:hidden"'}>
      <div class="meta"><div class="t1">${esc(a.t)}</div><div class="t2">${esc(a.a)}${a.reason ? " · " + esc(a.reason) : ""}</div></div>
      <span class="go">Sıraya al</span>
    </button>`).join("");
}

function renderPlays() {
  const list = S.plays.slice(0, 12);
  $("playsTitle").classList.toggle("hidden", !list.length);
  $("plays").classList.toggle("hidden", !list.length);
  $("plays").innerHTML = list.map((p, i) => `
    <button class="play res" data-i="${i}" style="border-radius:0">
      <img src="${esc(p.c || "")}" alt="" ${p.c ? "" : 'style="visibility:hidden"'}>
      <div class="meta"><div class="t1">${esc(p.t)}</div><div class="t2">${esc(p.a)}</div></div>
      ${p.ahenk ? '<span class="tag">AHENK</span>' : ""}
    </button>`).join("");
}

let META = null;
function chipHtml(items, sel) {
  return items.map((m) => `<button class="chip${m.id === sel ? " on" : ""}" data-id="${esc(m.id)}">${esc(m.emoji || "")} ${esc(m.label)}</button>`).join("");
}
function renderChips() {
  if (!META) return;
  $("pMood").innerHTML = chipHtml(META.moods, S.settings.mood);
  $("pGenre").innerHTML = chipHtml(S.settings.lang === "global" ? META.genresGl : META.genresTr, S.settings.genre);
  $("vibe").value = S.settings.text || "";
  for (const b of $("lang").children) b.classList.toggle("on", b.dataset.v === S.settings.lang);
  for (const b of $("explore").children) b.classList.toggle("on", +b.dataset.v === S.settings.explore);
  $("awake").querySelector(".switch").classList.toggle("on", !!S.awake);
  renderTarget();
}
function renderTarget() {
  const s = S.settings;
  let label = "";
  if (s.mood && META) { const m = META.moods.find((x) => x.id === s.mood); if (m) label = m.emoji + " " + m.label; }
  if (s.genre && META) { const g = META.genresTr.concat(META.genresGl).find((x) => x.id === s.genre); if (g) label = g.emoji + " " + g.label; }
  if (s.text) label = "✍️ " + s.text;
  $("target").innerHTML = label ? `Hedef: <b>${esc(S.target || label)}</b>` : "Seçim yoksa Ahenk çalan şarkının tarzını ve temposunu takip eder.";
  $("startMood").classList.toggle("hidden", !label || !!S.cur);
}

// Seçenek değişince: çalan bir şarkı varsa sıradakini yeniden seç
function settingsChanged() {
  save();
  renderChips();
  if (S.cur) fetchNext(true);
}

// ---------------- Olaylar ----------------
$("bPlay").onclick = () => {
  if (!S.cur) { if (S.settings.mood || S.settings.genre || S.settings.text) startMood(); else toast("Bir şarkı ara ya da ruh hali seç."); return; }
  if (!ready) return;
  const st = player.getPlayerState();
  if (st === 1 || st === 3) player.pauseVideo();
  else if (st === 5 || st === -1 || st === 0) { if (S.cur.v) { player.loadVideoById(S.cur.v); armTap(); } }
  else { player.playVideo(); armTap(); }
};
$("bNext").onclick = () => { if (S.cur || S.next) advance(); };
$("bPrev").onclick = () => {
  if (!ready || !S.cur) return;
  if (player.getCurrentTime() > 5 || !S.back.length) { player.seekTo(0, true); return; }
  const prev = S.back.pop();
  S.next = { ...S.cur, after: prev.k };
  S.cur = prev;
  startedFor = prev.k;   // geçmişe tekrar yazma
  save(); renderCur(); renderNext();
  playVideo(prev.v);
};
$("bDislike").onclick = () => {
  if (!S.cur) return;
  S.avoid = push(S.avoid, S.cur.k, 300);
  save();
  toast("Tamam, bu şarkı bir daha çalınmayacak.");
  advance();
};
$("bShare").onclick = () => {
  if (S.cur && S.cur.v) window.open("https://music.youtube.com/watch?v=" + encodeURIComponent(S.cur.v), "_blank", "noopener");
};

$("nOther").onclick = () => { if (S.next) S.recentKeys = push(S.recentKeys, S.next.k, 60); fetchNext(true); };
$("nDislike").onclick = () => { if (S.next) { S.avoid = push(S.avoid, S.next.k, 300); toast("Bu şarkı bir daha önerilmeyecek."); } fetchNext(true); };
$("nPlay").onclick = () => advance();
$("alts").onclick = (e) => {
  const b = e.target.closest("[data-i]");
  if (!b) return;
  const i = +b.dataset.i;
  const alt = S.alts[i];
  if (!alt) return;
  S.alts = [S.next, ...S.alts.filter((_, j) => j !== i)].filter(Boolean).slice(0, 4);
  S.next = { ...alt, after: S.cur ? S.cur.k : null, ahenk: true };
  save();
  renderNext();
  toast("Sıradaki: " + alt.t);
};
$("plays").onclick = (e) => {
  const b = e.target.closest("[data-i]");
  if (b && S.plays[+b.dataset.i]) startWith(S.plays[+b.dataset.i]);
};

// arama
let qT = 0, qN = 0, RES = [];
$("q").addEventListener("input", () => {
  clearTimeout(qT);
  const q = $("q").value.trim();
  if (q.length < 2) { $("results").innerHTML = ""; return; }
  qT = setTimeout(async () => {
    const my = ++qN;
    const j = await api("/api/search?q=" + encodeURIComponent(q));
    if (my !== qN) return;
    RES = j.items || [];
    $("results").innerHTML = RES.length ? RES.map((r, i) => `
      <button class="res" data-i="${i}">
        <img src="${esc(r.c || "")}" alt="" ${r.c ? "" : 'style="visibility:hidden"'}>
        <div class="meta"><div class="t1">${esc(r.t)}</div><div class="t2">${esc(r.a)}</div></div>
        <span class="go">▶ Çal</span>
      </button>`).join("") : `<div class="hint">${esc(j.error || "Sonuç yok.")}</div>`;
  }, 350);
});
$("results").onclick = (e) => {
  const b = e.target.closest("[data-i]");
  if (!b || !RES[+b.dataset.i]) return;
  startWith(RES[+b.dataset.i]);
  $("results").innerHTML = "";
  $("q").value = "";
  $("q").blur();
  window.scrollTo({ top: 0, behavior: "smooth" });
};

// ruh hali / tür / anlat
$("tabs").onclick = (e) => {
  const b = e.target.closest("button");
  if (!b) return;
  for (const x of $("tabs").children) x.classList.toggle("on", x === b);
  $("pMood").classList.toggle("hidden", b.dataset.t !== "mood");
  $("pGenre").classList.toggle("hidden", b.dataset.t !== "genre");
  $("pText").classList.toggle("hidden", b.dataset.t !== "text");
};
$("pMood").onclick = (e) => {
  const b = e.target.closest(".chip");
  if (!b) return;
  const id = b.dataset.id;
  Object.assign(S.settings, { mood: S.settings.mood === id ? null : id, genre: null, text: null });
  S.target = null;
  settingsChanged();
};
$("pGenre").onclick = (e) => {
  const b = e.target.closest(".chip");
  if (!b) return;
  const id = b.dataset.id;
  Object.assign(S.settings, { genre: S.settings.genre === id ? null : id, mood: null, text: null });
  S.target = null;
  settingsChanged();
};
const applyVibe = () => {
  const t = $("vibe").value.trim();
  Object.assign(S.settings, { text: t || null, mood: null, genre: null });
  S.target = null;
  $("vibe").blur();
  settingsChanged();
};
$("vibeGo").onclick = applyVibe;
$("vibe").addEventListener("keydown", (e) => { if (e.key === "Enter") applyVibe(); });
$("startMood").onclick = () => startMood();
$("lang").onclick = (e) => {
  const b = e.target.closest("button");
  if (!b || S.settings.lang === b.dataset.v) return;
  S.settings.lang = b.dataset.v;
  S.settings.genre = null;
  settingsChanged();
};
$("explore").onclick = (e) => {
  const b = e.target.closest("button");
  if (!b) return;
  S.settings.explore = +b.dataset.v;
  settingsChanged();
};
$("awake").onclick = () => {
  S.awake = !S.awake;
  save();
  renderChips();
  if (ready && player.getPlayerState() === 1) wake(S.awake);
};
$("forget").onclick = () => {
  if (!confirm("Dinleme geçmişin, beğenmediklerin ve ayarların bu cihazdan silinsin mi?")) return;
  try { localStorage.removeItem(LS); } catch {}
  location.reload();
};

// ilerleme çubuğu
setInterval(() => {
  if (!ready || !S.cur || !player.getDuration) return;
  const d = player.getDuration() || 0, t = player.getCurrentTime() || 0;
  $("bar").style.width = d ? Math.min(100, (t / d) * 100) + "%" : "0";
  $("tNow").textContent = fmt(t);
  $("tDur").textContent = fmt(d);
}, 500);

// ana ekrana ekle ipucu
(function installHint() {
  const standalone = window.navigator.standalone || (window.matchMedia && matchMedia("(display-mode: standalone)").matches);
  if (standalone) return;
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent);
  $("installText").innerHTML = ios
    ? 'Safari\'de alttaki <b style="color:var(--text)">Paylaş</b> düğmesi → <b style="color:var(--text)">Ana Ekrana Ekle</b>. Oynatıcı uygulama gibi açılır.'
    : 'Chrome\'da sağ üstteki <b style="color:var(--text)">⋮</b> menüsü → <b style="color:var(--text)">Ana ekrana ekle</b> ya da <b style="color:var(--text)">Uygulamayı yükle</b>.';
  $("installHint").classList.remove("hidden");
})();

// açılış
renderCur();
if (S.cur && S.next) renderNext(); else $("nextBox").classList.add("hidden");
renderPlays();
(async () => {
  const m = await api("/api/meta");
  if (m.error || !m.moods) { toast(m.error || "Sunucuya ulaşılamadı."); return; }
  META = m;
  renderChips();
  if (!META.youtube) toast("YouTube anahtarı henüz eklenmemiş; şarkılar çalınamaz.", 5000);
})();

if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {});
