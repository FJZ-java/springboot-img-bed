<template>
  <teleport to="body">
    <transition name="pv-fade">
      <div v-if="state.show" ref="rootEl" class="pv-root" @click="maybeClose">
        <!-- 顶部信息栏 -->
        <div class="pv-top" @click.stop>
          <div class="pv-info">
            <div class="pv-name" :title="current?.name">{{ current?.name }}</div>
            <div class="pv-meta">
              <span v-if="state.items.length > 1" class="pv-chip">
                {{ state.index + 1 }} / {{ state.items.length }}
              </span>
              <span v-if="current?.size">{{ formatSize(current.size) }}</span>
              <span v-if="natural">{{ natural.w }} × {{ natural.h }}</span>
              <span class="pv-scale">{{ Math.round(scale * 100) }}%</span>
            </div>
          </div>
          <div class="pv-top-actions">
            <button class="pv-btn" @click="copyLink">复制链接</button>
            <button
              v-if="current?.recordId"
              class="pv-btn"
              :disabled="sharing"
              title="生成可公开访问的分享页链接"
              @click="shareCurrent"
            >{{ sharing ? '生成中…' : '🔗 分享' }}</button>
            <button class="pv-btn" @click="openRaw">新标签打开</button>
            <button class="pv-btn pv-close" title="关闭（Esc）" @click="close">✕</button>
          </div>
        </div>

        <!-- 图片舞台 -->
        <div
          ref="stageEl"
          class="pv-stage"
          :class="{
            'is-grab': scale > 1 && !dragging,
            'is-grabbing': dragging
          }"
          @wheel.prevent="onWheel"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
          @dblclick="onDblClick"
          @click="maybeClose"
        >
          <button
            v-if="state.items.length > 1"
            class="pv-nav pv-prev"
            title="上一张（←）"
            @click.stop="step(-1)"
          >‹</button>

          <img
            v-if="!failed"
            ref="imgEl"
            class="pv-img"
            :class="{ 'is-sideways': sideways }"
            :src="current?.src"
            :alt="current?.name"
            :style="imgStyle"
            draggable="false"
            @load="onLoad"
            @error="onError"
            @click.stop
          />
          <div v-else class="pv-failed">
            <div class="pv-failed-icon">🖼️</div>
            <p>图片加载失败</p>
            <p class="pv-failed-tip">{{ failTip }}</p>
          </div>

          <div v-if="loadingImg && !failed" class="pv-loading"><span class="pv-spinner"></span></div>

          <button
            v-if="state.items.length > 1"
            class="pv-nav pv-next"
            title="下一张（→）"
            @click.stop="step(1)"
          >›</button>
        </div>

        <!-- 底部工具条 -->
        <div class="pv-bottom" @click.stop>
          <button class="pv-tool" :disabled="scale <= MIN" title="缩小（-）" @click="zoomBy(1 / 1.25)">－</button>
          <span class="pv-zoom">{{ Math.round(scale * 100) }}%</span>
          <button class="pv-tool" :disabled="scale >= MAX" title="放大（+）" @click="zoomBy(1.25)">＋</button>
          <span class="pv-line"></span>
          <button class="pv-tool" title="适应屏幕（0）" @click="resetTransform">适应屏幕</button>
          <button class="pv-tool" @click="setScale(1)">100%</button>
          <button class="pv-tool" title="旋转 90°" @click="rotateBy(90)">旋转 ⟳</button>
          <button class="pv-tool" :disabled="isDefault" title="重置" @click="fullReset">重置</button>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { previewState as state, closePreview } from '../utils/previewer'
import { copyText, createShare } from '../utils/share'

const MIN = 0.1
const MAX = 8

const message = useMessage()
const rootEl = ref(null)
const stageEl = ref(null)
const imgEl = ref(null)

const scale = ref(1)
const tx = ref(0)
const ty = ref(0)
const rot = ref(0)
const dragging = ref(false)
const loadingImg = ref(false)
const failed = ref(false)
const failTip = ref('')
const natural = ref(null)
const sharing = ref(false)

let dragStart = null
let dragMoved = false

const current = computed(() => state.items[state.index] || null)
const sideways = computed(() => Math.abs(rot.value % 180) === 90)
const isDefault = computed(() => scale.value === 1 && tx.value === 0 && ty.value === 0 && rot.value === 0)

const imgStyle = computed(() => ({
  transform: `translate3d(${tx.value}px, ${ty.value}px, 0) rotate(${rot.value}deg) scale(${scale.value})`,
  transition: dragging.value ? 'none' : 'transform 0.22s cubic-bezier(.22,.61,.36,1)'
}))

/* ---------------- 生命周期 ---------------- */

watch(
  () => state.show,
  (show) => {
    if (show) {
      window.addEventListener('keydown', onKey)
      loadState()
    } else {
      window.removeEventListener('keydown', onKey)
      if (document.body.style.overflow === 'hidden') document.body.style.overflow = ''
    }
  },
  { immediate: true }
)

