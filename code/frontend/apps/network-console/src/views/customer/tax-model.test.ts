import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  buildSetDefaultPayload,
  buildTaxPayload,
  canSetDefault,
  emptyTaxForm,
  taxFormFrom,
  taxTypeLabel
} from './tax-model.ts'

test('类型标签：取字典 label，字典里没有该值时回退原值', () => {
  const labels = new Map([
    ['CN_USCC', '统一社会信用代码'],
    ['US_STATE_SALES', '州销售税号']
  ])

  assert.equal(taxTypeLabel('CN_USCC', labels), '统一社会信用代码')
  // 停用项或新增类型：字典里没有就回退原值，不显示空白
  assert.equal(taxTypeLabel('US_EIN', labels), 'US_EIN')
  // 字典整批加载失败时同样回退原值
  assert.equal(taxTypeLabel('VAT', new Map()), 'VAT')
})

test('表单归一：空白州省转 null，必填项去两端空白，isDefault 缺省按 false 处理', () => {
  const payload = buildTaxPayload({
    country: ' US ',
    province: '   ',
    taxType: ' US_STATE_SALES ',
    taxNumber: ' 941234567 ',
    // 未传，等价于 Checkbox 没勾选
  })

  assert.equal(payload.country, 'US')
  assert.equal(payload.taxType, 'US_STATE_SALES')
  // 后端以 null 表示清空，空串会被当成「有值但为空」
  assert.equal(payload.province, null)
  // 契约写明后端「按原样保存，不去首尾空格」，清理责任在调用方，故提交前去两端空白
  assert.equal(payload.taxNumber, '941234567')
  // 编辑是全量替换：省略 isDefault 等于提交 false，所以缺省必须显式归一成 false
  assert.equal(payload.isDefault, false)
  assert.equal(emptyTaxForm().isDefault, false)
})

test('表单归一：已填的州省保留原值，勾选的默认标记照实提交', () => {
  const payload = buildTaxPayload({
    country: 'US',
    province: ' US-CA ',
    taxType: 'US_STATE_SALES',
    taxNumber: 'SR KH 123-456',
    isDefault: true
  })

  assert.equal(payload.province, 'US-CA')
  assert.equal(payload.taxNumber, 'SR KH 123-456')
  assert.equal(payload.isDefault, true)
})

test('表单回填：后端 null 州省显示为空串，回填后直接提交回到同一份值', () => {
  const form = taxFormFrom({
    id: '7',
    country: 'CN',
    province: null,
    taxType: 'CN_USCC',
    taxNumber: '91330100MA2AB1CD3E',
    isDefault: true
  })

  assert.equal(form.province, '')
  assert.equal(form.isDefault, true)
  // 空串与 null 的差异不能在回填→提交的往返里把州省写坏，默认标记也不能丢
  assert.deepEqual(buildTaxPayload(form), {
    country: 'CN',
    province: null,
    taxType: 'CN_USCC',
    taxNumber: '91330100MA2AB1CD3E',
    isDefault: true
  })
})

test('编辑态：国家与类型只读，改州省与税号后提交的仍是记录自身的国家与类型', () => {
  // 编辑表单由列表行回填，国家与类型两项在界面上置灰，用户改不动
  const form = taxFormFrom({
    id: '8',
    country: 'US',
    province: 'US-CA',
    taxType: 'US_STATE_SALES',
    taxNumber: 'SR KH 000-001',
    isDefault: false
  })
  form.province = 'US-TX'
  form.taxNumber = 'SR KH 000-002'
  form.isDefault = true

  assert.deepEqual(buildTaxPayload(form), {
    // 身份字段保持记录原值：编辑接口本就不认这两个字段，前端也不给它改的机会
    country: 'US',
    taxType: 'US_STATE_SALES',
    province: 'US-TX',
    taxNumber: 'SR KH 000-002',
    isDefault: true
  })
})

test('默认标记判定：只有非默认的税号可「设为默认」', () => {
  const primary = {
    id: '1',
    country: 'US',
    province: 'US-CA',
    taxType: 'US_STATE_SALES',
    taxNumber: 'SR KH 123-456',
    isDefault: true
  }
  const secondary = { ...primary, id: '2', isDefault: false }

  assert.equal(canSetDefault(primary), false)
  assert.equal(canSetDefault(secondary), true)
})

test('设默认的请求体：带上原值只改默认标记，避免全量替换清掉州省与税号', () => {
  const tax = {
    id: '3',
    country: 'US',
    province: null,
    taxType: 'US_EIN',
    taxNumber: '12-3456789',
    isDefault: false
  }

  assert.deepEqual(buildSetDefaultPayload(tax), {
    country: 'US',
    province: null,
    taxType: 'US_EIN',
    taxNumber: '12-3456789',
    isDefault: true
  })
})
