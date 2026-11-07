(function () {
  var token = document.getElementById('token');
  var gate = document.getElementById('gateId');
  var saved = localStorage.getItem('outpass.gateId');
  if (saved && gate) { gate.value = saved; }
  if (gate) gate.addEventListener('change', function () { localStorage.setItem('outpass.gateId', gate.value); token.focus(); });

  function focusToken() {
    var a = document.activeElement;
    if (!a || (a.tagName !== 'SELECT' && a.tagName !== 'A')) token.focus();
  }
  token.addEventListener('blur', function () { setTimeout(focusToken, 80); });
  document.addEventListener('click', function () { setTimeout(focusToken, 0); });
  setInterval(focusToken, 2000);
  token.focus();

  var ctx;
  function beep(ok) {
    try {
      ctx = ctx || new (window.AudioContext || window.webkitAudioContext)();
      var o = ctx.createOscillator(), g = ctx.createGain();
      o.frequency.value = ok ? 880 : 220;
      g.gain.value = 0.15;
      o.connect(g); g.connect(ctx.destination);
      o.start(); o.stop(ctx.currentTime + (ok ? 0.15 : 0.5));
    } catch (e) { }
  }

  document.querySelectorAll('.demo-scan').forEach(function (b) {
    b.addEventListener('click', function () {
      token.value = b.getAttribute('data-token');
      htmx.trigger(document.getElementById('scanForm'), 'submit');
    });
  });

  document.body.addEventListener('htmx:afterRequest', function () { token.value = ''; token.focus(); });
  document.body.addEventListener('htmx:afterSwap', function (e) {
    var r = document.getElementById('result');
    if (r && r.getAttribute('data-ok') !== '') beep(r.getAttribute('data-ok') === 'true');
  });
  document.body.addEventListener('htmx:responseError', function () {
    document.getElementById('result').outerHTML =
      '<div id="result" class="mt-6 rounded-xl border-4 border-red-600 bg-red-50 p-6 text-center text-xl font-bold text-red-800">Server error - please scan again</div>';
  });
})();
