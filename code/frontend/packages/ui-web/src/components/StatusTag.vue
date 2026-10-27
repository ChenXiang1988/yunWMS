<script setup lang="ts">
import { Tag } from 'ant-design-vue'

/**
 * 两套状态词表并存：
 *
 * - 小写五值是商品审核等存量页面的状态词（product 页仍在用），保留不动；
 * - 大写五值是后端货主状态枚举（DRAFT / POTENTIAL / ACTIVE / SUSPENDED / TERMINATED）。
 *
 * 一次性把存量页面改成大写会扩散到多个 change，故此处按 design D4 兼容两套。
 */
type LowercaseStatus = 'draft' | 'pending' | 'active' | 'disabled' | 'rejected'
type UppercaseStatus = 'DRAFT' | 'POTENTIAL' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED'

type Status = LowercaseStatus | UppercaseStatus

type Tone = 'default' | 'success' | 'processing' | 'warning' | 'error'

withDefaults(defineProps<{ status: Status; text?: string }>(), {
  text: ''
})

/**
 * 用 antd 的预设语义色，不传色值，颜色由 ConfigProvider 的 token 派生。
 *
 * 两套词表分别映射，互不覆盖：小写表沿用原取值，保证 product 页视觉不变。
 */
const TONE: Record<Status, Tone> = {
  draft: 'default',
  pending: 'warning',
  active: 'success',
  disabled: 'default',
  rejected: 'error',

  DRAFT: 'default',
  POTENTIAL: 'processing',
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  TERMINATED: 'error'
}
</script>

<template>
  <Tag :color="TONE[status]">{{ text || status }}</Tag>
</template>
