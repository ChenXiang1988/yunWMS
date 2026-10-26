import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { session } from '@/stores/session'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    // 待改密状态下唯一放行的页面，因此挂在布局外：侧栏菜单此时一律不可用
    path: '/change-password',
    name: 'change-password',
    component: () => import('@/views/change-password/index.vue'),
    meta: { title: '修改初始密码' }
  },
  {
    path: '/',
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'product',
        name: 'product',
        component: () => import('@/views/product/index.vue'),
        meta: { title: '商品管理' }
      }
    ]
  }
]

export const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 顺序有语义，不能调换：
 * 1. 公开页直接放行，否则登录页自己会被赶去登录页
 * 2. 无令牌先去登录页——没登录的人不该被送到改密页
 * 3. 待改密且目标不是改密页，送去改密页
 * 4. 其余放行
 */
router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!session.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (session.mustChangePassword && to.name !== 'change-password') {
    return { name: 'change-password' }
  }
  return true
})
