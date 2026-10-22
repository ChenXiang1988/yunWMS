<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  AppstoreOutlined,
  DashboardOutlined,
  SettingOutlined,
  ShopOutlined,
  TeamOutlined
} from '@ant-design/icons-vue'
import { Button, Divider, Menu as AntMenu, MenuItem as AntMenuItem } from 'ant-design-vue'
import { clearToken } from '@yunwms/request'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

const menuItems = computed(() => [
  { key: '/dashboard', label: t('menu.dashboard'), icon: DashboardOutlined },
  { key: '/customer', label: t('menu.customer'), icon: TeamOutlined },
  { key: '/warehouse', label: t('menu.warehouse'), icon: ShopOutlined },
  { key: '/product', label: t('menu.product'), icon: AppstoreOutlined },
  { key: '/system', label: t('menu.system'), icon: SettingOutlined }
])

const selectedKeys = computed(() => [route.path])

/**
 * antd 通过行内样式下发菜单项左内边距，CSS 覆盖不了，只能从这里给。
 * 取值须与 --menu-item-padding-x（8px）保持一致。
 */
const menuIndent = 8

function handleMenuClick(info: { key: string | number }) {
  const key = String(info.key)
  if (key !== route.path) {
    void router.push(key)
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

      <AntMenu
        class="layout__menu"
        mode="inline"
        :inline-indent="menuIndent"
        :selected-keys="selectedKeys"
        @click="handleMenuClick"
      >
        <AntMenuItem v-for="item in menuItems" :key="item.key">
          <template #icon>
            <component :is="item.icon" />
          </template>
          {{ item.label }}
        </AntMenuItem>
      </AntMenu>
    </aside>

    <div class="layout__body">
      <header class="layout__header">
        <div class="layout__crumb">{{ route.meta.title }}</div>
        <div class="layout__tools">
          <Button type="text" size="small" @click="toggleLocale">
            {{ locale === 'zh-CN' ? 'EN' : '中' }}
          </Button>
          <Divider type="vertical" />
          <Button type="text" size="small" @click="logout">{{ t('common.logout') }}</Button>
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

/* 菜单规格与货主端逐项一致，尺寸来自 shell.css，不随密度变化 */
.layout__menu {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-2);
  background: transparent;
  border-inline-end: none;
}

.layout__menu :deep(.ant-menu-item) {
  height: var(--menu-item-height);
  line-height: var(--menu-item-height);
  margin: 0 0 var(--space-1);
  padding-inline: var(--menu-item-padding-x);
  border-radius: var(--menu-radius);
  color: var(--sidebar-text);
  font-size: var(--menu-font-size);
  font-weight: var(--weight-medium);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.layout__menu :deep(.ant-menu-item .anticon) {
  font-size: var(--menu-icon-size);
}

.layout__menu :deep(.ant-menu-item:hover) {
  background: var(--sidebar-item-hover);
  color: var(--sidebar-text-active);
}

.layout__menu :deep(.ant-menu-item-selected) {
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
