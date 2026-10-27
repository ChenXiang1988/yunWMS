<script setup lang="ts">
import { computed } from 'vue'
import {
  Button,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Spin
} from 'ant-design-vue'
import { EmptyState, StatusTag } from '@yunwms/ui-web'
import type { OwnerDetail } from '@/api/owner'
import { PLACEHOLDER, displayText, formatIsoDateTime, ownerTypeLabel } from '../owner-model'
import { ownerStatusText } from '../owner-status'

/**
 * 货主详情抽屉。
 *
 * 只展示「基本信息」与「注册地址」：联系人、税号、主账号后端尚未提供数据来源，
 * 本页不造占位页签——没有数据的页签只会让人以为功能坏了。
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

const ownerTypeText = computed(() =>
  props.owner ? ownerTypeLabel(props.owner.ownerType, props.ownerTypeLabels) : PLACEHOLDER
)

function close(): void {
  emit('update:open', false)
}
</script>

<template>
  <Drawer :open="open" title="客户详情" :width="560" :mask-closable="false" @close="close">
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
        <DescriptionsItem label="创建时间">{{ formatIsoDateTime(owner.createdAt) }}</DescriptionsItem>
      </Descriptions>
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
