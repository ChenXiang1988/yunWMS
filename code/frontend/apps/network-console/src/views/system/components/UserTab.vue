<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Button,
  Form,
  FormItem,
  Input,
  InputPassword,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  type FormInstance
} from 'ant-design-vue'
import { EmptyState } from '@yunwms/ui-web'
import { listOwners, type PageResult } from '@/api/owner'
import type { SubjectType } from '@/api/auth'
import {
  assignUserRoles,
  changeUserStatus,
  createUser,
  getUser,
  kickUserSessions,
  listRoles,
  listUsers,
  resetUserPassword,
  updateUser,
  type RoleBrief,
  type UserDetail,
  type UserListItem,
  type UserStatus
} from '@/api/user'
import { notifyError, notifySuccess } from '@/api/notify'
import {
  SUBJECT_TYPE_OPTIONS,
  USER_DEFAULT_PAGE_SIZE,
  USER_DISPLAY_NAME_MAX,
  USER_EMAIL_MAX,
  USER_LOGIN_NAME_MAX,
  USER_OPTION_PAGE_SIZE,
  USER_STATUS_OPTIONS,
  USER_WECOM_MAX,
  buildCreateUserPayload,
  buildUpdateUserPayload,
  buildUserListQuery,
  canToggleUserStatus,
  displayText,
  editUserFormFrom,
  emptyUserForm,
  lastLoginText,
  nextUserStatus,
  passwordMismatch,
  roleLabelText,
  subjectTypeText,
  userStatusText,
  type UserFilterValues,
  type UserFormValues
} from '../user-model'

/**
 * 用户页签：列表与筛选、新建、编辑、启停、重置密码、分配角色、踢下线。
 *
 * 五条由契约与既有约定决定的取舍：
 *
 * 1. 三条停用保护（不能停用自己、不能停用最后一个可用的超级管理员、仍担任货主负责人的
 *    员工不能停用）都在后端判定，前端不复述：需要的全库视角前端没有。失败后把后端提示
 *    原样呈现并刷新列表——失败往往说明列表已经过时。
 * 2. 明文密码只活在「填表 → 提交」之间：提交结束（无论成败）立即从组件状态里清掉，
 *    不写存储、不回显；`catch` 里只记 `error.message`，不记错误对象——Axios 失败时
 *    错误对象带着请求体，里面有密码明文。
 * 3. 密码策略（长度与字符组合）不在前端复述，只校验非空、以及两次输入一致——后者是
 *    纯交互、后端看不到的信息。
 * 4. 角色分配与编辑都是全量替换：提交时把选中的角色标识、三项可编辑字段整批传，不做
 *    「只发改动的那一项」，否则后端会把没传的字段按 null 清空。
 * 5. 弹窗而非页内区块：本页签不在抽屉之上，不存在浮层被抽屉遮罩压住的问题（那是
 *    `views/customer` 用页内区块的原因），所以沿用客户页弹窗的交互。
 */

/* -------------------------- 列表与筛选 -------------------------- */
const rows = ref<UserListItem[]>([])
const loading = ref(false)
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(USER_DEFAULT_PAGE_SIZE)
/** 已生效的筛选条件：输入框改动不算数，点查询才生效 */
const filters = ref<UserFilterValues>({})
/** 列表请求序号：连续翻页时只认最后一次响应 */
let listRequestId = 0

const keywordInput = ref('')
const subjectTypeInput = ref<SubjectType | undefined>(undefined)
const statusInput = ref<UserStatus | undefined>(undefined)

async function loadUsers(): Promise<void> {
  const requestId = ++listRequestId
  loading.value = true
  try {
    const result = await listUsers(buildUserListQuery(filters.value, pageNo.value, pageSize.value))
    if (requestId === listRequestId) {
      rows.value = result.records
      total.value = result.total
    }
  } catch (error) {
    // 请求层已统一弹出提示。这里不清空 rows：空列表会被读成「一个账号都没有」，
    // 那是在用空态掩盖故障
    console.error('[system] 加载用户列表失败', error instanceof Error ? error.message : '未知错误')
  } finally {
    if (requestId === listRequestId) {
      loading.value = false
    }
  }
}

/** 查询条件变化时回到第 1 页，否则会落在新结果的空白页上 */
function handleSearch(): void {
  filters.value = {
    keyword: keywordInput.value,
    subjectType: subjectTypeInput.value,
    status: statusInput.value
  }
  pageNo.value = 1
  void loadUsers()
}

