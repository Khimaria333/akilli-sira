// Ahenk web uygulaması
(() => {
  const $ = (id) => document.getElementById(id);
  const LS = {
    get: (k) => { try { return localStorage.getItem(k); } catch { return null; } },
    set: (k, v) => { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch {} },
  };
  const SS = {
    get: (k) => { try { return sessionStorage.getItem(k); } catch { return null; } },
    set: (k, v) => { try { v == null ? sessionStorage.removeItem(k) : sessionStorage.setItem(k, v); } catch {} },
  };
  const REDIRECT = location.origin + "/callback";
  const SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing";
  const BLANK = "data:image/svg+xml;utf8," + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1" fill="#1F1F2A"/></svg>');

  let session = LS.get("ahenk_session");
  let meta = null;
  let view = null;
  let tab = "mood";
  let busy = false;

  function toast(msg) {
    const t = $("toast");
    t.textContent = msg;
    t.classList.add("show");
    clearTimeout(toast._t);
    toast._t = setTimeout(() => t.classList.remove("show"), 3200);
  }

  async function api(path, body) {
    const init = { headers: { Authorization: "Bearer " + session } };
    if (body !== undefined) { init.method = "POST"; init.headers["Content-Type"] = "application/json"; init.body = JSON.stringify(body); }
    const r = await fetch(path, init);
    const j = await r.json().catch(() => ({}));
    if (r.status === 401) { logoutLocal(); throw new Error("Oturum sona erdi"); }
    if (!r.ok) throw new Error(j.error || "Hata " + r.status);
    return j;
  }

  // ---------------- Bağlanma (PKCE) ----------------
  const b64url = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  function rand(n) {
    const c = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    const a = new Uint8Array(n); crypto.getRandomValues(a);
    return Array.from(a, (x) => c[x % c.length]).join("");
  }

  async function connect() {
    const cid = $("clientId").value.trim();
    if (!/^[0-9a-fA-F]{32}$/.test(cid)) { toast("Client ID 32 karakter olmalı (harf ve rakam)"); return; }
    LS.set("ahenk_client", cid);
    const verifier = rand(64), state = rand(16);
    const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
    SS.set("ahenk_pkce", JSON.stringify({ verifier, state, cid }));
    LS.set("ahenk_pkce", JSON.stringify({ verifier, state, cid }));   // iOS ana ekran uygulaması sekme değiştirebilir
    const p = new URLSearchParams({ response_type: "code", client_id: cid, redirect_uri: REDIRECT, code_challenge_method: "S256",
      code_challenge: challenge, scope: SCOPES, state });
    location.href = "https://accounts.spotify.com/authorize?" + p;
  }

  async function handleCallback() {
    const q = new URLSearchParams(location.search);
    if (location.pathname !== "/callback") return false;
    history.replaceState(null, "", "/");
    const saved = JSON.parse(SS.get("ahenk_pkce") || LS.get("ahenk_pkce") || "null");
    SS.set("ahenk_pkce", null); LS.set("ahenk_pkce", null);
    if (q.get("error")) { toast("Spotify bağlantısı iptal edildi"); return true; }
    if (!saved || q.get("state") !== saved.state || !q.get("code")) { toast("Geçersiz dönüş, tekrar dene"); return true; }
    try {
      const r = await fetch("/api/connect", { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ clientId: saved.cid, code: q.get("code"), verifier: saved.verifier, redirectUri: REDIRECT }) });
      const j = await r.json();
      if (!r.ok) throw new Error(j.error || "Bağlanamadı");
      session = j.session;
      LS.set("ahenk_session", session);
      toast("✓ Hoş geldin " + j.name);
      // saat dilimini bildir
      api("/api/settings", { settings: { tz: Intl.DateTimeFormat().resolvedOptions().timeZone } }).catch(() => {});
    } catch (e) {
      toast("Olmadı: " + e.message);
    }
    return true;
  }

  function logoutLocal() {
    session = null;
    LS.set("ahenk_session", null);
    show();
  }

  // ---------------- Görünüm ----------------
  function show() {
    $("welcome").classList.toggle("hidden", !!session);
    $("app").classList.toggle("hidden", !session);
    if (!session) {
      $("redirect").value = REDIRECT;
      $("clientId").value = LS.get("ahenk_client") || "";
    }
  }

  const fmt = (ms) => { const s = Math.max(0, Math.round(ms / 1000)); return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0"); };

  function render() {
    const v = view;
    if (!v) return;
    const en = $("enabled");
    en.classList.toggle("on", v.enabled);
    $("enabledText").textContent = v.enabled ? "Açık" : "Kapalı";

    // şimdi
    const n = v.now;
    $("nowCover").src = n?.cover || BLANK;
    $("nowTitle").textContent = n ? n.title : "Spotify'da bir şarkı aç";
    $("nowArtist").textContent = n ? n.artist : (v.error || "Ahenk birkaç saniye içinde görür.");
    $("check").textContent = v.lastCheck ? "az önce bakıldı" : "";
    tickBar();

    // sıradaki
    const p = v.pick;
    $("nextBox").style.opacity = v.enabled ? 1 : 0.55;
    $("nextCover").src = p?.c || BLANK;
    $("nextTitle").textContent = p ? p.t : !v.enabled ? "Ahenk kapalı" : n ? "Seçiliyor…" : "—";
    $("nextArtist").textContent = p ? p.a : "";
    $("nextReason").textContent = p?.reason || "";
    const st = $("status");
    st.className = "pill";
    if (!v.enabled) st.textContent = "Kapalı";
    else if (v.queueError) { st.textContent = "⚠ " + v.queueError; st.classList.add("warn"); }
    else if (v.queued) { st.textContent = "✓ Spotify sırasında"; st.classList.add("good"); }
    else if (p && n) {
      const rem = n.dur - n.prog - (n.playing ? Date.now() - (n.seenAt || Date.now()) : 0);
      st.textContent = rem > 75000 ? "son dakikada sıraya eklenecek" : "sıraya ekleniyor…";
    } else st.textContent = "";
    for (const id of ["other", "dislike", "playnow"]) $(id).disabled = !p || busy;

    const alts = $("alts");
    alts.innerHTML = "";
    (v.alts || []).slice(0, 3).forEach((x, i) => {
      const d = document.createElement("div");
      d.className = "alt";
      d.innerHTML = `<img alt=""><div class="meta"><div class="t1"></div><div class="t2"></div></div><span class="hint" style="margin:0">seç</span>`;
      d.querySelector("img").src = x.c || BLANK;
      d.querySelector(".t1").textContent = x.t;
      d.querySelector(".t2").textContent = x.a + (x.reason ? " · " + x.reason : "");
      d.onclick = () => act("/api/use", { index: i }, "Sıradaki değişti");
      alts.appendChild(d);
    });

    // mod
    renderModes();
    const tg = $("target");
    const s = v.settings;
    tg.innerHTML = v.target ? "Şu an: <b></b>" : "Şu an: <b>serbest akış</b>, çalan şarkıya göre";
    if (v.target) tg.querySelector("b").textContent = v.target;
    if (s.text && document.activeElement !== $("vibe")) $("vibe").value = s.text;
    segSet("lang", s.lang);
    segSet("explore", String(s.explore));
    $("lfmBox").classList.toggle("hidden", meta?.serverLastfm && !s.hasLastfm);
    $("lfm").placeholder = s.hasLastfm ? "✓ Kayıtlı (değiştirmek için yapıştır)" : "Tarzı daha isabetli seçmek için";

    // geçmiş
    const pl = $("plays");
    pl.innerHTML = "";
    $("playsTitle").classList.toggle("hidden", !(v.plays || []).length);
    pl.classList.toggle("hidden", !(v.plays || []).length);
    for (const x of v.plays || []) {
      const d = document.createElement("div");
      d.className = "play";
      d.innerHTML = `<div class="meta"><div class="t1"></div><div class="t2"></div></div>`;
      d.querySelector(".t1").textContent = x.t;
      d.querySelector(".t2").textContent = x.a + " · " + new Date(x.ts).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" });
      if (x.ahenk) { const s2 = document.createElement("span"); s2.className = "tag"; s2.textContent = "✨ AHENK"; d.appendChild(s2); }
      pl.appendChild(d);
    }
  }

  function tickBar() {
    const n = view?.now;
    if (!n || !n.dur) { $("nowBar").style.width = "0"; return; }
    const pos = n.prog + (n.playing ? Date.now() - (n.seenAt || Date.now()) : 0);
    $("nowBar").style.width = Math.min(100, (pos / n.dur) * 100) + "%";
  }

  function segSet(id, val) {
    for (const b of $(id).querySelectorAll("button")) b.classList.toggle("on", b.dataset.v === val);
  }

  function renderModes() {
    if (!meta || !view) return;
    const s = view.settings;
    for (const b of $("tabs").querySelectorAll("button")) b.classList.toggle("on", b.dataset.t === tab);
    $("pMood").classList.toggle("hidden", tab !== "mood");
    $("pGenre").classList.toggle("hidden", tab !== "genre");
    $("pText").classList.toggle("hidden", tab !== "text");

    const free = !s.mood && !s.genre && !s.text;
    const pm = $("pMood");
    pm.innerHTML = "";
    pm.appendChild(chip("✨ Serbest", free, () => setMode({ mood: "", genre: "", text: "" })));
    for (const m of meta.moods) pm.appendChild(chip(m.emoji + " " + m.label, s.mood === m.id, () => setMode({ mood: m.id })));
    const pg = $("pGenre");
    pg.innerHTML = "";
    for (const g of s.lang === "global" ? meta.genresGl : meta.genresTr) pg.appendChild(chip(g.emoji + " " + g.label, s.genre === g.id, () => setMode({ genre: g.id })));
  }

  function chip(text, on, fn) {
    const b = document.createElement("button");
    b.className = "chip" + (on ? " on" : "");
    b.textContent = text;
    b.onclick = fn;
    return b;
  }

  // ---------------- Eylemler ----------------
  async function act(path, body, okMsg) {
    if (busy) return;
    busy = true;
    render();
    try {
      const j = await api(path, body);
      setView(j);
      if (j.message) toast(j.message); else if (okMsg) toast(okMsg);
    } catch (e) {
      toast(e.message);
    } finally {
      busy = false;
      render();
    }
  }

  const setMode = (m) => act("/api/settings", { settings: m }, "Sıradaki yeniden seçildi");

  let tabChosen = false;
  function setView(j) {
    if (j.now) j.now.seenAt = Date.now();
    if (!tabChosen && j.settings) {   // ilk açılışta seçili moda ait sekme
      tab = j.settings.genre ? "genre" : j.settings.text ? "text" : "mood";
      tabChosen = true;
    }
    view = j;
    render();
  }

  async function refresh(fresh) {
    if (!session || document.hidden) return;
    try { setView(await api("/api/state" + (fresh ? "?fresh=1" : ""))); } catch {}
  }

  // ---------------- Başlat ----------------
  async function init() {
    $("connect").onclick = connect;
    $("copyRedirect").onclick = async () => {
      try { await navigator.clipboard.writeText(REDIRECT); toast("Kopyalandı"); }
      catch { $("redirect").select(); toast("Seçili, kopyala"); }
    };
    $("enabled").onclick = () => act("/api/settings", { enabled: !view?.enabled }, view?.enabled ? "Ahenk kapandı" : "Ahenk açıldı");
    $("other").onclick = () => act("/api/other", {}, "Başka bir şarkı seçildi");
    $("dislike").onclick = () => act("/api/dislike", {}, "👎 Bir daha önerilmeyecek");
    $("playnow").onclick = () => act("/api/playnow", {});
    for (const b of $("tabs").querySelectorAll("button")) b.onclick = () => { tab = b.dataset.t; tabChosen = true; renderModes(); };
    for (const b of $("lang").querySelectorAll("button")) b.onclick = () => act("/api/settings", { settings: { lang: b.dataset.v } }, b.dataset.v === "tr" ? "Türkçe şarkılar" : "Global");
    for (const b of $("explore").querySelectorAll("button")) b.onclick = () => act("/api/settings", { settings: { explore: +b.dataset.v } });
    const goVibe = () => { const t = $("vibe").value.trim(); if (t.length < 3) { toast("Biraz daha anlat"); return; } $("vibe").blur(); setMode({ text: t }); };
    $("vibeGo").onclick = goVibe;
    $("vibe").onkeydown = (e) => { if (e.key === "Enter") goVibe(); };
    $("lfmSave").onclick = () => {
      const k = $("lfm").value.trim();
      if (k && !/^[0-9a-fA-F]{32}$/.test(k)) { toast("Last.fm anahtarı 32 karakter olmalı"); return; }
      $("lfm").value = "";
      act("/api/settings", { settings: { lastfm: k } }, k ? "✓ Last.fm kaydedildi" : "Last.fm kaldırıldı");
    };
    $("logout").onclick = async () => { try { await api("/api/logout", {}); } catch {} logoutLocal(); };
    $("forget").onclick = async () => {
      if (!confirm("Ahenk'teki tüm verilerin (bağlantı, ayarlar, geçmiş) silinsin mi?")) return;
      try { await api("/api/forget", {}); } catch {}
      logoutLocal();
      toast("Verilerin silindi");
    };

    // iPhone Safari'de ana ekrana ekleme ipucu
    const ios = /iphone|ipad|ipod/i.test(navigator.userAgent);
    const standalone = navigator.standalone || matchMedia("(display-mode: standalone)").matches;
    if (ios && !standalone && !LS.get("ahenk_hint_closed")) {
      $("installHint").classList.remove("hidden");
      $("installHint").onclick = () => { LS.set("ahenk_hint_closed", "1"); $("installHint").classList.add("hidden"); };
    }

    await handleCallback();
    show();
    try { meta = await (await fetch("/api/meta")).json(); } catch {}
    if (session) await refresh(true);
    setInterval(() => refresh(false), 5000);
    setInterval(tickBar, 1000);
    document.addEventListener("visibilitychange", () => { if (!document.hidden) refresh(true); });
    if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {});
  }

  init();
})();
