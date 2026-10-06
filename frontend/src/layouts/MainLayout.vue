<template>
  <n-layout class="layout">
    <!-- 后台页面也保留壁纸背景（暗色遮罩保证可读性） -->
    <template v-if="!isHome">
      <div class="page-bg" :style="{ backgroundImage: `url(${pageBg})` }"></div>
      <div class="page-veil"></div>
    </template>

    <!-- 首页为全屏壁纸沉浸式布局，不显示顶部导航 -->
    <n-layout-header v-if="!isHome" class="header glass-card" bordered>
      <div class="brand" @click="router.push('/')">
        <span class="brand-mark">
          <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round">
            <circle cx="12" cy="12" r="8.4" />
            <path d="M8.4 9.2a4.5 4.5 0 0 1 3.2-1.6" />
            <circle cx="9.4" cy="14.3" r="1.15" fill="#fff" stroke="none" />
            <circle cx="14.8" cy="15.6" r="1.5" fill="#fff" stroke="none" />
          </svg>
        </span>
        <span class="brand-name">火星图床</span>
      </div>

      <n-menu
        mode="horizontal"
        :value="activeKey"
        :options="menuOptions"
        class="menu"
        @update:value="onMenuSelect"
      />

      <div class="user-area">
        <!-- 已登录：下拉菜单 -->
        <n-dropdown v-if="auth.isLoggedIn" :options="userMenu" trigger="click" @select="onUserMenuSelect">
          <button class="user-chip">
            <span class="avatar">{{ initial }}</span>
            <span class="username">{{ auth.user?.nickname || auth.user?.username }}</span>
            <span class="caret">▾</span>
          </button>
        </n-dropdown>

        <!-- 未登录：用户中心 -> 弹出登录 -->
        <button v-else class="user-chip guest" @click="openAuthModal('login')">
          <span class="avatar ghost">👤</span>
          <span class="username">用户中心</span>
        </button>
      </div>
    </n-layout-header>

    <n-layout-content class="content" :class="{ bleed: isHome }">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </n-layout-content>

    <AuthModal />
  </n-layout>
</template>

<script setup>
import { computed, h, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDialog } from 'naive-ui'
import AuthModal from '../components/AuthModal.vue'
import { useAuthStore } from '../stores/auth'
import { openAuthModal } from '../utils/authModal'

const route = useRoute()
const router = useRouter()
const dialog = useDialog()
const auth = useAuthStore()

const activeKey = computed(() => route.name)
const isHome = computed(() => route.name === 'home')
// 后台页面的壁纸背景（与首页一致，使用本地打包的 hero.jpg）
const pageBg = '/hero.jpg'
const initial = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || '?'
  return name.charAt(0).toUpperCase()
})

const baseMenu = [{ label: () => h('span', null, '🏠 首页'), key: 'home' }]
// 「上传图片」是后台功能，未登录时不展示（点了也会要求登录）
const uploadMenu = { label: () => h('span', null, '⬆️ 上传图片'), key: 'upload' }
// 「仓库管理 / 存储配置」仅管理员可见
const repoMenu = { label: () => h('span', null, '📦 仓库管理'), key: 'repo' }
const storageMenu = { label: () => h('span', null, '🗄️ 存储配置'), key: 'storage' }
const usersMenu = { label: () => h('span', null, '👥 用户管理'), key: 'users' }
const carouselMenu = { label: () => h('span', null, '🎞️ 轮播管理'), key: 'carousel' }
const statsMenu = { label: () => h('span', null, '📊 数据统计'), key: 'stats' }
const recordsMenu = { label: () => h('span', null, '🖼️ 上传记录'), key: 'records' }
// 「API」所有登录用户可见：查看自己的 API Key 与调用文档
const apiMenu = { label: () => h('span', null, '🔑 API'), key: 'api' }
const menuOptions = computed(() => {
  if (!auth.isLoggedIn) return baseMenu
  return auth.isAdmin
    ? [...baseMenu, uploadMenu, recordsMenu, apiMenu, repoMenu, storageMenu, usersMenu, carouselMenu, statsMenu]
    : [...baseMenu, uploadMenu, recordsMenu, apiMenu]
})

