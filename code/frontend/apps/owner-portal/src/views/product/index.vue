<script setup lang="ts">
import { ref } from 'vue'
import {
  Button,
  Form,
  FormItem,
  Input,
  Pagination,
  Select,
  SelectOption,
  Table
} from 'ant-design-vue'
import { PageHeader, StatusTag } from '@yunwms/ui-web'

type Status = 'draft' | 'pending' | 'active' | 'disabled' | 'rejected'

interface SkuRow {
  skuId: string
  ownerCode: string
  name: string
  barcode: string
  pack: string
  status: Status
  updatedAt: string
}

const STATUS_TEXT: Record<Status, string> = {
  draft: '草稿',
  pending: '待审核',
  active: '已启用',
  disabled: '已停用',
  rejected: '已驳回'
}

const keyword = ref('')
const status = ref<string | undefined>(undefined)

const columns = [
  { title: 'SKUID', dataIndex: 'skuId', key: 'skuId', width: 135 },
  { title: '货主商品编码', dataIndex: 'ownerCode', key: 'ownerCode', width: 120 },
  { title: '商品名称', dataIndex: 'name', key: 'name' },
  { title: '条码', dataIndex: 'barcode', key: 'barcode', width: 150 },
  { title: '包装', dataIndex: 'pack', key: 'pack', width: 125 },
  { title: '状态', key: 'status', width: 90 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 155 },
  { title: '操作', key: 'actions', width: 130 }
]

const rows = ref<SkuRow[]>([
  {
    skuId: 'AMZ1000001',
    ownerCode: 'A-ERP-8821',
    name: '氨基酸洁面乳 120ml',
    barcode: '6973497202704',
    pack: '1 箱 = 24 支',
    status: 'active',
    updatedAt: '2026-10-21 14:20'
  },
  {
    skuId: 'AMZ1000002',
    ownerCode: 'A-ERP-8822',
    name: '水润精华 30ml',
    barcode: '6973497202711',
    pack: '1 箱 = 48 支',
    status: 'pending',
    updatedAt: '2026-10-21 10:02'
  },
  {
    skuId: 'AMZ1000003',
    ownerCode: 'A-ERP-8830',
    name: '便携化妆棉 100 片',
    barcode: '6973497202728',
    pack: '1 箱 = 60 包',
    status: 'draft',
    updatedAt: '2026-10-20 16:45'
  },
  {
    skuId: 'AMZ1000004',
    ownerCode: 'A-ERP-8840',
    name: '保湿面膜 5 片装',
    barcode: '6973497202735',
    pack: '1 箱 = 120 盒',
    status: 'rejected',
    updatedAt: '2026-10-19 09:12'
  }
])

function statusText(record: SkuRow): string {
  return STATUS_TEXT[record.status]
}
</script>

<template>
  <div>
    <PageHeader title="商品管理" description="SKUID 由系统分配，实物需贴对应标签">
      <template #actions>
        <Button>批量导入</Button>
        <Button>打印标签</Button>
        <Button type="primary">新建商品</Button>
      </template>
    </PageHeader>

    <section class="panel">
      <Form layout="inline" class="filters">
        <FormItem label="关键词">
          <Input
            v-model:value="keyword"
            placeholder="SKUID / 编码 / 名称 / 条码"
            allow-clear
            style="width: 260px"
          />
        </FormItem>
        <FormItem label="状态">
          <Select
            v-model:value="status"
            placeholder="全部"
            allow-clear
            style="width: 140px"
          >
            <SelectOption value="draft">草稿</SelectOption>
            <SelectOption value="pending">待审核</SelectOption>
            <SelectOption value="active">已启用</SelectOption>
            <SelectOption value="rejected">已驳回</SelectOption>
            <SelectOption value="disabled">已停用</SelectOption>
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
        row-key="skuId"
        bordered
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <StatusTag :status="(record as SkuRow).status" :text="statusText(record as SkuRow)" />
          </template>
          <template v-else-if="column.key === 'actions'">
            <Button type="link" size="small">详情</Button>
            <Button type="link" size="small">打印</Button>
          </template>
        </template>
      </Table>

      <div class="pager">
        <Pagination :total="1248" :page-size="20" />
      </div>
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

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-4);
}
</style>
