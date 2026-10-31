/**
 * 开工前环境核查。
 *
 * 背景：设备管理汇报返工 20 轮，根因不是能力不足，是没先看现场——
 * 没读 reports/ 已有什么（结果写了重复的第二份汇报）、
 * 没查权威源里有没有对应词（结果自造了「纳管」「宿主 PC」）、
 * 没确认载体形式（长文档改成 PPT 又改回来）。
 *
 * 本脚本在动手前跑一次，把「现场有什么、权威源怎么说」一次列清楚。
 * 它不判断对错，只负责让你看见。
 *
 * 用法：
 *   node tools/preflight.mjs                 # 全量
 *   node tools/preflight.mjs --reports       # 只看汇报材料
 *   node tools/preflight.mjs --terms 纳管     # 查某个词是否在权威源里
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/* 路径一律按本文件位置定位，不依赖 process.cwd()：
   脚本按本文件位置定位路径，不依赖 process.cwd()
   用 cwd 拼相对路径会在换目录运行时落空，existsSync 过滤后校验 0 个文件却报「通过」。 */
const HERE = path.dirname(fileURLToPath(import.meta.url));
const DOCS = path.resolve(HERE, '..');  // 本目录即自包含根
const ROOT = DOCS; // 本目录即自包含根

/* ---------- 现场盘点 ---------- */

function listDir(dir, ext) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter((f) => !ext || f.endsWith(ext))
    .map((f) => {
      const p = path.join(dir, f);
      const st = fs.statSync(p);
      return { name: f, size: st.size, mtime: st.mtime };
    })
    .sort((a, b) => b.mtime - a.mtime);
}

function reportSection() {
  const out = [];
  out.push('\n=== 一、现有汇报材料（动手前先看，避免写重复的） ===');
  const reports = listDir(path.join(DOCS, 'reports')).filter((r) => !r.name.startsWith('_模板'));
  if (!reports.length) out.push('  （无）');
  for (const r of reports) {
    const kb = (r.size / 1024).toFixed(0);
    const t = r.mtime.toISOString().slice(0, 16).replace('T', ' ');
    out.push(`  ${r.name.padEnd(42)} ${kb.padStart(6)} KB  ${t}`);
  }

  out.push('\n=== 一之二、周报（按周分目录） ===');
  const weekly = path.join(DOCS, '周报');
  if (!fs.existsSync(weekly)) { out.push('  （无）'); }
  else {
    for (const d of fs.readdirSync(weekly).filter((n) => fs.statSync(path.join(weekly, n)).isDirectory())) {
      const files = listDir(path.join(weekly, d));
      out.push(`  ${d}/  （${files.length} 个文件：${files.map((f) => f.name).join('、')}）`);
    }
  }

  out.push('\n=== 二、PRD（结论与规则的权威来源） ===');
  const prds = listDir(path.join(DOCS, 'PRD'));
  for (const p of prds) {
    const kb = (p.size / 1024).toFixed(0);
    out.push(`  ${p.name.padEnd(42)} ${kb.padStart(6)} KB`);
  }

  out.push('\n=== 三、规范（交付前必读） ===');
  const specs = listDir(path.join(DOCS, 'specs'));
  for (const s of specs) out.push(`  ${s.name}`);

  return out.join('\n');
}

/* ---------- 权威源词表 ---------- */

/**
 * 从 PRD 与边界文档中抽取术语，用来判断一个词是不是自造。
 * 只抽「加粗词」「引号词」与表格首列，这三处最可能是正式术语。
 */
function buildGlossary() {
  const files = [
    ...listDir(path.join(DOCS, 'PRD')).map((f) => path.join(DOCS, 'PRD', f.name)),
    path.join(DOCS, '产品边界.md'),
    path.join(DOCS, '硬件与执行单元的分工.md'),
  ].filter((f) => fs.existsSync(f));

  const terms = new Set();
  for (const f of files) {
    const src = fs.readFileSync(f, 'utf8');
    /* 加粗 **词** */
    for (const m of src.matchAll(/\*\*([^*\n]{2,12})\*\*/g)) terms.add(m[1].trim());
    /* 反引号 `词` */
    for (const m of src.matchAll(/`([^`\n]{2,16})`/g)) terms.add(m[1].trim());
    /* 表格首列 | 词 | */
    for (const m of src.matchAll(/^\|\s*([^|\n]{2,14})\s*\|/gm)) terms.add(m[1].trim());
  }
  return terms;
}

/**
 * 查一个词在权威源里有没有出处。
 * @param {string} word
 * @param {Set<string>} glossary
 * @returns {{found:boolean, where:string[]}}
 */
function checkTerm(word, glossary) {
  const where = [];
  /* 这里的 rel 已是相对 DOCS 的路径；不要再用 path.join(DOCS, f) 探一次存在性，
     否则除根目录两份外全部被过滤，PRD 里的词会被误判为自造词。 */
  const files = [
    ...listDir(path.join(DOCS, 'PRD')).map((f) => path.join('PRD', f.name)),
    '产品边界.md', '硬件与执行单元的分工.md',
  ].filter((f) => fs.existsSync(path.join(DOCS, f)));

  for (const rel of files) {
    const src = fs.readFileSync(path.join(DOCS, rel), 'utf8');
    if (src.includes(word)) where.push(rel);
  }

  /* 也看 glossary 里的近似词 */
  const similar = [...glossary].filter((t) => t !== word && (t.includes(word) || word.includes(t))).slice(0, 5);
  return { found: where.length > 0, where, similar };
}

function termSection(words) {
  const glossary = buildGlossary();
  const out = ['\n=== 四、术语核查 ==='];
  out.push(`  权威源共抽到 ${glossary.size} 个术语`);

  for (const w of words) {
    const r = checkTerm(w, glossary);
    if (r.found) {
      out.push(`  ✓ 「${w}」 出处：${r.where.join('、')}`);
    } else {
      out.push(`  ✗ 「${w}」 权威源里没有 → 很可能是自造词`);
      if (r.similar.length) out.push(`     近似说法：${r.similar.join('、')}`);
    }
  }
  return out.join('\n');
}

/* ---------- 规范硬约束 ---------- */

function constraintSection() {
  return [
    '\n=== 五、开工前必须确认的四件事 ===',
    '  1. 载体形式：长文档 / 分页 / 汇报 HTML？先问，不要默认。',
    '  2. 与已有材料的关系：新增还是并入？先看第一节的清单。',
    '  3. 数据口径：数量、状态取值以 PRD 为准，不凭印象。',
    '  4. 完成后必跑：',
    '       node tools/check-report-copy.mjs reports/*.html',
    '       cd pc && node src/build.mjs --check',
  ].join('\n');
}

/* ---------- CLI ---------- */

function main() {
  const argv = process.argv.slice(2);
  const termsIdx = argv.indexOf('--terms');
  const terms = termsIdx >= 0 ? argv.slice(termsIdx + 1).filter((a) => !a.startsWith('--')) : [];

  console.log('设备管理汇报 · 开工前核查');
  console.log('工作区：' + ROOT);
  console.log('时间：' + new Date().toISOString().slice(0, 19).replace('T', ' '));

  if (!argv.includes('--reports')) console.log(reportSection());
  if (terms.length) console.log(termSection(terms));
  else console.log('\n=== 四、术语核查 ===\n  （用 --terms 词1 词2 指定要查的词）');
  console.log(constraintSection());
}

main();
