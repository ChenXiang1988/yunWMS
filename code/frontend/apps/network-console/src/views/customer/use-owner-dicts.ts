import { ref } from 'vue'
import { listCountries, listOwnerTypes, listProvinces } from '@/api/dict'
import { toProvinceOption, type OwnerProvinceOption } from './owner-form'
import type { OwnerSelectOption } from './owner-model'

/**
 * 客户页用到的三份字典：客户类型、国家、州省。
 *
 * 字典失败不阻断页面：下拉变空、类型列按原值回退，最终由后端字典校验否决。
 * 请求只在这里发，组件通过页面注入的选项渲染（`docs/frontend.md` §4.3）。
 */
export function useOwnerDicts() {
  const ownerTypeOptions = ref<OwnerSelectOption[]>([])
  const ownerTypeLabels = ref<ReadonlyMap<string, string>>(new Map())
  const countryOptions = ref<OwnerSelectOption[]>([])
  const provinceOptions = ref<OwnerProvinceOption[]>([])
  const provincesLoading = ref(false)

  async function loadOwnerTypes(): Promise<void> {
    try {
      const items = await listOwnerTypes()
      ownerTypeOptions.value = items.map((item) => ({ value: item.value, label: item.label }))
      ownerTypeLabels.value = new Map(items.map((item) => [item.value, item.label]))
    } catch (error) {
      // 字典失败只让下拉变空、类型列按原值回退，不阻断列表本身
      console.error('[owner] 加载客户类型字典失败', error)
    }
  }

  async function loadCountries(): Promise<void> {
    try {
      const items = await listCountries()
      countryOptions.value = items.map((item) => ({ value: item.value, label: item.label }))
    } catch (error) {
      // 国家下拉为空时表单仍可手填，最终由后端字典校验否决
      console.error('[owner] 加载国家字典失败', error)
    }
  }

  /** 州省请求序号：国家切得快时只认最后一次响应，避免旧结果覆盖新国家的列表 */
  let provinceRequestId = 0

  async function loadProvinces(country: string | undefined): Promise<void> {
    const requestId = ++provinceRequestId
    provinceOptions.value = []
    if (!country) {
      provincesLoading.value = false
      return
    }
    provincesLoading.value = true
    try {
      const items = await listProvinces(country)
      if (requestId === provinceRequestId) {
        provinceOptions.value = items.map(toProvinceOption)
      }
    } catch (error) {
      console.error('[owner] 加载州省字典失败', error)
    } finally {
      if (requestId === provinceRequestId) {
        provincesLoading.value = false
      }
    }
  }

  return {
    ownerTypeOptions,
    ownerTypeLabels,
    countryOptions,
    provinceOptions,
    provincesLoading,
    loadOwnerTypes,
    loadCountries,
    loadProvinces
  }
}
