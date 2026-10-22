<script setup lang="ts">
import { PageHeader } from '@yunwms/ui'

const stats = [
  { label: '合作货主', value: '18' },
  { label: '在管仓库', value: '3' },
  { label: 'SKUID 总数', value: '30.2 万' },
  { label: '待审核档案', value: '126' }
]

const rows = [
  { warehouse: '宝安一号仓', owner: '深圳西之月', sku: 24810, pending: 42 },
  { warehouse: '龙岗二号仓', owner: '广州南风', sku: 18620, pending: 31 },
  { warehouse: '东莞三号仓', owner: '海外代运营', sku: 9320, pending: 53 }
]
</script>

<template>
  <div>
    <PageHeader title="工作台" description="骨架阶段数据为静态示例" />

    <div class="stats">
      <div v-for="item in stats" :key="item.label" class="stats__item panel">
        <span class="stats__label">{{ item.label }}</span>
        <strong class="stats__value">{{ item.value }}</strong>
      </div>
    </div>

    <section class="panel">
      <h2 class="panel__title">各仓商品与待审量</h2>
      <el-table :data="rows" border>
        <el-table-column prop="warehouse" label="仓库" min-width="160" />
        <el-table-column prop="owner" label="货主" min-width="160" />
        <el-table-column prop="sku" label="SKUID 数" width="140" />
        <el-table-column prop="pending" label="待审核" width="120" />
      </el-table>
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
  gap: var(--space-1);
}

.stats__label {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.stats__value {
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: var(--weight-semibold);
}
</style>
