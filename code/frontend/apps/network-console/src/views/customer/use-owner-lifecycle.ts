import { computed, onMounted, ref } from 'vue'
import {
  createOwner,
  getOwner,
  listOwners,
  updateOwner,
  type OwnerDetail,
  type OwnerInitialStatus,
  type OwnerListItem
} from '@/api/owner'
import { notifySuccess } from '@/api/notify'
import { DEFAULT_PAGE_SIZE, normalizeOwnerQuery, type OwnerFilterValues } from './owner-model'
import { buildCreatePayload, buildUpdatePayload, type OwnerFormValues } from './owner-form'
import { canDeleteOwner, isOwnerWritable } from './owner-status'
import { useOwnerDicts } from './use-owner-dicts'
import { useOwnerTransitions } from './use-owner-transitions'

/**
 * 客户列表页的编排入口：列表查询、字典注入、详情、抽屉/弹窗状态与提交动作。
 *
 * 纯转换在 `owner-model.ts`、`owner-form.ts` 与 `owner-status.ts`；状态流转与删除在
 * `use-owner-transitions.ts`；展示与交互分别落在 `components/` 下的各组件里。
 * 本文件只持有页面状态并编排请求，顺序即模板里自上而下的区块顺序。
 */
export function useOwnerLifecycle() {
  const dicts = useOwnerDicts()

  /* -------------------------- 列表 -------------------------- */
  const loading = ref(false)
  const rows = ref<OwnerListItem[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)
  /** 已生效的筛选条件：输入框改动不算数，点查询才生效 */
  const filters = ref<OwnerFilterValues>({})

  async function fetchOwners(): Promise<void> {
    loading.value = true
    try {
      const result = await listOwners(
        normalizeOwnerQuery({ ...filters.value, pageNo: pageNo.value, pageSize: pageSize.value })
      )
      rows.value = result.records
      total.value = result.total
    } catch (error) {
      // 请求层已统一弹出错误提示。这里不动 rows：把失败清成空数组，用户会以为
      // 「一条货主都没有」，那是在用空态掩盖故障。
      console.error('[owner] 加载货主列表失败', error)
    } finally {
      loading.value = false
    }
  }

  /** 分页受控：页码与每页条数都回写到页面状态，再由下一次查询带给后端 */
  function handlePageChange(current: number, nextPageSize?: number): void {
    pageNo.value = current
    pageSize.value = nextPageSize ?? pageSize.value
    void fetchOwners()
  }

  /** 查询条件变化时回到第 1 页，否则会落在新结果的空白页上 */
  function handleSearch(next: OwnerFilterValues): void {
    filters.value = next
    pageNo.value = 1
    void fetchOwners()
  }

  function handleReset(): void {
    filters.value = {}
    pageNo.value = 1
    void fetchOwners()
  }

  /* -------------------------- 详情 -------------------------- */
  const detailOpen = ref(false)
  const detailLoading = ref(false)
  const detailOwner = ref<OwnerDetail | null>(null)
  let detailRequestId = 0

  /** 详情抽屉的写操作入口：状态取自详情本身，不依赖列表行是否已刷新 */
  const detailWritable = computed(() =>
    detailOwner.value ? isOwnerWritable(detailOwner.value.status) : false
  )
  const detailDeletable = computed(() =>
    detailOwner.value ? canDeleteOwner(detailOwner.value.status) : false
  )

  async function refreshDetail(id: string): Promise<void> {
    const requestId = ++detailRequestId
    detailLoading.value = true
    try {
      const detail = await getOwner(id)
      if (requestId === detailRequestId) {
        detailOwner.value = detail
      }
    } catch (error) {
      // 详情失败保留上一次内容：清空会让抽屉闪成空态，看不出是加载失败还是没数据
      console.error('[owner] 加载货主详情失败', error)
    } finally {
      if (requestId === detailRequestId) {
        detailLoading.value = false
      }
    }
  }

  function openDetail(record: OwnerListItem): void {
    detailOwner.value = null
    detailOpen.value = true
    void refreshDetail(record.id)
  }

  /* -------------------------- 新建 / 编辑 -------------------------- */
  const formOpen = ref(false)
  const formMode = ref<'create' | 'edit'>('create')
  const formOwner = ref<OwnerDetail | null>(null)
  const formSubmitting = ref(false)
  /** 编辑前取详情的请求序号：连点两行的编辑时，只打开最后点的那一条 */
  let editRequestId = 0

  function openCreate(): void {
    editRequestId += 1
    formMode.value = 'create'
    formOwner.value = null
    void dicts.loadProvinces(undefined)
    formOpen.value = true
  }

  async function openEdit(record: OwnerListItem): Promise<void> {
    const requestId = ++editRequestId
    try {
      const detail = await getOwner(record.id)
      if (requestId !== editRequestId) {
        return
      }
      formMode.value = 'edit'
      formOwner.value = detail
      formOpen.value = true
      void dicts.loadProvinces(detail.country)
    } catch (error) {
      console.error('[owner] 编辑前加载货主详情失败', error)
    }
  }

  /** 从详情抽屉进入编辑：详情已是全量档案，不必再取一次 */
  function editFromDetail(): void {
    const owner = detailOwner.value
    if (!owner) {
      return
    }
    editRequestId += 1
    // 详情抽屉先关：两个浮层叠放时，后开抽屉会落在详情遮罩下方，
    // 保存按钮坐标与详情底部删除按钮重合，浏览器实测会误触删除。
    detailOpen.value = false
    formMode.value = 'edit'
    formOwner.value = owner
    formOpen.value = true
    void dicts.loadProvinces(owner.country)
  }

  /** 国家变化由表单抽屉抛出：州省属于国家，须按新国家重取 */
  function handleFormCountryChange(country: string): void {
    void dicts.loadProvinces(country)
  }

  async function handleCreate(
    form: OwnerFormValues,
    initialStatus: OwnerInitialStatus
  ): Promise<void> {
    formSubmitting.value = true
    try {
      const created = await createOwner(buildCreatePayload(form, initialStatus))
      notifySuccess(`客户 ${created.code} 已创建`)
      formOpen.value = false
      await fetchOwners()
    } catch (error) {
      // 40905 前缀占用、40906 外部码重复等由请求层把后端原文弹出，抽屉保持打开供修改
      console.error('[owner] 新建货主失败', error)
    } finally {
      formSubmitting.value = false
    }
  }

  async function handleUpdate(form: OwnerFormValues): Promise<void> {
    const current = formOwner.value
    if (!current) {
      return
    }
    formSubmitting.value = true
    try {
      await updateOwner(current.id, buildUpdatePayload(form))
      notifySuccess('客户信息已保存')
      formOpen.value = false
      await fetchOwners()
      // 编辑入口可能来自详情抽屉，此时详情要跟着刷新——编辑接口不回带最新值
      if (detailOpen.value && detailOwner.value?.id === current.id) {
        await refreshDetail(current.id)
      }
    } catch (error) {
      console.error('[owner] 保存货主失败', error)
    } finally {
      formSubmitting.value = false
    }
  }

  const transitions = useOwnerTransitions({ fetchOwners, refreshDetail, detailOpen, detailOwner })

  onMounted(() => {
    void dicts.loadOwnerTypes()
    void dicts.loadCountries()
    void fetchOwners()
  })

  return {
    // 列表
    loading,
    rows,
    total,
    pageNo,
    pageSize,
    handlePageChange,
    handleSearch,
    handleReset,
    // 字典
    ownerTypeOptions: dicts.ownerTypeOptions,
    ownerTypeLabels: dicts.ownerTypeLabels,
    countryOptions: dicts.countryOptions,
    provinceOptions: dicts.provinceOptions,
    provincesLoading: dicts.provincesLoading,
    handleFormCountryChange,
    // 详情
    detailOpen,
    detailLoading,
    detailOwner,
    detailWritable,
    detailDeletable,
    openDetail,
    // 新建 / 编辑
    formOpen,
    formMode,
    formOwner,
    formSubmitting,
    openCreate,
    openEdit,
    editFromDetail,
    handleCreate,
    handleUpdate,
    // 状态流转与删除
    ...transitions
  }
}
