<script setup lang="ts">
import { PageHeader } from '@yunwms/ui'

const stats = [
  { label: '在库商品', value: '1,248' },
  { label: '待审核档案', value: '12' },
  { label: '本月新建', value: '86' },
  { label: '子账号', value: '5' }
]

const todos = [
  { title: '12 条商品档案待 3PL 审核', time: '今天 09:20' },
  { title: '合同将于 28 天后到期', time: '昨天 17:40' },
  { title: '3 个商品尚未打印 SKUID 标签', time: '昨天 11:05' }
]
</script>

<template>
  <div>
    <PageHeader title="工作台" description="当前仅展示骨架数据，接口接入后替换" />

    <div class="stats">
      <div v-for="item in stats" :key="item.label" class="stats__item panel">
        <span class="stats__label">{{ item.label }}</span>
        <strong class="stats__value">{{ item.value }}</strong>
      </div>
    </div>

    <section class="panel">
      <h2 class="panel__title">待办</h2>
      <ul class="todo">
        <li v-for="item in todos" :key="item.title" class="todo__item">
          <span>{{ item.title }}</span>
          <time>{{ item.time }}</time>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--card-gap);
  margin-bottom: var(--card-gap);
}

.stats__item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.stats__label {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}

.stats__value {
  font-size: var(--text-2xl);
  line-height: var(--leading-2xl);
  font-weight: var(--weight-semibold);
  color: var(--color-text-primary);
}

.todo {
  margin: 0;
  padding: 0;
  list-style: none;
}

.todo__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-border-light);
  font-size: var(--font-size-base);
}

.todo__item:last-child {
  border-bottom: none;
}

.todo__item time {
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
}
</style>
