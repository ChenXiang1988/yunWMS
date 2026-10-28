<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Alert,
  Button,
  Checkbox,
  Form,
  FormItem,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Textarea,
  type FormInstance
} from 'ant-design-vue'
import { EmptyState } from '@yunwms/ui-web'
import {
  createOwnerContact,
  deleteOwnerContact,
  listOwnerContacts,
  updateOwnerContact,
  type OwnerContact
} from '@/api/owner'
import { listDictItems } from '@/api/dict'
import { notifySuccess } from '@/api/notify'
import { PLACEHOLDER, displayText, type OwnerSelectOption } from '../owner-model'
import {
  CONTACT_EMAIL_MAX,
  CONTACT_NAME_MAX,
  CONTACT_PHONE_MAX,
  CONTACT_REMARK_MAX,
  CONTACT_ROLE_DICT,
  buildContactPayload,
  buildPrimaryPayload,
  canPromoteToPrimary,
  contactFormFrom,
  contactRoleLabel,
  emptyContactForm,
  type ContactFormValues
} from '../contact-model'

/**
 * 联系人页签：列表、新增、编辑、删除与设为主。
 *
 * 三条由契约与既有实测决定的取舍：
 *
 * 1. 写操作后无条件重拉列表。请求层把业务失败统一 reject 成 `Error(后端中文消息)`，
 *    业务码（如并发抢主的 40900）没有透出，`api/errors.ts` 也明确反对靠 message 文案
 *    猜测错误类型。所以不判错误码、不匹配文案，成败都以服务端列表为准：成功时「设为主」
 *    会连带改掉另一行，失败时更可能是主标记已被别人占走。主标记的唯一性由后端保证，
 *    前端不自己算「谁是主」。
 * 2. 新增/编辑表单与删除确认都做成页内区块，不用 Modal。本应用已有浏览器实测记录
 *    （见 `use-owner-lifecycle.ts` 的 `editFromDetail` 与 `use-owner-transitions.ts` 的
 *    `statusFromDetail`）：在详情抽屉之上再叠一层浮层，后开的浮层会落到抽屉遮罩下方，
 *    按钮点不到。页内区块绕开该问题，且不引入第二套交互。
 * 3. 不按货主状态置灰写入口：联系人接口的契约只对「货主不可见 / 已逻辑删除」返回 40400，
 *    没有按货主状态拒绝写操作的规则。藏掉后端允许的操作等于自造规则。
 *
 * 请求在这里发（`docs/frontend.md` §4.3 要求数据由页面注入）：本页签是抽屉自持的容器，
 * 本次未引入新的 composable 文件；后续抽出 `use-owner-contacts.ts` 可回收这一点。
 */
const props = defineProps<{
  /** 所属货主标识，取自详情；联系人接口的路径参数都用它 */
  ownerId: string
}>()

/* -------------------------- 列表 -------------------------- */
const rows = ref<OwnerContact[]>([])
const loading = ref(false)
/** 列表请求序号：切换货主或连续刷新时，只认最后一次响应 */
let listRequestId = 0

async function loadContacts(): Promise<void> {
  const requestId = ++listRequestId
  loading.value = true
  try {
    const items = await listOwnerContacts(props.ownerId)
    if (requestId === listRequestId) {
      rows.value = items
    }
  } catch (error) {
    // 请求层已统一弹出提示。这里不清空 rows：空列表会被读成「这个货主没有联系人」，
    // 那是在用空态掩盖故障
    console.error('[owner] 加载联系人失败', error)
  } finally {
    if (requestId === listRequestId) {
      loading.value = false
    }
  }
}

/* -------------------------- 角色字典 -------------------------- */
const roleOptions = ref<OwnerSelectOption[]>([])
const roleLabels = ref<ReadonlyMap<string, string>>(new Map())

async function loadRoles(): Promise<void> {
  try {
    const items = await listDictItems(CONTACT_ROLE_DICT)
    roleOptions.value = items.map((item) => ({ value: item.value, label: item.label }))
    roleLabels.value = new Map(items.map((item) => [item.value, item.label]))
  } catch (error) {
    // 字典失败只让下拉变空、角色列按原值回退，最终由后端字典校验否决
    console.error('[owner] 加载联系人角色字典失败', error)
  }
}

/* -------------------------- 新增 / 编辑 -------------------------- */
const formRef = ref<FormInstance>()
const formValues = reactive<ContactFormValues>(emptyContactForm())
const formOpen = ref(false)
const formSubmitting = ref(false)
/** 正在编辑的联系人标识；null 表示新增 */
const editingId = ref<string | null>(null)

const formTitle = computed(() => (editingId.value ? '编辑联系人' : '新增联系人'))

/**
 * 校验规则与后端一致：三项必填加长度上限。
 *
 * `whitespace: true` 对应后端的「只填空白按未填处理」；长度上限取自契约的列宽
 * （姓名 64、电话 32、邮箱 128、备注 255）。角色只校验必填——它的合法取值来自字典，
 * 下拉选项本身就是字典项，不另立「是否属于字典」的规则。
 */
