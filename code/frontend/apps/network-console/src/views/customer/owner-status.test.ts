import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  OWNER_STATUS_OPTIONS,
  OWNER_STATUS_TEXT,
  allowedStatusTargets,
  canDeleteOwner,
  isOwnerWritable,
  ownerStatusText
} from './owner-status.ts'

test('五态文案覆盖后端全部状态', () => {
  assert.deepEqual(
    OWNER_STATUS_OPTIONS.map((option) => option.value),
    ['DRAFT', 'POTENTIAL', 'ACTIVE', 'SUSPENDED', 'TERMINATED']
  )
  assert.deepEqual(
    OWNER_STATUS_OPTIONS.map((option) => option.label),
    ['草稿', '潜在', '合作中', '暂停', '终止']
  )
  assert.equal(Object.keys(OWNER_STATUS_TEXT).length, 5)
})

test('状态文案：已知状态取中文，未知状态回退原值', () => {
  assert.equal(ownerStatusText('ACTIVE'), '合作中')
  assert.equal(ownerStatusText('TERMINATED'), '终止')
  assert.equal(ownerStatusText('ARCHIVED'), 'ARCHIVED')
})

test('状态流转：与后端允许的流向一致', () => {
  assert.deepEqual(allowedStatusTargets('DRAFT'), ['POTENTIAL', 'ACTIVE'])
  assert.deepEqual(allowedStatusTargets('POTENTIAL'), ['ACTIVE', 'TERMINATED'])
  assert.deepEqual(allowedStatusTargets('ACTIVE'), ['SUSPENDED', 'TERMINATED'])
  assert.deepEqual(allowedStatusTargets('SUSPENDED'), ['ACTIVE', 'TERMINATED'])
  // 终止是终态
  assert.deepEqual(allowedStatusTargets('TERMINATED'), [])
})

test('状态流转：同状态不作为目标列出', () => {
  for (const status of Object.keys(OWNER_STATUS_TEXT)) {
    const targets: string[] = allowedStatusTargets(status as keyof typeof OWNER_STATUS_TEXT)
    assert.equal(targets.includes(status), false, `${status} 不应出现在自己的目标里`)
  }
})

test('可删除：只有草稿', () => {
  assert.equal(canDeleteOwner('DRAFT'), true)
  for (const status of ['POTENTIAL', 'ACTIVE', 'SUSPENDED', 'TERMINATED']) {
    assert.equal(canDeleteOwner(status), false, `${status} 不该可删除`)
  }
})

test('可写：终止态置灰，其余状态可写', () => {
  assert.equal(isOwnerWritable('TERMINATED'), false)
  for (const status of ['DRAFT', 'POTENTIAL', 'ACTIVE', 'SUSPENDED']) {
    assert.equal(isOwnerWritable(status), true, `${status} 应可写`)
  }
})