watch(
  () => current.value?.src,
  () => {
    if (!state.show) return
    loadState()
    preloadNeighbors()
  }
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  if (document.body.style.overflow === 'hidden') document.body.style.overflow = ''
})

function loadState() {
  fullReset()
  failed.value = false
  failTip.value = ''
  natural.value = null
  loadingImg.value = true
}

/* ---------------- 尺寸 / 变换 ---------------- */

function clamp(v, min, max) {
  return Math.min(max, Math.max(min, v))
}

function setScale(v) {
  scale.value = clamp(v, MIN, MAX)
  if (scale.value <= 1) {
    tx.value = 0
    ty.value = 0
  }
}

function zoomBy(k) {
  setScale(scale.value * k)
}

/** 以某个屏幕坐标为中心缩放，保证鼠标指向的像素不跑偏 */
function zoomAt(k, clientX, clientY) {
  const rect = stageEl.value?.getBoundingClientRect()
  if (!rect) return zoomBy(k)
  const cx = clientX - rect.left - rect.width / 2
  const cy = clientY - rect.top - rect.height / 2
  const next = clamp(scale.value * k, MIN, MAX)
  const real = next / scale.value
  tx.value = cx - real * (cx - tx.value)
  ty.value = cy - real * (cy - ty.value)
  scale.value = next
}

function onWheel(e) {
  zoomAt(e.deltaY < 0 ? 1.12 : 1 / 1.12, e.clientX, e.clientY)
}

function onDblClick(e) {
  if (scale.value > 1) {
    resetTransform()
  } else {
    zoomAt(2, e.clientX, e.clientY)
  }
}

function rotateBy(deg) {
  rot.value = (rot.value + deg) % 360
}

function resetTransform() {
  scale.value = 1
  tx.value = 0
  ty.value = 0
}

function fullReset() {
  resetTransform()
  rot.value = 0
}

function onLoad(e) {
  loadingImg.value = false
  failed.value = false
  natural.value = { w: e.target.naturalWidth, h: e.target.naturalHeight }
}

function onError() {
  loadingImg.value = false
  failed.value = true
  failTip.value = current.value?.cdnUrl
    ? 'CDN 缓存可能尚未生效（新上传的图片通常需要几分钟），可直接点「新标签打开」重试'
    : '文件可能已被删除，或本地文件读取失败'
}

/* ---------------- 拖拽平移 ---------------- */

function onPointerDown(e) {
  if (e.button !== 0 || scale.value <= 1) return
  dragging.value = true
  dragMoved = false
  dragStart = { x: e.clientX - tx.value, y: e.clientY - ty.value }
  stageEl.value?.setPointerCapture?.(e.pointerId)
}

function onPointerMove(e) {
  if (!dragging.value || !dragStart) return
  const nextX = e.clientX - dragStart.x
  const nextY = e.clientY - dragStart.y
  if (Math.abs(nextX - tx.value) > 2 || Math.abs(nextY - ty.value) > 2) dragMoved = true
  tx.value = nextX
  ty.value = nextY
}

function onPointerUp(e) {
  if (!dragging.value) return
  dragging.value = false
  dragStart = null
  stageEl.value?.releasePointerCapture?.(e.pointerId)
}

/* ---------------- 切换 / 关闭 ---------------- */

function step(delta) {
  const len = state.items.length
  if (len < 2) return
  state.index = (state.index + delta + len) % len
}

function preloadNeighbors() {
  const len = state.items.length
  if (len < 2) return
  ;[1, -1].forEach((d) => {
    const it = state.items[(state.index + d + len) % len]
    if (it?.src) new Image().src = it.src
  })
}

function maybeClose(e) {
  if (e.target !== rootEl.value && e.target !== stageEl.value) return
  if (dragMoved) {
    dragMoved = false
    return
  }
  close()
}

function close() {
  closePreview()
}

/* ---------------- 外部动作 ---------------- */

