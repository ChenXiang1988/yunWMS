import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  SKU_PREFIX_PATTERN,
  buildCreatePayload,
  buildUpdatePayload,
  emptyOwnerForm,
  isPostalCodeRequired,
  ownerFormFromDetail,
  toProvinceOption,
  type OwnerFormValues
} from './owner-form.ts'

/** 以空表单为底，只覆盖本用例关心的字段 */
function formOf(overrides: Partial<OwnerFormValues> = {}): OwnerFormValues {
  return { ...emptyOwnerForm(), ...overrides }
}

test('编辑请求体：空串与纯空白转 null，且不含 skuPrefix、code、status', () => {
  const payload = buildUpdatePayload(
    formOf({
      name: ' 深圳某某贸易 ',
      ownerType: 'BRAND',
      settlementEntity: ' 深圳某某贸易有限公司 ',
      country: 'CN',
      city: '深圳',
      addressLine: ' 宝安大道 1 号 ',
      nameEn: '   ',
      externalCode: '',
      remark: ''
    })
  )

  assert.equal(payload.name, '深圳某某贸易')
  assert.equal(payload.settlementEntity, '深圳某某贸易有限公司')
  assert.equal(payload.addressLine, '宝安大道 1 号')
  assert.equal(payload.nameEn, null)
  assert.equal(payload.externalCode, null)
  assert.equal(payload.remark, null)
  assert.equal(payload.province, null)
  assert.equal(payload.district, null)
  assert.equal(payload.postalCode, null)

  // 这三个字段不在编辑接口的可编辑范围内，多传会被后端拒绝
  assert.equal('skuPrefix' in payload, false)
  assert.equal('code' in payload, false)
  assert.equal('status' in payload, false)
})

test('编辑请求体：已填的可选字段原样保留', () => {
  const payload = buildUpdatePayload(
    formOf({
      name: 'A',
      nameEn: 'A Inc',
      shortName: 'A',
      externalCode: 'EXT-1',
      ownerType: 'BRAND',
      settlementEntity: 'B',
      country: 'US',
      province: 'US-CA',
      city: 'LA',
      district: 'LA County',
      postalCode: '90001',
      addressLine: '1 Main St',
      remark: '备注'
    })
  )

  assert.deepEqual(payload, {
    name: 'A',
    nameEn: 'A Inc',
    shortName: 'A',
    externalCode: 'EXT-1',
    ownerType: 'BRAND',
    settlementEntity: 'B',
    country: 'US',
    province: 'US-CA',
    city: 'LA',
    district: 'LA County',
    postalCode: '90001',
    addressLine: '1 Main St',
    remark: '备注'
  })
})

test('新建请求体：在编辑字段之外带 skuPrefix 与初始状态', () => {
  const form = formOf({
    name: 'A',
    skuPrefix: ' AMZ2 ',
    ownerType: 'BRAND',
    settlementEntity: 'B',
    country: 'CN',
    city: '深圳',
    addressLine: 'X'
  })

  assert.deepEqual(buildCreatePayload(form, 'DRAFT'), {
    ...buildUpdatePayload(form),
    skuPrefix: 'AMZ2',
    initialStatus: 'DRAFT'
  })
  assert.equal(buildCreatePayload(form, 'ACTIVE').initialStatus, 'ACTIVE')
})

test('详情回填表单：null 字段显示为空串', () => {
  const form = ownerFormFromDetail({
    id: '1',
    code: 'OWNER0001',
    name: '深圳某某贸易',
    ownerType: 'BRAND',
    status: 'ACTIVE',
    updatedAt: '2026-10-24T08:30:00Z',
    nameEn: null,
    shortName: null,
    externalCode: null,
    skuPrefix: 'AMZ2',
    settlementEntity: '深圳某某贸易有限公司',
    country: 'CN',
    province: 'CN-SH',
    city: '上海',
    district: null,
    postalCode: null,
    addressLine: '宝安大道 1 号',
    remark: null,
    createdAt: '2026-10-24T08:30:00Z'
  })

  assert.equal(form.name, '深圳某某贸易')
  assert.equal(form.skuPrefix, 'AMZ2')
  assert.equal(form.province, 'CN-SH')
  assert.equal(form.nameEn, '')
  assert.equal(form.shortName, '')
  assert.equal(form.externalCode, '')
  assert.equal(form.district, '')
  assert.equal(form.postalCode, '')
  assert.equal(form.remark, '')
})

test('SKU 前缀格式：字符区间与后端 SkuPrefix 一致', () => {
  assert.equal(SKU_PREFIX_PATTERN.test('AMZ2'), true)
  assert.equal(SKU_PREFIX_PATTERN.test('AB9Z'), true)

  // 0 / O / 1 / I 被排除；不做大小写转换；长度必须正好 4 位
  for (const bad of ['AMZ0', 'AMZO', 'AMZ1', 'AMZI', 'amz2', 'AMZ', 'AMZ23', 'AM-Z']) {
    assert.equal(SKU_PREFIX_PATTERN.test(bad), false, `${bad} 不该通过`)
  }
})

test('省州字典项：postalCodeRequired 缺失即必填，只有明确 false 才豁免', () => {
  assert.equal(toProvinceOption({ value: 'CN-SH', label: '上海市' }).postalCodeRequired, true)
  assert.equal(
    toProvinceOption({ value: 'CN-SH', label: '上海市', ext: { parent: 'CN' } }).postalCodeRequired,
    true
  )
  assert.equal(
    toProvinceOption({
      value: 'CN-HK',
      label: '香港特别行政区',
      ext: { parent: 'CN', postalCodeRequired: false }
    }).postalCodeRequired,
    false
  )
  // 非布尔值不算豁免：口径是「明确写 false」
  assert.equal(
    toProvinceOption({ value: 'X', label: 'X', ext: { postalCodeRequired: 'false' } })
      .postalCodeRequired,
    true
  )
})

test('邮编必填判定：未选州省与未知州省按必填', () => {
  const provinces = [
    toProvinceOption({ value: 'CN-SH', label: '上海市', ext: { parent: 'CN' } }),
    toProvinceOption({
      value: 'CN-HK',
      label: '香港特别行政区',
      ext: { parent: 'CN', postalCodeRequired: false }
    })
  ]

  assert.equal(isPostalCodeRequired('CN-SH', provinces), true)
  assert.equal(isPostalCodeRequired('CN-HK', provinces), false)
  assert.equal(isPostalCodeRequired('', provinces), true)
  assert.equal(isPostalCodeRequired(undefined, provinces), true)
  assert.equal(isPostalCodeRequired('CN-XX', provinces), true)
})
