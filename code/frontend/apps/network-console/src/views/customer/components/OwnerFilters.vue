<script setup lang="ts">
import { ref } from 'vue'
import { Button, Form, FormItem, Input, Select, SelectOption } from 'ant-design-vue'
import type { OwnerStatus } from '@/api/owner'
import type { OwnerFilterValues } from '../owner-model'
import { OWNER_STATUS_OPTIONS } from '../owner-status'

/**
 * 客户类型下拉的选项。字典由页面加载后注入——组件内不发起请求，
 * 这样字典失败也不影响筛选栏渲染。
 */
interface OwnerTypeOption {
  value: string
  label: string
}

withDefaults(defineProps<{ ownerTypeOptions: OwnerTypeOption[] }>(), {
  ownerTypeOptions: () => []
})

const emit = defineEmits<{
  search: [filters: OwnerFilterValues]
  reset: []
}>()

const keyword = ref('')
const ownerType = ref<string | undefined>(undefined)
const status = ref<OwnerStatus | undefined>(undefined)

function search(): void {
  emit('search', {
    keyword: keyword.value,
    ownerType: ownerType.value,
    status: status.value
  })
}

/** 重置只清本组件的输入，查询由页面重新发起 */
function reset(): void {
  keyword.value = ''
  ownerType.value = undefined
  status.value = undefined
  emit('reset')
}
</script>

<template>
  <Form layout="inline" class="filters">
    <FormItem label="关键词">
      <Input
        v-model:value="keyword"
        placeholder="编码 / 名称"
        allow-clear
        style="width: 240px"
        @press-enter="search"
      />
    </FormItem>
    <FormItem label="客户类型">
      <Select v-model:value="ownerType" placeholder="全部" allow-clear style="width: 140px">
        <SelectOption v-for="option in ownerTypeOptions" :key="option.value" :value="option.value">
          {{ option.label }}
        </SelectOption>
      </Select>
    </FormItem>
    <FormItem label="状态">
      <Select v-model:value="status" placeholder="全部" allow-clear style="width: 120px">
        <SelectOption
          v-for="option in OWNER_STATUS_OPTIONS"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </SelectOption>
      </Select>
    </FormItem>
    <FormItem>
      <Button type="primary" @click="search">查询</Button>
      <Button class="filters__reset" @click="reset">重置</Button>
    </FormItem>
  </Form>
</template>

<style scoped>
.filters {
  margin-bottom: var(--space-2);
}

.filters__reset {
  margin-left: var(--space-2);
}
</style>