function handleReset(): void {
  keywordInput.value = ''
  subjectTypeInput.value = undefined
  statusInput.value = undefined
  filters.value = {}
  pageNo.value = 1
  void loadUsers()
}

function handlePageChange(current: number, nextPageSize?: number): void {
  pageNo.value = current
  pageSize.value = nextPageSize ?? pageSize.value
  void loadUsers()
}

interface TableChangePaging {
  current?: number
  pageSize?: number
}

function handleTableChange(paging: TableChangePaging): void {
  handlePageChange(paging.current ?? 1, paging.pageSize)
}

const pagination = computed(() => ({
  current: pageNo.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (count: number) => `共 ${count} 条`
}))

/**
 * 当前登录用户的标识。
 *
 * 台账里没有「当前登录用户」的读端点，登录响应也不回带用户标识，所以前端拿不到它，
 * 这里只能是 null——`canToggleUserStatus` 因此在页面上不拦任何行，「不能停用自己」
 * 由后端 40900 兜底。等身份接口落地后把值填进来即可生效。
 */
const selfUserId: string | null = null

/* -------------------------- 角色与货主选项 -------------------------- */
const roles = ref<RoleBrief[]>([])
const roleNameByCode = computed(() => new Map(roles.value.map((role) => [role.code, role.name])))
const roleSelectOptions = computed(() =>
  roles.value.map((role) => ({
    value: role.id,
    label:
      role.status === 'ACTIVE'
        ? `${role.name}（${role.code}）`
        : `${role.name}（${role.code} · 已停用）`
  }))
)

const ownerOptions = ref<Array<{ value: string; label: string }>>([])
const ownersLoaded = ref(false)
/** 货主下拉加载失败：不挡住建员工账号，但要说明为什么建不了货主账号 */
const ownerLoadFailed = ref(false)

/**
 * 逐页取完整个集合。
 *
 * 角色与货主只有分页端点，下拉又需要全量选项；只取第一页会让超过一页的选项静默消失。
 * 取到空页也停，避免后端 total 与实际条数不一致时空转。
 */
async function collectAllPages<T>(load: (page: number) => Promise<PageResult<T>>): Promise<T[]> {
  const collected: T[] = []
  for (let page = 1; ; page += 1) {
    const result = await load(page)
    collected.push(...result.records)
    if (result.records.length === 0 || collected.length >= result.total) {
      return collected
    }
  }
}

async function loadRoles(): Promise<void> {
  try {
    roles.value = await collectAllPages((page) =>
      listRoles({ pageNo: page, pageSize: USER_OPTION_PAGE_SIZE })
    )
  } catch (error) {
    // 角色列表失败只让角色列回退编码、下拉变空，用户列表本身照常展示
    console.error('[system] 加载角色列表失败', error instanceof Error ? error.message : '未知错误')
  }
}

/**
 * 货主下拉按需加载：只在打开新建表单时取，不替用户在进入页签时就拉一遍全量货主。
 *
 * 接口按当前账号的数据范围过滤，所以下拉里只有自己看得见的货主；越界范围由后端在
 * 提交时再拒一次。不按货主状态过滤——建号的 ownerId 只要求货主存在。
 */
async function loadOwners(): Promise<void> {
  if (ownersLoaded.value || ownerLoadFailed.value) {
    return
  }
  try {
    const items = await collectAllPages((page) =>
      listOwners({ pageNo: page, pageSize: USER_OPTION_PAGE_SIZE })
    )
    ownerOptions.value = items.map((owner) => ({
      value: owner.id,
      label: `${owner.name}（${owner.code}）`
    }))
    ownersLoaded.value = true
  } catch (error) {
    ownerLoadFailed.value = true
    console.error('[system] 加载货主选项失败', error instanceof Error ? error.message : '未知错误')
  }
}

/* -------------------------- 新建 / 编辑 -------------------------- */
const formRef = ref<FormInstance>()
const formValues = reactive<UserFormValues>(emptyUserForm())
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSubmitting = ref(false)
/** 编辑态要先取详情才能回填：列表行没有邮箱、企业微信 userid 与角色标识 */
const formLoading = ref(false)
/** 正在编辑的用户标识；null 表示新建 */
const editingId = ref<string | null>(null)
/**
 * 编辑态只读展示的所属货主名称。
 *
 * 表单值里的 `ownerId` 是十进制标识，直接摆出来没人看得懂；详情带回了名称就用名称。
 * 货主已被逻辑删除时名称为 null，退回标识——至少能与接口日志对上。
 */
