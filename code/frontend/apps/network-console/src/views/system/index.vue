<script setup lang="ts">
import { ref } from 'vue'
import { TabPane, Tabs } from 'ant-design-vue'
import { EmptyState, PageHeader } from '@yunwms/ui-web'

const activeTab = ref('user')
</script>

<template>
  <div>
    <PageHeader title="系统管理" description="权限、编码规则、字典与审计" />

    <section class="panel">
      <Tabs v-model:active-key="activeTab">
        <TabPane key="user" tab="用户">
          <EmptyState description="用户列表待接入" />
        </TabPane>
        <TabPane key="role" tab="角色与权限">
          <EmptyState description="角色与功能权限待接入" />
        </TabPane>
        <TabPane key="scope" tab="数据权限">
          <EmptyState description="仓库 / 货主 / 库区三维范围待接入" />
        </TabPane>
        <TabPane key="code" tab="编码规则">
          <EmptyState description="客户、仓库、库区、库位、SKUID 五类规则待接入" />
        </TabPane>
        <TabPane key="dict" tab="字典">
          <EmptyState description="字典项与双语名称待接入" />
        </TabPane>
        <TabPane key="audit" tab="审计日志">
          <EmptyState description="审计日志待接入" />
        </TabPane>
      </Tabs>
    </section>
  </div>
</template>