const userMenu = computed(() => {
  const items = [
    { label: '上传图片', key: 'upload', icon: () => h('span', null, '⬆️') },
    { label: '上传记录', key: 'records', icon: () => h('span', null, '🖼️') },
    { label: 'API 接口', key: 'api', icon: () => h('span', null, '🔑') }
  ]
  if (auth.isAdmin) {
    items.push({ label: '仓库管理', key: 'repo', icon: () => h('span', null, '📦') })
    items.push({ label: '存储配置', key: 'storage', icon: () => h('span', null, '🗄️') })
    items.push({ label: '用户管理', key: 'users', icon: () => h('span', null, '👥') })
    items.push({ label: '轮播管理', key: 'carousel', icon: () => h('span', null, '🎞️') })
    items.push({ label: '数据统计', key: 'stats', icon: () => h('span', null, '📊') })
  }
  items.push({ type: 'divider', key: 'd1' })
  items.push({ label: '退出登录', key: 'logout', icon: () => h('span', null, '🚪') })
  return items
})

// 需要登录的页面：未登录时直接弹出登录框
const AUTH_PAGES = ['records', 'api', 'repo', 'storage', 'users', 'carousel', 'stats', 'upload']

function onMenuSelect(key) {
  if (AUTH_PAGES.includes(key) && !auth.isLoggedIn) {
    openAuthModal('login')
    return
  }
  // 仓库管理仅管理员可进
  if (key === 'repo' && !auth.isAdmin) {
    dialog.warning({ title: '无访问权限', content: '仓库管理仅管理员可用。', positiveText: '知道了' })
    return
  }
  router.push({ name: key })
}

function onUserMenuSelect(key) {
  if (key === 'logout') {
    dialog.warning({
      title: '确认退出',
      content: '确定要退出登录吗？',
      positiveText: '退出',
      negativeText: '取消',
      onPositiveClick: () => {
        auth.logout()
        router.push('/')
      }
    })
    return
  }
  router.push({ name: key })
}

// 任何带 ?login=1 的跳转都会弹出登录框（弹出后立刻清掉参数，避免刷新重复弹）
watch(
  () => route.query.login,
  (v) => {
    if (!v) return
    openAuthModal('login')
    router.replace({ path: route.path, query: {} })
  },
  { immediate: true }
)
</script>

<style scoped>
.layout { min-height: 100vh; background: transparent; }
.header {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 0 28px;
  height: 62px;
  position: sticky;
  top: 0;
  z-index: 10;
  border-radius: 0;
  border-left: none;
  border-right: none;
  border-top: none;
  /* 首页有壁纸背景，顶栏需要更实的底才不糊 */
  background: rgba(12, 11, 22, 0.74);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.brand { display: flex; align-items: center; gap: 10px; cursor: pointer; flex-shrink: 0; }
.brand-mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: linear-gradient(135deg, #7c5cff, #22d3ee);
  box-shadow: 0 6px 18px rgba(124, 92, 255, 0.4);
}
.brand-mark svg { width: 19px; height: 19px; }
.brand-name { font-size: 18px; font-weight: 700; letter-spacing: 0.3px; }

.menu { flex: 1; background: transparent; }
.menu :deep(.n-menu-item) { background: transparent; }

.user-area { flex-shrink: 0; }
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 6px;
  font-family: inherit;
  font-size: 13px;
  color: var(--text);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.18s;
}
.user-chip:hover {
  background: rgba(124, 92, 255, 0.18);
  border-color: rgba(124, 92, 255, 0.55);
}
.user-chip.guest { padding-left: 12px; }
.avatar {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  border-radius: 50%;
  background: linear-gradient(135deg, #7c5cff, #22d3ee);
}
.avatar.ghost { background: rgba(255, 255, 255, 0.08); font-size: 14px; }
.caret { font-size: 10px; color: var(--text-dim); }

.content { padding: 28px; max-width: 1200px; margin: 0 auto; width: 100%; position: relative; z-index: 1; }
/* 首页 Hero 需要通栏铺满 */
.content.bleed { padding: 0; max-width: none; }

/* —— 后台页面壁纸背景 —— */
.page-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background-size: cover;
  background-position: center;
  pointer-events: none;
}
.page-veil {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(120% 90% at 50% 0%, rgba(8, 10, 18, 0.38), rgba(8, 10, 18, 0.66) 70%),
    rgba(8, 10, 18, 0.42);
}

@media (max-width: 720px) {
  .header { gap: 14px; padding: 0 14px; }
  .brand-name { display: none; }
  .username { display: none; }
  .content { padding: 16px; }
}
</style>
