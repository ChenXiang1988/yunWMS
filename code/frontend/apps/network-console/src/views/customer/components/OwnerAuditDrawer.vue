<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Drawer, Table } from 'ant-design-vue'
import { EmptyState } from '@yunwms/ui-web'
import { listAuditLogs } from '@/api/owner'
import {
  AUDIT_BIZ_TYPE_OWNER,
  AUDIT_PAGE_SIZE,
  auditRowViews,
  type AuditRowView
} from '../account-model'
import { displayText, formatIsoDateTime } from '../owner-model'

/**
 * 货主操作日志抽屉：按货主编码查该货主的审计记录，只读。
 *
 * 五条取舍：
 *
 * 1. 与详情抽屉并列，不塞进详情的内容区。本应用用的是 ant-design-vue 4.2.6，`Drawer`
 *    在未配置 `getContainer` 时直接渲染在组件树原位（不是挂到 body），两层浮层 z-index
 *    相同，谁在上面由 DOM 顺序决定——所以后声明的日志抽屉盖在先声明的详情之上，
 *    与打开先后无关。反过来在详情之上再开一个先声明的浮层就会落到遮罩下面，这正是
 *    `use-owner-lifecycle.ts` 里 `editFromDetail` 先关详情再开表单的原因。
 * 2. 关闭只置本抽屉的 `open=false`，不动详情抽屉的 `v-model:open`：日志是「看一眼」，
 *    看完应当回到详情，而不是连带把详情关掉。
 * 3. 按对象查：`bizType=OWNER` + `bizId=货主编码`。审计记录的 bizId 是货主编码而不是
 *    数字标识，用详情里的 `owner.id` 查会一条也查不到。
 * 4. 顺序照后端：接口按操作时间倒序返回，前端不排序、不重排；首屏取最近 100 条，
 *    更早的靠翻页（`ui-structure.md` 的「超出给查看全部」由分页承担）。
 * 5. 只读：不提供删除与修改入口，审计日志本身也只有追加。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    /** 货主系统编码：审计记录的 bizId 是它 */
    ownerCode: string
    /** 货主名称，仅用于标题 */
    ownerName?: string
  }>(),
  { ownerName: '' }
)

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const rows = ref<AuditRowView[]>([])
const loading = ref(false)
const total = ref(0)
const pageNo = ref(1)
/** 请求序号：翻页或关掉再开时，只认最后一次响应 */
let requestId = 0

async function load(): Promise<void> {
  const bizId = props.ownerCode
  if (!bizId) {
    return
  }
  const current = ++requestId
  loading.value = true
  try {
    const result = await listAuditLogs({
      bizType: AUDIT_BIZ_TYPE_OWNER,
      bizId,
      pageNo: pageNo.value,
      pageSize: AUDIT_PAGE_SIZE
    })
    if (current === requestId) {
      rows.value = auditRowViews(result.records)
      total.value = result.total
    }
  } catch (error) {
    // 请求层已统一弹出提示。这里不清空 rows：空列表会被读成「这个货主没有任何操作记录」，
    // 那是在用空态掩盖故障
    console.error('[owner] 加载操作日志失败', error)
  } finally {
    if (current === requestId) {
      loading.value = false
    }
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) {
      // 关掉就清空并回到第 1 页：换一个货主再打开时，不能先闪出上一个货主的日志
      requestId += 1
      rows.value = []
      total.value = 0
      pageNo.value = 1
      loading.value = false
      return
    }
    void load()
  },
  { immediate: true }
)

function close(): void {
  emit('update:open', false)
}

const title = computed(() => (props.ownerName ? `操作日志 · ${props.ownerName}` : '操作日志'))

const pagination = computed(() => ({
  current: pageNo.value,
  pageSize: AUDIT_PAGE_SIZE,
  total: total.value,
  showSizeChanger: false,
  showTotal: (count: number) => `共 ${count} 条`
}))

/** 只有真带快照的行才给展开箭头，避免每行都挂一个空面板 */
const expandable = {
  rowExpandable: (record: AuditRowView) => record.hasSnapshot
}

const columns = [
  { title: '操作时间', key: 'createdAt', width: 150 },
  { title: '操作类型', key: 'action', width: 96 },
  { title: '操作人', key: 'operator', width: 104 },
  { title: '变更摘要', key: 'summary' }
]

interface TableChangePaging {
  current?: number
}

function handleChange(paging: TableChangePaging): void {
  pageNo.value = paging.current ?? 1
  void load()
}
</script>

<template>
  <Drawer :open="open" :title="title" :width="640" @close="close">
    <p class="audit__note">按操作时间倒序，只读；日志只追加，界面不提供修改与删除。</p>

    <Table
      :data-source="rows"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      :expandable="expandable"
      row-key="key"
      size="small"
      @change="handleChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'createdAt'">
          {{ formatIsoDateTime((record as AuditRowView).createdAt) }}
        </template>
        <template v-else-if="column.key === 'action'">
          {{ displayText((record as AuditRowView).action) }}
        </template>
        <template v-else-if="column.key === 'operator'">
          {{ displayText((record as AuditRowView).operator) }}
        </template>
        <template v-else-if="column.key === 'summary'">
          {{ displayText((record as AuditRowView).summary) }}
        </template>
      </template>

      <template #expandedRowRender="{ record }">
        <div class="audit__snapshots">
          <div class="audit__snapshot">
            <span class="audit__snapshot-label">变更前</span>
            <pre class="audit__json">{{ displayText((record as AuditRowView).beforeText) }}</pre>
          </div>
          <div class="audit__snapshot">
            <span class="audit__snapshot-label">变更后</span>
            <pre class="audit__json">{{ displayText((record as AuditRowView).afterText) }}</pre>
          </div>
        </div>
      </template>

      <template #emptyText>
        <EmptyState description="暂无操作日志" />
      </template>
    </Table>
  </Drawer>
</template>

<style scoped>
.audit__note {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-tertiary);
}

.audit__snapshots {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.audit__snapshot-label {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.audit__json {
  margin: 0;
  padding: var(--space-3);
  overflow-x: auto;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-primary);
}
</style>