const rules = {
  name: [
    { required: true, whitespace: true, message: '请输入联系人姓名' },
    { max: CONTACT_NAME_MAX, message: `联系人姓名不能超过 ${CONTACT_NAME_MAX} 个字符` }
  ],
  roleType: [{ required: true, message: '请选择联系人角色' }],
  phone: [
    { required: true, whitespace: true, message: '请输入联系电话' },
    { max: CONTACT_PHONE_MAX, message: `联系电话不能超过 ${CONTACT_PHONE_MAX} 个字符` }
  ],
  email: [{ max: CONTACT_EMAIL_MAX, message: `邮箱不能超过 ${CONTACT_EMAIL_MAX} 个字符` }],
  remark: [{ max: CONTACT_REMARK_MAX, message: `备注不能超过 ${CONTACT_REMARK_MAX} 个字符` }]
}

function openCreate(): void {
  cancelDelete()
  editingId.value = null
  Object.assign(formValues, emptyContactForm())
  formOpen.value = true
  formRef.value?.clearValidate()
}

function openEdit(record: OwnerContact): void {
  cancelDelete()
  editingId.value = record.id
  Object.assign(formValues, contactFormFrom(record))
  formOpen.value = true
  formRef.value?.clearValidate()
}

function closeForm(): void {
  formOpen.value = false
  editingId.value = null
}

/** 校验通过后把表单值拷一份交出去，避免提交期间用户继续打字改到已在途的请求体 */
async function validate(): Promise<ContactFormValues | null> {
  try {
    await formRef.value?.validate()
    return { ...formValues }
  } catch {
    // antd 的 validate 以 reject 表示校验不通过，错误已由表单项就地展示
    return null
  }
}

async function submitForm(): Promise<void> {
  const values = await validate()
  if (!values) {
    return
  }
  const contactId = editingId.value
  const payload = buildContactPayload(values)
  formSubmitting.value = true
  try {
    if (contactId) {
      await updateOwnerContact(props.ownerId, contactId, payload)
      notifySuccess('联系人已更新')
    } else {
      await createOwnerContact(props.ownerId, payload)
      notifySuccess('联系人已新增')
    }
    closeForm()
  } catch (error) {
    // 失败时表单保持打开供修改；40000 的字段级提示已由请求层弹出
    console.error('[owner] 保存联系人失败', error)
  } finally {
    formSubmitting.value = false
    // 成败都重拉，原因见文件头注释第 1 条
    await loadContacts()
  }
}

/* -------------------------- 设为主 -------------------------- */
const promotingId = ref<string | null>(null)

async function promote(record: OwnerContact): Promise<void> {
  promotingId.value = record.id
  try {
    // 编辑是全量替换：请求体带上该行原值，只把主标记改成 true，否则会清掉邮箱与备注
    await updateOwnerContact(props.ownerId, record.id, buildPrimaryPayload(record))
    notifySuccess(`已将 ${record.name} 设为主联系人`)
  } catch (error) {
    console.error('[owner] 设为主联系人失败', error)
  } finally {
    promotingId.value = null
    await loadContacts()
  }
}

/* -------------------------- 删除 -------------------------- */
/** 待确认删除的联系人；非 null 时表格上方出现确认区块 */
const deleteTarget = ref<OwnerContact | null>(null)
const deleteSubmitting = ref(false)

function openDelete(record: OwnerContact): void {
  closeForm()
  deleteTarget.value = record
}

function cancelDelete(): void {
  deleteTarget.value = null
}

async function confirmDelete(): Promise<void> {
  const target = deleteTarget.value
  if (!target) {
    return
  }
  deleteSubmitting.value = true
  try {
    await deleteOwnerContact(props.ownerId, target.id)
    notifySuccess(`联系人 ${target.name} 已删除`)
  } catch (error) {
    // 40400（已被删除或货主不可见）与网络失败都落到这里。删除没有可修正的输入，
    // 弹不出「改完再提交」，因此成败都以刷新后的列表为准
    console.error('[owner] 删除联系人失败', error)
  } finally {
    deleteSubmitting.value = false
    deleteTarget.value = null
    await loadContacts()
  }
}

/* -------------------------- 表格列 -------------------------- */
/**
 * 列名用「角色」而不是 `ui-structure.md` 第 218 行写的「职务」：接口字段是 `roleType`，
 * 取值来自 CONTACT_ROLE 字典，「职务」在这份契约里没有对应字段。
 */
const columns = [
  { title: '姓名', key: 'name', width: 110 },
  { title: '角色', key: 'roleType', width: 110 },
  { title: '联系电话', key: 'phone', width: 140 },
  { title: '邮箱', key: 'email', width: 170 },
  { title: '主联系人', key: 'isPrimary', width: 84 },
  { title: '操作', key: 'actions', width: 150 }
]

watch(
  () => props.ownerId,
  () => {
    void loadContacts()
  },
  { immediate: true }
)

