<script setup lang="ts">
import { ref } from 'vue'
import { PageHeader } from '@yunwms/ui'

interface TreeNode {
  label: string
  children?: TreeNode[]
}

const keyword = ref('')

const treeData: TreeNode[] = [
  {
    label: '宝安一号仓（WH0001）',
    children: [
      {
        label: 'A 栋',
        children: [
          {
            label: '3 层',
            children: [
              {
                label: 'A 区 · 专属深圳西之月',
                children: [{ label: 'A-01-02-03-04' }, { label: 'A-01-02-03-05' }]
              },
              { label: 'B 区 · 共享' }
            ]
          }
        ]
      },
      { label: '月台 · 南侧' }
    ]
  }
]

const profile = [
  { label: '仓库编码', value: 'WH0001' },
  { label: '仓库别名', value: '宝安一号仓' },
  { label: '仓库模式', value: '国内仓' },
  { label: '布局级别', value: '多楼栋' },
  { label: '时区', value: 'Asia/Shanghai' },
  { label: '状态', value: '启用' }
]
</script>

<template>
  <div>
    <PageHeader title="仓库网络" description="仓库档案、空间层级与库位">
      <template #actions>
        <el-button>库位导入</el-button>
        <el-button type="primary">新建仓库</el-button>
      </template>
    </PageHeader>

    <div class="warehouse">
      <section class="panel warehouse__tree">
        <h2 class="panel__title">空间树</h2>
        <el-input v-model="keyword" placeholder="搜索库位编码" clearable size="small" class="warehouse__search" />
        <el-tree :data="treeData" node-key="label" default-expand-all :expand-on-click-node="false" />
      </section>

      <section class="panel warehouse__detail">
        <h2 class="panel__title">仓库档案</h2>
        <el-descriptions :column="2" border>
          <el-descriptions-item v-for="item in profile" :key="item.label" :label="item.label">
            {{ item.value }}
          </el-descriptions-item>
        </el-descriptions>

        <h2 class="panel__title warehouse__section">服务能力</h2>
        <div class="warehouse__caps">
          <el-tag v-for="cap in ['入库', '出库', '退货', '换标', '贴标', '塑封']" :key="cap" type="info">
            {{ cap }}
          </el-tag>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.warehouse {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: var(--card-gap);
  align-items: start;
}

.warehouse__search {
  margin-bottom: var(--space-2);
}

.warehouse__section {
  margin-top: var(--space-4);
}

.warehouse__caps {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}
</style>
