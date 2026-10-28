import { test } from 'node:test'
import assert from 'node:assert/strict'
import type { AuditLogItem } from '@/api/owner'
import {
  auditRowView,
  auditRowViews,
  buildOpenAccountPayload,
  canOpenOwnerAccount,
  emptyAccountForm
} from './account-model.ts'
import { PLACEHOLDER, displayText, formatIsoDateTime } from './owner-model.ts'

/** 审计记录样例：各用例只覆盖自己关心的字段，其余取这份默认值 */
const BASE_LOG: AuditLogItem = {
  id: '1',
  bizType: 'OWNER',
  bizId: 'OWNER0001',
  action: 'UPDATE',
  operatorId: '7',
  operatorName: '张三',
  operatorIp: '10.0.0.1',
  sourcePortal: 'CONSOLE',
  summary: '修改货主档案',
  beforeValue: null,
  afterValue: null,
  createdAt: '2026-10-28T02:30:00Z'
}

test('开号表单归一：纯空白展示名不构造请求体，初始密码不去空白', () => {
  assert.deepEqual(emptyAccountForm(), { displayName: '', initialPassword: '' })

  // 后端把只填空白按未填处理：这里直接不构造请求体，不发一个必然被拒的请求
  assert.equal(buildOpenAccountPayload({ displayName: '   ', initialPassword: 'Abc12345' }), null)

  const payload = buildOpenAccountPayload({
    displayName: ' 王五 ',
    initialPassword: ' Abc12345 '
  })
  assert.equal(payload?.displayName, '王五')
  // 空格也是密码的一部分：裁掉会让用户设的密码与登录时输入的对不上
  // 密码的其它改写一律不做，长度与字符组合由后端判定
  assert.equal(payload?.initialPassword, ' Abc12345 ')
})

test('开号入口：只有合作中且本次会话内没开过号才可用', () => {
  assert.equal(canOpenOwnerAccount('ACTIVE', null), true)
  // 开号是一次性动作：开出账号后入口不可再点，后端对重复开号返回 40900
  assert.equal(canOpenOwnerAccount('ACTIVE', 'OWNER0001'), false)
  // 只有合作中可以开号，其余四态后端一律 40900
  for (const status of ['DRAFT', 'POTENTIAL', 'SUSPENDED', 'TERMINATED'] as const) {
    assert.equal(canOpenOwnerAccount(status, null), false)
  }
})

test('审计行展示：缺失的时间、摘要与操作人落到占位符，动作编码取中文名', () => {
  const row = auditRowView({
    ...BASE_LOG,
    summary: '   ',
    operatorName: null,
    createdAt: ''
  })

  assert.equal(row.action, '修改')
  // 缺失或纯空白一律显示占位符：模板里不会出现空白单元格与 undefined
  assert.equal(displayText(row.summary), PLACEHOLDER)
  assert.equal(displayText(row.operator), PLACEHOLDER)
  assert.equal(formatIsoDateTime(row.createdAt), PLACEHOLDER)
  // 没有快照的展开区也是占位符，而不是 null
  assert.equal(row.hasSnapshot, false)
  assert.equal(displayText(row.beforeText), PLACEHOLDER)
  assert.equal(displayText(row.afterText), PLACEHOLDER)

  // 有值时时间照常格式化，不受占位分支影响
  assert.match(formatIsoDateTime(BASE_LOG.createdAt), /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
})

test('审计行展示：不认识的动作用占位符，不回退英文枚举名', () => {
  const row = auditRowView({ ...BASE_LOG, action: 'UNKNOWN_ACTION' })

  assert.equal(row.action, '')
  assert.equal(displayText(row.action), PLACEHOLDER)
})

test('审计行快照：原样展开成多行 JSON，并标记为可展开', () => {
  const row = auditRowView({
    ...BASE_LOG,
    beforeValue: { status: 'DRAFT' },
    afterValue: { status: 'ACTIVE' }
  })

  assert.equal(row.hasSnapshot, true)
  assert.equal(row.beforeText, JSON.stringify({ status: 'DRAFT' }, null, 2))
  // 只读展示，不改写后端给的快照内容
  assert.deepEqual(JSON.parse(row.afterText), { status: 'ACTIVE' })
})

test('审计列表：投影保持后端顺序（按操作时间倒序），前端不重排', () => {
  const rows = auditRowViews([
    { ...BASE_LOG, id: '3', createdAt: '2026-10-28T10:00:00Z' },
    { ...BASE_LOG, id: '1', createdAt: '2026-10-27T10:00:00Z' },
    { ...BASE_LOG, id: '2', createdAt: '2026-10-26T10:00:00Z' }
  ])

  assert.deepEqual(
    rows.map((row) => row.key),
    ['3', '1', '2']
  )
})
