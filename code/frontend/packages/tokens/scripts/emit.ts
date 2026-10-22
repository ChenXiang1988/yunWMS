/**
 * 把 primitives.ts 渲染成 CSS。
 * 只做渲染与汇总，不写文件（写文件在 gen-css.ts，测试复用本模块）。
 */
import { primitives } from '../src/primitives.ts'

export interface Entry {
  group: string
  name: string
  value: string
}

const toPx = (value: number): string => (value === 0 ? '0' : `${value}px`)
const toKebab = (value: string): string => value.replace(/[A-Z]/g, (m) => `-${m.toLowerCase()}`)

const COLOR_LABELS: Record<string, string> = {
  blue: '主色',
  gray: '中性',
  green: '功能色 · 成功',
  orange: '功能色 · 警告',
  red: '功能色 · 危险',
  cyan: '功能色 · 信息'
}

/** 输出顺序固定，保证生成结果稳定可比 */
export function collectEntries(): Entry[] {
  const entries: Entry[] = []

  for (const [scale, steps] of Object.entries(primitives.color)) {
    const group = COLOR_LABELS[scale] ?? scale
    for (const [step, value] of Object.entries(steps)) {
      entries.push({ group, name: `--${scale}-${step}`, value })
    }
  }

  for (const [key, value] of Object.entries(primitives.font.family)) {
    entries.push({ group: '字体族', name: `--font-${key}`, value })
  }
  for (const [key, value] of Object.entries(primitives.font.size)) {
    entries.push({ group: '字号', name: `--text-${key}`, value: toPx(value) })
  }
  for (const [key, value] of Object.entries(primitives.font.leading)) {
    entries.push({ group: '行高', name: `--leading-${key}`, value: toPx(value) })
  }
  for (const [key, value] of Object.entries(primitives.font.weight)) {
    entries.push({ group: '字重', name: `--weight-${key}`, value: String(value) })
  }
  for (const [key, value] of Object.entries(primitives.space)) {
    entries.push({ group: '间距：4px 网格', name: `--space-${key}`, value: toPx(value) })
  }
  for (const [key, value] of Object.entries(primitives.radius)) {
    entries.push({ group: '圆角', name: `--radius-${key}`, value: toPx(value) })
  }
  for (const [key, value] of Object.entries(primitives.shadow)) {
    entries.push({ group: '阴影', name: `--shadow-${key}`, value })
  }
  entries.push({ group: '遮罩', name: '--overlay-mask', value: primitives.overlay.mask })
  for (const [key, value] of Object.entries(primitives.z)) {
    entries.push({ group: '层级', name: `--z-${toKebab(key)}`, value: String(value) })
  }
  for (const [key, value] of Object.entries(primitives.motion)) {
    entries.push({ group: '动效', name: `--${toKebab(key)}`, value })
  }

  return entries
}

export function listPrimitiveVars(): string[] {
  return collectEntries().map((entry) => entry.name)
}

const HEADER = `/* ==========================================================================
   由 scripts/gen-css.ts 生成，禁止手改
   源：src/primitives.ts
   改值请改源文件后运行 pnpm --filter @yunwms/tokens gen
   ========================================================================== */`

export function renderPrimitiveCss(): string {
  const lines: string[] = [HEADER, '', ':root {']
  let currentGroup = ''

  for (const entry of collectEntries()) {
    if (entry.group !== currentGroup) {
      if (currentGroup !== '') lines.push('')
      lines.push(`  /* ${entry.group} */`)
      currentGroup = entry.group
    }
    lines.push(`  ${entry.name}: ${entry.value};`)
  }

  lines.push('}')
  return `${lines.join('\n')}\n`
}
