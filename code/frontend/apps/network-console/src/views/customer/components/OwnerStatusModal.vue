<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Alert, Button, Checkbox, Modal, Radio, RadioGroup, Spin } from 'ant-design-vue'
import type { OwnerStatus, TerminationImpact } from '@/api/owner'
import { OWNER_STATUS_TEXT, allowedStatusTargets, ownerStatusText } from '../owner-status'

/**
 * 状态流转弹窗。
 *
 * 只列当前状态的合法目标（流转表见 `owner-status.ts`），后端仍做最终校验——表里没有
 * 的流向不显示，是省掉一次注定 40900 的提交，不是权限。
 *
 * 目标为 TERMINATED 时要先看影响清单并勾选确认：终止只改货主状态，不停用账号、
 * 不改商品档案，清单是提示而非级联动作，因此必须由人明确确认。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    /** 目标货主：列表行或详情都满足这个结构 */
    owner?: { id: string; code: string; name: string; status: OwnerStatus } | null
    /** 终止影响清单；未选终止或尚未取回时为 null */
    impact?: TerminationImpact | null
    impactLoading?: boolean
    submitting?: boolean
  }>(),
  {
    owner: null,
    impact: null,
    impactLoading: false,
    submitting: false
  }
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  /** 目标状态变化，页面据此在选定终止时加载影响清单 */
  'target-change': [target: OwnerStatus]
  /** 影响清单取失败后重试 */
  'reload-impact': []
  submit: [payload: { targetStatus: OwnerStatus; acknowledgedImpact?: boolean }]
}>()

const target = ref<OwnerStatus | undefined>(undefined)
const acknowledged = ref(false)

/** 可选目标：由当前状态决定；终止态为空 */
const targets = computed(() =>
  props.owner
    ? allowedStatusTargets(props.owner.status).map((value) => ({
        value,
        label: OWNER_STATUS_TEXT[value]
      }))
    : []
)

const terminating = computed(() => target.value === 'TERMINATED')

/** 终止时必须勾选确认，且影响清单已取回；未取回时不允许提交 */
const okDisabled = computed(() => {
  if (!target.value) {
    return true
  }
  if (!terminating.value) {
    return false
  }
  return props.impactLoading || !props.impact || !acknowledged.value
})

watch(
  () => props.open,
  (open) => {
    if (!open) {
      return
    }
    target.value = undefined
    acknowledged.value = false
  }
)

watch(target, (next) => {
  acknowledged.value = false
  if (next) {
    emit('target-change', next)
  }
})

function close(): void {
  emit('update:open', false)
}

function submit(): void {
  if (!target.value || okDisabled.value) {
    return
  }
  emit('submit', {
    targetStatus: target.value,
    // 非终止目标不带该字段：后端只在目标为终止时读它
    acknowledgedImpact: terminating.value ? true : undefined
  })
}
</script>

<template>
  <Modal
    :open="open"
    title="状态流转"
    ok-text="确认流转"
    cancel-text="取消"
    :confirm-loading="submitting"
    :ok-button-props="{ disabled: okDisabled }"
    :mask-closable="false"
    @ok="submit"
    @cancel="close"
  >
    <p v-if="owner" class="status__current">
      当前货主：{{ owner.name }}（{{ owner.code }}），当前状态
      <strong>{{ ownerStatusText(owner.status) }}</strong>
    </p>

    <Alert
      v-if="targets.length === 0"
      type="info"
      show-icon
      message="该货主已是终止状态，没有可用的流转目标"
    />

    <template v-else>
      <RadioGroup v-model:value="target" class="status__targets">
        <Radio v-for="option in targets" :key="option.value" :value="option.value">
          {{ option.label }}
        </Radio>
      </RadioGroup>

      <div v-if="terminating" class="status__impact">
        <div v-if="impactLoading" class="status__impact-loading">
          <Spin />
        </div>
        <template v-else-if="impact">
          <Alert
            type="warning"
            show-icon
            message="终止后不可恢复"
            description="终止只变更货主状态：商品档案状态不变、账号不会被停用，但这些商品与账号不能再用于新的业务单据。"
          />
          <ul class="status__impact-list">
            <li>在册商品：{{ impact.skuCount }} 个</li>
            <li>在册账号：{{ impact.accountCount }} 个</li>
          </ul>
          <Checkbox v-model:checked="acknowledged">
            我已了解上述影响，确认终止该货主
          </Checkbox>
        </template>
        <Alert
          v-else
          type="error"
          show-icon
          message="影响清单加载失败，请重试后再终止"
          description="未取到影响清单时不允许终止：确认弹窗不能在没有数据的情况下让人点确认。"
        >
          <template #action>
            <Button size="small" @click="emit('reload-impact')">重新加载</Button>
          </template>
        </Alert>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
.status__current {
  margin: 0 0 var(--space-3);
  color: var(--color-text-secondary);
}

.status__targets {
  display: flex;
  gap: var(--space-4);
}

.status__impact {
  margin-top: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.status__impact-loading {
  display: flex;
  justify-content: center;
  padding: var(--space-6) 0;
}

.status__impact-list {
  margin: 0;
  padding-left: var(--space-5);
  color: var(--color-text-primary);
}
</style>
