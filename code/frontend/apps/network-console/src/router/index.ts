import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@yunwms/request'
import BasicLayout from '@/layouts/BasicLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true, title: '登录' }
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
        path: 'customer',
        name: 'customer',
        component: () => import('@/views/customer/index.vue'),
        meta: { title: '客户管理' }
      },
      {
        path: 'warehouse',
        name: 'warehouse',
        component: () => import('@/views/warehouse/index.vue'),
        meta: { title: '仓库网络' }
      },
      {
        path: 'product',
        name: 'product',
        component: () => import('@/views/product/index.vue'),
        meta: { title: '商品中心' }
      },
      {
        path: 'system',
        name: 'system',
        component: () => import('@/views/system/index.vue'),
        meta: { title: '系统管理' }
      }
    ]
  }
]

export const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!getToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})