const editingOwnerName = ref('')

const displayNameRules = [
  { required: true, whitespace: true, message: '请输入展示名' },
  { max: USER_DISPLAY_NAME_MAX, message: `展示名不能超过 ${USER_DISPLAY_NAME_MAX} 个字符` }
]
const emailRules = [{ max: USER_EMAIL_MAX, message: `邮箱不能超过 ${USER_EMAIL_MAX} 个字符` }]
const wecomRules = [
  { max: USER_WECOM_MAX, message: `企业微信 userid 不能超过 ${USER_WECOM_MAX} 个字符` }
]

/**
 * 新建规则：必填与长度上限取自契约；密码只校验非空。
 *
 * 邮箱不做格式校验：格式由后端判定并返回字段级提示，前端再写一遍正则就成了第二份规则。
 * `whitespace: true` 对应后端「只填空白按未填处理」。
 */
const createRules = {
  loginName: [
    { required: true, whitespace: true, message: '请输入登录名' },
    { max: USER_LOGIN_NAME_MAX, message: `登录名不能超过 ${USER_LOGIN_NAME_MAX} 个字符` }
  ],
  displayName: displayNameRules,
  subjectType: [{ required: true, message: '请选择主体类型' }],
  ownerId: [{ required: true, message: '请选择所属货主' }],
  email: emailRules,
  wecomUserId: wecomRules,
  initialPassword: [{ required: true, message: '请输入初始密码' }],
  confirmPassword: [{ required: true, message: '请再次输入初始密码' }]
}

/** 编辑规则：内部员工清空邮箱会被后端拒绝（40000），必填口子随主体类型变化 */
const editRules = computed(() => ({
  displayName: displayNameRules,
  email:
    formValues.subjectType === 'STAFF'
      ? [{ required: true, whitespace: true, message: '内部员工必须保留邮箱' }, ...emailRules]
      : emailRules,
  wecomUserId: wecomRules
}))

/** 取用户详情：编辑与分配角色都要回填列表行里没有的字段 */
async function fetchUserDetail(id: string): Promise<UserDetail | null> {
  try {
    return await getUser(id)
  } catch (error) {
    console.error('[system] 加载用户详情失败', error instanceof Error ? error.message : '未知错误')
    return null
  }
}

function openCreate(): void {
  formMode.value = 'create'
  editingId.value = null
  editingOwnerName.value = ''
  Object.assign(formValues, emptyUserForm())
  formOpen.value = true
  formRef.value?.clearValidate()
  void loadOwners()
}

async function openEdit(record: UserListItem): Promise<void> {
  formMode.value = 'edit'
  editingId.value = record.id
  editingOwnerName.value = record.ownerName ?? record.ownerId ?? ''
  Object.assign(formValues, emptyUserForm())
  formOpen.value = true
  formRef.value?.clearValidate()

  formLoading.value = true
  try {
    const detail = await fetchUserDetail(record.id)
    if (!detail) {
      // 取不到详情就无法回填，关掉表单比留一个空表单更诚实
      closeForm()
      return
    }
    // 期间用户可能已经关掉表单或改点另一行：只回填仍然匹配的那一次
    if (formOpen.value && editingId.value === record.id) {
      Object.assign(formValues, editUserFormFrom(detail))
      editingOwnerName.value = detail.ownerName ?? detail.ownerId ?? ''
    }
  } finally {
    formLoading.value = false
  }
}

/** 关闭表单并清掉明文：密码不留在组件状态里 */
function closeForm(): void {
  formOpen.value = false
  editingId.value = null
  editingOwnerName.value = ''
  formValues.initialPassword = ''
  formValues.confirmPassword = ''
}

/**
 * 校验表单。
 *
 * antd 的 `validate` 以 reject 表示不符合，错误已由表单项就地展示。表单还没挂载时没有
 * 可校验的字段，返回 true 交给请求体构造的空白判据兜底。
 */
async function passesValidation(instance: FormInstance | undefined): Promise<boolean> {
  if (!instance) {
    return true
  }
  try {
    await instance.validate()
    return true
  } catch {
    return false
  }
}

