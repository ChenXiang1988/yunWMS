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
  type FormInstance
} from 'ant-design-vue'
import { EmptyState } from '@yunwms/ui-web'
import {
  createOwnerTax,
  deleteOwnerTax,
  listOwnerTaxes,
  updateOwnerTax,
  type OwnerTax
} from '@/api/owner'
import { listDictItems } from '@/api/dict'
import { notifySuccess } from '@/api/notify'
import { PLACEHOLDER, displayText, type OwnerSelectOption } from '../owner-model'
import { useOwnerDicts } from '../use-owner-dicts'
import {
  TAX_NUMBER_MAX,
  TAX_TYPE_DICT,
  buildSetDefaultPayload,
  buildTaxPayload,
  canSetDefault,
  emptyTaxForm,
  taxFormFrom,
  taxTypeLabel,
  type TaxFormValues
} from '../tax-model'

/**
 * 税号页签：列表、新增、编辑、删除与设为默认。
 *
 * 四条由契约与既有实测决定的取舍：
 *
 * 1. 写操作后无条件重拉列表。请求层把业务失败统一 reject 成 `Error(后端中文消息)`，
 *    业务码（如并发抢默认的 40900）没有透出，`api/errors.ts` 也明确反对靠 message
 *    文案猜测错误类型。所以不判错误码、不匹配文案，成败都以服务端列表为准：成功时
 *    「设为默认」会连带改掉同国的另一行，失败时更可能是默认标记已被别人占走
 *    （`ux_owner_tax_default`）。默认的唯一性由后端保证，前端不自己算「谁是默认」。
 * 2. 新增/编辑表单与删除确认都做成页内区块，不用 Modal。本应用已有浏览器实测记录
 *    （见 `use-owner-lifecycle.ts` 的 `editFromDetail` 与 `use-owner-transitions.ts` 的
 *    `statusFromDetail`）：在详情抽屉之上再叠一层浮层，后开的浮层会落到抽屉遮罩下方，
 *    按钮点不到。页内区块绕开该问题，且不引入第二套交互。
 * 3. 不按货主状态置灰写入口：税号接口的契约只对「货主不可见 / 已逻辑删除」返回 40400，
 *    没有按货主状态拒绝写操作的规则。藏掉后端允许的操作等于自造规则。
 * 4. 编辑态把国家与税号类型置为只读：后端编辑时以记录自身为准，传别的值既不生效也不
 *    报错；留成可改会让人以为改得动，改完发现没变才是真问题。换国家或换类型走删旧登新。
 *
 * 业务规则一概不在这里复述：18 位形态、类型与国家是否匹配、州销售税号必须填州省，
 * 都由后端判。前端只做必填与长度，理由见 `tax-model.ts` 文件头。
 *
 * 请求在这里发（`docs/frontend.md` §4.3 要求数据由页面注入）：本页签是抽屉自持的容器，
 * 与联系人页签同一处理；后续抽出 `use-owner-taxes.ts` 可回收这一点。
 */
const props = defineProps<{
  /** 所属货主标识，取自详情；税号接口的路径参数都用它 */
  ownerId: string
}>()

/* -------------------------- 列表 -------------------------- */
const rows = ref<OwnerTax[]>([])
const loading = ref(false)
/** 列表请求序号：切换货主或连续刷新时，只认最后一次响应 */
let listRequestId = 0

async function loadTaxes(): Promise<void> {
  const requestId = ++listRequestId
  loading.value = true
  try {
    const items = await listOwnerTaxes(props.ownerId)
    if (requestId === listRequestId) {
      rows.value = items
    }
  } catch (error) {
    // 请求层已统一弹出提示。这里不清空 rows：空列表会被读成「这个货主没有税号」，
    // 那是在用空态掩盖故障
    console.error('[owner] 加载税号失败', error)
  } finally {
    if (requestId === listRequestId) {
      loading.value = false
    }
  }
}

