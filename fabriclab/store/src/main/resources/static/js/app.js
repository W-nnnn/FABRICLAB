/* FABRICLAB 用户端交互：页面切换动效、按钮反馈、滚动入场 */
/* 切换动效方向语义：fwd=系列间向前（内容左移、新页自右入）
   bwd=回退（内容右移、新页自左入） up=深入一层（旧页上移、新页自下升起）
   dn=退出层级（旧页下沉、上层自顶落回） fade=无向流程流转 */
(function () {
  'use strict';
  if (window.__flApp) return; /* orders 页两处引用 app.js，防重复初始化 */
  window.__flApp = 1;

  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var page = document.querySelector('.page');
  var KEY = 'fl:nav';
  var OUT = { fwd: 'pg-out-fwd', bwd: 'pg-out-bwd', up: 'pg-out-up', dn: 'pg-out-dn', fade: 'pg-out-fade' };
  var IN = { fwd: 'pg-in-fwd', bwd: 'pg-in-bwd', up: 'pg-in-up', dn: 'pg-in-dn', fade: 'pg-in-fade' };
  /* 退出动画时长（毫秒），与 CSS 对齐后跳转 */
  var OUT_MS = { fwd: 220, bwd: 220, up: 230, dn: 210, fade: 190 };

  function sameOrigin(href) {
    try { return new URL(href, location.href).origin === location.origin; }
    catch (e) { return false; }
  }

  /* ---- 进入动画：读取上一页留下的方向 ---- */
  if (page && !reduce) {
    var dirIn = null;
    try { dirIn = sessionStorage.getItem(KEY); sessionStorage.removeItem(KEY); } catch (e) {}
    if (dirIn && IN[dirIn]) {
      page.classList.add(IN[dirIn]);
      setTimeout(function () { page.classList.remove(IN[dirIn]); }, 900);
    }
  }
  /* bfcache 返回：清洗残留的退出态（含前进后退恢复） */
  window.addEventListener('pageshow', function (e) {
    if (e.persisted && page) {
      Object.keys(OUT).forEach(function (k) { page.classList.remove(OUT[k]); });
    }
  });

  /* ---- 由被点击的链接推断方向 ---- */
  function dirOf(a) {
    var mark = a.getAttribute('data-nav');
    if (mark === 'back') return 'dn';
    if (mark === 'next') return 'fwd';
    if (mark === 'prev') return 'bwd';
    if (a.closest('.crumb')) return 'dn'; /* 面包屑：退出当前层级 */
    var nav = a.closest('.navbar');
    if (nav && a.classList.contains('nitem')) {
      var items = Array.prototype.slice.call(nav.querySelectorAll('.nitem'));
      var i = items.indexOf(a);
      var curEl = nav.querySelector('.nitem.on');
      var ci = curEl ? items.indexOf(curEl) : -1;
      if (i >= 0 && ci >= 0 && i !== ci) return i > ci ? 'fwd' : 'bwd';
      return 'fade';
    }
    if (a.classList.contains('card')) return 'up'; /* 商品卡：深入详情 */
    return 'fade';
  }

  /* ---- 播放退出动画并延迟跳转（双击防抖） ---- */
  var leaving = false;
  function run(dir, done) {
    if (leaving) return;
    leaving = true;
    try { sessionStorage.setItem(KEY, dir); } catch (e) {}
    if (page) page.classList.add(OUT[dir]);
    setTimeout(done, OUT_MS[dir] || 220);
  }

  document.addEventListener('click', function (e) {
    if (e.defaultPrevented || e.button || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    var a = e.target.closest('a');
    if (!a) return;
    var href = a.getAttribute('href');
    if (!href || href.charAt(0) === '#') return;
    if (a.target === '_blank' || a.hasAttribute('download')) return;
    if (!sameOrigin(a.href)) return;
    if (a.href === location.href || a.href === location.href + '#') return;
    if (reduce) return; /* 降级：直接原生跳转 */
    e.preventDefault();
    var target = a.href;
    run(dirOf(a), function () { window.location.href = target; });
  });

  /* ---- 表单提交：无向淡出，再接新页淡入 ---- */
  document.addEventListener('submit', function (e) {
    var f = e.target;
    if (!f || !f.tagName || f.tagName !== 'FORM') return;
    if (f.hasAttribute('data-pjax')) return; /* 局部刷新表单由账户中心处理 */
    if (e.defaultPrevented) return; /* 已有脚本阻止（校验失败），不接管 */
    if (reduce || !page) return;
    var action = f.getAttribute('action') || location.href;
    if (!sameOrigin(action)) return;
    e.preventDefault();
    run('fade', function () { f.submit(); });
  });

  /* ---- 按钮水波纹反馈 ---- */
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

  /* ---- 滚动进入视口时触发商品卡入场 ---- */
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

  /* ---- 头部搜索：首次点击展开并聚焦，空关键字不提交 ---- */
  var sform = document.querySelector('.hdr form.inline');
  if (sform) {
    var sinput = sform.querySelector('input[type="text"]');
    var sbtn = sform.querySelector('button');
    if (sinput) {
      if (sbtn) sbtn.addEventListener('click', function (e) {
        if (!sform.classList.contains('open') && !sinput.value.trim()) {
          e.preventDefault(); /* 首击只展开，不提交 */
          sform.classList.add('open');
        }
        sinput.focus();
      });
      sform.addEventListener('submit', function (e) {
        if (!sinput.value.trim()) { /* 空关键字：保持展开，不跳转 */
          e.preventDefault();
          sform.classList.add('open');
          sinput.focus();
        }
      });
      sinput.addEventListener('blur', function (e) {
        if (sform.contains(e.relatedTarget)) return;
        if (!sinput.value.trim()) sform.classList.remove('open');
      });
      sinput.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') { sinput.value = ''; sinput.blur(); }
      });
    }
  }
})();

