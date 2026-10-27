<script setup lang="ts">
import { Alert, Modal } from 'ant-design-vue'

/**
 * 删除草稿货主的二次确认。
 *
 * 只有草稿可删（`owner-status.ts` 的 `canDeleteOwner`），已提交的货主应先走状态流转：
 * 删除不是「停止合作」的替代品。后端对非草稿返回 40900，这里提前不给入口。
 *
 * 删除是软删除，且不释放系统编码与 SKU 前缀——文案要说清楚，否则用户会以为
 * 删掉之后能拿同一个前缀重建档。
 */
withDefaults(
  defineProps<{
    open: boolean
    owner?: { id: string; code: string; name: string } | null
    submitting?: boolean
  }>(),
  {
    owner: null,
    submitting: false
  }
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  submit: []
}>()

function close(): void {
  emit('update:open', false)
}
</script>

<template>
  <Modal
    :open="open"
    title="删除草稿货主"
    ok-text="确认删除"
    cancel-text="取消"
    :confirm-loading="submitting"
    :ok-button-props="{ danger: true }"
    :mask-closable="false"
    @ok="emit('submit')"
    @cancel="close"
  >
    <p v-if="owner" class="delete__target">
      确定删除草稿货主 <strong>{{ owner.name }}</strong>
      （<span class="delete__mono">{{ owner.code }}</span>）吗？
    </p>

    <Alert
      type="warning"
      show-icon
      message="删除后不可恢复"
      description="该货主将不再出现在列表与详情中。系统编码与 SKU 前缀不会被释放，重新建档时用同一个前缀仍会被判为已占用。"
    />
  </Modal>
</template>

<style scoped>
.delete__target {
  margin: 0 0 var(--space-3);
  color: var(--color-text-primary);
}

.delete__mono {
  font-family: var(--font-mono);
}
</style>
