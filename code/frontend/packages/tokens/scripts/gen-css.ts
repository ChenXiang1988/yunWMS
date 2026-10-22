/**
 * 由 primitives.ts 生成 src/primitive.css。
 * 生成物禁止手改；改值请改 primitives.ts 后重新运行。
 */
import { writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { renderPrimitiveCss } from './emit.ts'

const here = dirname(fileURLToPath(import.meta.url))
const target = resolve(here, '../src/primitive.css')

writeFileSync(target, renderPrimitiveCss(), 'utf8')
console.log(`[tokens] 已生成 ${target}`)
