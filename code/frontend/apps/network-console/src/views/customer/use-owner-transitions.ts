import { ref, type Ref } from 'vue'
import {
  changeOwnerStatus,
  deleteOwner,
  getTerminationImpact,
  type OwnerDetail,
  type OwnerListItem,
  type OwnerStatus,
  type TerminationImpact
} from '@/api/owner'
import { notifySuccess } from '@/api/notify'
import { ownerStatusText } from './owner-status'

/** 状态流转与删除要用的页面上下文：列表刷新与详情回读由页面提供 */
export interface OwnerListContext {
  fetchOwners: () => Promise<void>
  refreshDetail: (id: string) => Promise<void>
  detailOpen: Ref<boolean>
  detailOwner: Ref<OwnerDetail | null>
}

/**
 * 状态流转与删除的编排：弹窗状态、终止影响清单与提交动作。
 *
 * 合法目标与可删除判定在 `owner-status.ts`，本文件只负责弹窗编排。
 */
export function useOwnerTransitions(context: OwnerListContext) {
  const { fetchOwners, refreshDetail, detailOpen, detailOwner } = context

  /* -------------------------- 状态流转 -------------------------- */
  const statusOpen = ref(false)
  const statusOwner = ref<OwnerListItem | null>(null)
  const impact = ref<TerminationImpact | null>(null)
  const impactLoading = ref(false)
  const statusSubmitting = ref(false)
  /** 影响清单请求序号：换货主或换目标时，旧响应不能落到新弹窗上 */
  let impactRequestId = 0

  function openStatus(record: OwnerListItem): void {
    // 作废上一次未完成的清单请求：上一个货主的影响数不能出现在这个货主的弹窗里
    impactRequestId += 1
    statusOwner.value = record
    impact.value = null
    impactLoading.value = false
    statusOpen.value = true
  }

  async function loadImpact(id: string): Promise<void> {
    const requestId = ++impactRequestId
    impact.value = null
    impactLoading.value = true
    try {
      const result = await getTerminationImpact(id)
      if (requestId === impactRequestId) {
        impact.value = result
      }
    } catch (error) {
      // 取不到影响清单时允许重试；未取到就不允许提交终止，见弹窗的确认逻辑
      console.error('[owner] 加载终止影响清单失败', error)
    } finally {
      if (requestId === impactRequestId) {
        impactLoading.value = false
      }
    }
  }

  function handleTargetChange(target: OwnerStatus): void {
    const owner = statusOwner.value
    if (!owner) {
      return
    }
    if (target === 'TERMINATED') {
      void loadImpact(owner.id)
      return
    }
    impact.value = null
  }

  function reloadImpact(): void {
    const owner = statusOwner.value
    if (owner) {
      void loadImpact(owner.id)
    }
  }

  async function handleStatusSubmit(payload: {
    targetStatus: OwnerStatus
    acknowledgedImpact?: boolean
  }): Promise<void> {
    const owner = statusOwner.value
    if (!owner) {
      return
    }
    statusSubmitting.value = true
    try {
      await changeOwnerStatus(owner.id, payload)
      notifySuccess(`状态已变更为${ownerStatusText(payload.targetStatus)}`)
      statusOpen.value = false
      await fetchOwners()
      if (detailOpen.value && detailOwner.value?.id === owner.id) {
        await refreshDetail(owner.id)
      }
    } catch (error) {
      console.error('[owner] 流转货主状态失败', error)
    } finally {
      statusSubmitting.value = false
    }
  }

  /** 从详情抽屉进入状态流转：沿用详情里的状态，不依赖列表行是否已刷新 */
  function statusFromDetail(): void {
    const owner = detailOwner.value
    if (owner) {
      // 先关详情再开弹窗：两个浮层叠放时，后开的弹窗会落在详情遮罩下方，
      // 按钮点击会被详情底部操作区截走（浏览器实测）。
      detailOpen.value = false
      openStatus(owner)
    }
  }

  /* -------------------------- 删除 -------------------------- */
  const deleteOpen = ref(false)
  const deleteTarget = ref<OwnerListItem | null>(null)
  const deleteSubmitting = ref(false)

  function openDelete(record: OwnerListItem): void {
    deleteTarget.value = record
    deleteOpen.value = true
  }

  function deleteFromDetail(): void {
    const owner = detailOwner.value
    if (owner) {
      detailOpen.value = false
      openDelete(owner)
    }
  }

  async function handleDeleteSubmit(): Promise<void> {
    const owner = deleteTarget.value
    if (!owner) {
      return
    }
    deleteSubmitting.value = true
    try {
      await deleteOwner(owner.id)
      notifySuccess('草稿货主已删除')
      deleteOpen.value = false
      if (detailOwner.value?.id === owner.id) {
        detailOpen.value = false
        detailOwner.value = null
      }
      await fetchOwners()
    } catch (error) {
      console.error('[owner] 删除货主失败', error)
    } finally {
      deleteSubmitting.value = false
    }
  }

  return {
    statusOpen,
    statusOwner,
    impact,
    impactLoading,
    statusSubmitting,
    openStatus,
    handleTargetChange,
    reloadImpact,
    handleStatusSubmit,
    statusFromDetail,
    deleteOpen,
    deleteTarget,
    deleteSubmitting,
    openDelete,
    deleteFromDetail,
    handleDeleteSubmit
  }
}
