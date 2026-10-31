/**
 * 列表分页。
 *
 * 表格行在构建期已全量渲染（列的渲染逻辑留在源里），这里只按页显隐，
 * 不改 DOM 结构、不重新拼 HTML。行用 data-row-idx 标记序号。
 *
 * 一个页面里可能有多张表（列表页 + 抽屉里的明细表），分页器只接管
 * 同一个 .page-card 内的那张，用 closest 向上定位，不全局搜。
 *
 * 作用域（scope）：单仓页的每行带 data-wh，页头仓库下拉切换时由
 * shell/warehouse-scope.js 调 setScope() 告知当前仓。此后所有分页计算
 * 都以「当前仓的行」为全集（行数、页码、区间、显隐），
 * 因此不存在两个脚本争抢 hidden 的情况。
 */
(function () {
  'use strict';

  function pagesOf(total, size) { return Math.max(1, Math.ceil(total / size)); }

  /* 当前作用域内、且属于本分页器所在卡片的行。
     scope 为空串表示不按仓筛选（默认）。 */
  function scopedRows(bar, scope) {
    var card = bar.closest('.page-card');
    if (!card) return [];
    var rows = [].slice.call(card.querySelectorAll('[data-row-idx]'));
    if (!scope) return rows;
    return rows.filter(function (r) { return r.getAttribute('data-wh') === scope; });
  }

  /* 重画分页器：页码窗口 = 首页 + 当前页两侧 + 末页，中间省略号 */
  function pagerHTML(total, size, cur, unit) {
    var pages = pagesOf(total, size);
    var lo = total === 0 ? 0 : (cur - 1) * size + 1;
    var hi = total === 0 ? 0 : Math.min(total, cur * size);

    var nums = [];
    function push(n) { if (nums.indexOf(n) < 0 && n >= 1 && n <= pages) nums.push(n); }
    push(1);
    if (cur - 2 > 1) nums.push('...');
    push(cur - 1); push(cur); push(cur + 1);
    if (cur + 2 < pages) nums.push('...');
    push(pages);

    var h = '<span class="pager-total">共 ' + total + ' ' + unit
      + (total ? ' · 第 ' + lo + '-' + hi + ' ' + unit : '') + '</span>'
      + '<div class="pager-right">'
      + '<label class="pager-size">每页 <select data-page-size>'
      + [10, 20, 50, 100].map(function (n) {
        return '<option value="' + n + '"' + (n === size ? ' selected' : '') + '>' + n + '</option>';
      }).join('')
      + '</select> ' + unit + '</label>'
      + '<div class="pager">'
      + '<button type="button" data-page-prev' + (cur <= 1 ? ' disabled' : '') + '>上一页</button>'
      + nums.map(function (n) {
        return n === '...'
          ? '<span class="pager-gap">…</span>'
          : '<button type="button" data-page-num="' + n + '"'
            + (n === cur ? ' class="active" aria-current="page"' : '') + '>' + n + '</button>';
      }).join('')
      + '<button type="button" data-page-next' + (cur >= pages ? ' disabled' : '') + '>下一页</button>'
      + '</div>'
      + '<label class="pager-jump">跳至 <input type="text" inputmode="numeric" data-page-jump value="'
      + cur + '" aria-label="跳至页"><button type="button" data-page-go>GO</button></label>'
      + '</div>';
    return h;
  }

  function apply(bar, cur, size) {
    var card = bar.closest('.page-card');
    if (!card) return;
    var scope = bar.getAttribute('data-wh-active') || '';
    var total = parseInt(bar.getAttribute('data-total'), 10) || 0;
    var unit = bar.getAttribute('data-unit') || '条';
    var pages = pagesOf(total, size);
    if (cur < 1) cur = 1;
    if (cur > pages) cur = pages;

    bar.setAttribute('data-page', cur);
    bar.setAttribute('data-size', size);

    /* 行显隐：先取出当前作用域内的行，再按页码窗口切一段。
       不在作用域内的行一律藏起。 */
    var all = [].slice.call(card.querySelectorAll('[data-row-idx]'));
    var scoped = scope
      ? all.filter(function (r) { return r.getAttribute('data-wh') === scope; })
      : all;
    var lo = (cur - 1) * size;
    var hi = lo + size;

    var rank = 0;
    var inScope = new Set();
    for (var i = 0; i < all.length; i++) {
      var row = all[i];
      if (scope && row.getAttribute('data-wh') !== scope) {
        row.setAttribute('hidden', '');
        continue;
      }
      inScope.add(row);
      if (rank >= lo && rank < hi) row.removeAttribute('hidden');
      else row.setAttribute('hidden', '');
      rank++;
    }

    /* 全选框只勾当前可见行 */
    var selAll = card.querySelector('.dt-select-all');
    if (selAll) selAll.checked = false;

    /* 空态行：有数据时藏起来 */
    var empty = card.querySelector('.dt-empty');
    if (empty) empty.hidden = total > 0;

    bar.innerHTML = pagerHTML(total, size, cur, unit);
    var wrap = card.querySelector('.table-wrap');
    if (wrap) wrap.scrollTop = 0;
  }

  /* 对外：设置某个页面卡的作用域仓，并重算总数、回到第一页。
     供 shell/warehouse-scope.js 调用。 */
  function setScope(card, wh) {
    if (!card) return;
    var bar = card.querySelector('[data-pager]');
    if (!bar) return;
    var rows = [].slice.call(card.querySelectorAll('[data-row-idx]'));
    if (!rows.length) return;
    var total = wh
      ? rows.filter(function (r) { return r.getAttribute('data-wh') === wh; }).length
      : rows.length;
    bar.setAttribute('data-total', String(total));
    bar.setAttribute('data-wh-active', wh || '');
    apply(bar, 1, parseInt(bar.getAttribute('data-size'), 10) || 20);
  }

  window.__WMS_PAGER__ = { apply: apply, setScope: setScope };

  document.addEventListener('click', function (e) {
    var t = e.target;

    var num = t.closest && t.closest('[data-page-num]');
    if (num) {
      var bar1 = num.closest('[data-pager]');
      if (bar1) apply(bar1, parseInt(num.getAttribute('data-page-num'), 10),
        parseInt(bar1.getAttribute('data-size'), 10) || 20);
      return;
    }
    var prev = t.closest && t.closest('[data-page-prev]');
    if (prev && !prev.disabled) {
      var bar2 = prev.closest('[data-pager]');
      if (bar2) apply(bar2, (parseInt(bar2.getAttribute('data-page'), 10) || 1) - 1,
        parseInt(bar2.getAttribute('data-size'), 10) || 20);
      return;
    }
    var next = t.closest && t.closest('[data-page-next]');
    if (next && !next.disabled) {
      var bar3 = next.closest('[data-pager]');
      if (bar3) apply(bar3, (parseInt(bar3.getAttribute('data-page'), 10) || 1) + 1,
        parseInt(bar3.getAttribute('data-size'), 10) || 20);
      return;
    }
    var go = t.closest && t.closest('[data-page-go]');
    if (go) {
      var bar4 = go.closest('[data-pager]');
      if (!bar4) return;
      var input = bar4.querySelector('[data-page-jump]');
      var n = parseInt(input && input.value, 10);
      if (!n || n < 1) { if (input) input.value = bar4.getAttribute('data-page'); return; }
      apply(bar4, n, parseInt(bar4.getAttribute('data-size'), 10) || 20);
    }
  });

  /* 跳页框回车即跳 */
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter') return;
    var input = e.target.closest && e.target.closest('[data-page-jump]');
    if (!input) return;
    var bar = input.closest('[data-pager]');
    if (!bar) return;
    var n = parseInt(input.value, 10);
    if (!n || n < 1) { input.value = bar.getAttribute('data-page'); return; }
    apply(bar, n, parseInt(bar.getAttribute('data-size'), 10) || 20);
  });

  document.addEventListener('change', function (e) {
    var sel = e.target.closest && e.target.closest('[data-page-size]');
    if (!sel) return;
    var bar = sel.closest('[data-pager]');
    if (!bar) return;
    /* 换每页条数后回到第一页，避免停在中途看空表 */
    apply(bar, 1, parseInt(sel.value, 10) || 20);
  });

  /* 首屏归一：按条数隐藏越界行，并让分页器与表格对齐 */
  var bars = document.querySelectorAll('[data-pager]');
  for (var i = 0; i < bars.length; i++) {
    var b = bars[i];
    b.setAttribute('data-unit', b.getAttribute('data-unit') || '条');
    apply(b, parseInt(b.getAttribute('data-page'), 10) || 1,
      parseInt(b.getAttribute('data-size'), 10) || 20);
  }
})();
