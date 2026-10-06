<template>
  <div class="share-page">
    <div class="aurora-bg"></div>

    <header class="top">
      <div class="brand" @click="$router.push('/')">
        <span class="logo">🪐</span>
        <span class="name gradient-text">火星图床</span>
      </div>
      <div class="top-right">
        <button class="ghost-btn" @click="$router.push('/')">去上传我的图片 →</button>
      </div>
    </header>

    <main class="wrap">
      <n-spin :show="loading">
        <div v-if="info" class="glass-card card">
          <div class="stage" @click="openPreview">
            <img :src="info.cdnUrl" :alt="info.originName" />
            <div class="stage-mask"><span>🔍 点击看大图</span></div>
          </div>

          <div class="meta">
            <h1 class="title">{{ info.originName }}</h1>
            <div class="sub">
              <span>{{ formatSize(info.size) }}</span>
              <span v-if="info.owner">· 由 {{ info.owner }} 分享</span>
              <span>· {{ info.shareTime }}</span>
              <span>· 已访问 {{ info.viewCount }} 次</span>
            </div>

            <div class="links">
              <div class="link-row">
                <span class="label">图片直链</span>
                <n-input :value="info.cdnUrl" readonly size="small" />
                <n-button size="small" secondary @click="copy(info.cdnUrl)">复制</n-button>
              </div>
              <div class="link-row">
                <span class="label">Markdown</span>
                <n-input :value="md" readonly size="small" />
                <n-button size="small" secondary @click="copy(md)">复制</n-button>
              </div>
              <div class="link-row">
                <span class="label">HTML</span>
                <n-input :value="html" readonly size="small" />
                <n-button size="small" secondary @click="copy(html)">复制</n-button>
              </div>
            </div>

            <div class="foot">
              <n-button type="primary" size="small" @click="copy(info.cdnUrl)">复制直链</n-button>
              <n-button size="small" secondary @click="openRaw">新标签打开</n-button>
              <n-button size="small" secondary @click="download">下载原图</n-button>
            </div>
          </div>
        </div>

        <div v-else-if="!loading" class="glass-card empty-card">
          <div class="empty-icon">🫥</div>
          <p class="empty-title">{{ errorMsg || '分享链接不存在' }}</p>
          <p class="empty-desc">链接可能已被创建者取消，或对应图片已被删除。</p>
          <n-button type="primary" size="small" @click="$router.push('/')">回到首页</n-button>
        </div>
      </n-spin>
    </main>

    <footer class="foot-note">图片由 火星图床 托管 · 通过 jsDelivr CDN 加速分发</footer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import api from '../api'
import { copyText } from '../utils/share'
import { openPreview as showPreview } from '../utils/previewer'

const route = useRoute()
const message = useMessage()

const loading = ref(true)
const info = ref(null)
const errorMsg = ref('')

onMounted(async () => {
  try {
    const res = await api.get(`/share/${route.params.code}`)
    info.value = res.data
    document.title = `${res.data.originName} · 图片分享`
  } catch (e) {
    errorMsg.value = e.message || '分享链接不存在'
  } finally {
    loading.value = false
  }
})

const md = computed(() => `![${info.value?.originName || 'image'}](${info.value?.cdnUrl || ''})`)
const html = computed(() => `<img src="${info.value?.cdnUrl || ''}" alt="${info.value?.originName || ''}" />`)

async function copy(text) {
  const ok = await copyText(text)
  ok ? message.success('已复制到剪贴板') : message.error('复制失败，请手动复制')
}

function openRaw() {
  if (info.value?.cdnUrl) window.open(info.value.cdnUrl, '_blank', 'noopener')
}

function openPreview() {
  if (!info.value) return
  showPreview([{ src: info.value.cdnUrl, cdnUrl: info.value.cdnUrl, name: info.value.originName, size: info.value.size }], 0)
}

function download() {
  if (!info.value?.cdnUrl) return
  const a = document.createElement('a')
  a.href = info.value.cdnUrl
  a.download = info.value.originName || 'image'
  a.target = '_blank'
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

function formatSize(bytes) {
  const n = Number(bytes) || 0
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.share-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  z-index: 1;
}
.top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 28px;
}
.brand { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.logo { font-size: 20px; }
.name { font-size: 17px; font-weight: 700; }
.ghost-btn {
  padding: 7px 14px;
  font-size: 12.5px;
  color: var(--text);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.18s;
}
.ghost-btn:hover { background: rgba(124, 92, 255, 0.28); border-color: rgba(124, 92, 255, 0.6); }

.wrap {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 10px 20px 30px;
}
.card {
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 20px;
  padding: 18px;
  height: fit-content;
}
.stage {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.32);
  border: 1px solid var(--border);
  cursor: zoom-in;
  min-height: 280px;
  display: grid;
  place-items: center;
}
.stage img { width: 100%; display: block; object-fit: contain; max-height: 52vh; }
.stage-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: #fff;
  background: rgba(10, 8, 24, 0.5);
  opacity: 0;
  transition: opacity 0.18s;
}
.stage:hover .stage-mask { opacity: 1; }

.meta { display: flex; flex-direction: column; gap: 10px; min-width: 0; }
.title {
  margin: 0;
  font-size: 19px;
  word-break: break-all;
  line-height: 1.35;
}
.sub { display: flex; flex-wrap: wrap; gap: 8px; font-size: 12px; color: var(--text-dim); }
.links { display: flex; flex-direction: column; gap: 8px; margin-top: 4px; }
.link-row { display: flex; align-items: center; gap: 8px; }
.label { width: 62px; flex-shrink: 0; font-size: 12px; color: var(--text-dim); }
.link-row :deep(.n-input) { flex: 1; min-width: 0; }
.foot { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 6px; }

.empty-card {
  width: min(460px, 100%);
  margin: 40px auto;
  padding: 44px 28px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.empty-icon { font-size: 40px; }
.empty-title { margin: 0; font-size: 16px; }
.empty-desc { margin: 0 0 8px; font-size: 12.5px; color: var(--text-dim); }

.foot-note {
  position: relative;
  z-index: 2;
  text-align: center;
  padding: 14px 20px 20px;
  font-size: 11.5px;
  color: var(--text-dim);
}

@media (max-width: 860px) {
  .card { grid-template-columns: 1fr; }
  .stage img { max-height: 44vh; }
  .top { padding: 14px 16px; }
}
</style>
