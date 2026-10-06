import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('../views/HomeView.vue'), meta: { title: '首页' } },
      { path: 'upload', name: 'upload', component: () => import('../views/UploadView.vue'), meta: { title: '上传图片', requiresAuth: true } },
      {
        path: 'records',
        name: 'records',
        component: () => import('../views/RecordsView.vue'),
        meta: { title: '上传记录', requiresAuth: true }
      },
      {
        path: 'api',
        name: 'api',
        component: () => import('../views/ApiView.vue'),
        meta: { title: 'API 接口', requiresAuth: true }
      },
      {
        path: 'repo',
        name: 'repo',
        component: () => import('../views/RepoView.vue'),
        meta: { title: '仓库管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'storage',
        name: 'storage',
        component: () => import('../views/StorageView.vue'),
        meta: { title: '存储配置', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UsersView.vue'),
        meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'carousel',
        name: 'carousel',
        component: () => import('../views/CarouselView.vue'),
        meta: { title: '轮播管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'stats',
        name: 'stats',
        component: () => import('../views/StatsView.vue'),
        meta: { title: '数据统计', requiresAuth: true, requiresAdmin: true }
      }
    ]
  },
  // 独立整页登录/注册：默认入口是弹窗，这两个路由保留给直链访问
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { title: '注册' } },
  // 公开分享页：任何人拿到链接都能看，无需登录
  { path: '/share/:code', name: 'share', component: () => import('../views/ShareView.vue'), meta: { title: '图片分享' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  document.title = (to.meta.title ? to.meta.title + ' · ' : '') + '火星图床'
  const token = sessionStorage.getItem('imgbed_token')
  // 需要登录的页面 -> 回首页并自动弹出登录框；同时记住原本想去哪，登录后直达
  if (to.meta.requiresAuth && !token) {
    if (to.fullPath && to.fullPath !== '/') {
      sessionStorage.setItem('imgbed_redirect', to.fullPath)
    }
    return { path: '/', query: { login: '1' } }
  }
  // 已登录再访问登录页 -> 回后台
  if ((to.name === 'login' || to.name === 'register') && token) {
    return '/upload'
  }
  // 管理员页面（仓库管理）-> 非管理员一律送回上传页
  if (to.meta.requiresAdmin) {
    let isAdmin = false
    try {
      isAdmin = !!JSON.parse(sessionStorage.getItem('imgbed_user') || 'null')?.admin
    } catch { isAdmin = false }
    if (!isAdmin) return '/upload'
  }
  return true
})

export default router
