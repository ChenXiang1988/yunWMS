<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button, Table } from 'ant-design-vue'
import { PageHeader, StatusTag } from '@yunwms/ui-web'

type Status = 'draft' | 'pending' | 'active' | 'disabled' | 'rejected'

interface AuditRow {
  skuId: string
  owner: string
  ownerCode: string
  name: string
  barcode: string
  reason: string
  status: Status
  submittedAt: string
}

const STATUS_TEXT: Record<Status, string> = {
  draft: '草稿',
  pending: '待审核',
  active: '已通过',
  disabled: '已停用',
  rejected: '已驳回'
}

const selectedKeys = ref<string[]>([])

const rows = ref<AuditRow[]>([
  {
    skuId: 'AMZ1000002',
    owner: '深圳西之月',
    ownerCode: 'A-ERP-8822',
    name: '水润精华 30ml',
    barcode: '6973497202711',
    reason: '条码与同货主其他商品重复',
    status: 'pending',
    submittedAt: '2026-10-21 10:02'
  },
  {
    skuId: 'OWN2000007',
    owner: '广州南风',
    ownerCode: 'NF-1027',
    name: '棉质短袖 T 恤',
    barcode: '6901234567890',
    reason: '品类模板要求填写颜色与尺码',
    status: 'pending',
    submittedAt: '2026-10-20 16:30'
  }
])

const columns = [
  { title: 'SKUID', dataIndex: 'skuId', key: 'skuId', width: 120 },
  { title: '货主', dataIndex: 'owner', key: 'owner', width: 120 },
  { title: '商品名称', dataIndex: 'name', key: 'name', width: 160 },
  { title: '条码', dataIndex: 'barcode', key: 'barcode', width: 145 },
  { title: '进入人工原因', dataIndex: 'reason', key: 'reason', width: 190 },
  { title: '状态', key: 'status', width: 85 },
  { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 150 },
  { title: '操作', key: 'actions', width: 115 }
]

const rowSelection = computed(() => ({
  selectedRowKeys: selectedKeys.value,
  onChange: (keys: (string | number)[]) => {
    selectedKeys.value = keys.map(String)
  }
}))

function statusText(record: AuditRow): string {
  return STATUS_TEXT[record.status]
}
</script>

<template>
  <div>
    <PageHeader title="商品中心" description="SKUID 审核与全域商品检索">
      <template #actions>
        <Button :disabled="selectedKeys.length === 0">批量通过</Button>
      </template>
    </PageHeader>

    <section class="panel">
      <h2 class="panel__title">待审核队列</h2>
      <Table
        :data-source="rows"
        :columns="columns"
        :pagination="false"
        :row-selection="rowSelection"
        :scroll="{ x: 1160 }"
        row-key="skuId"
        bordered
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <StatusTag :status="(record as AuditRow).status" :text="statusText(record as AuditRow)" />
          </template>
          <template v-else-if="column.key === 'actions'">
            <Button type="link" size="small">通过</Button>
            <Button type="link" size="small" danger>驳回</Button>
          </template>
        </template>
      </Table>
    </section>
  </div>
</template>
