<script setup lang="ts">
import { ref } from 'vue'
import { PageHeader, StatusTag } from '@yunwms/ui'

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
const status = ref('')

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
</script>

<template>
  <div>
    <PageHeader title="商品管理" description="SKUID 由系统分配，实物需贴对应标签">
      <template #actions>
        <el-button>批量导入</el-button>
        <el-button>打印标签</el-button>
        <el-button type="primary">新建商品</el-button>
      </template>
    </PageHeader>

    <section class="panel">
      <el-form inline class="filters">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="SKUID / 编码 / 名称 / 条码" clearable style="width: 260px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status" placeholder="全部" clearable style="width: 140px">
            <el-option label="草稿" value="draft" />
            <el-option label="待审核" value="pending" />
            <el-option label="已启用" value="active" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" border>
        <el-table-column prop="skuId" label="SKUID" width="135" />
        <el-table-column prop="ownerCode" label="货主商品编码" width="120" />
        <el-table-column prop="name" label="商品名称" min-width="150" />
        <el-table-column prop="barcode" label="条码" width="150" />
        <el-table-column prop="pack" label="包装" width="125" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StatusTag :status="row.status" :text="STATUS_TEXT[row.status as Status]" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="155" />
        <el-table-column label="操作" width="130">
          <template #default>
            <el-button link type="primary">详情</el-button>
            <el-button link>打印</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination layout="prev, pager, next" :total="1248" :page-size="20" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.filters {
  margin-bottom: var(--space-2);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-4);
}
</style>
