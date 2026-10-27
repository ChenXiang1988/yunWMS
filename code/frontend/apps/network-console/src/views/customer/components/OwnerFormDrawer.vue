<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  Button,
  Drawer,
  Form,
  FormItem,
  Input,
  Select,
  SelectOption,
  Textarea,
  type FormInstance
} from 'ant-design-vue'
import type { OwnerDetail, OwnerInitialStatus } from '@/api/owner'
import type { OwnerSelectOption } from '../owner-model'
import {
  SKU_PREFIX_HINT,
  SKU_PREFIX_PATTERN,
  emptyOwnerForm,
  isPostalCodeRequired,
  ownerFormFromDetail,
  type OwnerFormValues,
  type OwnerProvinceOption
} from '../owner-form'

/**
 * 新建 / 编辑货主抽屉。
 *
 * 本组件只负责表单本身：字典选项由页面注入，提交由页面编排（`docs/frontend.md`
 * §4.3「组件内不直接发起请求，数据由页面注入」）。国家变化时本组件清空已选州省，
 * 并通知页面重新取该国的州省——州省属于国家，沿用旧列表会选到不属于该国的值。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    mode: 'create' | 'edit'
    /** 编辑时的详情；新建时为 null */
    owner?: OwnerDetail | null
    ownerTypeOptions?: OwnerSelectOption[]
    countryOptions?: OwnerSelectOption[]
    provinceOptions?: OwnerProvinceOption[]
    provincesLoading?: boolean
    submitting?: boolean
  }>(),
  {
    owner: null,
    ownerTypeOptions: () => [],
    countryOptions: () => [],
    provinceOptions: () => [],
    provincesLoading: false,
    submitting: false
  }
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  /** 国家被改动，页面据此重新拉取州省 */
  'country-change': [country: string]
  'submit-create': [form: OwnerFormValues, initialStatus: OwnerInitialStatus]
  'submit-update': [form: OwnerFormValues]
}>()

const formRef = ref<FormInstance>()
const form = reactive<OwnerFormValues>(emptyOwnerForm())

/** 打开时初始化：编辑回填详情，新建清空。只在打开瞬间做，不监听 owner 的每次变化 */
watch(
  () => props.open,
  (open) => {
    if (!open) {
      return
    }
    const filled = props.mode === 'edit' && props.owner
      ? ownerFormFromDetail(props.owner)
      : emptyOwnerForm()
    Object.assign(form, filled)
    formRef.value?.clearValidate()
  }
)

/**
 * 校验规则与后端一致：必填项、长度上限照 `OwnerCreateRequest` / `OwnerUpdateRequest`，
 * SKU 前缀正则照 `SkuPrefix`。前端只做同样的提示，规则真源仍在后端。
 *
 * 邮编是否必填取决于所选省州的字典扩展（港澳台为 false），未选州省时按必填处理。
 */
const rules = computed(() => ({
  name: [
    { required: true, message: '请输入客户名称' },
    { max: 100, message: '客户名称不能超过 100 个字符' }
  ],
  nameEn: [{ max: 100, message: '英文名称不能超过 100 个字符' }],
  shortName: [{ max: 20, message: '货主简称不能超过 20 个字符' }],
  externalCode: [{ max: 64, message: '外部码不能超过 64 个字符' }],
  skuPrefix: [
    { required: true, message: '请输入 SKU 前缀' },
    { pattern: SKU_PREFIX_PATTERN, message: SKU_PREFIX_HINT }
  ],
  ownerType: [{ required: true, message: '请选择客户类型' }],
  settlementEntity: [
    { required: true, message: '请输入结算主体' },
    { max: 100, message: '结算主体不能超过 100 个字符' }
  ],
  country: [{ required: true, message: '请选择注册国家' }],
  province: provinceRequired.value
    ? [{ required: true, message: '注册国家为美国时必须选择州省' }]
    : [],
  city: [
    { required: true, message: '请输入注册城市' },
    { max: 64, message: '注册城市不能超过 64 个字符' }
  ],
  district: [{ max: 64, message: '区县不能超过 64 个字符' }],
  postalCode: postalCodeRequired.value
    ? [{ required: true, message: '该地区必须填写邮政编码' }]
    : [{ max: 20, message: '邮政编码不能超过 20 个字符' }],
  addressLine: [
    { required: true, message: '请输入详细地址' },
    { max: 255, message: '详细地址不能超过 255 个字符' }
  ],
  remark: [{ max: 500, message: '备注不能超过 500 个字符' }]
}))

/** 州省：只有美国必填，其余国家可留空（与后端一致） */
const provinceRequired = computed(() => form.country === 'US')

/** 邮编：按所选省州的字典口径判定，无邮编地区（港澳台）可留空 */
const postalCodeRequired = computed(() =>
  isPostalCodeRequired(form.province, props.provinceOptions)
)

