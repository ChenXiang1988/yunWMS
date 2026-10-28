<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  Button,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Spin,
  TabPane,
  Tabs
} from 'ant-design-vue'
import { EmptyState, StatusTag } from '@yunwms/ui-web'
import type { OwnerDetail } from '@/api/owner'
import { PLACEHOLDER, displayText, formatIsoDateTime, ownerTypeLabel } from '../owner-model'
import { ownerStatusText } from '../owner-status'
import OwnerAccountTab from './OwnerAccountTab.vue'
import OwnerAuditDrawer from './OwnerAuditDrawer.vue'
import OwnerContactTab from './OwnerContactTab.vue'
import OwnerTaxTab from './OwnerTaxTab.vue'

/**
 * 货主详情抽屉，自己持有四个页签的容器：基本信息 / 联系人 / 税号 / 账号，
 * 以及右上角的操作日志入口（`ui-structure.md`：日志是抽屉，不占页签）。
 *
 * 页签顺序取自 `ui-structure.md` 的「货主详情」。联系人（`OwnerContactTab.vue`）、
 * 税号（`OwnerTaxTab.vue`）与账号（`OwnerAccountTab.vue`）都已接入。
 *
 * 数据由页面注入（`docs/frontend.md` §4.3），这里只负责展示与抛出操作意图。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    /** 详情请求进行中；已有数据时不遮内容，避免每次刷新都闪白 */
    loading?: boolean
    owner?: OwnerDetail | null
    ownerTypeLabels?: ReadonlyMap<string, string>
    /** 是否可写：终止态为终态，写操作入口置灰 */
    writable?: boolean
    /** 是否可删除：只有草稿 */
    deletable?: boolean
  }>(),
  {
    loading: false,
    owner: null,
    ownerTypeLabels: () => new Map(),
    writable: true,
    deletable: false
  }
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  edit: []
  'change-status': []
  delete: []
}>()

/** 当前页签：每次打开都回到基本信息，与 `ui-structure.md` 的「停在基本信息」一致 */
const activeTab = ref('basic')

/**
 * 操作日志抽屉：与详情并列的第二层抽屉。
 *
 * 它只读、按货主编码查；关闭它只置自己的 `open`，不动详情的 `v-model:open`——看完日志
 * 应当回到详情，而不是连带把详情关掉。反过来，详情一开一关都必须把它收起：日志属于
 * 当前这次详情，留着会让下一个货主看到上一个的日志。
 */
const auditOpen = ref(false)

watch(
  () => props.open,
  (open) => {
    auditOpen.value = false
    if (open) {
      activeTab.value = 'basic'
    }
  }
)

const ownerTypeText = computed(() =>
  props.owner ? ownerTypeLabel(props.owner.ownerType, props.ownerTypeLabels) : PLACEHOLDER
)

function close(): void {
  emit('update:open', false)
}

function openAudit(): void {
  auditOpen.value = true
}
</script>

