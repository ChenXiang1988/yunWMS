<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Table } from 'ant-design-vue'
import { EmptyState, PageHeader, StatusTag } from '@yunwms/ui-web'
import { listOwnerTypes } from '@/api/dict'
import { listOwners, type OwnerListItem } from '@/api/owner'
import OwnerFilters from './components/OwnerFilters.vue'
import {
  DEFAULT_PAGE_SIZE,
  formatIsoDateTime,
  normalizeOwnerQuery,
  ownerStatusText,
  ownerTypeLabel,
  type OwnerFilterValues
} from './owner-model'

const columns = [
  { title: '系统编码', key: 'code', width: 140 },
  { title: '客户名称', dataIndex: 'name', key: 'name' },
  { title: '客户类型', key: 'ownerType', width: 120 },
  { title: '状态', key: 'status', width: 110 },
  { title: '更新时间', key: 'updatedAt', width: 170 }
]

const loading = ref(false)
const rows = ref<OwnerListItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
/** 已生效的筛选条件：输入框改动不算数，点查询才生效 */
const filters = ref<OwnerFilterValues>({})
const ownerTypeOptions = ref<{ value: string; label: string }[]>([])
const ownerTypeLabels = ref<ReadonlyMap<string, string>>(new Map())

/** 分页受控：页码、每页条数与总数都以后端响应为准，不拉全量 */
const pagination = computed(() => ({
  current: pageNo.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (count: number) => `共 ${count} 条`
}))

interface TableChangePaging {
  current?: number
  pageSize?: number
}

function handleTableChange(next: TableChangePaging): void {
  pageNo.value = next.current ?? 1
  pageSize.value = next.pageSize ?? pageSize.value
  void fetchOwners()
}

/** 查询条件变化时回到第 1 页，否则会落在新结果的空白页上 */
function handleSearch(next: OwnerFilterValues): void {
  filters.value = next
  pageNo.value = 1
  void fetchOwners()
}

function handleReset(): void {
  filters.value = {}
  pageNo.value = 1
  void fetchOwners()
}

async function fetchOwners(): Promise<void> {
  loading.value = true
  try {
    const result = await listOwners(
      normalizeOwnerQuery({ ...filters.value, pageNo: pageNo.value, pageSize: pageSize.value })
    )
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    // 请求层已统一弹出错误提示。这里不动 rows：把失败清成空数组，用户会以为
    // 「一条货主都没有」，那是在用空态掩盖故障。
    console.error('[owner] 加载货主列表失败', error)
  } finally {
    loading.value = false
  }
}

async function loadOwnerTypes(): Promise<void> {
  try {
    const items = await listOwnerTypes()
    ownerTypeOptions.value = items.map((item) => ({ value: item.value, label: item.label }))
    ownerTypeLabels.value = new Map(items.map((item) => [item.value, item.label]))
  } catch (error) {
    // 字典失败只让下拉变空、类型列按原值回退，不阻断列表本身
    console.error('[owner] 加载客户类型字典失败', error)
  }
}

onMounted(() => {
  void loadOwnerTypes()
  void fetchOwners()
})
</script>

<template>
  <div>
    <PageHeader title="客户管理" description="货主档案与状态流转" />

    <section class="panel">
      <OwnerFilters
        :owner-type-options="ownerTypeOptions"
        @search="handleSearch"
        @reset="handleReset"
      />

      <Table
        :data-source="rows"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 900 }"
        row-key="id"
        bordered
        size="middle"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <span class="owner-code">{{ (record as OwnerListItem).code }}</span>
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
        </template>

        <template #emptyText>
          <EmptyState description="暂无货主档案" />
        </template>
      </Table>
    </section>
  </div>
</template>

<style scoped>
.owner-code {
  font-family: var(--font-mono);
}
</style>