async function copyLink() {
  const link = current.value?.cdnUrl
  if (!link) {
    message.warning('这是本地待上传的图片，上传成功后才能复制 CDN 链接')
    return
  }
  try {
    await navigator.clipboard.writeText(link)
    message.success('CDN 链接已复制')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

function openRaw() {
  const url = current.value?.cdnUrl || current.value?.src
  if (url) window.open(url, '_blank', 'noopener')
}

/** 生成分享页链接并复制（仅已入库的记录支持） */
async function shareCurrent() {
  const id = current.value?.recordId
  if (!id || sharing.value) return
  sharing.value = true
  try {
    const info = await createShare(id)
    const ok = await copyText(info.fullUrl)
    if (ok) {
      message.success('分享链接已复制，任何人打开都能看到这张图')
    } else {
      message.info(`分享链接：${info.fullUrl}`)
    }
  } catch (e) {
    message.error(e.message)
  } finally {
    sharing.value = false
  }
}

function onKey(e) {
  switch (e.key) {
    case 'Escape':
      close()
      break
    case 'ArrowLeft':
      step(-1)
      break
    case 'ArrowRight':
      step(1)
      break
    case '+':
    case '=':
      zoomBy(1.25)
      break
    case '-':
    case '_':
      zoomBy(1 / 1.25)
      break
    case '0':
      resetTransform()
      break
    case 'r':
    case 'R':
      rotateBy(90)
      break
    default:
      return
  }
  e.preventDefault()
}

function formatSize(bytes) {
  if (typeof bytes !== 'number') return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.pv-root {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  background: rgba(6, 6, 16, 0.82);
  backdrop-filter: blur(14px) saturate(120%);
  -webkit-backdrop-filter: blur(14px) saturate(120%);
}

/* 顶部栏 */
.pv-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  background: linear-gradient(180deg, rgba(12, 12, 26, 0.9), rgba(12, 12, 26, 0.4));
}
.pv-info { min-width: 0; }
.pv-name {
  font-size: 14px;
  color: #ede9ff;
  max-width: 52vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pv-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}
.pv-chip {
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(124, 92, 255, 0.22);
  border: 1px solid rgba(124, 92, 255, 0.45);
  color: #c9b8ff;
}
.pv-scale { color: #8ee8d6; }
.pv-top-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.pv-btn {
  padding: 6px 14px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.78);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.18s;
}
.pv-btn:hover {
  color: #fff;
  background: rgba(124, 92, 255, 0.28);
  border-color: rgba(124, 92, 255, 0.6);
}
.pv-close {
  width: 32px;
  height: 32px;
  padding: 0;
  font-size: 15px;
  border-radius: 50%;
}
.pv-close:hover { background: rgba(248, 113, 113, 0.28); border-color: rgba(248, 113, 113, 0.6); }

/* 舞台 */
.pv-stage {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  touch-action: none;
  min-height: 0;
}
.pv-stage.is-grab { cursor: grab; }
.pv-stage.is-grabbing { cursor: grabbing; }

.pv-img {
  max-width: 92vw;
  max-height: calc(100vh - 210px);
  object-fit: contain;
  border-radius: 6px;
  box-shadow: 0 30px 90px rgba(0, 0, 0, 0.65);
  will-change: transform;
  user-select: none;
  -webkit-user-drag: none;
}
/* 旋转 90°/270° 时交换约束，避免图片横向溢出 */
.pv-img.is-sideways {
  max-width: calc(100vh - 210px);
  max-height: 92vw;
}

.pv-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 48px;
  height: 76px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34px;
  line-height: 1;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.18s;
  z-index: 2;
}
.pv-nav:hover {
  color: #fff;
  background: rgba(124, 92, 255, 0.35);
  border-color: rgba(124, 92, 255, 0.7);
}
.pv-prev { left: 20px; }
.pv-next { right: 20px; }

.pv-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.pv-spinner {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.15);
  border-top-color: #7c5cff;
  animation: pv-spin 0.8s linear infinite;
}
@keyframes pv-spin { to { transform: rotate(360deg); } }

.pv-failed {
  text-align: center;
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
}
.pv-failed-icon { font-size: 42px; margin-bottom: 10px; }
.pv-failed-tip { font-size: 12px; color: rgba(255, 255, 255, 0.38); max-width: 380px; }

/* 底部工具条 */
.pv-bottom {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 20px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  background: linear-gradient(0deg, rgba(12, 12, 26, 0.9), rgba(12, 12, 26, 0.35));
}
.pv-tool {
  padding: 6px 12px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.75);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 9px;
  cursor: pointer;
  transition: all 0.18s;
}
.pv-tool:hover:not(:disabled) {
  color: #fff;
  background: rgba(124, 92, 255, 0.28);
  border-color: rgba(124, 92, 255, 0.6);
}
.pv-tool:disabled { opacity: 0.35; cursor: not-allowed; }
.pv-zoom {
  min-width: 52px;
  text-align: center;
  font-size: 12px;
  color: #8ee8d6;
  font-variant-numeric: tabular-nums;
}
.pv-line {
  width: 1px;
  height: 18px;
  margin: 0 6px;
  background: rgba(255, 255, 255, 0.12);
}

/* 过渡 */
.pv-fade-enter-active, .pv-fade-leave-active { transition: opacity 0.22s ease; }
.pv-fade-enter-active .pv-img, .pv-fade-leave-active .pv-img { transition: transform 0.22s ease, opacity 0.22s ease; }
.pv-fade-enter-from, .pv-fade-leave-to { opacity: 0; }

@media (max-width: 720px) {
  .pv-name { max-width: 40vw; }
  .pv-nav { width: 38px; height: 58px; font-size: 26px; }
  .pv-prev { left: 8px; }
  .pv-next { right: 8px; }
  .pv-btn { padding: 6px 10px; }
}
</style>
