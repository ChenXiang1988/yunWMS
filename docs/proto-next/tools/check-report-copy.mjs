/**
 * 汇报材料文案校验。
 *
 * 背景：设备管理汇报返工 20 轮，其中 17 轮不是改内容，而是改表述——
 * 溯源式辩解、出处引用、元话语、自造词、内部排期语言、数量盘点。
 * 这些规则写进 05 号规范后仍然复发，说明「写在文档里」不构成约束。
 * 本脚本把规范第 5.1 节转成可执行检查，构建前跑一次即可拦截。
 *
 * 用法：
 *   node tools/check-report-copy.mjs reports/设备管理思路.html
 *   node tools/check-report-copy.mjs reports/*.html
 *
 * 退出码：0 通过，1 有违规。
 */

import fs from 'node:fs';
import path from 'node:path';

/* ---------- 规则表 ---------- */

/**
 * 每条规则：{ id, name, re, why, allow }
 * allow 是白名单：命中但仍属技术事实的上下文，用正则匹配整段后豁免。
 */
const RULES = [
  {
    id: 'R1',
    name: '溯源式辩解',
    re: /早期(认为|设计|只有|的做法)|以前认为|旧假设|过去以为|曾经(以为|认为)|绕不开|早就(不必|不用)/g,
    why: '讲我们过去怎么想错，评审不关心历史。改为直接陈述现在的结论。',
  },
  {
    id: 'R2',
    name: '出处引用',
    re: /依据\s*(PRD|BND|DOC-|FR-)|已由用户裁决|原话[:：]|原文[:：]|裁决日|BND-\d+|FR-[A-Z]+-\d+|DOC-[A-Z]+-\d+/g,
    why: '讲结论从哪来，不是结论本身。领导不关心文档编号与裁决日期。',
  },
  {
    id: 'R3',
    name: '元话语',
    re: /这一节(讲|说)|前面讲的是|本节只|接下来(讲|说)|不再重复|如上所述|综上所述/g,
    why: '作者对读者的编辑交代，正文不需要。',
  },
  {
    id: 'R4',
    name: '内部排期语言',
    re: /本期不做|本期出界|不在本期|下期再做|暂不实现|本期范围外/g,
    why: '汇报讲要做什么，不讲不做什么。边界问题等问再答。',
  },
  {
    id: 'R5',
    name: '自造词',
    re: /纳管|宿主\s*PC|抖动(?!测试)|工位电脑|待处置(?=页签|状态)/g,
    why: '权威源里没有的词。改用 PRD 既有说法：接入管理、所在 PC、重试结果、PC 主机。',
  },
  {
    id: 'R6',
    name: '辩解式推导',
    re: /只会(引出|产生|导致)|如果不(这么|这样).{0,12}就|否则(会|就).{0,10}(乱|崩|错)|正因如此|这才(是|让)/g,
    why: '用「不用就会很糟」反证，属辩解口吻。改为直接陈述做法与边界。',
  },
  {
    id: 'R7',
    name: '数量盘点',
    re: /(共|合计|总计)\s*\d+\s*(台|个|条)\s*[·，,]?\s*(PDA|手机|PC|打印机)/g,
    why: '思路讲设计不讲台账盘点。数量会过时，且会限制思路。计数放原型或 PRD。',
  },
];

/* ---------- 技术事实豁免 ---------- */

/**
 * 命中但属于客观技术约束的，不算违规。
 * 判据：讲的是设备/系统的客观限制，不是我们过去的判断或文档出处。
 */
const TECHNICAL_FACTS = [
  /Android\s*10\s*起.{0,40}(读不到|无法)/,
  /第三方\s*App\s*(无法|不能)/,
  /浏览器\s*(拿不到|调不动)/,
  /一台(打印机|机)同一时刻只装一种介质/,
  /签名级权限/,
  /不能独立联网/,
  /没有(账号|网络能力)/,
];

const isTechnicalFact = (line) => TECHNICAL_FACTS.some((re) => re.test(line));

/* ---------- 解析 ---------- */

/** 去掉 script/style，只留可见正文 */
function visibleText(html) {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<svg[\s\S]*?<\/svg>/gi, '');
}

/** 按行切分并保留行号，便于定位 */
function toLines(html) {
  const text = visibleText(html)
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|li|td|th|h[1-6]|tr)>/gi, '\n')
    .replace(/<[^>]+>/g, '');
  return text.split('\n').map((s) => s.replace(/\s+/g, ' ').trim());
}

/* ---------- 主检查 ---------- */

/**
 * @param {string} file 文件路径
 * @returns {{file:string, hits:Array}}
 */
export function checkFile(file) {
  const html = fs.readFileSync(file, 'utf8');
  const lines = toLines(html);
  const hits = [];

  lines.forEach((line, idx) => {
    if (!line || line.length < 6) return;
    if (isTechnicalFact(line)) return;

    for (const rule of RULES) {
      rule.re.lastIndex = 0;
      const m = rule.re.exec(line);
      if (m) {
        hits.push({
          line: idx + 1,
          rule: rule.id,
          name: rule.name,
          word: m[0],
          why: rule.why,
          text: line.slice(0, 80),
        });
      }
    }
  });

  return { file, hits };
}

/** CLI 入口 */
function main() {
  const args = process.argv.slice(2);
  if (!args.length) {
    console.error('用法：node tools/check-report-copy.mjs <html...>');
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
      console.log(`  [${h.rule} ${h.name}] 第 ${h.line} 行  「${h.word}」`);
      console.log(`     ${h.text}`);
      console.log(`     → ${h.why}`);
    }
    total += hits.length;
  }

  if (total) {
    console.log(`\n合计 ${total} 处违规。规则见 specs/05-PC页面构筑约定.md 第 5.1 节。`);
    process.exit(1);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) main();
