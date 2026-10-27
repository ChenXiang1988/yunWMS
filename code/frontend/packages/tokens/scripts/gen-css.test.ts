import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { listPrimitiveVars, renderPrimitiveCss } from './emit.ts'
import { primitives } from '../src/primitives.ts'

const here = dirname(fileURLToPath(import.meta.url))
const css = readFileSync(resolve(here, '../src/primitive.css'), 'utf8')

test('生成物与源码渲染结果完全一致（未被手改）', () => {
  assert.equal(css, renderPrimitiveCss())
})

test('关键令牌取值正确', () => {
  assert.match(css, new RegExp(`--blue-500:\\s*${primitives.color.blue['500']};`))
  assert.match(css, /--space-4:\s*16px;/)
  assert.match(css, /--space-0:\s*0;/)
  assert.match(css, /--weight-semibold:\s*600;/)
})

test('变量数量与 primitives 声明数量一致', () => {
  const emitted = css.match(/--[a-z0-9-]+(?=\s*:)/g) ?? []
  assert.equal(emitted.length, listPrimitiveVars().length)
})
