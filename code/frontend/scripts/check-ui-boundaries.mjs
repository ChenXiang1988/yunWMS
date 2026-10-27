#!/usr/bin/env node
/**
 * 前端架构边界检查（无第三方依赖，Node ESM）。
 *
 * 依据：
 *   - AGENTS.md「前端」「设计令牌」两节
 *   - .codebuddy/rules/frontend-ui/RULE.mdc「组件库」「令牌」两节
 *   - docs/frontend.md §1 包结构、§2 设计令牌
 *
 * 检查项：
 *   1. 每个应用只允许一套 UI 组件库，且必须是文档登记的那一套；
 *   2. packages/request、packages/tokens 不得依赖任何 UI 组件库；
 *   3. packages/ui-web 只允许 Web 端 UI 组件库；
 *   4. apps 与 packages 下 .vue/.ts/.scss 中不得出现色值字面量，
 *      唯一例外是令牌真源 packages/tokens/src/primitives.ts。
 *
 * 有违规时逐条打印「文件:行」并以 exit 1 结束；全部通过时打印摘要。
 */

import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const FRONTEND_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const APPS_DIR = join(FRONTEND_ROOT, 'apps')
const PACKAGES_DIR = join(FRONTEND_ROOT, 'packages')

/** Web 端 UI 组件库 */
const WEB_UI_LIBRARIES = ['ant-design-vue', 'element-plus', 'naive-ui', 'vuetify']
/** 触摸（PDA）端 UI 组件库 */
const PDA_UI_LIBRARIES = ['vant']
/** 需要识别的全部 UI 组件库 */
const UI_LIBRARIES = [...WEB_UI_LIBRARIES, ...PDA_UI_LIBRARIES]

/**
 * 应用 -> 允许的 UI 组件库。
 * 真源是 docs/frontend.md §1.1「端与应用」表；新增端须先更新该表，再登记到这里。
 */
const APP_UI_LIBRARY = {
  'network-console': ['ant-design-vue'],
  'owner-portal': ['ant-design-vue'],
}

/** 检查 UI 依赖的 package.json 字段 */
const DEPENDENCY_FIELDS = ['dependencies', 'devDependencies', 'peerDependencies']

/** 必须与任何 UI 组件库绝缘的共享包 */
const UI_FREE_PACKAGES = ['request', 'tokens']

/** 只允许 Web 端 UI 组件库的共享包 */
const WEB_ONLY_UI_PACKAGES = ['ui-web']

/** 参与色值扫描的文件后缀 */
const SOURCE_EXTENSIONS = ['.vue', '.ts', '.scss']

/** 不参与遍历的目录 */
const IGNORED_DIRECTORIES = new Set(['node_modules', 'dist', '.git', '.vite'])

/** 色值扫描的白名单：令牌唯一真源 */
const COLOR_EXEMPT_FILES = new Set([
  join(PACKAGES_DIR, 'tokens', 'src', 'primitives.ts'),
])

/**
 * 十六进制色值：3 / 4 / 6 / 8 位。
 * 长度不足或超出的 `#` 片段（含 Vue 插槽简写 `#actions`、挂载点 `#app`）不匹配。
 */
