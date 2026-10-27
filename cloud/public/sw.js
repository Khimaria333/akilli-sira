// Ahenk: uygulama kabuğunu önbellekte tut (çevrimdışıyken de açılsın); API her zaman ağdan.
const C = "ahenk-v1";
const SHELL = ["/", "/app.js", "/logo.png", "/manifest.webmanifest", "/icon-192.png"];
self.addEventListener("install", (e) => { e.waitUntil(caches.open(C).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener("activate", (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== C).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener("fetch", (e) => {
  const u = new URL(e.request.url);
  if (e.request.method !== "GET" || u.origin !== location.origin || u.pathname.startsWith("/api/")) return;
  e.respondWith(fetch(e.request).then((r) => {
    if (r.ok && SHELL.includes(u.pathname)) { const cp = r.clone(); caches.open(C).then((c) => c.put(e.request, cp)); }
    return r;
  }).catch(() => caches.match(e.request).then((m) => m || caches.match("/"))));
});