/* -------------------------- 字典 -------------------------- */
/**
 * 国家与州省取自 `useOwnerDicts`：州省按国家过滤（`parentValue`）与请求序号的守卫
 * 已经在那里做过一遍，这里不复制一份。客户类型的三份返回值本页签用不到，不解构。
 */
const { countryOptions, provinceOptions, provincesLoading, loadCountries, loadProvinces } =
  useOwnerDicts()

/** 税号类型：四项固定，但仍按字典取——字典可扩类型，前端不写死枚举 */
const taxTypeOptions = ref<OwnerSelectOption[]>([])
const taxTypeLabels = ref<ReadonlyMap<string, string>>(new Map())

async function loadTaxTypes(): Promise<void> {
  try {
    const items = await listDictItems(TAX_TYPE_DICT)
    taxTypeOptions.value = items.map((item) => ({ value: item.value, label: item.label }))
    taxTypeLabels.value = new Map(items.map((item) => [item.value, item.label]))
  } catch (error) {
    // 字典失败只让下拉变空、类型列按原值回退，最终由后端字典校验否决
    console.error('[owner] 加载税号类型字典失败', error)
  }
}

/* -------------------------- 新增 / 编辑 -------------------------- */
const formRef = ref<FormInstance>()
const formValues = reactive<TaxFormValues>(emptyTaxForm())
const formOpen = ref(false)
const formSubmitting = ref(false)
/** 正在编辑的税号标识；null 表示新增 */
const editingId = ref<string | null>(null)

const isEditing = computed(() => editingId.value !== null)
const formTitle = computed(() => (isEditing.value ? '编辑税号' : '新增税号'))

/**
 * 校验规则与后端一致，只做必填与长度。
 *
 * `whitespace: true` 对应后端的「只填空白按未填处理」；长度上限取自契约的列宽（税号 64）。
 * 国家、州省、类型都是字典驱动的下拉，取值只可能来自字典项，不另立「是否属于字典」
 * 「类型是否适用于该国」的规则——这两条判定都在后端。
 *
 * 州省没有必填规则：州销售税号必须填州省是后端的业务规则，前端不复述，
 * 否则两处口径一旦漂移，前端会拦住后端本可接受的值。
 */
const rules = {
  country: [{ required: true, message: '请选择国家' }],
  taxType: [{ required: true, message: '请选择税号类型' }],
  taxNumber: [
    { required: true, whitespace: true, message: '请输入税号' },
    { max: TAX_NUMBER_MAX, message: `税号不能超过 ${TAX_NUMBER_MAX} 个字符` }
  ]
}

function openCreate(): void {
  cancelDelete()
  editingId.value = null
  Object.assign(formValues, emptyTaxForm())
  formOpen.value = true
  formRef.value?.clearValidate()
  // 新增时还没有国家，先清掉上一次编辑留下的州省列表
  void loadProvinces(undefined)
}

function openEdit(record: OwnerTax): void {
  cancelDelete()
  editingId.value = record.id
  Object.assign(formValues, taxFormFrom(record))
  formOpen.value = true
  formRef.value?.clearValidate()
  // 州省下拉按国家过滤：回填的是这条记录的国家，须先取该国的州省
  void loadProvinces(record.country)
}

function closeForm(): void {
  formOpen.value = false
  editingId.value = null
}

/** 国家变化由用户的 Select 交互触发，故不用 watch：编辑态置灰，回填时也不会走到这里 */
function handleCountryChange(): void {
  formValues.province = ''
  void loadProvinces(formValues.country)
}

/** 校验通过后把表单值拷一份交出去，避免提交期间用户继续打字改到已在途的请求体 */
async function validate(): Promise<TaxFormValues | null> {
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
  const taxId = editingId.value
  const payload = buildTaxPayload(values)
  formSubmitting.value = true
  try {
    if (taxId) {
      await updateOwnerTax(props.ownerId, taxId, payload)
      notifySuccess('税号已更新')
    } else {
      await createOwnerTax(props.ownerId, payload)
      notifySuccess('税号已新增')
    }
    closeForm()
  } catch (error) {
    // 失败时表单保持打开供修改；40000 的字段级提示已由请求层弹出
    console.error('[owner] 保存税号失败', error)
  } finally {
    formSubmitting.value = false
    // 成败都重拉，原因见文件头注释第 1 条
    await loadTaxes()
  }
}