<template>
  <Drawer :open="open" title="客户详情" :width="720" :mask-closable="false" @close="close">
    <template #extra>
      <Button v-if="owner" size="small" @click="openAudit">操作日志</Button>
    </template>

    <div v-if="loading && !owner" class="detail__loading">
      <Spin />
    </div>

    <template v-else-if="owner">
      <header class="detail__header">
        <div class="detail__title">
          <span class="detail__name">{{ owner.name }}</span>
          <StatusTag :status="owner.status" :text="ownerStatusText(owner.status)" />
        </div>
        <p class="detail__meta">
          {{ owner.code }} · 更新于 {{ formatIsoDateTime(owner.updatedAt) }}
        </p>
      </header>

      <Tabs v-model:activeKey="activeTab" size="small">
        <TabPane key="basic" tab="基本信息">
          <Descriptions :column="1" bordered size="small" class="detail__group">
            <DescriptionsItem label="系统编码">
              <span class="detail__mono">{{ owner.code }}</span>
            </DescriptionsItem>
            <DescriptionsItem label="客户名称">{{ owner.name }}</DescriptionsItem>
            <DescriptionsItem label="英文名称">{{ displayText(owner.nameEn) }}</DescriptionsItem>
            <DescriptionsItem label="货主简称">{{ displayText(owner.shortName) }}</DescriptionsItem>
            <DescriptionsItem label="外部码">{{ displayText(owner.externalCode) }}</DescriptionsItem>
            <DescriptionsItem label="SKU 前缀">
              <span class="detail__mono">{{ owner.skuPrefix }}</span>
            </DescriptionsItem>
            <DescriptionsItem label="客户类型">{{ ownerTypeText }}</DescriptionsItem>
            <DescriptionsItem label="结算主体">{{ owner.settlementEntity }}</DescriptionsItem>
            <DescriptionsItem label="状态">
              <StatusTag :status="owner.status" :text="ownerStatusText(owner.status)" />
            </DescriptionsItem>
          </Descriptions>

          <Descriptions :column="1" bordered size="small" class="detail__group">
            <DescriptionsItem label="国家 / 地区">{{ owner.country }}</DescriptionsItem>
            <DescriptionsItem label="州省">{{ displayText(owner.province) }}</DescriptionsItem>
            <DescriptionsItem label="城市">{{ owner.city }}</DescriptionsItem>
            <DescriptionsItem label="区县">{{ displayText(owner.district) }}</DescriptionsItem>
            <DescriptionsItem label="邮政编码">{{ displayText(owner.postalCode) }}</DescriptionsItem>
            <DescriptionsItem label="详细地址">{{ owner.addressLine }}</DescriptionsItem>
          </Descriptions>

          <Descriptions :column="1" bordered size="small" class="detail__group">
            <DescriptionsItem label="备注">{{ displayText(owner.remark) }}</DescriptionsItem>
            <DescriptionsItem label="创建时间">
              {{ formatIsoDateTime(owner.createdAt) }}
            </DescriptionsItem>
          </Descriptions>
        </TabPane>

        <TabPane key="contacts" tab="联系人">
          <OwnerContactTab :owner-id="owner.id" />
        </TabPane>

        <TabPane key="taxes" tab="税号">
          <OwnerTaxTab :owner-id="owner.id" />
        </TabPane>

        <TabPane key="accounts" tab="账号">
          <OwnerAccountTab :owner-id="owner.id" :owner-code="owner.code" :status="owner.status" />
        </TabPane>
      </Tabs>
    </template>

    <EmptyState v-else description="未取到货主信息" />

    <template #footer>
      <div class="detail__footer">
        <Button type="primary" :disabled="!writable" @click="emit('edit')">编辑</Button>
        <Button :disabled="!writable" @click="emit('change-status')">状态流转</Button>
        <Button v-if="deletable" danger :disabled="!writable" @click="emit('delete')">
          删除
        </Button>
      </div>
    </template>
  </Drawer>

  <!--
    日志抽屉作为详情抽屉的同级根节点，不放进上者的内容区：两层的 z-index 相同，
    谁在上由 DOM 顺序决定，写在后面才稳定盖在详情之上（见 `OwnerAuditDrawer.vue` 文件头）。
  -->
  <OwnerAuditDrawer
    v-model:open="auditOpen"
    :owner-code="owner?.code ?? ''"
    :owner-name="owner?.name ?? ''"
  />
</template>

<style scoped>
.detail__loading {
  display: flex;
  justify-content: center;
  padding: var(--space-10) 0;
}

.detail__header {
  margin-bottom: var(--space-4);
}

.detail__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.detail__name {
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  color: var(--color-text-primary);
}

.detail__meta {
  margin: var(--space-1) 0 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.detail__group + .detail__group {
  margin-top: var(--space-4);
}

.detail__mono {
  font-family: var(--font-mono);
}

.detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
