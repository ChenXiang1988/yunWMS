/**
 * 样式类名冲突校验。
 *
 * 背景：两个页面模块各自定义 `.hw-drawer`，一个当全屏遮罩（inset:0），
 * 一个当右侧抽屉（right:0）。同名后先加载的 inset:0 生效，
 * 抽屉被拉满全屏、跑到左侧。这类问题肉眼极难发现。
 *
 * 规则：不同页面模块不得定义同名 CSS 类，白名单除外。
 */

/* 白名单：跨模块共用是合理的类名前缀 */
const SHARED_PREFIXES = [
  'is-',      /* 状态类：is-active / is-show / is-current */
  'ant-',     /* antd 组件库 */
  'wds-',     /* WDS 令牌 */
  'wm-',      /* 共享 UI 层（l3 / l4） */
  'page',     /* 框架级 */
  'btn', 'tag', 'dt', 'col', 'row', 'tab', 'status',
];

const isShared = (name) =>
  SHARED_PREFIXES.some((p) => (p.endsWith('-') ? name.startsWith(p) : name === p || name.startsWith(p + '-')));

/**
 * 从源码中提取 CSS 类定义名。
 * 只在模板字符串内查找「.类名{」，跳过普通字符串与注释里的干扰。
 * @param {string} src
 * @returns {Set<string>}
 */
export function extractClassNames(src) {
  const names = new Set();
  /* 抓取所有 <style> 段或 CSS 模板字符串里的 .name{ 定义 */
  const re = /\.([a-z][a-z0-9]*(?:-[a-z0-9]+)*)\s*\{/g;
  let m;
  while ((m = re.exec(src)) !== null) {
    names.add(m[1]);
  }
  return names;
}

/**
 * @param {object} o
 * @param {Array<{name:string, source:string}>} o.modules  页面模块与源码
 * @returns {string[]} 违规描述
 */
export function verifyClassNames({ modules = [] } = {}) {
  const bad = [];
  const byClass = new Map();   /* 类名 → [模块名] */

  for (const mod of modules) {
    const names = extractClassNames(mod.source);
    for (const n of names) {
      if (isShared(n)) continue;
      if (!byClass.has(n)) byClass.set(n, []);
      const owners = byClass.get(n);
      if (!owners.includes(mod.name)) owners.push(mod.name);
    }
  }

  for (const [cls, owners] of byClass) {
    if (owners.length > 1) {
      bad.push(`.${cls} 被多个模块同时定义：${owners.join('、')}。`
        + '同名类会互相覆盖，请给各模块加独立前缀');
    }
  }

  return bad.sort();
}