/* -------------------------- 设为默认 -------------------------- */
const promotingId = ref<string | null>(null)

async function promote(record: OwnerTax): Promise<void> {
  promotingId.value = record.id
  try {
    // 编辑是全量替换：请求体带上该行原值，只把默认标记改成 true，否则会清掉州省与税号
    await updateOwnerTax(props.ownerId, record.id, buildSetDefaultPayload(record))
    notifySuccess(`已将 ${record.taxNumber} 设为该国默认税号`)
  } catch (error) {
    console.error('[owner] 设为默认税号失败', error)
  } finally {
    promotingId.value = null
    await loadTaxes()
  }
}

/* -------------------------- 删除 -------------------------- */
/** 待确认删除的税号；非 null 时表格上方出现确认区块 */
const deleteTarget = ref<OwnerTax | null>(null)
const deleteSubmitting = ref(false)

function openDelete(record: OwnerTax): void {
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
    await deleteOwnerTax(props.ownerId, target.id)
    notifySuccess(`税号 ${target.taxNumber} 已删除`)
  } catch (error) {
    // 40400（已被删除或货主不可见）与网络失败都落到这里。删除没有可修正的输入，
    // 弹不出「改完再提交」，因此成败都以刷新后的列表为准
    console.error('[owner] 删除税号失败', error)
  } finally {
    deleteSubmitting.value = false
    deleteTarget.value = null
    await loadTaxes()
  }
}

/* -------------------------- 表格列 -------------------------- */
/**
 * 国家与州省按接口原值展示（与详情「基本信息」页签同口径）：州省的字典项要按国家
 * 逐份取（`parentValue`），为一个列名给列表里每个国家各发一次请求不值得。
 * 税号类型取字典中文标签——契约写明 `taxType` 接口只给 value，展示时按字典取标签。
 */
const columns = [
  { title: '国家', key: 'country', width: 90 },
  { title: '州省', key: 'province', width: 130 },
  { title: '税号类型', key: 'taxType', width: 150 },
  { title: '税号', key: 'taxNumber', width: 200 },
  { title: '默认', key: 'isDefault', width: 80 },
  { title: '操作', key: 'actions', width: 190 }
]

watch(
  () => props.ownerId,
  () => {
    // 切到另一个货主时收起页内区块：表单里可能留着上一个货主填了一半的值，
    // 留着会让「保存」把 A 的输入提交到 B 名下
    closeForm()
    cancelDelete()
    void loadTaxes()
  },
  { immediate: true }
)

onMounted(() => {
  void loadCountries()
  void loadTaxTypes()
})
</script>

