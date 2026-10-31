/**
 * 设计简报校验。
 *
 * 背景：本工作区已有过一次「文档写一套、代码跑另一套」——
 * specs/01 只列 43 行摘要，tokens.css 却有 573 行 423 个令牌，语义色取值还不一致。
 * DESIGN.md 如果再复制一份取值进来，只会腐烂得更快。
 *
 * 本脚本守一条规矩：索引层（三份 DESIGN.md）出现的每个取值，
 * 必须能在实现层（baseline-tokens / tokens.css / pda-ui-tokens.json）里原样找到。
 * 找不到就是编的，编的就会腐烂。
 *
 * 用法：
 *   node tools/check-design.mjs           # 硬检查 + 打印已知缺口
 *   node tools/check-design.mjs --strict  # 已知缺口也当失败
 *   node tools/check-design.mjs --quiet   # 只报失败
 *
 * 退出码：0 通过，1 有硬性违规（或 --strict 下有缺口）。
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/* 路径一律按本文件位置定位，不依赖 process.cwd()：
   脚本按本文件位置定位路径，不依赖 process.cwd()
   用 cwd 拼相对路径会在换目录运行时落空，existsSync 过滤后校验 0 个文件却报「通过」。 */
const HERE = path.dirname(fileURLToPath(import.meta.url));
const PROTO = path.resolve(HERE, '..');  // 本目录即自包含根
const ROOT = PROTO;
const ARGS = process.argv.slice(2);
const STRICT = ARGS.includes('--strict');
const QUIET = ARGS.includes('--quiet');

/* ---------- 路径 ---------- */

const F = {
  designRoot: path.join(PROTO, 'DESIGN.md'),
  designPc: path.join(PROTO, 'pc/DESIGN.md'),
  designApp: path.join(PROTO, 'app/DESIGN.md'),
  baseline: path.join(PROTO, 'shared/ui/baseline-tokens.css.mjs'),
  tokensCss: path.join(PROTO, 'shared/ui/tokens.css'),
  pdaJson: path.join(PROTO, 'pda-ui-tokens.json'),
  appShell: path.join(PROTO, 'app/src/app-shell.mjs'),
  distPc: path.join(PROTO, 'dist/pc.html'),
  distApp: path.join(PROTO, 'dist/app.html'),
};

const rel = (p) => path.relative(ROOT, p);

/* ---------- 收集实现层取值 ---------- */

