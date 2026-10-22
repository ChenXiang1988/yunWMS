<script setup lang="ts">
import { Empty } from 'ant-design-vue'

withDefaults(defineProps<{ description?: string }>(), {
  description: '暂无数据'
})
</script>

<template>
  <div class="empty-state">
    <Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="description">
      <slot />
    </Empty>
  </div>
</template>

<style scoped>
.empty-state {
  padding: var(--space-12) var(--space-6);
  text-align: center;
}
</style>
