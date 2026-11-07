(function () {
  var cfg = document.getElementById('pushConfig');
  var status = document.getElementById('pushStatus');
  var bEnable = document.getElementById('pushEnable');
  var bTest = document.getElementById('pushTest');
  var bDisable = document.getElementById('pushDisable');
  var key = cfg.getAttribute('data-key');
  var csrf = cfg.getAttribute('data-csrf');
  var header = cfg.getAttribute('data-header');

  function show(el, on) { el.classList.toggle('hidden', !on); }
  function post(url, params) {
    var h = {'Content-Type': 'application/x-www-form-urlencoded'};
    h[header] = csrf;
    return fetch(url, {method: 'POST', headers: h, body: new URLSearchParams(params), credentials: 'same-origin'});
  }
  function b64ToBytes(b64) {
    var pad = '='.repeat((4 - b64.length % 4) % 4);
    var raw = atob((b64 + pad).replace(/-/g, '+').replace(/_/g, '/'));
    var out = new Uint8Array(raw.length);
    for (var i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
    return out;
  }
  function bytesToB64(buf) {
    var s = ''; var a = new Uint8Array(buf);
    for (var i = 0; i < a.length; i++) s += String.fromCharCode(a[i]);
    return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
    status.textContent = 'This browser cannot receive push notifications. On iPhone, add the app to your Home Screen first.';
    return;
  }
  if (!window.isSecureContext) {
    status.textContent = 'Push notifications need a secure (HTTPS) address. They work on localhost and on the college HTTPS site.';
    return;
  }

  function refresh() {
    navigator.serviceWorker.ready.then(function (reg) {
      return reg.pushManager.getSubscription();
    }).then(function (sub) {
      if (Notification.permission === 'denied') {
        status.textContent = 'Notifications are blocked for this site in your browser settings. Allow them there, then reload.';
        show(bEnable, false); show(bTest, false); show(bDisable, false); return;
      }
      if (sub) {
        status.textContent = 'Notifications are ON for this device.';
        show(bEnable, false); show(bTest, true); show(bDisable, true);
      } else {
        status.textContent = 'Notifications are OFF for this device.';
        show(bEnable, true); show(bTest, false); show(bDisable, false);
      }
    });
  }

  bEnable.addEventListener('click', function () {
    Notification.requestPermission().then(function (perm) {
      if (perm !== 'granted') { refresh(); return; }
      return navigator.serviceWorker.ready.then(function (reg) {
        return reg.pushManager.subscribe({userVisibleOnly: true, applicationServerKey: b64ToBytes(key)});
      }).then(function (sub) {
        var j = sub.toJSON();
        return post('/account/push/subscribe', {endpoint: sub.endpoint, p256dh: j.keys.p256dh, auth: j.keys.auth});
      }).then(function () { location.reload(); });
    }).catch(function (e) { status.textContent = 'Could not enable notifications: ' + e.message; });
  });
  bTest.addEventListener('click', function () { post('/account/push/test', {}).then(function () { status.textContent = 'Test sent - it should appear in a few seconds.'; }); });
  bDisable.addEventListener('click', function () {
    navigator.serviceWorker.ready.then(function (reg) { return reg.pushManager.getSubscription(); }).then(function (sub) {
      if (!sub) return;
      var ep = sub.endpoint;
      return sub.unsubscribe().then(function () { return post('/account/push/unsubscribe', {endpoint: ep}); });
    }).then(function () { location.reload(); });
  });
  refresh();
})();
