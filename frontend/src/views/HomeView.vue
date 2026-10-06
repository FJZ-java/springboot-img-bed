<template>
  <div class="home">
    <!-- 全屏 Hero：壁纸 + 缓慢缩放动画 -->
    <section class="hero">
      <div class="hero-bg" :style="{ backgroundImage: `url(${heroBg})` }"></div>
      <div class="hero-veil"></div>

      <div class="hero-inner">
        <h1 class="hero-title">火星图床</h1>
        <p class="hero-lead">提供免费、稳定、高速，搭载全球 CDN 的图片托管服务</p>
        <p v-if="stats" class="hero-stats">
          本站已托管 <b>{{ stats.images.toLocaleString() }}</b> 张图片，共占用储存
          <b>{{ formatSize(stats.size) }}</b>
        </p>

        <div class="hero-actions">
          <button class="cta cta-main" @click="onQuickUpload">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 16V6.5M8.6 9.9 12 6.5l3.4 3.4" />
              <path d="M5.5 15.5A3.5 3.5 0 0 0 6 22h12a3.5 3.5 0 0 0 .5-6.5" />
            </svg>
            立刻上传
          </button>
          <button class="cta cta-user" @click="onUserCenter">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="8" r="3.6" />
              <path d="M4.8 20a7.2 7.2 0 0 1 14.4 0" />
            </svg>
            {{ auth.isLoggedIn ? '我的记录' : '用户中心' }}
          </button>
        </div>
      </div>
    </section>

    <!-- 上传面板：队列以右侧抽屉形式呈现 -->
    <UploadPanel ref="panel" :dropzone="false" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import UploadPanel from '../components/UploadPanel.vue'
import api from '../api'
import { useAuthStore } from '../stores/auth'
import { openAuthModal } from '../utils/authModal'

// 首页背景：使用本地打包的壁纸（public/hero.jpg），避免依赖外部图床导致加载慢
const heroBg = '/hero.jpg'

const router = useRouter()
const auth = useAuthStore()
const panel = ref(null)
const stats = ref(null)

onMounted(async () => {
  try {
    const res = await api.get('/stats')
    stats.value = res.data
  } catch {
    stats.value = null
  }
})

function onQuickUpload() {
  // 游客：直接选图即传（首页的「立刻上传」专为游客体验准备）
  if (!auth.isLoggedIn) {
    panel.value?.pick()
    return
  }
  // 已登录：进入后台上传页（支持批量、队列与记录管理）
  router.push('/upload')
}

function onUserCenter() {
  if (auth.isLoggedIn) {
    router.push('/records')
  } else {
    openAuthModal('login')
  }
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}
</script>

<style scoped>
.home { height: 100vh; overflow: hidden; }

/* ===== 全屏 Hero ===== */
.hero {
  position: relative;
  height: 100%;
  overflow: hidden;
}
.hero-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center 30%;
  /* 轻微缩放效果（Ken Burns 呼吸感） */
  animation: bgZoom 22s ease-in-out infinite alternate;
  will-change: transform;
}
@keyframes bgZoom {
  from { transform: scale(1); }
  to { transform: scale(1.08); }
}
/* 压暗，保证文字可读 */
.hero-veil {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 90% 80% at 30% 55%, rgba(8, 6, 18, 0.5) 0%, rgba(8, 6, 18, 0.18) 55%, rgba(8, 6, 18, 0.42) 100%);
}

.hero-inner {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  padding: 0 24px;
}
.hero-title {
  margin: 0 0 18px;
  font-size: 58px;
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: 2px;
  color: #fff;
  text-shadow: 0 4px 24px rgba(0, 0, 0, 0.55);
}
.hero-lead {
  margin: 0 0 20px;
  font-size: 20px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.88);
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.55);
}
/* 统计小胶囊 */
.hero-stats {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin: 0 0 28px;
  padding: 7px 18px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.72);
  background: rgba(0, 0, 0, 0.42);
  border-radius: 999px;
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}
.hero-stats b { color: #fff; font-weight: 600; font-variant-numeric: tabular-nums; }

/* ===== 胶囊按钮 ===== */
.hero-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 50px;
  padding: 0 30px;
  font-family: inherit;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #fff;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.25s ease, filter 0.25s ease;
}
.cta svg { width: 19px; height: 19px; }
.cta-main {
  background: linear-gradient(120deg, #ff4d67 0%, #f43f5e 55%, #e11d48 120%);
  box-shadow: 0 8px 22px rgba(244, 63, 94, 0.42);
}
.cta-main:hover {
  filter: brightness(1.08);
  transform: translateY(-1.5px);
  box-shadow: 0 12px 28px rgba(244, 63, 94, 0.55);
}
.cta-main:active { transform: translateY(0) scale(0.985); }
.cta-user {
  background: linear-gradient(120deg, #10d98a 0%, #0bbf7e 55%, #0aa06c 120%);
  box-shadow: 0 8px 22px rgba(16, 217, 138, 0.35);
}
.cta-user:hover {
  filter: brightness(1.08);
  transform: translateY(-1.5px);
  box-shadow: 0 12px 28px rgba(16, 217, 138, 0.48);
}
.cta-user:active { transform: translateY(0) scale(0.985); }

@media (max-width: 720px) {
  .hero-inner { padding: 0 20px; }
  .hero-title { font-size: 40px; }
  .hero-lead { font-size: 17px; }
  .cta { height: 46px; padding: 0 22px; font-size: 15px; }
}
</style>
