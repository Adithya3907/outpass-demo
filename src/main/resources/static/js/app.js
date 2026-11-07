// Live updates (Server-Sent Events) + service worker registration
(function () {
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () { navigator.serviceWorker.register('/sw.js').catch(function () {}); });
  }
  if (!window.EventSource || location.pathname === '/login') return;
  var live = document.body && document.body.hasAttribute('data-live');
  var es = null, leaving = false;
  window.addEventListener('pagehide', function () { leaving = true; if (es) es.close(); });
  function connect() {
    if (leaving) return;
    es = new EventSource('/events');
    es.addEventListener('notify', function (e) {
      if (live) { location.reload(); return; }
      toast(e.data);
    });
    es.addEventListener('refresh', function () { if (live) location.reload(); });
    es.onerror = function () { es.close(); if (!leaving) setTimeout(connect, 5000); };
  }
  function toast(text) {
    var d = document.createElement('div');
    d.textContent = text + ' - open Notifications';
    d.className = 'fixed bottom-4 right-4 z-50 rounded-md border border-brand bg-white px-4 py-3 text-sm shadow-lg';
    document.body.appendChild(d);
    setTimeout(function () { d.remove(); }, 6000);
  }
  document.addEventListener('DOMContentLoaded', function () {
    connect();
    var el = document.getElementById('demoReset');
    if (el) {
      var at = parseInt(el.getAttribute('data-reset'), 10);
      var tick = function () {
        var m = Math.max(0, Math.ceil((at - Date.now()) / 60000));
        el.textContent = m <= 1 ? 'about a minute' : m + ' minutes';
      };
      tick(); setInterval(tick, 20000);
    }
  });
})();
