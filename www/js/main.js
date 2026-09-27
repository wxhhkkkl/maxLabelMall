/* 赋签 MaxLabel - 轮播组件（首页 Hero / 商城横幅通用）
 * 自动播放 5s · 圆点/箭头切换 · 悬停暂停 · 移动端滑动
 */
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-carousel]').forEach(function (root) {
    var track = root.querySelector('.car-track');
    var slides = root.querySelectorAll('.car-slide');
    var dotsWrap = root.querySelector('.car-dots');
    var i = 0, n = slides.length, timer;
    if (!track || n === 0) return;

    for (var k = 0; k < n; k++) {
      var d = document.createElement('button');
      d.className = 'car-dot' + (k === 0 ? ' active' : '');
      d.setAttribute('aria-label', '切换到第 ' + (k + 1) + ' 张');
      (function (dot, idx) {
        dot.addEventListener('click', function () { go(idx); restart(); });
      })(d, k);
      dotsWrap.appendChild(d);
    }
    var dots = dotsWrap.children;

    function go(idx) {
      i = ((idx % n) + n) % n;
      track.style.transform = 'translateX(-' + i * 100 + '%)';
      for (var k = 0; k < n; k++) dots[k].classList.toggle('active', k === i);
    }
    function restart() {
      clearInterval(timer);
      timer = setInterval(function () { go(i + 1); }, 5000);
    }

    var prev = root.querySelector('.car-arrow.prev');
    var next = root.querySelector('.car-arrow.next');
    if (prev) prev.addEventListener('click', function () { go(i - 1); restart(); });
    if (next) next.addEventListener('click', function () { go(i + 1); restart(); });

    root.addEventListener('mouseenter', function () { clearInterval(timer); });
    root.addEventListener('mouseleave', restart);

    var sx = 0;
    root.addEventListener('touchstart', function (e) { sx = e.touches[0].clientX; }, { passive: true });
    root.addEventListener('touchend', function (e) {
      var dx = e.changedTouches[0].clientX - sx;
      if (Math.abs(dx) > 40) { go(dx < 0 ? i + 1 : i - 1); restart(); }
    }, { passive: true });

    restart();
  });
});

/* 商城商品搜索：实时筛选 + 关键词高亮 + 结果计数 + 空状态 */
document.addEventListener('DOMContentLoaded', function () {
  var input = document.getElementById('mallSearch');
  if (!input) return;
  var box = document.getElementById('mallSearchBox');
  var btn = document.getElementById('mallSearchBtn');
  var clearBtn = document.getElementById('mallSearchClear');
  var resetBtn = document.getElementById('mallSearchReset');
  var empty = document.getElementById('mallEmpty');
  var emptyDesc = document.getElementById('mallEmptyDesc');
  var grid = document.querySelector('.mall-right .grid-3');
  var pager = document.querySelector('.mall-right .pager');
  var countEl = document.querySelector('.sort-count');
  var cards = Array.prototype.slice.call(grid.querySelectorAll('.p-card'));
  var total = cards.length;

  // 缓存原始 HTML，便于清除高亮
  cards.forEach(function (card) {
    card.querySelectorAll('.p-title, .p-sell').forEach(function (el) {
      el.dataset.origin = el.innerHTML;
    });
  });

  function highlight(card, kw) {
    card.querySelectorAll('.p-title, .p-sell').forEach(function (el) {
      var origin = el.dataset.origin;
      if (!kw) { el.innerHTML = origin; return; }
      var reg = new RegExp('(' + kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'gi');
      el.innerHTML = origin.replace(reg, '<mark>$1</mark>');
    });
  }

  function apply() {
    var kw = input.value.trim();
    box.classList.toggle('has-value', kw.length > 0);
    var hit = 0;
    cards.forEach(function (card) {
      var text = card.textContent.replace(/\s+/g, '');
      var show = !kw || text.toLowerCase().indexOf(kw.toLowerCase()) !== -1;
      card.style.display = show ? '' : 'none';
      highlight(card, show ? kw : '');
      if (show) hit++;
    });
    empty.hidden = hit !== 0;
    grid.style.display = hit === 0 ? 'none' : '';
    if (pager) pager.style.display = kw ? 'none' : '';
    if (emptyDesc && kw) emptyDesc.textContent = '没有找到与「' + kw + '」相关的商品，换个关键词试试';
    if (countEl) countEl.textContent = kw ? ('找到 ' + hit + ' 件相关商品') : ('共 ' + total + ' 件商品');
  }

  function reset() { input.value = ''; apply(); input.focus(); }

  input.addEventListener('input', apply);
  input.addEventListener('keydown', function (e) { if (e.key === 'Enter') apply(); });
  btn.addEventListener('click', apply);
  clearBtn.addEventListener('click', reset);
  if (resetBtn) resetBtn.addEventListener('click', reset);
});
