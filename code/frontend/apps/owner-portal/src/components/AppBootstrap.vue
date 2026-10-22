<script setup lang="ts">
import { useInjectAppContext } from 'ant-design-vue/es/app/context'
import { setHttpErrorHandler } from '@/api/client'

/**
 * 必须是 <App> 的子组件：useInjectAppContext 依赖 App 提供的作用域。
 * 这样拿到的 message 实例会继承 ConfigProvider 的主题，提示框颜色才走令牌。
 */
const { message } = useInjectAppContext()

setHttpErrorHandler((content) => {
  message.error(content)
})
</script>

<template>
  <span class="app-bootstrap" />
</template>

<style scoped>
.app-bootstrap {
  display: none;
}
</style>
