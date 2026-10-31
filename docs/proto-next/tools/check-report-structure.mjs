/**
 * 汇报材料结构校验。
 *
 * 背景：2026-10-30 周报返工七轮，多数不是改内容而是改结构——
 * 把交付物表格换成自制卡片、为省行数删掉白卡容器、加回打印按钮、
 * 写进"本页是某某版本"这类自述。03 号规范写了这些禁止事项，
 * 但「写在文档里」不构成约束，所以转成脚本。
 *
 * 与 check-report-copy.mjs 分工：
 *   copy     查写什么（措辞、黑话、状态口径）
 *   structure 查怎么搭（骨架、类名、无用交互）
 *
 * 用法：
 *   node tools/check-report-structure.mjs 周报/2026-10-26-10-30/xxx.html
 *   node tools/check-report-structure.mjs reports/_模板-汇报.html
 *
 * 退出码：0 通过，1 有违规。
 */

import fs from 'node:fs';
import path from 'node:path';

/* ---------- 规则 ---------- */

/**
 * 骨架五件套：每份汇报材料必须全部出现。
 * 判据是类名出现在 class 属性里，不区分组合写法。
 */
const SKELETON = [
  { cls: 'sidebar', name: '左侧目录', hint: '长文档导航，滚动跟随高亮' },
  { cls: 'sec-kicker', name: '眉标', hint: '01 / ENGLISH LABEL 形式，给读者位置感' },
  { cls: 'count', name: '右侧角标', hint: '一句话提要或数量，与 h2 同行' },
  { cls: 'lead', name: '页首导语', hint: '蓝底引言，一句话结论' },
  { cls: 'panel', name: '白卡容器', hint: '表格与正文都包在卡里，不裸贴背景' },
];

/**
 * 容器类白名单。出现在 class 属性里、但不在本表的容器型类名一律报违规。
 * 依据：03 号规范 4.1「不得自造容器」。
 * 判断方式：只查「看起来像容器」的类——命中下表的语法特征（成对卡片、栅格、hero、
 * wrapper 等）才拦，避免误伤 badge / num / prog 这类小部件。
 */
const CONTAINER_PATTERN = /(?:^|\s)(?:card|cards|hero|grid|box|wrap2|wrapper|tile|tiles|block|column|columns|col-grid)(?:$|\s)/;

/** 允许的容器型类名（模板自带） */
const CONTAINER_ALLOW = new Set(['card', 'cards', 'table-wrap']);

/**
 * 无用交互与装饰文案。03 号规范 4.4。
 * 逐条：{ id, name, re, why }
 */
const DECOR = [
  {
    id: 'S1',
    name: '打印按钮',
    re: /<button[^>]*>\s*打印|<button[^>]*>\s*Print/i,
    why: 'window.print() 是浏览器自带能力，页面上放按钮是多余入口。',
  },
  {
    id: 'S2',
    name: 'window.print 调用',
    re: /window\.print\s*\(/,
    why: '同上。删除按钮时连带删掉 script 内的绑定。',
  },
  {
    id: 'S3',
    name: '实现方式自述',
    re: /单文件\s*[·・]\s*无外部依赖|无外部依赖|不连接任何服务/,
    why: '读者不关心文件怎么打包。',
  },
  {
    id: 'S4',
    name: '双版本自述',
    re: /本页是.{0,20}版本|可阅读版本|方便直接粘进|同目录还有.{0,10}版本/,
    why: '交付说明，不属于正文内容。',
  },
  {
    id: 'S5',
    name: '流程时间约定',
    re: /每周[一二三四五六日天]\s*下班前|每周[一二三四五六日天]发出/,
    why: '发出时间是流程约定，不是材料内容。',
  },
];

/**
 * 历史材料豁免清单。
 * 这些文件按旧规范（01 号紫系令牌）产出，2026-10-30 用户明确裁定「不刷」。
 * 豁免的是「骨架/容器」类结构检查；外链、title、无用交互仍照查——
 * 那几项与版式无关，属于硬底线。
 */
const LEGACY = new Set([
  '任务中心思路.html',
  '任务中心看板调研.html',
  '硬件接入管理.html',
  '设备管理思路.html',
]);

/** 外链 */
const EXTERNAL = /(?:src|href)\s*=\s*["']https?:\/\//i;

/* ---------- 工具 ---------- */

/** 去掉 script/style，只留正文标记 */
function bodyOnly(html) {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '');
}

/** 抽 class 属性里的全部类名 */
function classNames(html) {
  const out = [];
  for (const m of html.matchAll(/class\s*=\s*"([^"]*)"/g)) {
    for (const c of m[1].split(/\s+/)) if (c) out.push(c);
  }
  return out;
}

