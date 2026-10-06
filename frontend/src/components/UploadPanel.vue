<template>
  <div class="upload-panel">
    <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onSelect" />

    <!-- 内嵌拖拽区（/upload 页面用；首页用 Hero 按钮直接唤起文件选择） -->
    <template v-if="dropzone">
      <div
        class="glass-card drop-zone"
        :class="{ dragging }"
        @click="pick"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <div class="drop-icon">🚀</div>
        <p class="drop-text">点击选择 或 拖拽图片到此处</p>
        <p class="drop-hint">支持 PNG / JPG / GIF / WebP / SVG 等格式，单文件最大 20MB，单次最多 {{ max }} 张</p>
      </div>
      <p v-if="!auth.isLoggedIn" class="guest-line">
        当前为<b>游客模式</b>：选中即自动上传，图片会真实存入仓库并返回 CDN 链接，但不会记入上传记录。
        <a @click="openAuthModal('login')">登录后管理全部图片 →</a>
      </p>
    </template>

    <!-- 整页拖拽提示 -->
    <teleport to="body">
      <transition name="fade">
        <div v-if="dragging" class="drag-veil">
          <div class="drag-card">
            <div class="drag-icon">📥</div>
            <p>松开鼠标，加入上传队列</p>
          </div>
        </div>
      </transition>

      <!-- 右侧上传队列抽屉 -->
      <transition name="queue-slide">
        <aside v-if="queueOpen && queue.length" class="queue-drawer" :style="{ top: topOffset + 'px' }">
          <header class="qd-head">
            <div class="qd-head-text">
              <div class="qd-title">
                上传队列
                <span class="qd-count">{{ doneCount }} / {{ queue.length }}</span>
              </div>
              <div class="qd-sub">
                {{ auth.isLoggedIn ? '上传后自动记入我的上传记录' : '游客上传不会记入记录' }} · 单次最多 {{ max }} 张
              </div>
            </div>
            <button class="qd-close" title="收起队列" @click="queueOpen = false">✕</button>
          </header>

          <n-progress
            v-if="uploading"
            type="line"
            :percentage="overallPercent"
            :show-indicator="false"
            processing
            class="qd-progress"
          />

          <div class="qd-body">
            <div v-for="(item, idx) in queue" :key="item.id" class="qd-item">
              <div class="thumb-wrap" title="点击预览大图" @click="previewAt(idx)">
                <img :src="item.preview" class="thumb" :alt="item.name" />
                <div class="thumb-mask"><span>🔍</span></div>
              </div>

              <div class="meta">
                <div class="name" :title="item.name">{{ item.name }}</div>
                <div class="size">
                  {{ formatSize(item.size) }}
                  <span v-if="item.status === 'success' && !item.saved" class="badge-guest">未记录</span>
                  <span v-else-if="item.status === 'success'" class="badge-ok">已记录</span>
                </div>

                <n-progress
                  v-if="item.status === 'uploading'"
                  type="line"
                  :percentage="item.progress"
                  :show-indicator="false"
                  processing
                />

                <div v-if="item.status === 'success'" class="result">
                  <n-input :value="item.cdnUrl" size="tiny" readonly />
                  <n-button size="tiny" type="primary" secondary @click="copy(item.cdnUrl)">复制</n-button>
                  <n-button size="tiny" secondary @click="copy(markdown(item))">MD</n-button>
                </div>

                <div v-if="item.status === 'error'" class="error-text">❌ {{ item.error }}</div>
              </div>

              <div class="status-col">
                <span class="status-icon">
                  <template v-if="item.status === 'success'">✅</template>
                  <template v-else-if="item.status === 'error'">❌</template>
                  <template v-else-if="item.status === 'uploading'">{{ item.progress }}%</template>
                  <template v-else>⏳</template>
                </span>
                <button
                  v-if="item.status !== 'uploading'"
                  class="del-btn"
                  title="从队列移除"
                  @click="removeItem(idx)"
                >✕</button>
              </div>
            </div>
          </div>

          <footer class="qd-foot">
            <n-button
              v-if="hasPending"
              type="primary"
              size="small"
              :loading="uploading"
              @click="uploadAll"
            >上传剩余 {{ pendingCount }} 张</n-button>
            <n-button v-else-if="failedCount" type="primary" size="small" :loading="uploading" @click="retryFailed">
              重试失败 {{ failedCount }} 张
            </n-button>
            <n-button size="small" quaternary @click="clearQueue">清空队列</n-button>
          </footer>
        </aside>
      </transition>
    </teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import api from '../api'
