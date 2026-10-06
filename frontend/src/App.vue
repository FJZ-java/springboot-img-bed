<template>
  <n-config-provider :theme="darkTheme" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <div class="aurora-bg"></div>
        <div style="position: relative; z-index: 1; min-height: 100vh;">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
          <!-- 全局图片预览器（上传队列 / 上传记录 / 仓库管理共用） -->
          <ImagePreview />
        </div>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup>
import { darkTheme } from 'naive-ui'
import ImagePreview from './components/ImagePreview.vue'

const themeOverrides = {
  common: {
    primaryColor: '#7c5cff',
    primaryColorHover: '#8f73ff',
    primaryColorPressed: '#6a4de8',
    borderRadius: '10px',
    fontSize: '14px',
    errorColor: '#f87171',
    errorColorHover: '#fca5a5'
  },
  Card: {
    color: 'rgba(255,255,255,0.04)',
    borderColor: 'rgba(255,255,255,0.08)'
  },
  Button: {
    textColorPrimary: '#fff',
    heightLarge: '46px',
    fontWeight: '600'
  },
  Input: {
    heightLarge: '46px',
    color: 'rgba(255,255,255,0.04)',
    colorFocus: 'rgba(124,92,255,0.06)',
    border: '1px solid rgba(255,255,255,0.10)',
    borderHover: '1px solid rgba(124,92,255,0.45)',
    borderFocus: '1px solid rgba(124,92,255,0.85)',
    boxShadowFocus: '0 0 0 3px rgba(124,92,255,0.18)',
    borderRadius: '12px',
    caretColor: '#a78bfa',
    placeholderColor: 'rgba(255,255,255,0.32)',
    textColor: '#e6e6eb'
  },
  Form: {
    feedbackHeight: '22px'
  }
}
</script>
