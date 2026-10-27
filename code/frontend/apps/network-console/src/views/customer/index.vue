<script setup lang="ts">
import { Button } from 'ant-design-vue'
import { PageHeader } from '@yunwms/ui-web'
import OwnerFilters from './components/OwnerFilters.vue'
import OwnerTable from './components/OwnerTable.vue'
import OwnerFormDrawer from './components/OwnerFormDrawer.vue'
import OwnerDetailDrawer from './components/OwnerDetailDrawer.vue'
import OwnerStatusModal from './components/OwnerStatusModal.vue'
import OwnerDeleteModal from './components/OwnerDeleteModal.vue'
import { useOwnerLifecycle } from './use-owner-lifecycle'

/**
 * 客户列表页：只组合模板，页面状态与请求编排都在 `use-owner-lifecycle.ts`。
 *
 * 区块与入口以 `openspec/specs/customer-management` 为准：页头新建、筛选栏、
 * 列表、四个弹层，不在此增删。
 */
const {
  loading,
  rows,
  total,
  pageNo,
  pageSize,
  handlePageChange,
  handleSearch,
  handleReset,
  ownerTypeOptions,
  ownerTypeLabels,
  countryOptions,
  provinceOptions,
  provincesLoading,
  handleFormCountryChange,
  detailOpen,
  detailLoading,
  detailOwner,
  detailWritable,
  detailDeletable,
  openDetail,
  formOpen,
  formMode,
  formOwner,
  formSubmitting,
  openCreate,
  openEdit,
  editFromDetail,
  handleCreate,
  handleUpdate,
  statusOpen,
  statusOwner,
  impact,
  impactLoading,
  statusSubmitting,
  openStatus,
  handleTargetChange,
  reloadImpact,
  handleStatusSubmit,
  deleteOpen,
  deleteTarget,
  deleteSubmitting,
  openDelete,
  statusFromDetail,
  deleteFromDetail,
  handleDeleteSubmit
} = useOwnerLifecycle()
</script>

<template>
  <div>
    <PageHeader title="客户管理" description="货主档案与状态流转">
      <template #actions>
        <Button type="primary" @click="openCreate">新建客户</Button>
      </template>
    </PageHeader>

    <section class="panel">
      <OwnerFilters
        :owner-type-options="ownerTypeOptions"
        @search="handleSearch"
        @reset="handleReset"
      />

      <OwnerTable
        :rows="rows"
        :loading="loading"
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        :owner-type-labels="ownerTypeLabels"
        @view="openDetail"
        @edit="openEdit"
        @change-status="openStatus"
        @delete="openDelete"
        @page-change="handlePageChange"
      />
    </section>

    <OwnerFormDrawer
      v-model:open="formOpen"
      :mode="formMode"
      :owner="formOwner"
      :owner-type-options="ownerTypeOptions"
      :country-options="countryOptions"
      :province-options="provinceOptions"
      :provinces-loading="provincesLoading"
      :submitting="formSubmitting"
      @country-change="handleFormCountryChange"
      @submit-create="handleCreate"
      @submit-update="handleUpdate"
    />

    <OwnerDetailDrawer
      v-model:open="detailOpen"
      :loading="detailLoading"
      :owner="detailOwner"
      :owner-type-labels="ownerTypeLabels"
      :writable="detailWritable"
      :deletable="detailDeletable"
      @edit="editFromDetail"
      @change-status="statusFromDetail"
      @delete="deleteFromDetail"
    />

    <OwnerStatusModal
      v-model:open="statusOpen"
      :owner="statusOwner"
      :impact="impact"
      :impact-loading="impactLoading"
      :submitting="statusSubmitting"
      @target-change="handleTargetChange"
      @reload-impact="reloadImpact"
      @submit="handleStatusSubmit"
    />

    <OwnerDeleteModal
      v-model:open="deleteOpen"
      :owner="deleteTarget"
      :submitting="deleteSubmitting"
      @submit="handleDeleteSubmit"
    />
  </div>
</template>