/** 从文本里抽所有 hex 色值，统一小写 */
function hexesOf(text) {
  const out = new Set();
  for (const m of text.matchAll(/#([0-9a-fA-F]{3,8})\b/g)) {
    let h = m[1].toLowerCase();
    if (h.length === 3) h = h.split('').map((c) => c + c).join('');
    out.add(h);
  }
  return out;
}

/** 实现层允许来源：PC 两套令牌 + APP json + APP shell */
function collectSourceValues() {
  const hexes = new Set();
  const texts = [];

  for (const f of [F.baseline, F.tokensCss, F.appShell]) {
    if (!fs.existsSync(f)) continue;
    const t = fs.readFileSync(f, 'utf8');
    texts.push(t);
    for (const h of hexesOf(t)) hexes.add(h);
  }

  if (fs.existsSync(F.pdaJson)) {
    const t = fs.readFileSync(F.pdaJson, 'utf8');
    texts.push(t);
    for (const h of hexesOf(t)) hexes.add(h);
    // json 里可能有未加 # 的颜色写法，一并收
    try {
      const j = JSON.parse(t);
      const walk = (o) => {
        if (typeof o === 'string') {
          for (const h of hexesOf(o)) hexes.add(h);
          if (/^[0-9a-fA-F]{6}$/.test(o)) hexes.add(o.toLowerCase());
        } else if (Array.isArray(o)) o.forEach(walk);
        else if (o && typeof o === 'object') Object.values(o).forEach(walk);
      };
      walk(j);
    } catch { /* json 坏了下面会单独报 */ }
  }

  return { hexes, texts };
}

/* ---------- 检查 1：索引层不许编取值 ---------- */

function checkDesignHexes(sources) {
  const bad = [];
  const candidates = [F.designRoot, F.designPc, F.designApp];
  const files = candidates.filter((f) => fs.existsSync(f));

  /* 三份缺一即失败：少读一份等于少守一份取值。
     静默缩小校验范围，比路径写错更难发现。 */
  if (files.length < candidates.length) {
    const missing = candidates.filter((f) => !fs.existsSync(f)).map(rel);
    return [{ file: '(缺失)', value: '-', line: `应校验 ${candidates.length} 份 DESIGN.md，缺失 ${missing.length} 份：${missing.join(' | ')}` }];
  }

  for (const f of files) {
    const text = fs.readFileSync(f, 'utf8');
    for (const line of text.split('\n')) {
      // 跳过 frontmatter 的注释性引用与「示例」行标记
      for (const m of line.matchAll(/#([0-9a-fA-F]{3,8})\b/g)) {
        let h = m[1].toLowerCase();
        if (h.length === 3) h = h.split('').map((c) => c + c).join('');
        if (!sources.hexes.has(h)) {
          bad.push({ file: rel(f), value: '#' + m[1], line: line.trim().slice(0, 70) });
        }
      }
    }
  }
  return bad;
}

/* ---------- 检查 2：dist 零外链 ---------- */

function checkNoExternal() {
  const bad = [];
  for (const f of [F.distPc, F.distApp]) {
    if (!fs.existsSync(f)) continue;
    const html = fs.readFileSync(f, 'utf8');
    // 只看资源引用，正文里的 URL 字符串不算
    const patterns = [
      { re: /<link[^>]+href=["']https?:\/\//gi, name: '<link href> 外链' },
      { re: /<script[^>]+src=["']https?:\/\//gi, name: '<script src> 外链' },
      { re: /@import\s+url\(\s*["']?https?:\/\//gi, name: '@import 外链' },
      { re: /<img[^>]+src=["']https?:\/\//gi, name: '<img src> 外链' },
      { re: /url\(\s*["']?https?:\/\/[^)]*\)/gi, name: 'CSS url() 外链' },
    ];
    for (const p of patterns) {
      const n = (html.match(p.re) || []).length;
      if (n) bad.push({ file: rel(f), what: p.name, count: n });
    }
  }
  return bad;
}

/* ---------- 检查 3：APP 不得含 rpx ---------- */

function checkNoRpx() {
  const bad = [];
  const files = [
    path.join(PROTO, 'app/src/app-shell.mjs'),
    path.join(PROTO, 'app/src/app-pages.mjs'),
    F.distApp,
  ];
  for (const f of files) {
    if (!fs.existsSync(f)) continue;
    const t = fs.readFileSync(f, 'utf8');
    const n = (t.match(/\d+(\.\d+)?rpx/g) || []).length;
    if (n) bad.push({ file: rel(f), what: 'rpx 单位', count: n });
  }
  return bad;
}

/* ---------- 报告项：已知缺口 ---------- */

/** APP 令牌：json 定义 vs app-shell 实现 */
function appTokenGaps() {
  if (!fs.existsSync(F.pdaJson) || !fs.existsSync(F.appShell)) return null;
  let json;
  try { json = JSON.parse(fs.readFileSync(F.pdaJson, 'utf8')); } catch {
    return { error: 'pda-ui-tokens.json 解析失败' };
  }
  const tokens = json.tokens || {};
  const shell = fs.readFileSync(F.appShell, 'utf8');
  const impl = new Set();
  for (const m of shell.matchAll(/(--pda-[a-z0-9-]+)\s*:/g)) impl.add(m[1]);

  const missing = [];
  for (const [, v] of Object.entries(tokens)) {
    const cv = v && v.cssVariable;
    if (cv && !impl.has(cv)) missing.push({ name: cv, value: v.value });
  }
  const extra = [...impl].filter((k) => !Object.values(tokens).some((v) => v.cssVariable === k));
  return { total: Object.keys(tokens).length, implemented: impl.size, missing, extra };
}

/**
 * 状态胶囊：页面传给 StatusTag 的 tone vs l3 的 .status.* 定义。
 *
 * 只统计 StatusTag()，不统计 Tag()——两者是两套东西：
 *   StatusTag → .status.<tone>（l3 第 5xx 行，走 baseline 令牌）
 *   Tag       → .wm-tag--<tone>（走 --wds-component-tag-*），“warning”属后者，不是缺口。
 * 判据取字面调用，不靠变量名猜，避免把 Tag 的色调算进 StatusTag。
 */
function statusToneGaps() {
  const l3 = path.join(PROTO, 'shared/ui/l3.mjs');
  if (!fs.existsSync(l3)) return null;
  const t = fs.readFileSync(l3, 'utf8');

  const defined = new Set();
  for (const m of t.matchAll(/\.status\.([a-z]+)\s*\{/g)) defined.add(m[1]);
  const tagDefined = new Set();
  for (const m of t.matchAll(/\.wm-tag--([a-z]+)\s*\{/g)) tagDefined.add(m[1]);

  const used = new Set();
  const pagesDir = path.join(PROTO, 'pc/src/pages');
  if (fs.existsSync(pagesDir)) {
    for (const f of fs.readdirSync(pagesDir).filter((x) => x.endsWith('.mjs'))) {
      const s = fs.readFileSync(path.join(pagesDir, f), 'utf8');
      // 1) 字面量：StatusTag(x, 'ok')
      for (const m of s.matchAll(/StatusTag\([^)]*,\s*'([a-z]+)'\s*\)/g)) used.add(m[1]);
      // 2) 经由 TONE 常量：只认被 StatusTag 用到的那些映射
      const statusToneMaps = new Set();
      for (const m of s.matchAll(/StatusTag\([^)]*?\b([A-Z_]+TONE)\[[^\]]*\]/g)) statusToneMaps.add(m[1]);
      for (const name of statusToneMaps) {
        const re = new RegExp(`const\\s+${name}\\s*=\\s*\\{([^}]*)\\}`, 's');
        const mm = s.match(re);
        if (mm) for (const t2 of mm[1].matchAll(/:\s*'([a-z]+)'/g)) used.add(t2[1]);
      }
    }
  }

  // default 不算缺口：.status 基类本身就是中性灰底灰字，正是 default 想要的外观。
  // ok / out / low 是语义色（成功/危险/警告），落回灰底会丢语义，才算缺口。
  const NEUTRAL = new Set(['default']);
  const missing = [...used].filter((u) => !defined.has(u) && !NEUTRAL.has(u));
  return {
    defined: [...defined].sort(),
    tagDefined: [...tagDefined].sort(),
    used: [...used].sort(),
    missing: missing.sort(),
    neutral: [...used].filter((u) => !defined.has(u) && NEUTRAL.has(u)).sort(),
  };
}

/** 圆角越界：specs/01 限定 {6,8,999} */
function radiusOutliers() {
  const allowed = new Set(['6px', '8px', '999px', '0', '0px', '50%']);
  const files = ['l3.mjs', 'l4.mjs', 'base.css.mjs'].map((f) =>
    path.join(PROTO, 'shared/ui', f));
  const out = [];
  for (const f of files) {
    if (!fs.existsSync(f)) continue;
    const t = fs.readFileSync(f, 'utf8');
    for (const m of t.matchAll(/border-radius:\s*([^;}]+)/g)) {
      const v = m[1].trim();
      if (v.startsWith('var(')) continue;
      const parts = v.split(/\s+/).filter(Boolean);
      const bad = parts.filter((p) => !allowed.has(p) && !/^\d+%$/.test(p));
      if (bad.length) out.push({ file: path.basename(f), value: v });
    }
  }
  return out;
}

/* ---------- 主流程 ---------- */

function main() {
  const lines = [];
  const push = (s = '') => lines.push(s);

  const sources = collectSourceValues();

  // --- 硬检查 ---
  const hexBad = checkDesignHexes(sources);
  const extBad = checkNoExternal();
  const rpxBad = checkNoRpx();
  const hardFail = hexBad.length + extBad.length + rpxBad.length;

  push('=== 一、索引层取值校验（DESIGN.md 的每个 hex 必须能在实现层找到） ===');
  if (!hexBad.length) {
    push(`  ok  三份 DESIGN.md 未出现实现层以外的色值`);
  } else {
    for (const b of hexBad) {
      push(`  ✗ ${b.file}  值 ${b.value} 在实现层找不到`);
      push(`      ${b.line}`);
    }
  }

  push('');
  push('=== 二、产物零外链 ===');
  if (!extBad.length) {
    push('  ok  dist 无外部资源引用');
  } else {
    for (const b of extBad) push(`  ✗ ${b.file}  ${b.what} × ${b.count}`);
  }

  push('');
  push('=== 三、APP 不得使用 rpx ===');
  if (!rpxBad.length) {
    push('  ok  APP 源与产物均无 rpx');
  } else {
    for (const b of rpxBad) push(`  ✗ ${b.file}  ${b.what} × ${b.count}`);
  }

  // --- 报告项 ---
  let gapCount = 0;

  push('');
  push('=== 四、APP 令牌缺口（json 已定义、CSS 未实现） ===');
  const appGap = appTokenGaps();
  if (!appGap) {
    push('  （跳过：文件缺失）');
  } else if (appGap.error) {
    push(`  ✗ ${appGap.error}`);
    gapCount++;
  } else {
    push(`  json 定义 ${appGap.total} 项，app-shell 实现 ${appGap.implemented} 项，缺 ${appGap.missing.length} 项`);
    for (const m of appGap.missing) push(`    · ${m.name} = ${m.value}`);
    if (appGap.extra.length) push(`  另有 ${appGap.extra.length} 项 CSS 有、json 无（机身装饰件，正常）：${appGap.extra.join(' ')}`);
    gapCount += appGap.missing.length;
  }

  push('');
  push('=== 五、状态胶囊色型缺口（页面传入 vs l3 定义） ===');
  const toneGap = statusToneGaps();
  if (!toneGap) {
    push('  （跳过：l3.mjs 缺失）');
  } else {
    push(`  l3 已定义：${toneGap.defined.join(' / ')}`);
    push(`  页面传入：${toneGap.used.join(' / ')}`);
    if (toneGap.neutral && toneGap.neutral.length) {
      push(`  · ${toneGap.neutral.join(' / ')} 无专属样式，落基类中性灰（预期，非缺口）`);
    }
    if (toneGap.missing.length) {
      push(`  ✗ 未定义色型 ${toneGap.missing.length} 个：${toneGap.missing.join(' / ')}`);
      push('    这些胶囊会落到基类的灰底灰字，语义丢失。');
      gapCount += toneGap.missing.length;
    } else {
      push('  ok  所有语义色型都有对应样式');
    }
  }

  push('');
  push('=== 六、圆角越界（specs/01 限定 6 / 8 / 999） ===');
  const radBad = radiusOutliers();
  if (!radBad.length) {
    push('  ok  圆角取值均在允许集内');
  } else {
    const agg = new Map();
    for (const r of radBad) {
      const k = `${r.file} → ${r.value}`;
      agg.set(k, (agg.get(k) || 0) + 1);
    }
    for (const [k, n] of agg) push(`  ✗ ${k}  × ${n}`);
    gapCount += radBad.length;
  }

  // --- 输出 ---
  if (!QUIET || hardFail) {
    console.log(lines.join('\n'));
  }

  console.log('');
  if (hardFail) {
    console.log(`硬性违规 ${hardFail} 处。索引层不许编取值、产物不许外链、APP 不许 rpx。`);
    process.exit(1);
  }
  if (gapCount) {
    console.log(`硬检查通过；已知缺口 ${gapCount} 项（见上，属现状，不计失败）。`);
    if (STRICT) {
      console.log('--strict 模式：缺口亦计失败。');
      process.exit(1);
    }
    process.exit(0);
  }
  console.log('全部通过。');
  process.exit(0);
}

if (import.meta.url === `file://${process.argv[1]}`) main();
