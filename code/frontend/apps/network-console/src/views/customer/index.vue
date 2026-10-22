<script setup lang="ts">
import { ref } from 'vue'
import { Button, Form, FormItem, Input, Select, SelectOption, Table } from 'ant-design-vue'
import { PageHeader, StatusTag } from '@yunwms/ui-web'

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
const customerType = ref<string | undefined>(undefined)

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

const columns = [
  { title: '客户编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '客户名称', dataIndex: 'name', key: 'name', width: 220 },
  { title: '类型', dataIndex: 'type', key: 'type', width: 110 },
  { title: '状态', key: 'status', width: 100 },
  { title: '联系人', dataIndex: 'contact', key: 'contact', width: 110 },
  { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 140 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 150 },
  { title: '操作', key: 'actions', width: 130 }
]

function statusText(record: CustomerRow): string {
  return STATUS_TEXT[record.status]
}
</script>

<template>
  <div>
    <PageHeader title="客户管理" description="货主档案、合同附件与账号开通">
      <template #actions>
        <Button type="primary">新建客户</Button>
      </template>
    </PageHeader>

    <section class="panel">
      <Form layout="inline" class="filters">
        <FormItem label="关键词">
          <Input
            v-model:value="keyword"
            placeholder="编码 / 名称 / 联系人"
            allow-clear
            style="width: 240px"
          />
        </FormItem>
        <FormItem label="类型">
          <Select
            v-model:value="customerType"
            placeholder="全部"
            allow-clear
            style="width: 130px"
          >
            <SelectOption value="brand">品牌方</SelectOption>
            <SelectOption value="forwarder">货代同行</SelectOption>
          </Select>
        </FormItem>
        <FormItem>
          <Button type="primary">查询</Button>
          <Button class="filters__reset">重置</Button>
        </FormItem>
      </Form>

      <Table
        :data-source="rows"
        :columns="columns"
        :pagination="false"
        :scroll="{ x: 1100 }"
        row-key="code"
        bordered
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <StatusTag
              :status="(record as CustomerRow).status"
              :text="statusText(record as CustomerRow)"
            />
          </template>
          <template v-else-if="column.key === 'actions'">
            <Button type="link" size="small">详情</Button>
            <Button type="link" size="small">子账号</Button>
          </template>
        </template>
      </Table>
    </section>
  </div>
</template>

<style scoped>
.filters {
  margin-bottom: var(--space-2);
}

.filters__reset {
  margin-left: var(--space-2);
}
</style>