/** 国家变化由用户的 Select 交互触发，故不用 watch：打开抽屉回填时不该当成一次改动 */
function handleCountryChange(): void {
  form.province = ''
  emit('country-change', form.country)
}

function close(): void {
  emit('update:open', false)
}

/** 校验通过后把表单值拷一份交给页面，避免页面拿到仍在变化的响应式对象 */
async function validate(): Promise<OwnerFormValues | null> {
  try {
    await formRef.value?.validate()
    return { ...form }
  } catch {
    // antd 的 validate 以 reject 表示校验不通过，错误已由表单项就地展示
    return null
  }
}

async function submitCreate(initialStatus: OwnerInitialStatus): Promise<void> {
  const values = await validate()
  if (values) {
    emit('submit-create', values, initialStatus)
  }
}

async function submitUpdate(): Promise<void> {
  const values = await validate()
  if (values) {
    emit('submit-update', values)
  }
}
</script>

<template>
  <Drawer
    :open="open"
    :title="mode === 'create' ? '新建客户' : '编辑客户'"
    :width="560"
    :mask-closable="false"
    @close="close"
  >
    <Form ref="formRef" :model="form" :rules="rules" layout="vertical">
      <h3 class="section">基本信息</h3>

      <FormItem v-if="mode === 'edit'" label="系统编码">
        <Input :value="owner?.code ?? ''" disabled />
      </FormItem>
      <FormItem label="客户名称" name="name">
        <Input v-model:value="form.name" placeholder="如：深圳某某贸易" allow-clear />
      </FormItem>
      <FormItem label="英文名称" name="nameEn">
        <Input v-model:value="form.nameEn" allow-clear />
      </FormItem>
      <FormItem label="货主简称" name="shortName">
        <Input v-model:value="form.shortName" placeholder="用于 SKUID 标签，留空取名称前 8 字" allow-clear />
      </FormItem>
      <FormItem label="外部码" name="externalCode">
        <Input v-model:value="form.externalCode" placeholder="外部系统编码，未删除的货主之间唯一" allow-clear />
      </FormItem>
      <FormItem label="SKU 前缀" name="skuPrefix">
        <Input
          v-model:value="form.skuPrefix"
          :disabled="mode === 'edit'"
          :placeholder="SKU_PREFIX_HINT"
        />
      </FormItem>
      <FormItem label="客户类型" name="ownerType">
        <Select v-model:value="form.ownerType" placeholder="请选择">
          <SelectOption v-for="option in ownerTypeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </SelectOption>
        </Select>
      </FormItem>
      <FormItem label="结算主体" name="settlementEntity">
        <Input v-model:value="form.settlementEntity" placeholder="开票与对账的抬头" allow-clear />
      </FormItem>

      <h3 class="section">注册地址</h3>

      <!-- 国家与州省条目多，用 options 数据渲染并开启按 label 搜索；children 模式下
           optionFilterProp 匹配不到插槽文本 -->
      <FormItem label="国家 / 地区" name="country">
        <Select
          v-model:value="form.country"
          :options="countryOptions"
          placeholder="请选择"
          show-search
          option-filter-prop="label"
          @change="handleCountryChange"
        />
      </FormItem>
      <FormItem :label="provinceRequired ? '州省' : '州省（可留空）'" name="province">
        <Select
          v-model:value="form.province"
          :options="provinceOptions"
          :placeholder="form.country ? '请选择' : '请先选择国家'"
          :disabled="!form.country"
          :loading="provincesLoading"
          show-search
          option-filter-prop="label"
          allow-clear
        />
      </FormItem>
      <FormItem label="城市" name="city">
        <Input v-model:value="form.city" allow-clear />
      </FormItem>
      <FormItem label="区县" name="district">
        <Input v-model:value="form.district" allow-clear />
      </FormItem>
      <FormItem :label="postalCodeRequired ? '邮政编码' : '邮政编码（该地区可不填）'" name="postalCode">
        <Input v-model:value="form.postalCode" allow-clear />
      </FormItem>
      <FormItem label="详细地址" name="addressLine">
        <Input v-model:value="form.addressLine" allow-clear />
      </FormItem>
      <FormItem label="备注" name="remark">
        <Textarea v-model:value="form.remark" :rows="3" :maxlength="500" allow-clear />
      </FormItem>
    </Form>

    <template #footer>
      <div class="drawer-footer">
        <Button @click="close">取消</Button>
        <template v-if="mode === 'create'">
          <Button :loading="submitting" @click="submitCreate('DRAFT')">保存草稿</Button>
          <Button type="primary" :loading="submitting" @click="submitCreate('ACTIVE')">
            直接提交
          </Button>
        </template>
        <Button v-else type="primary" :loading="submitting" @click="submitUpdate">保存</Button>
      </div>
    </template>
  </Drawer>
</template>

<style scoped>
.section {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-base);
  font-weight: var(--weight-medium);
  color: var(--color-text-secondary);
}

.section + * {
  margin-top: var(--space-2);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