/** 是否为模板文件（模板允许出现占位与示例，跳过部分检查） */
function isTemplate(file) {
  return path.basename(file).startsWith('_模板');
}

/* ---------- 主检查 ---------- */

/**
 * @param {string} file
 * @returns {{file:string, hits:Array}}
 */
export function checkFile(file) {
  const html = fs.readFileSync(file, 'utf8');
  const tpl = isTemplate(file);
  const legacy = LEGACY.has(path.basename(file));
  const hits = [];

  /* 1. 骨架五件套 */
  const body = bodyOnly(html);
  const classes = new Set(classNames(body));
  for (const s of legacy ? [] : SKELETON) {
    if (!classes.has(s.cls)) {
      hits.push({
        rule: 'K',
        name: '缺骨架',
        word: s.name,
        why: `缺少 .${s.cls}（${s.hint}）。03 号规范第 2 节要求骨架五件套齐全。`,
      });
    }
  }

  /* 2. .lead 唯一且位于 header 内 */
  const leadCount = legacy ? 0 : classNames(body).filter((c) => c === 'lead').length;
  if (leadCount > 1) {
    hits.push({
      rule: 'L',
      name: '.lead 复用',
      word: `出现 ${leadCount} 次`,
      why: '.lead 只给页首导语。卡片内说明段落用普通 <p>，否则背景与边框会被一起继承。',
    });
  }
  const headerMatch = html.match(/<header[\s\S]*?<\/header>/i);
  if (leadCount === 1) {
    if (!headerMatch || !/class="[^"]*\blead\b/.test(headerMatch[0])) {
      hits.push({
        rule: 'L',
        name: '.lead 位置',
        word: '不在 <header> 内',
        why: '页首导语应紧接 h1 / .sub，放在 <header> 里。',
      });
    }
  }

  /* 3. 自造容器 */
  if (!tpl && !legacy) {
    for (const c of new Set(classNames(body))) {
      if (!CONTAINER_PATTERN.test(c)) continue;
      if (CONTAINER_ALLOW.has(c)) continue;
      hits.push({
        rule: 'C',
        name: '自造容器',
        word: c,
        why: '模板没有这个容器类。想表达并列项，用一张多行表格，不要发明卡片。',
      });
    }
  }

  /* 4. 无用交互与装饰（模板自带禁止示例说明，跳过） */
  for (const d of (tpl || legacy) ? [] : DECOR) {
    d.re.lastIndex = 0;
    const m = d.re.exec(body);
    if (m) {
      hits.push({
        rule: d.id,
        name: d.name,
        word: m[0].slice(0, 40),
        why: d.why,
      });
    }
  }

  /* 5. 外链 */
  if (EXTERNAL.test(html)) {
    hits.push({
      rule: 'X',
      name: '外链',
      word: 'http(s) 外部引用',
      why: '汇报材料必须零外链，不引 CDN、网络字体、图片外链。',
    });
  }

  /* 6. 必须有 title */
  if (!/<title>[^<]+<\/title>/i.test(html)) {
    hits.push({
      rule: 'T',
      name: '缺 title',
      word: '<title>',
      why: '浏览器标签与打印页眉取这个值。',
    });
  }

  return { file, hits };
}

/* ---------- CLI ---------- */

function main() {
  const args = process.argv.slice(2);
  if (!args.length) {
    console.error('用法：node tools/check-report-structure.mjs <html...>');
    process.exit(2);
  }

  let total = 0;
  for (const a of args) {
    if (!fs.existsSync(a)) {
      console.error(`文件不存在：${a}`);
      continue;
    }
    const { hits } = checkFile(a);
    const rel = path.relative(process.cwd(), a);
    if (!hits.length) {
      console.log(`check ok   ${rel}`);
      continue;
    }
    console.log(`\n违规 ${hits.length} 处  ${rel}`);
    for (const h of hits) {
      console.log(`  [${h.rule} ${h.name}] ${h.word}`);
      console.log(`     → ${h.why}`);
    }
    total += hits.length;
  }

  if (total) {
    console.log(`\n合计 ${total} 处违规。规则见 specs/03-汇报材料设计规范.md 第 2~4 节。`);
    process.exit(1);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) main();
