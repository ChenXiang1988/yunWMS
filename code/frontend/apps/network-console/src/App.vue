<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'
import AntApp from 'ant-design-vue/es/app'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { antdTheme } from '@yunwms/tokens/web-antd'
import AppBootstrap from '@/components/AppBootstrap.vue'
</script>

<template>
  <ConfigProvider :theme="antdTheme" :locale="zhCN">
    <AntApp>
      <AppBootstrap />
      <router-view />
    </AntApp>
  </ConfigProvider>
</template>
