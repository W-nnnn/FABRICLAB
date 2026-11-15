/* FABRICLAB 面料档案：滚动入场、章节索引联动、阅读进度、编号视差、织纹鼠标视差。
   只在 /fabrics 引入；脚本失效或系统开启「减少动态效果」时，页面内容全部保持可读。 */
(function () {
  'use strict';
  if (window.__flFabric) return;
  window.__flFabric = 1;

  var reduce = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  var hero = document.querySelector('.fa-hero');
  if (!hero) return;

  var reveals = Array.prototype.slice.call(document.querySelectorAll('.fa-reveal'));
  var chapters = Array.prototype.slice.call(document.querySelectorAll('.fa-chapter'));
  var items = Array.prototype.slice.call(document.querySelectorAll('.fa-index__item'));
  var progress = document.querySelector('.fa-progress i');
  var weave = document.querySelector('.fa-weave');

  /* 脚本确认可用后才把待入场元素隐藏；任何异常路径下内容都是默认可见的 */
  if (!reduce) document.documentElement.classList.add('fa-anim');

  if (!reduce && 'IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (!en.isIntersecting) return;
        en.target.classList.add('in');
        io.unobserve(en.target);
      });
    }, { threshold: 0.08, rootMargin: '0px 0px -6% 0px' });
    reveals.forEach(function (el) { io.observe(el); });
  } else {
    reveals.forEach(function (el) { el.classList.add('in'); });
  }

  /* 索引点击：原生锚点会瞬间跳，改为平滑滚动；CSS 的 scroll-margin-top 避开双层吸顶头部 */
  items.forEach(function (a) {
    a.addEventListener('click', function (e) {
      var id = a.getAttribute('href');
      var target = null;
      try { target = id ? document.querySelector(id) : null; } catch (err) { target = null; }
      if (!target) return;
      e.preventDefault();
      target.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
      try { history.replaceState(null, '', id); } catch (err) { /* 无权限时只滚动即可 */ }
    });
  });

  var ticking = false;
  function update() {
    ticking = false;
    var doc = document.documentElement;
    var max = doc.scrollHeight - window.innerHeight;
    var y = window.scrollY || window.pageYOffset || 0;
    if (progress) {
      progress.style.width = (max > 0 ? Math.max(0, Math.min(100, y / max * 100)) : 0).toFixed(2) + '%';
    }

    /* 当前章节：取最后一个已越过视口 200px 线的章节，避免下一章刚露头就抢高亮 */
    var best = 0;
    chapters.forEach(function (c, i) {
      if (c.getBoundingClientRect().top <= 200) best = i;
    });
    items.forEach(function (a, i) { a.classList.toggle('on', i === best); });

    if (!reduce) {
      var mid = window.innerHeight / 2;
      chapters.forEach(function (c) {
        var ghost = c.querySelector('.fa-chapter__ghost');
        if (!ghost) return;
        var r = c.getBoundingClientRect();
        var delta = -(r.top + r.height / 2 - mid) * 0.045;
        ghost.style.setProperty('--par', Math.max(-60, Math.min(60, delta)).toFixed(1) + 'px');
      });
    }
  }
  function onScroll() {
    if (ticking) return;
    ticking = true;
    window.requestAnimationFrame(update);
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('resize', onScroll);
  window.addEventListener('load', update);
  update();

  /* 织纹随鼠标做小幅视差（仅精确指针设备） */
  if (weave && !reduce && window.matchMedia && window.matchMedia('(hover: hover)').matches) {
    weave.addEventListener('mousemove', function (e) {
      var r = weave.getBoundingClientRect();
      var x = (e.clientX - r.left) / r.width - 0.5;
      var y = (e.clientY - r.top) / r.height - 0.5;
      weave.style.setProperty('--mxp', (x * 16).toFixed(1) + 'px');
      weave.style.setProperty('--myp', (y * 16).toFixed(1) + 'px');
    });
    weave.addEventListener('mouseleave', function () {
      weave.style.setProperty('--mxp', '0px');
      weave.style.setProperty('--myp', '0px');
    });
  }
})();
