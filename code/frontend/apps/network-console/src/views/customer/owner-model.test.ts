import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_PAGE_SIZE,
  MAX_PAGE_SIZE,
  OWNER_STATUS_OPTIONS,
  OWNER_STATUS_TEXT,
  PLACEHOLDER,
  formatIsoDateTime,
  normalizeOwnerQuery,
  ownerStatusText,
  ownerTypeLabel
} from './owner-model.ts'

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

test('客户类型标签：取字典 label，未知类型回退原值，空值显示占位符', () => {
  const labels = new Map([
    ['BRAND', '品牌方'],
    ['FORWARDER', '货代同行']
  ])

  assert.equal(ownerTypeLabel('BRAND', labels), '品牌方')
  // 字典里没有（如停用项或新增类型）时回退原值，不显示空白
  assert.equal(ownerTypeLabel('CARRIER', labels), 'CARRIER')
  // 字典整批加载失败时同样回退原值
  assert.equal(ownerTypeLabel('BRAND', new Map()), 'BRAND')
  assert.equal(ownerTypeLabel('', labels), PLACEHOLDER)
})

test('ISO 时间格式化：无效值返回占位符', () => {
  assert.equal(formatIsoDateTime(''), PLACEHOLDER)
  assert.equal(formatIsoDateTime(null), PLACEHOLDER)
  assert.equal(formatIsoDateTime(undefined), PLACEHOLDER)
  assert.equal(formatIsoDateTime('not-a-time'), PLACEHOLDER)
  assert.equal(formatIsoDateTime('2026-13-45T00:00:00Z'), PLACEHOLDER)
})

test('ISO 时间格式化：合法值输出到分钟且与本地时区无关地指向同一时刻', () => {
  const text = formatIsoDateTime('2026-10-24T08:30:00Z')
  assert.match(text, /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
  // 同一时刻换一种 ISO 写法（带偏移量）必须得到同一结果
  assert.equal(formatIsoDateTime('2026-10-24T16:30:00+08:00'), text)
})

test('查询参数归一化：空字符串与纯空白去空', () => {
  assert.deepEqual(normalizeOwnerQuery({ keyword: '   ', ownerType: '' }), {
    pageNo: 1,
    pageSize: DEFAULT_PAGE_SIZE
  })
})

test('查询参数归一化：保留有效筛选并裁掉两端空白', () => {
  assert.deepEqual(
    normalizeOwnerQuery({
      keyword: ' OWNER0001 ',
      ownerType: ' BRAND ',
      status: 'ACTIVE',
      pageNo: 2,
      pageSize: 50
    }),
    {
      pageNo: 2,
      pageSize: 50,
      keyword: 'OWNER0001',
      ownerType: 'BRAND',
      status: 'ACTIVE'
    }
  )
})

test('查询参数归一化：页码至少为 1', () => {
  assert.equal(normalizeOwnerQuery({ pageNo: 0 }).pageNo, 1)
  assert.equal(normalizeOwnerQuery({ pageNo: -3 }).pageNo, 1)
  assert.equal(normalizeOwnerQuery({ pageNo: 7 }).pageNo, 7)
})

test('查询参数归一化：每页条数落在后端允许区间内', () => {
  assert.equal(normalizeOwnerQuery({ pageSize: 5000 }).pageSize, MAX_PAGE_SIZE)
  assert.equal(normalizeOwnerQuery({ pageSize: 0 }).pageSize, DEFAULT_PAGE_SIZE)
})
