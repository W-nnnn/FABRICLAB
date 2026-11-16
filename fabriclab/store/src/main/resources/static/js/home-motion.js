/* FABRICLAB 首页动效：首屏分块入场、滚动揭示（分组错峰）、数字滚动、
   横贯大片视差、五条系列线与卡片悬停微动的状态类。
   只在首页引入；脚本失效或系统开启「减少动态效果」时内容全部默认可见。 */
(function () {
  'use strict';
  if (window.__flHome) return;
  window.__flHome = 1;

  var reduce = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  if (reduce || !('IntersectionObserver' in window)) return;

  /* 脚本确认可用后才挂动画根类；此前所有元素都是默认可见的 */
  document.documentElement.classList.add('hm-anim');

  /* 分组错峰：data-hm-group 的直接子元素自动成为揭示项，按序给 70ms 步进延迟 */
  Array.prototype.forEach.call(document.querySelectorAll('[data-hm-group]'), function (group) {
    Array.prototype.forEach.call(group.children, function (child, i) {
      child.setAttribute('data-hm', '');
      child.style.transitionDelay = Math.min(i * 70, 420) + 'ms';
    });
  });

  var reveals = Array.prototype.slice.call(document.querySelectorAll('[data-hm]'));
  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (en) {
      if (!en.isIntersecting) return;
      en.target.classList.add('in');
      io.unobserve(en.target);
    });
  }, { threshold: 0.08, rootMargin: '0px 0px -6% 0px' });
  reveals.forEach(function (el) { io.observe(el); });

  /* 数字滚动：data-count 指向目标值，入视口后 1.2s 缓出到位，至少两位补零 */
  function pad2(n) { return (n < 10 ? '0' : '') + n; }
  var counters = Array.prototype.slice.call(document.querySelectorAll('[data-count]'));
  var cio = new IntersectionObserver(function (entries) {
    entries.forEach(function (en) {
      if (!en.isIntersecting) return;
      cio.unobserve(en.target);
      var el = en.target;
      var target = parseInt(el.getAttribute('data-count'), 10) || 0;
      var t0 = null;
      function step(ts) {
        if (t0 === null) t0 = ts;
        var k = Math.min((ts - t0) / 1200, 1);
        var eased = 1 - Math.pow(1 - k, 3);
        el.textContent = pad2(Math.round(target * eased));
        if (k < 1) requestAnimationFrame(step);
      }
      requestAnimationFrame(step);
    });
  }, { threshold: 0.5 });
  counters.forEach(function (el) { cio.observe(el); });

  /* 视差：data-hm-parallax 指向最大位移 px；元素带 scale 基线避免露边 */
  var layers = Array.prototype.map.call(document.querySelectorAll('[data-hm-parallax]'), function (el) {
    return { el: el, amp: parseFloat(el.getAttribute('data-hm-parallax')) || 30 };
  });
  if (layers.length && window.innerWidth > 1024) {
    var ticking = false;
    function update() {
      ticking = false;
      var vh = window.innerHeight;
      layers.forEach(function (l) {
        var r = l.el.getBoundingClientRect();
        if (r.bottom < -80 || r.top > vh + 80) return;
        /* 元素中心偏离视口中心的程度 → -1..1 → 位移 */
        var p = ((r.top + r.height / 2) - vh / 2) / (vh / 2 + r.height / 2);
        p = Math.max(-1, Math.min(1, p));
        l.el.style.transform = 'translate3d(0,' + (-p * l.amp).toFixed(1) + 'px,0) scale(1.1)';
      });
    }
    function onScroll() {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(update);
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    update();
  }
})();