import { useAuthStore } from '../stores/auth'
import { openPreview } from '../utils/previewer'
import { openAuthModal } from '../utils/authModal'

const props = defineProps({
  /** 是否显示内嵌的大拖拽区（首页由 Hero 按钮触发，不需要） */
  dropzone: { type: Boolean, default: true },
  /** 选中后是否自动开始上传 */
  autoUpload: { type: Boolean, default: true },
  /** 单次最多可上传的文件数 */
  max: { type: Number, default: 9 },
  /** 抽屉顶部偏移（有导航栏的后台页面传 62） */
  topOffset: { type: Number, default: 0 }
})

const MAX_SIZE = 20 * 1024 * 1024

const emit = defineEmits(['uploaded'])

const message = useMessage()
const auth = useAuthStore()

const fileInput = ref(null)
const dragging = ref(false)
const uploading = ref(false)
const queueOpen = ref(false)
const queue = ref([])

let seq = 0
const hasPending = computed(() => queue.value.some(i => i.status === 'pending'))
const pendingCount = computed(() => queue.value.filter(i => i.status === 'pending').length)
const failedCount = computed(() => queue.value.filter(i => i.status === 'error').length)
const doneCount = computed(() => queue.value.filter(i => i.status === 'success').length)
const overallPercent = computed(() => {
  if (!queue.value.length) return 0
  const sum = queue.value.reduce((acc, i) => acc + (i.status === 'success' ? 100 : i.progress || 0), 0)
  return Math.round(sum / queue.value.length)
})

/** 供父组件（首页 Hero 的「立刻上传」）调用 */
function pick() {
  fileInput.value?.click()
}
defineExpose({ pick })

onMounted(() => {
  window.addEventListener('dragover', onWindowDragOver)
  window.addEventListener('dragleave', onWindowDragLeave)
  window.addEventListener('drop', onWindowDrop)
})

onBeforeUnmount(() => {
  window.removeEventListener('dragover', onWindowDragOver)
  window.removeEventListener('dragleave', onWindowDragLeave)
  window.removeEventListener('drop', onWindowDrop)
  queue.value.forEach(i => {
    if (i.preview?.startsWith('blob:')) URL.revokeObjectURL(i.preview)
  })
})

/* ---------- 全页拖拽 ---------- */
function hasFiles(e) {
  const dt = e.dataTransfer
  return !!dt && Array.from(dt.types || []).includes('Files')
}

function onWindowDragOver(e) {
  if (!hasFiles(e)) return
  e.preventDefault()
  dragging.value = true
}

function onWindowDragLeave(e) {
  // relatedTarget 为 null 说明真正离开了窗口
  if (e.relatedTarget === null) dragging.value = false
}

function onWindowDrop(e) {
  if (!hasFiles(e)) return
  e.preventDefault()
  dragging.value = false
  addFiles(e.dataTransfer.files)
}

function onSelect(e) {
  addFiles(e.target.files)
  e.target.value = ''
}

function onDrop(e) {
  dragging.value = false
  addFiles(e.dataTransfer.files)
}

