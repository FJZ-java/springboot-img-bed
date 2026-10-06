<template>
  <n-modal
    v-model:show="authModalVisible"
    :auto-focus="false"
    :mask-closable="true"
    transform-origin="center"
    class="auth-modal"
  >
    <div class="auth-card">
      <button class="modal-close" title="关闭" @click="closeAuthModal">✕</button>

      <header class="auth-head">
        <h2>{{ authModalMode === 'login' ? '登录火星图床' : '注册火星图床' }}</h2>
        <p>
          {{ authModalMode === 'login'
            ? '登录后即可上传图片，并获得全球 CDN 加速访问链接'
            : '注册即可免费托管图片，全球 CDN 加速访问' }}
        </p>
      </header>

      <AuthPanel
        :initial-mode="authModalMode"
        @success="onSuccess"
        @mode-change="(m) => (authModalMode = m)"
      />
    </div>
  </n-modal>

  <!-- 弹窗打开时左上角提供「返回首页」 -->
  <Teleport to="body">
    <button v-if="authModalVisible" class="back-home" @click="goHome">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M19 12H5m6-6-6 6 6 6" />
      </svg>
      返回首页
    </button>
  </Teleport>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import AuthPanel from './AuthPanel.vue'
import { authModalVisible, authModalMode, closeAuthModal } from '../utils/authModal'

const router = useRouter()
const message = useMessage()

function onSuccess(user) {
  closeAuthModal()
  message.success(`欢迎，${user?.nickname || user?.username || '用户'}！`)

  // 登录后进入后台管理：优先回到因未登录被拦下的那个页面，默认进「上传图片」
  const target = sessionStorage.getItem('imgbed_redirect')
  sessionStorage.removeItem('imgbed_redirect')
  router.replace(target || '/upload')
}

function goHome() {
  closeAuthModal()
  router.push('/')
}
</script>

<style scoped>
.auth-card {
  position: relative;
  width: min(400px, 92vw);
  max-width: none;
  padding: 32px 30px 26px;
  border-radius: 14px;
  background: rgba(13, 18, 28, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.09);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  animation: card-in 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}
@keyframes card-in {
  from { opacity: 0; transform: translateY(12px) scale(0.985); }
  to { opacity: 1; transform: none; }
}

.modal-close {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: rgba(230, 236, 245, 0.45);
  background: transparent;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.18s;
}
.modal-close:hover { color: #fff; background: rgba(255, 255, 255, 0.08); }

.auth-head { margin-bottom: 22px; }
.auth-head h2 {
  margin: 0 0 8px;
  font-size: 23px;
  font-weight: 700;
  letter-spacing: -0.2px;
  color: #f2f5f9;
}
.auth-head p { margin: 0; font-size: 12.5px; color: rgba(230, 236, 245, 0.5); }

/* 左上角的返回首页 */
.back-home {
  position: fixed;
  top: 20px;
  left: 24px;
  z-index: 2401;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  font-family: inherit;
  font-size: 13px;
  color: rgba(230, 236, 245, 0.85);
  background: rgba(13, 18, 28, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  cursor: pointer;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition: all 0.18s;
}
.back-home:hover { color: #fff; border-color: rgba(56, 189, 208, 0.6); background: rgba(13, 18, 28, 0.8); }
.back-home svg { width: 15px; height: 15px; }
</style>
