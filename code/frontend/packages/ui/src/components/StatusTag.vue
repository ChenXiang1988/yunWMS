<script setup lang="ts">
import { computed } from 'vue'

type Status = 'draft' | 'pending' | 'active' | 'disabled' | 'rejected'

const props = withDefaults(defineProps<{ status: Status; text?: string }>(), {
  text: ''
})

const TYPE_MAP: Record<Status, 'info' | 'warning' | 'success' | 'danger'> = {
  draft: 'info',
  pending: 'warning',
  active: 'success',
  disabled: 'info',
  rejected: 'danger'
}

const tagType = computed(() => TYPE_MAP[props.status])
</script>

<template>
  <el-tag :type="tagType" disable-transitions size="small">
    {{ text || status }}
  </el-tag>
</template>
