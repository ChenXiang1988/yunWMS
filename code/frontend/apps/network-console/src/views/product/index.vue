<script setup lang="ts">
import { ref } from 'vue'
import { PageHeader, StatusTag } from '@yunwms/ui'

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

const selected = ref<AuditRow[]>([])

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
</script>

<template>
  <div>
    <PageHeader title="商品中心" description="SKUID 审核与全域商品检索">
      <template #actions>
        <el-button :disabled="selected.length === 0">批量通过</el-button>
      </template>
    </PageHeader>

    <section class="panel">
      <h2 class="panel__title">待审核队列</h2>
      <el-table :data="rows" border @selection-change="(val: AuditRow[]) => (selected = val)">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="skuId" label="SKUID" width="120" />
        <el-table-column prop="owner" label="货主" width="120" />
        <el-table-column prop="name" label="商品名称" min-width="160" />
        <el-table-column prop="barcode" label="条码" width="145" />
        <el-table-column prop="reason" label="进入人工原因" min-width="190" />
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <StatusTag :status="row.status" :text="STATUS_TEXT[row.status as Status]" />
          </template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" width="150" />
        <el-table-column label="操作" width="115">
          <template #default>
            <el-button link type="primary">通过</el-button>
            <el-button link type="danger">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>