<template>
  <div class="tax">
    <div class="tax__toolbar">
      <Button type="primary" @click="openCreate">新增税号</Button>
    </div>

    <section v-if="formOpen" class="tax__form">
      <h4 class="tax__form-title">{{ formTitle }}</h4>

      <Form ref="formRef" :model="formValues" :rules="rules" layout="vertical">
        <FormItem label="国家 / 地区" name="country">
          <Select
            v-model:value="formValues.country"
            :options="countryOptions"
            :disabled="isEditing"
            placeholder="请选择"
            show-search
            option-filter-prop="label"
            @change="handleCountryChange"
          />
        </FormItem>
        <FormItem label="州省" name="province">
          <Select
            v-model:value="formValues.province"
            :options="provinceOptions"
            :placeholder="formValues.country ? '请选择' : '请先选择国家'"
            :disabled="!formValues.country"
            :loading="provincesLoading"
            show-search
            option-filter-prop="label"
            allow-clear
          />
        </FormItem>
        <FormItem label="税号类型" name="taxType">
          <Select
            v-model:value="formValues.taxType"
            :options="taxTypeOptions"
            :disabled="isEditing"
            placeholder="请选择"
          />
        </FormItem>
        <FormItem label="税号" name="taxNumber">
          <Input
            v-model:value="formValues.taxNumber"
            :maxlength="TAX_NUMBER_MAX"
            placeholder="按登记的原文填写，不做格式化"
            allow-clear
          />
        </FormItem>
        <FormItem name="isDefault">
          <Checkbox v-model:checked="formValues.isDefault">设为此国家的默认税号</Checkbox>
          <p class="tax__form-hint">
            同一货主在同一国家至多一条默认税号。勾选后本条成为该国默认，原默认由后端自动取消。
          </p>
        </FormItem>
      </Form>

      <p v-if="isEditing" class="tax__form-hint tax__form-identity">
        国家与税号类型创建后不可改，需要变更请删除本条后重新登记。
      </p>

      <div class="tax__form-actions">
        <Button @click="closeForm">取消</Button>
        <Button type="primary" :loading="formSubmitting" @click="submitForm">保存</Button>
      </div>
    </section>

    <Alert
      v-if="deleteTarget"
      class="tax__confirm"
      type="warning"
      show-icon
      message="删除后不可恢复"
    >
      <p class="tax__confirm-target">
        确定删除税号 <strong>{{ deleteTarget.taxNumber }}</strong>
        （{{ deleteTarget.country }} / {{ displayText(deleteTarget.province) }} /
        {{ displayText(taxTypeLabel(deleteTarget.taxType, taxTypeLabels)) }}）吗？
      </p>
      <p class="tax__confirm-hint">
        删除后该税号不再出现在本页签，记录本身保留供追溯。删除会释放该国家的默认名额，
        之后可以直接把另一条设为默认。
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
      class="tax__table"
      :data-source="rows"
      :columns="columns"
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 840 }"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'country'">
          {{ (record as OwnerTax).country }}
        </template>
        <template v-else-if="column.key === 'province'">
          {{ displayText((record as OwnerTax).province) }}
        </template>
        <template v-else-if="column.key === 'taxType'">
          {{ displayText(taxTypeLabel((record as OwnerTax).taxType, taxTypeLabels)) }}
        </template>
        <template v-else-if="column.key === 'taxNumber'">
          <span class="tax__number">{{ (record as OwnerTax).taxNumber }}</span>
        </template>
        <template v-else-if="column.key === 'isDefault'">
          <Tag v-if="(record as OwnerTax).isDefault" color="processing">默认</Tag>
          <span v-else class="tax__muted">{{ PLACEHOLDER }}</span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <Space :size="0">
            <Button type="link" size="small" @click="openEdit(record as OwnerTax)">编辑</Button>
            <Button
              v-if="canSetDefault(record as OwnerTax)"
              type="link"
              size="small"
              :loading="promotingId === (record as OwnerTax).id"
              @click="promote(record as OwnerTax)"
            >
              设为默认
            </Button>
            <Button
              type="link"
              size="small"
              danger
              @click="openDelete(record as OwnerTax)"
            >
              删除
            </Button>
          </Space>
        </template>
      </template>

      <template #emptyText>
        <EmptyState description="暂无税号" />
      </template>
    </Table>
  </div>
</template>

<style scoped>
.tax__toolbar {
  display: flex;
  justify-content: flex-end;
}

.tax__form {
  margin-top: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
}

.tax__form-title {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-base);
  font-weight: var(--weight-medium);
  color: var(--color-text-secondary);
}

.tax__form-hint {
  margin: var(--space-1) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-tertiary);
}

.tax__form-identity {
  margin-bottom: var(--space-3);
}

.tax__form-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.tax__confirm {
  margin-top: var(--space-3);
}

.tax__confirm-target {
  margin: 0 0 var(--space-2);
  color: var(--color-text-primary);
}

.tax__confirm-hint {
  margin: 0;
  color: var(--color-text-secondary);
}

.tax__table {
  margin-top: var(--space-3);
}

.tax__number {
  font-family: var(--font-mono);
}

.tax__muted {
  color: var(--color-text-tertiary);
}
</style>
