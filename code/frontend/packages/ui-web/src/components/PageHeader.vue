<script setup lang="ts">
withDefaults(defineProps<{ title: string; description?: string }>(), {
  description: ''
})
</script>

<template>
  <header class="page-header">
    <div class="page-header__main">
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
    </div>
    <div class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--section-gap);
}

.page-header__title {
  margin: 0;
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: var(--weight-semibold);
  color: var(--color-text-primary);
}

.page-header__desc {
  margin: var(--space-1) 0 0;
  font-size: var(--font-size-base);
  line-height: var(--line-height-base);
  color: var(--color-text-tertiary);
}

.page-header__actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}
</style>
