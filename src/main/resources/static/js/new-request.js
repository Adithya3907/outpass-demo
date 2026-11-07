(function () {
  var dateInput = document.getElementById('permissionDate');
  var label = document.getElementById('dateLabel');
  var btns = document.querySelectorAll('.day-btn');
  function paint() {
    var v = dateInput.value;
    label.textContent = v
      ? new Date(v + 'T00:00:00').toLocaleDateString('en-IN', {weekday: 'long', day: '2-digit', month: 'long', year: 'numeric'})
      : '-';
    btns.forEach(function (b) {
      var on = b.getAttribute('data-iso') === v;
      b.classList.toggle('!border-brand', on);
      b.classList.toggle('!bg-brand-light', on);
    });
  }
  btns.forEach(function (b) {
    b.addEventListener('click', function () { dateInput.value = b.getAttribute('data-iso'); paint(); });
  });
  dateInput.addEventListener('change', paint);
  paint();

  // custom reason box
  var reason = document.getElementById('reason');
  var box = document.getElementById('customBox');
  var custom = document.getElementById('customReason');
  var count = document.getElementById('wordCount');
  function words() { var t = custom.value.trim(); return t ? t.split(/\s+/).length : 0; }
  function syncReason() {
    var on = reason.value === 'Custom';
    box.classList.toggle('hidden', !on);
    custom.required = on;
    if (on) custom.focus();
  }
  function syncCount() {
    var n = words();
    count.textContent = n;
    custom.setCustomValidity(n > 20 ? 'Please use 20 words or fewer' : '');
    count.parentElement.classList.toggle('text-red-700', n > 20);
  }
  reason.addEventListener('change', syncReason);
  custom.addEventListener('input', syncCount);
  syncReason(); syncCount();
  if (reason.value !== 'Custom') custom.blur();

  var clock = document.getElementById('clock');
  function tick() {
    clock.textContent = new Date().toLocaleTimeString('en-IN', {hour: '2-digit', minute: '2-digit', hour12: true});
  }
  tick();
  setInterval(tick, 15000);
})();
