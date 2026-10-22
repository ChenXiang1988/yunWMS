<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Box, Odometer, OfficeBuilding, Setting, User } from '@element-plus/icons-vue'
import { clearToken } from '@yunwms/request'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

const menuItems = computed(() => [
  { index: '/dashboard', label: t('menu.dashboard'), icon: Odometer },
  { index: '/customer', label: t('menu.customer'), icon: User },
  { index: '/warehouse', label: t('menu.warehouse'), icon: OfficeBuilding },
  { index: '/product', label: t('menu.product'), icon: Box },
  { index: '/system', label: t('menu.system'), icon: Setting }
])

const activeMenu = computed(() => route.path)

function handleSelect(index: string) {
  if (index !== route.path) {
    router.push(index)
  }
}

function toggleLocale() {
  locale.value = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  document.documentElement.lang = locale.value
}

function logout() {
  clearToken()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <aside class="layout__aside">
      <div class="layout__brand">
        <span class="layout__brand-mark">Y</span>
        <div class="layout__brand-text">
          <strong>{{ t('app.name') }}</strong>
          <small>{{ t('app.subtitle') }}</small>
        </div>
      </div>

      <el-menu :default-active="activeMenu" class="layout__menu" @select="handleSelect">
        <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
          <el-icon class="layout__menu-icon">
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="layout__body">
      <header class="layout__header">
        <div class="layout__crumb">{{ route.meta.title }}</div>
        <div class="layout__tools">
          <el-button link @click="toggleLocale">
            {{ locale === 'zh-CN' ? 'EN' : '中' }}
          </el-button>
          <el-divider direction="vertical" />
          <el-button link @click="logout">{{ t('common.logout') }}</el-button>
        </div>
      </header>

      <main class="layout__main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--color-bg-page);
}

.layout__aside {
  display: flex;
  flex-direction: column;
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--sidebar-border);
}

.layout__brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: var(--header-height);
  padding: 0 var(--space-2);
  border-bottom: 1px solid var(--sidebar-border);
}

.layout__brand-mark {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-text-inverse);
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
}

.layout__brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.layout__brand-text strong {
  font-size: var(--font-size-base);
  font-weight: var(--weight-semibold);
  color: var(--sidebar-text-active);
}

.layout__brand-text small {
  font-size: var(--text-2xs);
  color: var(--sidebar-text);
}

.layout__menu {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-2);
  border-right: none;
  background: transparent;
  /* 覆盖 Element Plus 的层级缩进计算，统一由本组件的 padding 控制 */
  --el-menu-base-level-padding: var(--menu-item-padding-x);
  --el-menu-level-padding: 0px;
}

.layout__menu :deep(.el-menu-item) {
  gap: var(--space-2);
  height: var(--menu-item-height);
  line-height: var(--menu-item-height);
  padding: 0 var(--menu-item-padding-x);
  margin: 0 0 var(--space-1);
  border-radius: var(--menu-radius);
  color: var(--sidebar-text);
  font-size: var(--menu-font-size);
  font-weight: var(--weight-medium);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.layout__menu :deep(.el-menu-item .layout__menu-icon) {
  margin-right: 0;
  font-size: var(--menu-icon-size);
  color: inherit;
}

.layout__menu :deep(.el-menu-item:hover) {
  background: var(--sidebar-item-hover);
  color: var(--sidebar-text-active);
}

.layout__menu :deep(.el-menu-item.is-active) {
  background: var(--sidebar-item-active);
  color: var(--sidebar-text-active);
  font-weight: var(--weight-medium);
}

.layout__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 var(--page-padding);
  background: var(--color-bg-container);
  border-bottom: 1px solid var(--color-border);
}

.layout__crumb {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.layout__tools {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.layout__main {
  flex: 1;
  padding: var(--page-padding);
  overflow: auto;
}
</style>
