import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  buildContactPayload,
  buildPrimaryPayload,
  canPromoteToPrimary,
  contactFormFrom,
  contactRoleLabel,
  emptyContactForm
} from './contact-model.ts'

test('角色标签：取字典 label，字典里没有该值时回退原值', () => {
  const labels = new Map([
    ['BUSINESS', '业务对接人'],
    ['FINANCE', '财务联系人']
  ])

  assert.equal(contactRoleLabel('BUSINESS', labels), '业务对接人')
  // 停用项或新增角色：字典里没有就回退原值，不显示空白
  assert.equal(contactRoleLabel('WAREHOUSE', labels), 'WAREHOUSE')
  // 字典整批加载失败时同样回退原值
  assert.equal(contactRoleLabel('FINANCE', new Map()), 'FINANCE')
})

test('表单归一：必填项去两端空白，空白的邮箱与备注转 null', () => {
  const payload = buildContactPayload({
    name: ' 张三 ',
    roleType: ' BUSINESS ',
    phone: ' +86 13800000000 ',
    email: '   ',
    isPrimary: true,
    remark: ''
  })

  assert.equal(payload.name, '张三')
  assert.equal(payload.roleType, 'BUSINESS')
  assert.equal(payload.phone, '+86 13800000000')
  // 后端以 null 表示清空，空串会被当成「有值但为空」
  assert.equal(payload.email, null)
  assert.equal(payload.remark, null)
  assert.equal(payload.isPrimary, true)
})

test('表单归一：已填的可选字段保留原值，isPrimary 缺省按 false 处理', () => {
  const payload = buildContactPayload({
    name: '李四',
    roleType: 'FINANCE',
    phone: '0755-12345678',
    email: ' lisi@example.com ',
    remark: '对账联系人'
  })

  assert.equal(payload.email, 'lisi@example.com')
  assert.equal(payload.remark, '对账联系人')
  // 编辑是全量替换：省略 isPrimary 等于提交 false，所以缺省必须显式归一成 false
  assert.equal(payload.isPrimary, false)
  assert.equal(emptyContactForm().isPrimary, false)
})

test('表单回填：后端 null 字段显示为空串，主标记原样带出', () => {
  const form = contactFormFrom({
    id: '3',
    name: '王五',
    roleType: 'LOGISTICS',
    phone: '13800000000',
    email: null,
    isPrimary: true,
    remark: null
  })

  assert.equal(form.email, '')
  assert.equal(form.remark, '')
  assert.equal(form.isPrimary, true)
  // 回填后直接提交必须回到同一份值，不因空串与 null 的差异把字段清掉
  assert.deepEqual(buildContactPayload(form), {
    name: '王五',
    roleType: 'LOGISTICS',
    phone: '13800000000',
    email: null,
    isPrimary: true,
    remark: null
  })
})

test('主联系人判定：只有非主联系人可「设为主」', () => {
  const primary = {
    id: '1',
    name: '主联系人',
    roleType: 'BUSINESS',
    phone: '13800000000',
    email: null,
    isPrimary: true,
    remark: null
  }
  const secondary = { ...primary, id: '2', isPrimary: false }

  assert.equal(canPromoteToPrimary(primary), false)
  assert.equal(canPromoteToPrimary(secondary), true)
})

test('设为主的请求体：带上原值只改主标记，避免全量替换清掉邮箱与备注', () => {
  const contact = {
    id: '2',
    name: '赵六',
    roleType: 'ONSITE',
    phone: '13900000000',
    email: 'zhao@example.com',
    isPrimary: false,
    remark: '现场收货'
  }

  assert.deepEqual(buildPrimaryPayload(contact), {
    name: '赵六',
    roleType: 'ONSITE',
    phone: '13900000000',
    email: 'zhao@example.com',
    isPrimary: true,
    remark: '现场收货'
  })
})
