<script setup lang="ts">
withDefaults(defineProps<{ description?: string }>(), {
  description: '暂无数据'
})
</script>

<template>
  <div class="empty-state">
    <p class="empty-state__text">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  padding: var(--space-12) var(--space-6);
  text-align: center;
}

.empty-state__text {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}
</style>
