<script setup lang="ts">
import { computed } from 'vue'
import { Button, Space, Table } from 'ant-design-vue'
import { EmptyState, StatusTag } from '@yunwms/ui-web'
import type { OwnerListItem } from '@/api/owner'
import { formatIsoDateTime, ownerTypeLabel } from '../owner-model'
import { canDeleteOwner, isOwnerWritable, ownerStatusText } from '../owner-status'

/**
 * 客户列表表格：列定义、单元格渲染与操作入口。
 *
 * 数据由页面注入，操作只抛出意图（`docs/frontend.md` §4.3），发请求与刷新留在页面。
 * 分页受控：页码、每页条数与总数都以后端响应为准，不拉全量。
 */
const props = defineProps<{
  rows: OwnerListItem[]
  loading: boolean
  current: number
  pageSize: number
  total: number
  ownerTypeLabels: ReadonlyMap<string, string>
}>()

const emit = defineEmits<{
  /** 打开详情抽屉 */
  view: [record: OwnerListItem]
  /** 打开编辑抽屉，页面据此按 id 取详情回填 */
  edit: [record: OwnerListItem]
  /** 打开状态流转弹窗 */
  'change-status': [record: OwnerListItem]
  /** 打开删除确认弹窗 */
  delete: [record: OwnerListItem]
  /** 分页变更：页码必传，每页条数缺省表示沿用当前值 */
  'page-change': [current: number, pageSize?: number]
}>()

const columns = [
  { title: '系统编码', key: 'code', width: 130 },
  { title: '客户名称', key: 'name' },
  { title: '客户类型', key: 'ownerType', width: 120 },
  { title: '状态', key: 'status', width: 100 },
  { title: '更新时间', key: 'updatedAt', width: 170 },
  { title: '操作', key: 'actions', width: 200 }
]

const pagination = computed(() => ({
  current: props.current,
  pageSize: props.pageSize,
  total: props.total,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (count: number) => `共 ${count} 条`
}))

interface TableChangePaging {
  current?: number
  pageSize?: number
}

function handleChange(paging: TableChangePaging): void {
  emit('page-change', paging.current ?? 1, paging.pageSize)
}
</script>

<template>
  <Table
    :data-source="rows"
    :columns="columns"
    :loading="loading"
    :pagination="pagination"
    :scroll="{ x: 1080 }"
    row-key="id"
    bordered
    size="middle"
    @change="handleChange"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'code'">
        <span class="owner-code">{{ (record as OwnerListItem).code }}</span>
      </template>
      <template v-else-if="column.key === 'name'">
        <button type="button" class="owner-name" @click="emit('view', record as OwnerListItem)">
          {{ (record as OwnerListItem).name }}
        </button>
      </template>
      <template v-else-if="column.key === 'ownerType'">
        {{ ownerTypeLabel((record as OwnerListItem).ownerType, ownerTypeLabels) }}
      </template>
      <template v-else-if="column.key === 'status'">
        <StatusTag
          :status="(record as OwnerListItem).status"
          :text="ownerStatusText((record as OwnerListItem).status)"
        />
      </template>
      <template v-else-if="column.key === 'updatedAt'">
        {{ formatIsoDateTime((record as OwnerListItem).updatedAt) }}
      </template>
      <template v-else-if="column.key === 'actions'">
        <Space :size="0">
          <Button type="link" size="small" @click="emit('view', record as OwnerListItem)">
            查看
          </Button>
          <Button
            type="link"
            size="small"
            :disabled="!isOwnerWritable((record as OwnerListItem).status)"
            @click="emit('edit', record as OwnerListItem)"
          >
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            :disabled="!isOwnerWritable((record as OwnerListItem).status)"
            @click="emit('change-status', record as OwnerListItem)"
          >
            状态
          </Button>
          <Button
            v-if="canDeleteOwner((record as OwnerListItem).status)"
            type="link"
            size="small"
            danger
            @click="emit('delete', record as OwnerListItem)"
          >
            删除
          </Button>
        </Space>
      </template>
    </template>

    <template #emptyText>
      <EmptyState description="暂无货主档案" />
    </template>
  </Table>
</template>

<style scoped>
.owner-code {
  font-family: var(--font-mono);
}

/* 客户名称可点进详情：按钮只是可点击文本，视觉按链接处理，不自造链接样式 */
.owner-name {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--color-text-link);
  cursor: pointer;
}

.owner-name:hover {
  color: var(--color-primary-hover);
}
</style>
