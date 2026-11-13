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
