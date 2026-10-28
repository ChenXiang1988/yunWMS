import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  PLACEHOLDER,
  buildCreateUserPayload,
  buildUpdateUserPayload,
  buildUserListQuery,
  canToggleUserStatus,
  editUserFormFrom,
  emptyUserForm,
  lastLoginText,
  nextUserStatus,
  passwordMismatch,
  roleLabelText,
  subjectTypeText,
  userStatusText
} from './user-model.ts'

test('角色标签回退：取角色名，角色列表里没有该编码时回退编码本身', () => {
  const names = new Map([
    ['SUPER_ADMIN', '超级管理员'],
    ['CS', '客服']
  ])

  assert.equal(roleLabelText(['SUPER_ADMIN'], names), '超级管理员')
  assert.equal(roleLabelText(['SUPER_ADMIN', 'CS'], names), '超级管理员、客服')
  // 角色已被删除（历史用户仍挂着）或新增编码：回退编码，不显示空白
  assert.equal(roleLabelText(['LEGACY_ROLE'], names), 'LEGACY_ROLE')
  // 角色列表加载失败时整批回退编码
  assert.equal(roleLabelText(['CS'], new Map()), 'CS')
  // 没有角色：空串，占位符由展示层统一套
  assert.equal(roleLabelText([], names), '')
})

test('表单归一：登录名与展示名去两端空白，空白的邮箱与企业微信转 null', () => {
  const payload = buildCreateUserPayload({
    ...emptyUserForm(),
    loginName: '  zhang.san  ',
    displayName: ' 张三 ',
    email: '   ',
    wecomUserId: '',
    initialPassword: 'Abc12345',
    roleIds: ['2', '3']
  })

  assert.ok(payload)
  assert.equal(payload.loginName, 'zhang.san')
  assert.equal(payload.displayName, '张三')
  // 后端以 null 表示未填，空串会被当成「有值但为空」
  assert.equal(payload.email, null)
  assert.equal(payload.wecomUserId, null)
  assert.deepEqual(payload.roleIds, ['2', '3'])
})

test('表单归一：密码不裁剪，必填项为空白时不下发请求', () => {
  const kept = buildCreateUserPayload({
    ...emptyUserForm(),
    loginName: 'zhang.san',
    displayName: '张三',
    initialPassword: ' Abc12345 '
  })
  assert.ok(kept)
  // 前后的空格也是密码的一部分：裁掉会让管理员设的密码与本人登录时输入的对不上
  assert.equal(kept.initialPassword, ' Abc12345 ')

  assert.equal(
    buildCreateUserPayload({
      ...emptyUserForm(),
      loginName: '   ',
      displayName: '张三',
      initialPassword: 'Abc12345'
    }),
    null
  )
  assert.equal(
    buildCreateUserPayload({
      ...emptyUserForm(),
      loginName: 'zhang.san',
      displayName: '张三',
      initialPassword: ''
    }),
    null
  )
})

test('新建请求体：货主用户带所属货主且去空白，内部员工不传该字段', () => {
  const ownerUser = buildCreateUserPayload({
    ...emptyUserForm(),
    loginName: 'lisi',
    displayName: '李四',
    subjectType: 'OWNER',
    ownerId: ' 12 ',
    initialPassword: 'Abc12345'
  })
  assert.ok(ownerUser)
  assert.equal(ownerUser.ownerId, '12')

  const staff = buildCreateUserPayload({
    ...emptyUserForm(),
    loginName: 'wangwu',
    displayName: '王五',
    initialPassword: 'Abc12345'
  })
  assert.ok(staff)
  // 契约要求内部员工必须不传 ownerId，传 null 也算「传了」，所以字段整个不出现
  assert.equal('ownerId' in staff, false)
})

test('两次密码一致性：不一致时判为不一致，空格算密码内容的一部分', () => {
  assert.equal(passwordMismatch('Abc12345', 'Abc12345'), false)
  assert.equal(passwordMismatch('Abc12345', 'Abc1234'), true)
  assert.equal(passwordMismatch(' Abc12345', 'Abc12345'), true)
  // 两次都为空算「一致」，非空由表单的必填规则拦
  assert.equal(passwordMismatch('', ''), false)
})

test('启停方向与可用性：不能停用自己，其余两条保护交由后端裁决', () => {
  assert.equal(nextUserStatus('ACTIVE'), 'DISABLED')
  assert.equal(nextUserStatus('DISABLED'), 'ACTIVE')

  // 自己那一行不给停用入口：停用会把操作人自己锁在门外，后端同样返回 40900
  assert.equal(canToggleUserStatus('7', '7'), false)
  assert.equal(canToggleUserStatus('7', '8'), true)
  // 前端拿不到当前登录用户标识时不拦任何行，失败后由后端 40900 的提示兜底
  assert.equal(canToggleUserStatus('7', null), true)
})

test('词表：状态与主体类型按契约翻译，未知编码回退原值', () => {
  assert.equal(userStatusText('ACTIVE'), '启用')
  assert.equal(userStatusText('DISABLED'), '停用')
  // 后端将来新增状态时显示原文，不显示空白或英文之外的猜测
  assert.equal(userStatusText('LOCKED'), 'LOCKED')

  assert.equal(subjectTypeText('STAFF'), '内部员工')
  assert.equal(subjectTypeText('OWNER'), '货主用户')
  assert.equal(subjectTypeText('VENDOR'), 'VENDOR')
})

test('列表查询归一：空白关键词不下发，筛选项按值附加', () => {
  assert.deepEqual(buildUserListQuery({ keyword: '   ' }, 1, 20), { pageNo: 1, pageSize: 20 })
  assert.deepEqual(buildUserListQuery({}, 3, 50), { pageNo: 3, pageSize: 50 })
  assert.deepEqual(
    buildUserListQuery({ keyword: ' 张 ', subjectType: 'OWNER', status: 'DISABLED' }, 2, 20),
    { pageNo: 2, pageSize: 20, keyword: '张', subjectType: 'OWNER', status: 'DISABLED' }
  )
})

test('编辑回填与提交：null 字段显示为空串，全量提交保留原值', () => {
  const form = editUserFormFrom({
    id: '9',
    loginName: 'wang.wu',
    displayName: '王五',
    subjectType: 'OWNER',
    ownerId: '3',
    ownerName: '深圳某客户',
    status: 'ACTIVE',
    roles: ['CS'],
    lastLoginAt: null,
    email: null,
    wecomUserId: null,
    dataScopeType: null,
    ownerIds: [],
    createdAt: '2026-10-24T08:30:00Z',
    roleIds: ['2']
  })

  assert.equal(form.email, '')
  assert.equal(form.wecomUserId, '')
  assert.equal(form.ownerId, '3')
  assert.deepEqual(form.roleIds, ['2'])
  // 编辑态不带明文密码
  assert.equal(form.initialPassword, '')
  assert.equal(form.confirmPassword, '')

  // 回填后直接提交必须回到同一份值，不因空串与 null 的差异把字段清掉
  assert.deepEqual(buildUpdateUserPayload(form), {
    displayName: '王五',
    email: null,
    wecomUserId: null
  })
})

test('最后登录：从未登录显示固定文案，时间格式与客户页一致', () => {
  assert.equal(lastLoginText(null), '从未登录')
  // 时区由运行环境决定，这里只断言格式与非法值兜底，不断言具体时刻
  assert.match(lastLoginText('2026-10-24T08:30:00Z'), /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
  assert.equal(lastLoginText('not-a-date'), PLACEHOLDER)
})