/* ============================================================ 商品图集 */
/* 缩略图点击切换主图（交叉淡入+微推近）、悬停箭头、键盘左右方向键、自动轮播 */
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
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* 数字微跳：值变化时给输入框一个短促的上跳回弹 */
  function tick(input) {
    if (reduce) return;
    input.classList.remove('tick');
    void input.offsetWidth; /* 强制重排以重放动画 */
    input.classList.add('tick');
  }

  Array.prototype.forEach.call(document.querySelectorAll('[data-qty]'), function (box) {
    var input = box.querySelector('input');
    if (!input || input.disabled) return;
    var minus = box.querySelector('[data-minus]');
    var plus = box.querySelector('[data-plus]');

    function set(v) {
      v = parseInt(v, 10);
      if (isNaN(v)) v = 1;
      v = Math.min(99, Math.max(1, v));
      if (v !== (parseInt(input.value, 10) || 1)) tick(input);
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

  /* 无按钮的裸数量框（购物袋页）：直接改数也微跳 */
  Array.prototype.forEach.call(document.querySelectorAll('.qbox input'), function (input) {
    if (input.closest('[data-qty]')) return;
    input.addEventListener('change', function () { tick(input); });
  });
})();

/* ============================================================ 账户中心 */
/* 左侧板块在当前页内切换；订单面板的筛选/搜索/分页走局部刷新，不整页跳转 */
(function () {
  'use strict';
  var pane = document.getElementById('opanel');
  if (!pane) return;
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---- 板块切换（hash 记忆，前进后退可用；新板块上浮进场） ---- */
  var tabs = Array.prototype.slice.call(document.querySelectorAll('.anav [data-pane]'));
  function showPane(name) {
    tabs.forEach(function (t) {
      t.classList.toggle('on', t.getAttribute('data-pane') === name);
    });
    Array.prototype.forEach.call(document.querySelectorAll('.apane'), function (p) {
      var on = p.id === 'pane-' + name;
      p.hidden = !on;
      if (on && !reduce) {
        p.classList.remove('pane-in');
        void p.offsetWidth;
        p.classList.add('pane-in');
      }
    });
  }
  tabs.forEach(function (t) {
    t.addEventListener('click', function (e) {
      e.preventDefault();
      var name = t.getAttribute('data-pane');
      showPane(name);
      if (history.replaceState) history.replaceState(null, '', '#' + name);
    });
  });
  /* 快捷卡「账户设置」同页切换 */
  Array.prototype.forEach.call(document.querySelectorAll('[data-pane-link]'), function (a) {
    a.addEventListener('click', function (e) {
      e.preventDefault();
      var name = a.getAttribute('data-pane-link');
      showPane(name);
      if (history.replaceState) history.replaceState(null, '', '#' + name);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  });
  var initial = (location.hash || '#orders').slice(1);
  showPane(document.getElementById('pane-' + initial) ? initial : 'orders');

  /* ---- 订单面板局部刷新：轻暗 → 换内容 → 上浮入场 ---- */
  var busy = false;
  function load(url, push) {
    if (busy) return;
    busy = true;
    var t0 = Date.now();
    pane.classList.add('loading');
    fetch(url, { credentials: 'same-origin' })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.text(); })
      .then(function (text) {
        var doc = new DOMParser().parseFromString(text, 'text/html');
        var next = doc.getElementById('opanel');
        /* 最快路径下也保留一小段暗场，让「换页」被感知到 */
        var wait = Math.max(0, 170 - (Date.now() - t0));
        setTimeout(function () {
          if (next) pane.innerHTML = next.innerHTML;
          if (push && history.pushState) history.pushState(null, '', url);
          pane.classList.remove('loading');
          if (!reduce) {
            pane.classList.remove('swapped');
            void pane.offsetWidth;
            pane.classList.add('swapped');
          }
          busy = false;
        }, wait);
      })
      .catch(function () { window.location.href = url; });
  }
  pane.addEventListener('click', function (e) {
    var a = e.target.closest('a.ptab, a.pglink');
    if (!a) return;
    e.preventDefault();
    load(a.href, true);
  });
  pane.addEventListener('submit', function (e) {
    var f = e.target.closest('form[data-pjax]');
    if (!f) return;
    e.preventDefault();
    var qs = new URLSearchParams(new FormData(f)).toString();
    load(f.getAttribute('action') + (qs ? '?' + qs : ''), true);
  });
  window.addEventListener('popstate', function () { load(location.href, false); });
})();
