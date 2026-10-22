<script setup lang="ts">
import { ref } from 'vue'
import { Button, Descriptions, DescriptionsItem, Input, Tag, Tree } from 'ant-design-vue'
import { PageHeader } from '@yunwms/ui-web'

interface RawNode {
  label: string
  children?: RawNode[]
}

interface TreeNode {
  key: string
  label: string
  children?: TreeNode[]
}

const keyword = ref('')

/** antd 的 Tree 要求每个节点有 key，这里用 label 派生，保持数据源只写一次 */
function withKey(nodes: RawNode[]): TreeNode[] {
  return nodes.map((node) => ({
    key: node.label,
    label: node.label,
    children: node.children ? withKey(node.children) : undefined
  }))
}

const rawTree: RawNode[] = [
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

const treeData = withKey(rawTree)

/** 数据用 label 作字段名，映射到 antd Tree 的 title */
const treeFieldNames = {
  title: 'label'
}

const profile = [
  { label: '仓库编码', value: 'WH0001' },
  { label: '仓库别名', value: '宝安一号仓' },
  { label: '仓库模式', value: '国内仓' },
  { label: '布局级别', value: '多楼栋' },
  { label: '时区', value: 'Asia/Shanghai' },
  { label: '状态', value: '启用' }
]

const capabilities = ['入库', '出库', '退货', '换标', '贴标', '塑封']
</script>

<template>
  <div>
    <PageHeader title="仓库网络" description="仓库档案、空间层级与库位">
      <template #actions>
        <Button>库位导入</Button>
        <Button type="primary">新建仓库</Button>
      </template>
    </PageHeader>

    <div class="warehouse">
      <section class="panel warehouse__tree">
        <h2 class="panel__title">空间树</h2>
        <Input
          v-model:value="keyword"
          placeholder="搜索库位编码"
          allow-clear
          size="small"
          class="warehouse__search"
        />
        <Tree :tree-data="treeData" :field-names="treeFieldNames" default-expand-all />
      </section>

      <section class="panel warehouse__detail">
        <h2 class="panel__title">仓库档案</h2>
        <Descriptions :column="2" bordered size="small">
          <DescriptionsItem v-for="item in profile" :key="item.label" :label="item.label">
            {{ item.value }}
          </DescriptionsItem>
        </Descriptions>

        <h2 class="panel__title warehouse__section">服务能力</h2>
        <div class="warehouse__caps">
          <Tag v-for="cap in capabilities" :key="cap">{{ cap }}</Tag>
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