async function submitForm(): Promise<void> {
  if (formMode.value === 'create') {
    await submitCreate()
    return
  }
  await submitUpdate()
}

async function submitCreate(): Promise<void> {
  if (!(await passesValidation(formRef.value))) {
    return
  }
  if (passwordMismatch(formValues.initialPassword, formValues.confirmPassword)) {
    notifyError('两次输入的密码不一致')
    return
  }
  const payload = buildCreateUserPayload({ ...formValues })
  if (!payload) {
    return
  }
  formSubmitting.value = true
  try {
    const created = await createUser(payload)
    notifySuccess(`用户 ${created.loginName} 已创建，请通过安全渠道把初始密码交给本人`)
    closeForm()
  } catch (error) {
    // 40901 登录名被占用、40903 邮箱被占用、40000 密码策略不符都由请求层弹出后端原文；
    // 表单保持打开供修改。只记 message：错误对象带着含密码明文的请求体
    console.error('[system] 创建用户失败', error instanceof Error ? error.message : '未知错误')
  } finally {
    formSubmitting.value = false
    // 明文只在这一次请求里出现：成功或失败都立即清掉，不写存储、不回显、不进日志
    formValues.initialPassword = ''
    formValues.confirmPassword = ''
    await loadUsers()
  }
}

async function submitUpdate(): Promise<void> {
  const id = editingId.value
  if (!id) {
    return
  }
  if (!(await passesValidation(formRef.value))) {
    return
  }
  const payload = buildUpdateUserPayload(formValues)
  formSubmitting.value = true
  try {
    await updateUser(id, payload)
    notifySuccess('用户信息已更新')
    closeForm()
  } catch (error) {
    // 40903 邮箱被他人占用、40000 内部员工清空邮箱都由请求层弹出，表单保持打开
    console.error('[system] 更新用户失败', error instanceof Error ? error.message : '未知错误')
  } finally {
    formSubmitting.value = false
    await loadUsers()
  }
}

/* -------------------------- 启停 -------------------------- */
/** 待确认启停的用户；非 null 时确认弹窗打开 */
const statusTarget = ref<UserListItem | null>(null)
const statusSubmitting = ref(false)

async function confirmStatusChange(): Promise<void> {
  const target = statusTarget.value
  if (!target) {
    return
  }
  const next = nextUserStatus(target.status)
  statusSubmitting.value = true
  try {
    await changeUserStatus(target.id, next)
    notifySuccess(
      next === 'DISABLED' ? `已停用用户 ${target.loginName}` : `已启用用户 ${target.loginName}`
    )
  } catch (error) {
    // 三条保护由后端判定并以 40900 返回；提示原样呈现，不判文案、不猜错误码
    console.error('[system] 切换账号状态失败', error instanceof Error ? error.message : '未知错误')
  } finally {
    statusSubmitting.value = false
    statusTarget.value = null
    // 成败都重拉：失败往往说明列表状态已经过时
    await loadUsers()
  }
}

/* -------------------------- 重置密码 -------------------------- */
const passwordFormRef = ref<FormInstance>()
/** 待重置密码的用户；非 null 时弹窗打开 */
const passwordTarget = ref<UserListItem | null>(null)
const passwordValues = reactive({ newPassword: '', confirmPassword: '' })
const passwordSubmitting = ref(false)
const passwordRules = {
  newPassword: [{ required: true, message: '请输入新密码' }],
  confirmPassword: [{ required: true, message: '请再次输入新密码' }]
}

function openResetPassword(record: UserListItem): void {
  passwordTarget.value = record
  clearPasswords()
  passwordFormRef.value?.clearValidate()
}

/** 清掉两份明文：成功、失败与取消都要走到这里 */
function clearPasswords(): void {
  passwordValues.newPassword = ''
  passwordValues.confirmPassword = ''
}

function closeResetPassword(): void {
  passwordTarget.value = null
  clearPasswords()
}

