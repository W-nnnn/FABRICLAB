/* FABRICLAB 用户端交互：页面切换动效、按钮反馈、滚动入场 */
(function () {
  'use strict';
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function sameOrigin(href) {
    try { return new URL(href, location.href).origin === location.origin; }
    catch (e) { return false; }
  }

  /* 站内链接：先播退出动画，再跳转，营造页面切换感 */
  document.addEventListener('click', function (e) {
    if (e.defaultPrevented || e.button || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    var a = e.target.closest('a');
    if (!a) return;
    var href = a.getAttribute('href');
    if (!href || href.charAt(0) === '#') return;
    if (a.target === '_blank' || a.hasAttribute('download')) return;
    if (!sameOrigin(a.href)) return;
    if (a.href === location.href || a.href === location.href + '#') return;
    if (reduce) return;
    e.preventDefault();
    var page = document.querySelector('.page');
    var go = a.href;
    if (page) {
      page.classList.add('pg-out');
      setTimeout(function () { window.location.href = go; }, 260);
    } else {
      window.location.href = go;
    }
  });

  /* 按钮水波纹反馈 */
  document.addEventListener('click', function (e) {
    var b = e.target.closest('.btn');
    if (!b || reduce) return;
    var rect = b.getBoundingClientRect();
    var size = Math.max(rect.width, rect.height);
    var r = document.createElement('span');
    r.className = 'ripple';
    r.style.width = r.style.height = size + 'px';
    r.style.left = (e.clientX - rect.left - size / 2) + 'px';
    r.style.top = (e.clientY - rect.top - size / 2) + 'px';
    b.appendChild(r);
    setTimeout(function () { r.remove(); }, 600);
  });

  /* 滚动进入视口时触发商品卡入场 */
  var cards = document.querySelectorAll('.card');
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
    cards.forEach(function (el) { io.observe(el); });
  } else {
    cards.forEach(function (el) { el.classList.add('in'); });
  }
})();

/* ============================================================ 商品图集 */
/* 缩略图点击切换主图（交叉淡入）、悬停箭头、键盘左右方向键、自动轮播 */
(function () {
  'use strict';
  var root = document.querySelector('[data-gallery]');
  if (!root) return;
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var imgs = Array.prototype.slice.call(root.querySelectorAll('.gstage img'));
  var thumbs = Array.prototype.slice.call(root.querySelectorAll('.gthumb'));
  var counter = root.querySelector('.gcount');
  var n = imgs.length;
  if (!n) return;
  var idx = 0, timer = null, DUR = 5200;

  function pad(x) { return (x < 10 ? '0' : '') + x; }

  function show(i) {
    idx = (i + n) % n;
    imgs.forEach(function (im, k) { im.classList.toggle('on', k === idx); });
    thumbs.forEach(function (t, k) { t.classList.toggle('on', k === idx); });
    if (counter) counter.textContent = pad(idx + 1) + ' / ' + pad(n);
  }

  function stop() { if (timer) { clearInterval(timer); timer = null; } }
  function play() { if (reduce || n < 2) return; stop(); timer = setInterval(function () { show(idx + 1); }, DUR); }

  thumbs.forEach(function (t, k) {
    t.addEventListener('click', function () { show(k); play(); });
  });
  var prev = root.querySelector('.garrow.prev'), next = root.querySelector('.garrow.next');
  if (prev) prev.addEventListener('click', function () { show(idx - 1); play(); });
  if (next) next.addEventListener('click', function () { show(idx + 1); play(); });

  var stage = root.querySelector('.gstage');
  if (stage) {
    stage.addEventListener('mouseenter', stop);
    stage.addEventListener('mouseleave', play);
  }

  /* 键盘左右方向键：仅当图集在视口内且焦点不在输入框时生效 */
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    var t = e.target;
    if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT')) return;
    var r = root.getBoundingClientRect();
    if (r.bottom < 0 || r.top > window.innerHeight) return;
    if (e.key === 'ArrowLeft') { show(idx - 1); } else { show(idx + 1); }
    play();
  });

  /* 详情区图集联动：点击后回到顶部并切换主图到对应张 */
  Array.prototype.forEach.call(document.querySelectorAll('[data-jump]'), function (b) {
    b.addEventListener('click', function () {
      var i = parseInt(b.getAttribute('data-jump'), 10);
      show(isNaN(i) ? 0 : i);
      play();
      window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' });
    });
  });

  show(0);
  play();
})();

/* ============================================================ 数量步进器 */
(function () {
  'use strict';
  Array.prototype.forEach.call(document.querySelectorAll('[data-qty]'), function (box) {
    var input = box.querySelector('input');
    if (!input || input.disabled) return;
    var minus = box.querySelector('[data-minus]');
    var plus = box.querySelector('[data-plus]');

    function set(v) {
      v = parseInt(v, 10);
      if (isNaN(v)) v = 1;
      v = Math.min(99, Math.max(1, v));
      input.value = v;
    }
    if (minus) minus.addEventListener('click', function () {
      set((parseInt(input.value, 10) || 1) - 1);
    });
    if (plus) plus.addEventListener('click', function () {
      set((parseInt(input.value, 10) || 1) + 1);
    });
    input.addEventListener('change', function () { set(input.value); });
  });
})();
