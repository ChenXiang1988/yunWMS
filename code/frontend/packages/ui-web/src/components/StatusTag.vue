<script setup lang="ts">
import { Tag } from 'ant-design-vue'

type Status = 'draft' | 'pending' | 'active' | 'disabled' | 'rejected'

withDefaults(defineProps<{ status: Status; text?: string }>(), {
  text: ''
})

/**
 * 用 antd 的预设语义色（success / warning / error / default），
 * 不传色值，颜色由 ConfigProvider 的 token 派生。
 */
const TONE: Record<Status, 'default' | 'success' | 'warning' | 'error'> = {
  draft: 'default',
  pending: 'warning',
  active: 'success',
  disabled: 'default',
  rejected: 'error'
}
</script>

<template>
  <Tag :color="TONE[status]">{{ text || status }}</Tag>
</template>
