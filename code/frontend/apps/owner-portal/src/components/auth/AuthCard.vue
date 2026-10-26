<script setup lang="ts">
/**
 * 登录与改密共用的居中卡片外壳。
 *
 * 两页的版式要求是同一套（360px 宽、同一间距与圆角体系），写两份迟早会漂移；
 * 表单内容由页面通过默认插槽注入，卡片本身不关心字段。
 */
defineProps<{
  title: string
  subtitle: string
}>()
</script>

<template>
  <div class="auth">
    <section class="auth__panel">
      <h1 class="auth__title">{{ title }}</h1>
      <p class="auth__subtitle">{{ subtitle }}</p>
      <slot />
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  place-items: center;
  min-height: 100vh;
  background: var(--color-bg-page);
}

.auth__panel {
  width: 360px;
  padding: var(--space-6);
  background: var(--color-bg-container);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.auth__title {
  margin: 0;
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: var(--weight-semibold);
  color: var(--color-text-primary);
}

.auth__subtitle {
  margin: var(--space-1) 0 var(--space-5);
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}
</style>
