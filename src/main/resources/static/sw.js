// Minimal service worker: makes the portal installable and shows an offline notice.
// Dynamic pages are never cached, so approvals and QR data are always live.
const STATIC_CACHE = 'outpass-static-v2';
self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(STATIC_CACHE).then(function (c) {
    return c.addAll(['/offline.html', '/css/tailwind.css', '/img/psgitech-logo.png']);
  }));
  self.skipWaiting();
});
self.addEventListener('activate', function (e) { e.waitUntil(self.clients.claim()); });
self.addEventListener('fetch', function (e) {
  var req = e.request;
  if (req.method !== 'GET') return;
  var url = new URL(req.url);
  if (url.origin !== location.origin || url.pathname === '/events') return;
  if (url.pathname.startsWith('/css/') || url.pathname.startsWith('/img/') || url.pathname.startsWith('/js/') || url.pathname.startsWith('/icons/')) {
    e.respondWith(caches.match(req).then(function (hit) { return hit || fetch(req); }));
    return;
  }
  if (req.mode === 'navigate') {
    e.respondWith(fetch(req).catch(function () { return caches.match('/offline.html'); }));
  }
});

// ---- Web Push: show a notification even when the app is closed
self.addEventListener('push', function (event) {
  var data = {title: 'PSGiTech Outpass', body: '', url: '/'};
  try { if (event.data) data = Object.assign(data, event.data.json()); } catch (e) { }
  event.waitUntil(self.registration.showNotification(data.title, {
    body: data.body,
    icon: '/icons/icon-192.png',
    badge: '/icons/icon-192.png',
    data: {url: data.url},
    tag: data.url
  }));
});
self.addEventListener('notificationclick', function (event) {
  event.notification.close();
  var url = (event.notification.data && event.notification.data.url) || '/';
  event.waitUntil(self.clients.matchAll({type: 'window', includeUncontrolled: true}).then(function (list) {
    for (var i = 0; i < list.length; i++) {
      if ('focus' in list[i]) { list[i].navigate(url); return list[i].focus(); }
    }
    return self.clients.openWindow(url);
  }));
});