/* ---------- 队列 ---------- */
async function addFiles(files) {
  let added = 0
  let overflowed = 0
  let skippedType = 0
  let skippedSize = 0

  for (const file of files) {
    // 数量上限：单次最多 max 张
    // 注意：push 之后 queue.value.length 已包含本次新增，这里不能再叠加 added（否则每张会被算两次）
    if (queue.value.length >= props.max) {
      overflowed++
      continue
    }
    if (!file.type.startsWith('image/')) {
      skippedType++
      continue
    }
    if (file.size > MAX_SIZE) {
      skippedSize++
      continue
    }
    queue.value.push({
      id: ++seq,
      file,
      name: file.name,
      size: file.size,
      preview: URL.createObjectURL(file),
      status: 'pending',
      progress: 0,
      cdnUrl: '',
      saved: false,
      error: ''
    })
    added++
  }
  if (overflowed) {
    message.warning(`单次最多上传 ${props.max} 张图片，已忽略多余的 ${overflowed} 张`)
  }
  if (skippedType) message.warning(`已跳过 ${skippedType} 个非图片文件`)
  if (skippedSize) message.warning(`已跳过 ${skippedSize} 个超过 20MB 的文件`)
  if (!added) return
  queueOpen.value = true // 选中图片后弹出上传队列
  if (props.autoUpload && !uploading.value) {
    await uploadAll()
  }
}

async function uploadAll() {
  if (uploading.value) return
  uploading.value = true
  const logged = auth.isLoggedIn
  for (const item of queue.value) {
    if (item.status !== 'pending') continue
    item.status = 'uploading'
    item.progress = 0
    const fd = new FormData()
    fd.append('file', item.file)
    try {
      // 登录用户走记录接口（写入上传记录），游客走免登录接口（不记录）
      const res = await api.post(logged ? '/records/upload' : '/guest/upload', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (e) => {
          item.progress = e.total ? Math.round((e.loaded / e.total) * 100) : 0
        }
      })
      item.status = 'success'
      item.progress = 100
      item.cdnUrl = res.data.cdnUrl
      item.saved = logged
    } catch (e) {
      item.status = 'error'
      item.error = e.message
    }
  }
  uploading.value = false
  const okCount = queue.value.filter(i => i.status === 'success').length
  if (okCount) {
    emit('uploaded', okCount)
    message.success(
      logged
        ? `已上传 ${okCount} 张，已记入上传记录`
        : `已上传 ${okCount} 张，CDN 链接可直接使用（登录后会记入记录）`,
      { duration: 4000 }
    )
  }
}

function retryFailed() {
  queue.value.forEach(i => {
    if (i.status === 'error') {
      i.status = 'pending'
      i.error = ''
    }
  })
  uploadAll()
}

function previewAt(idx) {
  openPreview(
    queue.value.map(i => ({ src: i.preview, name: i.name, size: i.size, cdnUrl: i.cdnUrl })),
    idx
  )
}

function removeItem(idx) {
  const item = queue.value[idx]
  if (item?.preview?.startsWith('blob:')) URL.revokeObjectURL(item.preview)
  queue.value.splice(idx, 1)
}

function clearQueue() {
  queue.value.forEach(i => {
    if (i.preview?.startsWith('blob:')) URL.revokeObjectURL(i.preview)
  })
  queue.value = []
  queueOpen.value = false
}

