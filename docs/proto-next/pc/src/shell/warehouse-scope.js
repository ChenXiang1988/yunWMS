/**
 * 页头「仓库」下拉的作用域。
 *
 * 单仓配置段的页面（容器管理、库位管理）是某个实体仓的现场台账，
 * 页头的仓库下拉决定看哪个仓的数据。此前这个下拉只是个摆设：
 * l4.mjs 渲染了 #warehouseSelect，但没有任何脚本监听它，
 * 于是五个仓的数据混在同一页里，"单仓"名不副实。
 *
 * 分工：
 *   行的显隐与分页由 shell/pager.js 统一负责（它按 data-wh-active 过滤分页全集）；
 *   本文件只做两件事 —— 把选中的仓告诉分页器，以及重算状态页签计数。
 * 这样 hidden 只有一个写入方，不会互相覆盖。
 *
 * 作用范围：只有页内存在 [data-wh] 行的页面才受影响；总仓页与其它页面
 * 不标 data-wh，切仓库对它们没有副作用。
 */
(function () {
  'use strict';

  /* 对所有带 data-wh 行的页面生效，不只当前可见页：
     切页是 hash 驱动的，若只在可见页上应用，先进入的页面会保留未过滤状态。 */
  function scopedPages() {
    return [].slice.call(document.querySelectorAll('.page[data-page]'))
      .filter(function (p) { return p.querySelector('[data-wh]'); });
  }

  function applyWarehouse(wh) {
    scopedPages().forEach(function (page) {
      var rows = [].slice.call(page.querySelectorAll('[data-wh]'));
      if (!rows.length) return;

      var scope = rows.filter(function (r) {
        return !wh || r.getAttribute('data-wh') === wh;
      });

      /* 一、分页与行显隐交给分页器，只传作用域 */
      var card = page.querySelector('.page-card');
      if (card && window.__WMS_PAGER__) {
        window.__WMS_PAGER__.setScope(card, wh || '');
      }

      /* 二、状态页签计数按同一仓范围重算 */
      var tabs = [].slice.call(page.querySelectorAll('[data-status-tab]'));
      tabs.forEach(function (btn) {
        var field = btn.getAttribute('data-tab-field');
        var value = btn.getAttribute('data-tab-value');
        var n;
        if (!field) {
          n = scope.length;                    /* 「全部」页签 */
        } else {
          n = scope.filter(function (r) {
            return r.getAttribute('data-' + field) === value;
          }).length;
        }
        var badge = btn.querySelector('.tab-count');
        if (badge) badge.textContent = String(n);
      });

      /* 三、计数文案跟着换，不写死在构建期 */
      var note = page.querySelector('.action-bar .muted');
      if (note) note.textContent = '当前范围：' + (wh || '全部仓库');
    });
  }

  function init() {
    var sel = document.getElementById('warehouseSelect');
    if (!sel) return;
    sel.addEventListener('change', function () {
      applyWarehouse(sel.value);
    });
    /* 首屏：按下拉默认值过滤一次，避免先铺开全部仓再跳变 */
    applyWarehouse(sel.value);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
