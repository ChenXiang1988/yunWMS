<script setup lang="ts">
import { ref } from 'vue'
import { PageHeader, StatusTag } from '@yunwms/ui'

type Status = 'draft' | 'pending' | 'active' | 'disabled'

interface CustomerRow {
  code: string
  name: string
  type: string
  status: Status
  contact: string
  mobile: string
  updatedAt: string
}

const STATUS_TEXT: Record<Status, string> = {
  draft: '潜在',
  pending: '暂停',
  active: '合作中',
  disabled: '终止'
}

const keyword = ref('')

const rows = ref<CustomerRow[]>([
  {
    code: 'OWNER0001',
    name: '深圳西之月网络科技',
    type: '品牌方',
    status: 'active',
    contact: '林飞宇',
    mobile: '138****6621',
    updatedAt: '2026-10-21 15:02'
  },
  {
    code: 'OWNER0002',
    name: '广州南风贸易',
    type: '货代同行',
    status: 'active',
    contact: '陈曦',
    mobile: '139****3088',
    updatedAt: '2026-10-20 09:41'
  },
  {
    code: 'OWNER0003',
    name: '东莞优选供应链',
    type: '品牌方',
    status: 'pending',
    contact: '黄敏',
    mobile: '137****9915',
    updatedAt: '2026-10-18 17:20'
  }
])
</script>

<template>
  <div>
    <PageHeader title="客户管理" description="货主档案、合同附件与账号开通">
      <template #actions>
        <el-button type="primary">新建客户</el-button>
      </template>
    </PageHeader>

    <section class="panel">
      <el-form inline class="filters">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="编码 / 名称 / 联系人" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select placeholder="全部" clearable style="width: 130px">
            <el-option label="品牌方" value="brand" />
            <el-option label="货代同行" value="forwarder" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" border>
        <el-table-column prop="code" label="客户编码" width="130" />
        <el-table-column prop="name" label="客户名称" min-width="220" />
        <el-table-column prop="type" label="类型" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" :text="STATUS_TEXT[row.status as Status]" />
          </template>
        </el-table-column>
        <el-table-column prop="contact" label="联系人" width="110" />
        <el-table-column prop="mobile" label="手机号" width="140" />
        <el-table-column prop="updatedAt" label="更新时间" width="150" />
        <el-table-column label="操作" width="130">
          <template #default>
            <el-button link type="primary">详情</el-button>
            <el-button link>子账号</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.filters {
  margin-bottom: var(--space-2);
}
</style>