async function submitResetPassword(): Promise<void> {
  const target = passwordTarget.value
  if (!target) {
    return
  }
  if (!(await passesValidation(passwordFormRef.value))) {
    return
  }
  if (passwordMismatch(passwordValues.newPassword, passwordValues.confirmPassword)) {
    notifyError('两次输入的密码不一致')
    return
  }
  passwordSubmitting.value = true
  try {
    await resetUserPassword(target.id, passwordValues.newPassword)
    notifySuccess(`已重置 ${target.loginName} 的密码，该账号需重新登录并先改密`)
    closeResetPassword()
  } catch (error) {
    // 40900（这条通道不能作用于自己）与 40000（密码策略不符，一次列全违规项）由请求层弹出
    console.error('[system] 重置密码失败', error instanceof Error ? error.message : '未知错误')
  } finally {
    passwordSubmitting.value = false
    // 明文不进状态留存，也不影响列表数据：重置不改变账号状态与最后登录时间，无需重拉
    clearPasswords()
  }
}

/* -------------------------- 分配角色 -------------------------- */
/** 待分配角色的用户；非 null 时弹窗打开 */
const rolesTarget = ref<UserListItem | null>(null)
const selectedRoleIds = ref<string[]>([])
const rolesLoading = ref(false)
const rolesSubmitting = ref(false)

async function openAssignRoles(record: UserListItem): Promise<void> {
  rolesTarget.value = record
  selectedRoleIds.value = []
  rolesLoading.value = true
  try {
    const detail = await fetchUserDetail(record.id)
    if (!detail) {
      // 连点另一行时不要把先失败的那次算到后一行头上
      if (rolesTarget.value?.id === record.id) {
        rolesTarget.value = null
      }
      return
    }
    // 列表行只有角色编码，勾选状态要用详情里的角色标识
    if (rolesTarget.value?.id === record.id) {
      selectedRoleIds.value = [...detail.roleIds]
    }
  } finally {
    rolesLoading.value = false
  }
}

async function submitRoles(): Promise<void> {
  const target = rolesTarget.value
  if (!target) {
    return
  }
  rolesSubmitting.value = true
  try {
    // 整批替换：选中的集合就是这个账号此后的全部角色，空数组表示移除全部
    await assignUserRoles(target.id, [...selectedRoleIds.value])
    notifySuccess(`已更新 ${target.loginName} 的角色`)
  } catch (error) {
    // 40000（角色不存在或已删除）与 40900（把自己替换成不含内置角色的组合）由请求层弹出
    console.error('[system] 分配角色失败', error instanceof Error ? error.message : '未知错误')
  } finally {
    rolesSubmitting.value = false
    rolesTarget.value = null
    // 列表的角色列取自角色的两种表示之一（编码），成败都以重新取回的列表为准
    await loadUsers()
  }
}

/* -------------------------- 踢下线 -------------------------- */
/** 待踢下线的用户；非 null 时确认弹窗打开 */
const kickTarget = ref<UserListItem | null>(null)
const kickSubmitting = ref(false)

async function confirmKick(): Promise<void> {
  const target = kickTarget.value
  if (!target) {
    return
  }
  kickSubmitting.value = true
  try {
    await kickUserSessions(target.id)
    notifySuccess(`已使 ${target.loginName} 的全部会话失效，该用户需重新登录`)
  } catch (error) {
    // 40400（账号不存在）由请求层弹出。踢下线没有可修正的输入，失败也无从就地补救：
    // 用户仍可正常使用，重新点一次即可，所以只关弹窗、不重拉列表
    console.error('[system] 踢下线失败', error instanceof Error ? error.message : '未知错误')
  } finally {
    kickSubmitting.value = false
    kickTarget.value = null
    // 不重拉列表：踢下线不改变账号状态，本页展示的字段一个都不变
  }
}

/* -------------------------- 表格列 -------------------------- */
const columns = [
  { title: '登录名', key: 'loginName', width: 160 },
  { title: '展示名', key: 'displayName', width: 140 },
  { title: '主体类型', key: 'subjectType', width: 110 },
  { title: '所属货主', key: 'ownerName', width: 180 },
  { title: '角色', key: 'roles', width: 220 },
  { title: '状态', key: 'status', width: 90 },
  { title: '最后登录', key: 'lastLoginAt', width: 160 },
  { title: '操作', key: 'actions', width: 320 }
]

onMounted(() => {
  void loadUsers()
  // 角色名用于列表的角色列与分配角色下拉，进页面就取；失败只影响这两处
  void loadRoles()
})
</script>

