/* FABRICLAB 选购页动效：页头/工具条/面板分步揭示、卡片组错峰入场、
   在售计数滚动。仅选购页引入；局部刷新替换 #catalog 内容后自动重新绑定。
   脚本失效或系统开启「减少动态效果」时，所有内容默认可见（不挂动画根类）。 */
(function () {
  'use strict';
  if (window.__flCatMotion) return;
  window.__flCatMotion = 1;

  var reduce = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  if (reduce || !('IntersectionObserver' in window)) return;

  /* 脚本确认可用后才挂动画根类；此前所有元素都是默认可见的 */
  document.documentElement.classList.add('cm-anim');

  var region = document.getElementById('catalog');

  /* 分组错峰：data-cm-group 的直接子元素自动成为揭示项，按序 60ms 步进（封顶 420ms） */
  function bindGroup(group) {
    Array.prototype.forEach.call(group.children, function (child, i) {
      child.setAttribute('data-cm', '');
      child.style.transitionDelay = Math.min(i * 60, 420) + 'ms';
    });
  }

  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (en) {
      if (!en.isIntersecting) return;
      en.target.classList.add('in');
      io.unobserve(en.target);
    });
  }, { threshold: 0.06, rootMargin: '0px 0px -5% 0px' });

  /* 数字滚动：data-count 指向目标值，入视口后 1.2s 缓出到位，至少两位补零 */
  function pad2(n) { return (n < 10 ? '0' : '') + n; }
  var cio = new IntersectionObserver(function (entries) {
    entries.forEach(function (en) {
      if (!en.isIntersecting) return;
      cio.unobserve(en.target);
      var el = en.target;
      var target = parseInt(el.getAttribute('data-count'), 10);
      if (isNaN(target)) return;
      var t0 = null;
      function step(ts) {
        if (!t0) t0 = ts;
        var k = Math.min((ts - t0) / 1200, 1);
        var eased = 1 - Math.pow(1 - k, 3);
        el.textContent = pad2(Math.round(target * eased));
        if (k < 1) requestAnimationFrame(step);
      }
      requestAnimationFrame(step);
    });
  }, { threshold: 0.4 });

  /* 绑定单个新节点及其内部；局部刷新替换内容后对新增节点重复调用即可 */
  function bind(root) {
    var scope = root || document;
    Array.prototype.forEach.call(scope.querySelectorAll('[data-cm-group]'), function (g) {
      if (g.__cmBound) return;
      g.__cmBound = 1;
      bindGroup(g);
    });
    Array.prototype.forEach.call(scope.querySelectorAll('[data-cm]'), function (el) {
      if (el.__cmBound) return;
      el.__cmBound = 1;
      io.observe(el);
    });
    Array.prototype.forEach.call(scope.querySelectorAll('[data-count]'), function (el) {
      if (el.__cmBound) return;
      el.__cmBound = 1;
      cio.observe(el);
    });
  }
  bind(document);

  /* 局部刷新：#catalog 子树新增节点（换筛选/换页/换排序）后重新绑定揭示与计数 */
  if (region && 'MutationObserver' in window) {
    new MutationObserver(function (muts) {
      muts.forEach(function (m) {
        Array.prototype.forEach.call(m.addedNodes, function (n) {
          if (n.nodeType === 1) bind(n);
        });
      });
    }).observe(region, { childList: true, subtree: false });
  }
})();
