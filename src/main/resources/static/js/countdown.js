(function () {
  var el = document.getElementById('countdown');
  if (!el) return;
  var label = document.getElementById('countdownLabel');
  var bar = document.getElementById('countbar');
  var from = parseInt(el.getAttribute('data-from'), 10);
  var exp = parseInt(el.getAttribute('data-expires'), 10);
  var reloaded = false;
  function pad(n) { return n < 10 ? '0' + n : '' + n; }
  function fmt(sec) {
    var h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    return (h > 0 ? h + ':' + pad(m) : pad(m)) + ':' + pad(s);
  }
  function tick() {
    var now = Date.now();
    if (now < from) {
      label.textContent = 'QR becomes valid in';
      el.textContent = fmt(Math.ceil((from - now) / 1000));
      bar.style.width = '100%';
      return;
    }
    var left = Math.floor((exp - now) / 1000);
    label.textContent = 'QR expires in';
    if (left <= 0) {
      label.textContent = 'QR status';
      el.textContent = 'EXPIRED';
      el.classList.add('text-red-700');
      bar.style.width = '0%';
      if (!reloaded) { reloaded = true; setTimeout(function () { location.reload(); }, 3000); }
      return;
    }
    el.textContent = fmt(left);
    bar.style.width = Math.max(0, Math.min(100, (exp - now) / (exp - from) * 100)) + '%';
    if (left < 300) { el.classList.add('text-red-700'); bar.className = 'h-2 rounded bg-red-600'; }
  }
  tick();
  setInterval(tick, 1000);
})();