<template>
  <div class="user">
    <Form layout="inline" class="user__filters">
      <FormItem label="关键词">
        <Input
          v-model:value="keywordInput"
          placeholder="登录名 / 展示名"
          allow-clear
          style="width: 220px"
          @press-enter="handleSearch"
        />
      </FormItem>
      <FormItem label="主体类型">
        <Select
          v-model:value="subjectTypeInput"
          placeholder="全部"
          allow-clear
          style="width: 140px"
          :options="SUBJECT_TYPE_OPTIONS"
        />
      </FormItem>
      <FormItem label="状态">
        <Select
          v-model:value="statusInput"
          placeholder="全部"
          allow-clear
          style="width: 120px"
          :options="USER_STATUS_OPTIONS"
        />
      </FormItem>
      <FormItem>
        <Button type="primary" @click="handleSearch">查询</Button>
        <Button class="user__reset" @click="handleReset">重置</Button>
      </FormItem>
    </Form>

    <div class="user__toolbar">
      <Button type="primary" @click="openCreate">新建用户</Button>
    </div>

    <Table
      :data-source="rows"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: 1380 }"
      row-key="id"
      size="small"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'loginName'">
          <span class="user__mono">{{ (record as UserListItem).loginName }}</span>
        </template>
        <template v-else-if="column.key === 'displayName'">
          {{ (record as UserListItem).displayName }}
        </template>
        <template v-else-if="column.key === 'subjectType'">
          {{ subjectTypeText((record as UserListItem).subjectType) }}
        </template>
        <template v-else-if="column.key === 'ownerName'">
          {{ displayText((record as UserListItem).ownerName) }}
        </template>
        <template v-else-if="column.key === 'roles'">
          {{ displayText(roleLabelText((record as UserListItem).roles, roleNameByCode)) }}
        </template>
        <template v-else-if="column.key === 'status'">
          <Tag :color="(record as UserListItem).status === 'ACTIVE' ? 'success' : 'default'">
            {{ userStatusText((record as UserListItem).status) }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'lastLoginAt'">
          {{ lastLoginText((record as UserListItem).lastLoginAt) }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <Space :size="0">
            <Button type="link" size="small" @click="openEdit(record as UserListItem)">
              编辑
            </Button>
            <Button type="link" size="small" @click="openAssignRoles(record as UserListItem)">
              角色
            </Button>
            <Button type="link" size="small" @click="openResetPassword(record as UserListItem)">
              重置密码
            </Button>
            <Button
              type="link"
              size="small"
              :disabled="!canToggleUserStatus((record as UserListItem).id, selfUserId)"
              @click="statusTarget = record as UserListItem"
            >
              {{ userStatusText(nextUserStatus((record as UserListItem).status)) }}
            </Button>
            <Button
              type="link"
              size="small"
              danger
              @click="kickTarget = record as UserListItem"
            >
              踢下线
            </Button>
          </Space>
        </template>
      </template>

      <template #emptyText>
        <EmptyState description="暂无用户账号" />
      </template>
    </Table>

    <Modal
      :open="formOpen"
      :title="formMode === 'create' ? '新建用户' : '编辑用户'"
      :ok-text="formMode === 'create' ? '创建' : '保存'"
      cancel-text="取消"
      :confirm-loading="formSubmitting || formLoading"
      :mask-closable="false"
      destroy-on-close
      :width="560"
      @ok="submitForm"
      @cancel="closeForm"
    >
      <Form
        ref="formRef"
        :model="formValues"
        :rules="formMode === 'create' ? createRules : editRules"
        layout="vertical"
      >
        <template v-if="formMode === 'create'">
          <FormItem label="登录名" name="loginName">
            <Input
              v-model:value="formValues.loginName"
              :maxlength="USER_LOGIN_NAME_MAX"
              placeholder="全局唯一，创建后不可修改"
              allow-clear
            />
          </FormItem>
          <FormItem label="展示名" name="displayName">
            <Input
              v-model:value="formValues.displayName"
              :maxlength="USER_DISPLAY_NAME_MAX"
              placeholder="界面上显示给人看的名字"
              allow-clear
            />
          </FormItem>
          <FormItem label="主体类型" name="subjectType">
            <Select v-model:value="formValues.subjectType" :options="SUBJECT_TYPE_OPTIONS" />
          </FormItem>
          <FormItem v-if="formValues.subjectType === 'OWNER'" label="所属货主" name="ownerId">
            <Select
              v-model:value="formValues.ownerId"
              :options="ownerOptions"
              placeholder="所属货主创建后不可修改"
              show-search
              option-filter-prop="label"
              allow-clear
            />
          </FormItem>
          <FormItem label="邮箱" name="email">
            <Input
              v-model:value="formValues.email"
              :maxlength="USER_EMAIL_MAX"
              placeholder="内部员工必填，用于自助找回密码"
              allow-clear
            />
          </FormItem>
          <FormItem label="企业微信 userid" name="wecomUserId">
            <Input
              v-model:value="formValues.wecomUserId"
              :maxlength="USER_WECOM_MAX"
              placeholder="用于应用消息推送，可留空"
              allow-clear
            />
          </FormItem>
          <FormItem label="初始密码" name="initialPassword">
            <InputPassword
              v-model:value="formValues.initialPassword"
              autocomplete="new-password"
              placeholder="由操作人设定，提交后不再回显"
            />
          </FormItem>
          <FormItem label="再次输入初始密码" name="confirmPassword">
            <InputPassword
              v-model:value="formValues.confirmPassword"
              autocomplete="new-password"
              placeholder="仅用于核对，不提交"
            />
          </FormItem>
          <FormItem label="角色" name="roleIds">
            <Select
              v-model:value="formValues.roleIds"
              mode="multiple"
              :options="roleSelectOptions"
              placeholder="可留空，稍后用「角色」分配"
              option-filter-prop="label"
              allow-clear
            />
          </FormItem>
        </template>

        <template v-else>
          <FormItem label="登录名">
            <Input :value="formValues.loginName" disabled />
          </FormItem>
          <FormItem label="主体类型">
            <Input :value="subjectTypeText(formValues.subjectType)" disabled />
          </FormItem>
          <FormItem v-if="formValues.subjectType === 'OWNER'" label="所属货主">
            <Input :value="editingOwnerName" disabled />
          </FormItem>
          <FormItem label="展示名" name="displayName">
            <Input
              v-model:value="formValues.displayName"
              :maxlength="USER_DISPLAY_NAME_MAX"
              allow-clear
            />
          </FormItem>
          <FormItem label="邮箱" name="email">
            <Input
              v-model:value="formValues.email"
              :maxlength="USER_EMAIL_MAX"
              placeholder="内部员工必须保留邮箱"
              allow-clear
            />
          </FormItem>
          <FormItem label="企业微信 userid" name="wecomUserId">
            <Input
              v-model:value="formValues.wecomUserId"
              :maxlength="USER_WECOM_MAX"
              allow-clear
            />
          </FormItem>
        </template>
      </Form>

      <p v-if="formMode === 'create'" class="user__hint">
        新账号一律启用，并被标记为首次登录须先修改密码。初始密码只用于这一次提交：提交后
        不再回显，也不写入浏览器存储；长度与字符要求由服务端校验，不符时会一次列出全部原因。
      </p>
      <p v-else class="user__hint">
        登录名、主体类型与所属货主不可修改：它们决定账号身份与数据归属，改动属于另一件事。
        邮箱与企业微信 userid 按全量替换提交，清空即删除。
      </p>
      <p v-if="formMode === 'create' && ownerLoadFailed" class="user__hint">
        货主选项加载失败，暂时无法为货主用户选择所属货主；内部员工账号不受影响。
      </p>
    </Modal>

    <Modal
      :open="passwordTarget !== null"
      title="重置密码"
      ok-text="确认重置"
      cancel-text="取消"
      :confirm-loading="passwordSubmitting"
      :mask-closable="false"
      destroy-on-close
      :width="480"
      @ok="submitResetPassword"
      @cancel="closeResetPassword"
    >
      <template v-if="passwordTarget">
        <p class="user__confirm">
          为 <strong>{{ passwordTarget.displayName }}</strong>
          （<span class="user__mono">{{ passwordTarget.loginName }}</span>）设置新密码。
        </p>
        <p class="user__confirm-hint">
          重置后该账号的全部会话立即失效，并被重新标记为首次登录须改密。
        </p>
      </template>

      <Form
        ref="passwordFormRef"
        class="user__password-form"
        :model="passwordValues"
        :rules="passwordRules"
        layout="vertical"
      >
        <FormItem label="新密码" name="newPassword">
          <InputPassword
            v-model:value="passwordValues.newPassword"
            autocomplete="new-password"
            placeholder="提交后不再回显"
          />
        </FormItem>
        <FormItem label="再次输入新密码" name="confirmPassword">
          <InputPassword
            v-model:value="passwordValues.confirmPassword"
            autocomplete="new-password"
            placeholder="仅用于核对，不提交"
          />
        </FormItem>
      </Form>

      <p class="user__hint">
        这条通道不校验旧密码，因此不能用来重置自己的密码——后端会拒绝，改自己请走改密流程。
      </p>
    </Modal>

    <Modal
      :open="rolesTarget !== null"
      title="分配角色"
      ok-text="提交"
      cancel-text="取消"
      :confirm-loading="rolesSubmitting"
      :mask-closable="false"
      :width="480"
      @ok="submitRoles"
      @cancel="rolesTarget = null"
    >
      <template v-if="rolesTarget">
        <p class="user__confirm">
          为 <strong>{{ rolesTarget.displayName }}</strong>
          （<span class="user__mono">{{ rolesTarget.loginName }}</span>）设置角色。
        </p>
      </template>

      <Select
        v-model:value="selectedRoleIds"
        mode="multiple"
        class="user__role-select"
        :options="roleSelectOptions"
        :loading="rolesLoading"
        placeholder="留空表示移除全部角色"
        option-filter-prop="label"
        allow-clear
      />

      <p class="user__hint">
        提交的是整批替换：上面选中的就是这个账号此后的全部角色，留空即移除全部。停用中的
        角色仍可分配，但它的权限点不生效。
      </p>
    </Modal>

    <Modal
      :open="statusTarget !== null"
      :title="statusTarget?.status === 'ACTIVE' ? '停用用户' : '启用用户'"
      :ok-text="statusTarget?.status === 'ACTIVE' ? '确认停用' : '确认启用'"
      cancel-text="取消"
      :confirm-loading="statusSubmitting"
      :ok-button-props="{ danger: statusTarget?.status === 'ACTIVE' }"
      :mask-closable="false"
      :width="480"
      @ok="confirmStatusChange"
      @cancel="statusTarget = null"
    >
      <template v-if="statusTarget">
        <p class="user__confirm">
          确定{{ statusTarget.status === 'ACTIVE' ? '停用' : '启用' }}用户
          <strong>{{ statusTarget.displayName }}</strong>
          （<span class="user__mono">{{ statusTarget.loginName }}</span>）吗？
        </p>
        <p v-if="statusTarget.status === 'ACTIVE'" class="user__confirm-hint">
          停用立即生效：该账号的既有会话随即失效，且无法再次登录。不能停用自己、不能停用
          最后一个可用的超级管理员，仍担任货主负责人的员工须先转交名下货主——这三条由
          服务端判定，被拒绝时会给出原因。
        </p>
        <p v-else class="user__confirm-hint">启用后该账号可以重新登录。</p>
      </template>
    </Modal>

    <Modal
      :open="kickTarget !== null"
      title="踢下线"
      ok-text="确认踢下线"
      cancel-text="取消"
      :confirm-loading="kickSubmitting"
      :ok-button-props="{ danger: true }"
      :mask-closable="false"
      :width="480"
      @ok="confirmKick"
      @cancel="kickTarget = null"
    >
      <template v-if="kickTarget">
        <p class="user__confirm">
          确定让 <strong>{{ kickTarget.displayName }}</strong>
          （<span class="user__mono">{{ kickTarget.loginName }}</span>）的全部会话立即失效吗？
        </p>
        <p class="user__confirm-hint">
          失效后该用户的下一次请求会返回未授权，需要重新登录。踢下线不改变账号状态，
          也不影响本页的任何数据；要禁止登录请改用停用。
        </p>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.user__filters {
  margin-bottom: var(--space-2);
}

.user__reset {
  margin-left: var(--space-2);
}

.user__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--space-2);
}

.user__mono {
  font-family: var(--font-mono);
}

.user__hint {
  margin: var(--space-2) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-tertiary);
}

.user__confirm {
  margin: 0 0 var(--space-2);
  color: var(--color-text-primary);
}

.user__confirm-hint {
  margin: 0;
  color: var(--color-text-secondary);
}

.user__password-form {
  margin-top: var(--space-3);
}

.user__role-select {
  width: 100%;
}
</style>
