<script setup lang="ts">
import { ref } from 'vue'
import { EmptyState, PageHeader } from '@yunwms/ui-web'

const activeTab = ref('user')
</script>

<template>
  <div>
    <PageHeader title="系统管理" description="权限、编码规则、字典与审计" />

    <section class="panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="用户" name="user">
          <EmptyState description="用户列表待接入" />
        </el-tab-pane>
        <el-tab-pane label="角色与权限" name="role">
          <EmptyState description="角色与功能权限待接入" />
        </el-tab-pane>
        <el-tab-pane label="数据权限" name="scope">
          <EmptyState description="仓库 / 货主 / 库区三维范围待接入" />
        </el-tab-pane>
        <el-tab-pane label="编码规则" name="code">
          <EmptyState description="客户、仓库、库区、库位、SKUID 五类规则待接入" />
        </el-tab-pane>
        <el-tab-pane label="字典" name="dict">
          <EmptyState description="字典项与双语名称待接入" />
        </el-tab-pane>
        <el-tab-pane label="审计日志" name="audit">
          <EmptyState description="审计日志待接入" />
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>