onMounted(() => {
  void loadRoles()
})
</script>

<template>
  <div class="contact">
    <div class="contact__toolbar">
      <Button type="primary" @click="openCreate">新增联系人</Button>
    </div>

    <section v-if="formOpen" class="contact__form">
      <h4 class="contact__form-title">{{ formTitle }}</h4>

      <Form ref="formRef" :model="formValues" :rules="rules" layout="vertical">
        <FormItem label="姓名" name="name">
          <Input
            v-model:value="formValues.name"
            :maxlength="CONTACT_NAME_MAX"
            placeholder="联系人的称呼，可重名"
            allow-clear
          />
        </FormItem>
        <FormItem label="角色" name="roleType">
          <Select v-model:value="formValues.roleType" :options="roleOptions" placeholder="请选择" />
        </FormItem>
        <FormItem label="联系电话" name="phone">
          <Input
            v-model:value="formValues.phone"
            :maxlength="CONTACT_PHONE_MAX"
            placeholder="国际区号加号码，如 +86 13800000000"
            allow-clear
          />
        </FormItem>
        <FormItem label="邮箱" name="email">
          <Input v-model:value="formValues.email" :maxlength="CONTACT_EMAIL_MAX" allow-clear />
        </FormItem>
        <FormItem label="备注" name="remark">
          <Textarea
            v-model:value="formValues.remark"
            :rows="3"
            :maxlength="CONTACT_REMARK_MAX"
            allow-clear
          />
        </FormItem>
        <FormItem name="isPrimary">
          <Checkbox v-model:checked="formValues.isPrimary">设为主联系人</Checkbox>
          <p class="contact__form-hint">
            同一货主至多一条主联系人。勾选后本条成为主联系人，原主联系人由后端自动取消。
          </p>
        </FormItem>
      </Form>

      <div class="contact__form-actions">
        <Button @click="closeForm">取消</Button>
        <Button type="primary" :loading="formSubmitting" @click="submitForm">保存</Button>
      </div>
    </section>

    <Alert
      v-if="deleteTarget"
      class="contact__confirm"
      type="warning"
      show-icon
      message="删除后不可恢复"
    >
      <p class="contact__confirm-target">
        确定删除联系人 <strong>{{ deleteTarget.name }}</strong>
        （{{ displayText(contactRoleLabel(deleteTarget.roleType, roleLabels)) }}）吗？
      </p>
      <p class="contact__confirm-hint">
        删除后该联系人不再出现在本页签，记录本身保留供追溯。删除会释放主联系人名额，
        之后可以直接把另一条设为主。
      </p>
      <template #action>
        <Space>
          <Button size="small" @click="cancelDelete">取消</Button>
          <Button size="small" danger :loading="deleteSubmitting" @click="confirmDelete">
            确认删除
          </Button>
        </Space>
      </template>
    </Alert>

    <Table
      class="contact__table"
      :data-source="rows"
      :columns="columns"
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 764 }"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          {{ (record as OwnerContact).name }}
        </template>
        <template v-else-if="column.key === 'roleType'">
          {{ displayText(contactRoleLabel((record as OwnerContact).roleType, roleLabels)) }}
        </template>
        <template v-else-if="column.key === 'phone'">
          {{ (record as OwnerContact).phone }}
        </template>
        <template v-else-if="column.key === 'email'">
          {{ displayText((record as OwnerContact).email) }}
        </template>
        <template v-else-if="column.key === 'isPrimary'">
          <Tag v-if="(record as OwnerContact).isPrimary" color="processing">主</Tag>
          <span v-else class="contact__muted">{{ PLACEHOLDER }}</span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <Space :size="0">
            <Button type="link" size="small" @click="openEdit(record as OwnerContact)">编辑</Button>
            <Button
              v-if="canPromoteToPrimary(record as OwnerContact)"
              type="link"
              size="small"
              :loading="promotingId === (record as OwnerContact).id"
              @click="promote(record as OwnerContact)"
            >
              设为主
            </Button>
            <Button
              type="link"
              size="small"
              danger
              @click="openDelete(record as OwnerContact)"
            >
              删除
            </Button>
          </Space>
        </template>
      </template>

      <template #emptyText>
        <EmptyState description="暂无联系人" />
      </template>
    </Table>
  </div>
</template>

<style scoped>
.contact__toolbar {
  display: flex;
  justify-content: flex-end;
}

.contact__form {
  margin-top: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
}

.contact__form-title {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-base);
  font-weight: var(--weight-medium);
  color: var(--color-text-secondary);
}

.contact__form-hint {
  margin: var(--space-1) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-tertiary);
}

.contact__form-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.contact__confirm {
  margin-top: var(--space-3);
}

.contact__confirm-target {
  margin: 0 0 var(--space-2);
  color: var(--color-text-primary);
}

.contact__confirm-hint {
  margin: 0;
  color: var(--color-text-secondary);
}

.contact__table {
  margin-top: var(--space-3);
}

.contact__muted {
  color: var(--color-text-tertiary);
}
</style>