function markdown(item) {
  return `![${item.name}](${item.cdnUrl})`
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.upload-panel { display: flex; flex-direction: column; gap: 14px; }

/* 内嵌拖拽区 */
.drop-zone {
  padding: 52px 24px;
  text-align: center;
  cursor: pointer;
  border: 1.5px dashed var(--border);
  transition: border-color 0.2s, transform 0.2s, background 0.2s;
}
.drop-zone:hover, .drop-zone.dragging {
  border-color: var(--accent);
  transform: translateY(-2px);
  background: rgba(124, 92, 255, 0.05);
}
.drop-icon { font-size: 40px; margin-bottom: 10px; }
.drop-text { font-size: 16px; margin: 0 0 6px; }
.drop-hint { color: var(--text-dim); font-size: 12px; margin: 0; }
.guest-line {
  margin: 0;
  font-size: 12.5px;
  color: var(--text-dim);
  padding-left: 2px;
}
.guest-line b { color: #c9b8ff; }
.guest-line a { color: var(--accent2); cursor: pointer; }
.guest-line a:hover { text-decoration: underline; }

/* 全页拖拽遮罩 */
.drag-veil {
  position: fixed;
  inset: 0;
  z-index: 2500;
  display: grid;
  place-items: center;
  background: rgba(6, 6, 16, 0.72);
  backdrop-filter: blur(6px);
}
.drag-card {
  padding: 40px 56px;
  text-align: center;
  border-radius: 20px;
  border: 2px dashed rgba(255, 122, 69, 0.65);
  background: rgba(255, 90, 54, 0.08);
  color: #ffd9c2;
}
.drag-icon { font-size: 44px; margin-bottom: 10px; }
.drag-card p { margin: 0; font-size: 15px; }

/* 右侧上传队列抽屉 */
.queue-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 392px;
  max-width: 92vw;
  z-index: 2200;
  display: flex;
  flex-direction: column;
  background: rgba(14, 13, 24, 0.92);
  border-left: 1px solid var(--border);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  box-shadow: -18px 0 50px rgba(0, 0, 0, 0.5);
}
.qd-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px 14px;
  border-bottom: 1px solid var(--border);
}
.qd-title { font-size: 15px; font-weight: 600; display: flex; align-items: center; gap: 10px; }
.qd-count {
  font-size: 12px;
  padding: 1px 9px;
  border-radius: 999px;
  color: #c9b8ff;
  background: rgba(124, 92, 255, 0.2);
  border: 1px solid rgba(124, 92, 255, 0.4);
  font-variant-numeric: tabular-nums;
}
.qd-sub { margin-top: 5px; font-size: 11.5px; color: var(--text-dim); }
.qd-close {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: var(--text-dim);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.18s;
}
.qd-close:hover { color: #fff; background: rgba(248, 113, 113, 0.28); border-color: rgba(248, 113, 113, 0.6); }
.qd-progress { padding: 10px 18px 0; }

.qd-body { flex: 1; overflow-y: auto; padding: 12px 14px; display: flex; flex-direction: column; gap: 10px; }
.qd-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid var(--border);
}
.thumb-wrap {
  position: relative;
  width: 62px;
  height: 62px;
  flex-shrink: 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  cursor: zoom-in;
  background-color: rgba(0, 0, 0, 0.28);
  background-image:
    linear-gradient(45deg, rgba(255, 255, 255, 0.05) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(255, 255, 255, 0.05) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(255, 255, 255, 0.05) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(255, 255, 255, 0.05) 75%);
  background-size: 12px 12px;
  background-position: 0 0, 0 6px, 6px -6px, -6px 0;
}
.thumb { width: 100%; height: 100%; object-fit: contain; display: block; }
.thumb-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 16px;
  background: rgba(10, 8, 24, 0.55);
  opacity: 0;
  transition: opacity 0.18s;
}
.thumb-wrap:hover .thumb-mask { opacity: 1; }

.meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 5px; }
.name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.size { display: flex; align-items: center; gap: 7px; color: var(--text-dim); font-size: 11.5px; }
.badge-guest {
  padding: 0 7px;
  border-radius: 999px;
  font-size: 10.5px;
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.12);
  border: 1px solid rgba(251, 191, 36, 0.3);
}
.badge-ok {
  padding: 0 7px;
  border-radius: 999px;
  font-size: 10.5px;
  color: #34d399;
  background: rgba(52, 211, 153, 0.12);
  border: 1px solid rgba(52, 211, 153, 0.3);
}
.result { display: flex; gap: 6px; align-items: center; }
.error-text { color: #f87171; font-size: 11.5px; }
.status-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.status-icon { font-size: 15px; color: var(--accent2); font-variant-numeric: tabular-nums; }
.del-btn {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  font-size: 11px;
  color: var(--text-dim);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.18s;
}
.del-btn:hover { color: #fff; background: rgba(248, 113, 113, 0.3); border-color: rgba(248, 113, 113, 0.65); }

.qd-foot {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--border);
}

/* 抽屉动画 */
.queue-slide-enter-active, .queue-slide-leave-active {
  transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.28s ease;
}
.queue-slide-enter-from, .queue-slide-leave-to { transform: translateX(100%); opacity: 0; }

@media (max-width: 720px) {
  .queue-drawer { width: 100%; max-width: 100%; }
}
</style>