const HEX_COLOR_PATTERN = /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![0-9a-fA-F])/g
const RGB_PATTERN = /\brgba?\(/gi
const HSL_PATTERN = /\bhsla?\(/gi

/** @type {string[]} */
const failures = []

/**
 * @param {string} message
 */
function fail(message) {
  failures.push(message)
}

/**
 * @param {string} filePath
 * @returns {Record<string, unknown> | null}
 */
function readJsonFile(filePath) {
  try {
    return JSON.parse(readFileSync(filePath, 'utf8'))
  } catch (error) {
    fail(`${rel(filePath)}: 无法解析 package.json（${/** @type {Error} */ (error).message}）`)
    return null
  }
}

/**
 * @param {string} absolutePath
 * @returns {string}
 */
function rel(absolutePath) {
  return relative(FRONTEND_ROOT, absolutePath).split('\\').join('/')
}

/**
 * @param {string} absolutePath
 * @returns {string[]}
 */
function listDirectories(absolutePath) {
  return readdirSync(absolutePath, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && !IGNORED_DIRECTORIES.has(entry.name))
    .map((entry) => entry.name)
    .sort()
}

/**
 * 取出某个 package.json 中声明的 UI 组件库。
 * @param {Record<string, unknown>} pkg
 * @returns {{ field: string, library: string }[]}
 */
function collectUiLibraries(pkg) {
  /** @type {{ field: string, library: string }[]} */
  const found = []
  for (const field of DEPENDENCY_FIELDS) {
    const section = pkg[field]
    if (!section || typeof section !== 'object') continue
    for (const library of Object.keys(/** @type {Record<string, unknown>} */ (section))) {
      if (UI_LIBRARIES.includes(library)) found.push({ field, library })
    }
  }
  return found
}

/**
 * @param {string} appName
 * @param {Record<string, unknown>} pkg
 */
function checkAppUiLibrary(appName, pkg) {
  const declared = collectUiLibraries(pkg)
  const libraries = [...new Set(declared.map((item) => item.library))]
  const allowed = APP_UI_LIBRARY[appName]

  if (!allowed) {
    fail(
      `apps/${appName}/package.json: 应用未登记 UI 组件库归属，` +
        '请先更新 docs/frontend.md §1.1「端与应用」表再登记到 scripts/check-ui-boundaries.mjs',
    )
    return
  }

  if (libraries.length > 1) {
    fail(
      `apps/${appName}/package.json: 应用出现多套 UI 组件库（${libraries.join('、')}），` +
        '一个应用只允许一套',
    )
  }

  for (const library of libraries) {
    if (!allowed.includes(library)) {
      fail(
        `apps/${appName}/package.json: 应用使用了未登记的 UI 组件库 ${library}，` +
          `只允许 ${allowed.join('、')}`,
      )
    }
  }
}

/**
 * @param {string} packageName
 * @param {Record<string, unknown>} pkg
 */
function checkSharedPackageUiLibrary(packageName, pkg) {
  const declared = collectUiLibraries(pkg)

  if (UI_FREE_PACKAGES.includes(packageName)) {
    for (const item of declared) {
      fail(
        `packages/${packageName}/package.json: 共享包不得依赖任何 UI 组件库，` +
          `却在 ${item.field} 出现 ${item.library}`,
      )
    }
    return
  }

  if (WEB_ONLY_UI_PACKAGES.includes(packageName)) {
    for (const item of declared) {
      if (!WEB_UI_LIBRARIES.includes(item.library)) {
        fail(
          `packages/${packageName}/package.json: 该包只允许 Web 端 UI 组件库，` +
            `却在 ${item.field} 出现 ${item.library}`,
        )
      }
    }
  }
}

/**
 * 递归收集待扫描的源文件。
 * @param {string} directory
 * @param {string[]} collected
 */
function collectSourceFiles(directory, collected) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (IGNORED_DIRECTORIES.has(entry.name)) continue
      collectSourceFiles(join(directory, entry.name), collected)
      continue
    }
    if (!entry.isFile()) continue
    if (!SOURCE_EXTENSIONS.some((extension) => entry.name.endsWith(extension))) continue
    collected.push(join(directory, entry.name))
  }
}

/**
 * 扫描单个文件中的色值字面量。
 * @param {string} filePath
 */
function checkColorLiterals(filePath) {
  if (COLOR_EXEMPT_FILES.has(filePath)) return

  const lines = readFileSync(filePath, 'utf8').split(/\r?\n/)
  lines.forEach((line, index) => {
    const hit = firstColorMatch(line)
    if (hit) {
      fail(`${rel(filePath)}:${index + 1}: 出现色值字面量 ${hit}，颜色必须取设计令牌`)
    }
  })
}

/**
 * @param {string} line
 * @returns {string | null}
 */
function firstColorMatch(line) {
  for (const pattern of [HEX_COLOR_PATTERN, RGB_PATTERN, HSL_PATTERN]) {
    pattern.lastIndex = 0
    const match = pattern.exec(line)
    if (match) return match[0]
  }
  return null
}

function main() {
  const appNames = listDirectories(APPS_DIR)
  const packageNames = listDirectories(PACKAGES_DIR)

  for (const appName of appNames) {
    const pkg = readJsonFile(join(APPS_DIR, appName, 'package.json'))
    if (pkg) checkAppUiLibrary(appName, pkg)
  }

  for (const packageName of packageNames) {
    const pkg = readJsonFile(join(PACKAGES_DIR, packageName, 'package.json'))
    if (pkg) checkSharedPackageUiLibrary(packageName, pkg)
  }

  /** @type {string[]} */
  const sourceFiles = []
  collectSourceFiles(APPS_DIR, sourceFiles)
  collectSourceFiles(PACKAGES_DIR, sourceFiles)
  sourceFiles.sort()

  for (const filePath of sourceFiles) {
    checkColorLiterals(filePath)
  }

  if (failures.length > 0) {
    console.error(`✖ 前端边界检查未通过，共 ${failures.length} 处违规：`)
    for (const message of failures) console.error(`  - ${message}`)
    process.exitCode = 1
    return
  }

  console.log('✔ 前端边界检查通过')
  console.log(`  · 应用 ${appNames.length} 个：${appNames.join('、')}`)
  console.log(`  · 共享包 ${packageNames.length} 个：${packageNames.join('、')}`)
  console.log(
    `  · 扫描源文件 ${sourceFiles.length} 个（${SOURCE_EXTENSIONS.join(' / ')}），` +
      `色值白名单 ${COLOR_EXEMPT_FILES.size} 个`,
  )
  console.log(`  · 已识别 UI 组件库：${UI_LIBRARIES.join('、')}`)
}

main()
