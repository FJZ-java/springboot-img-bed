<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="gradient-text">上传图片</h2>
        <p class="desc">支持批量选择，单次最多 9 张，上传完成后自动返回 jsDelivr CDN 链接</p>
      </div>
      <n-button secondary size="small" @click="$router.push('/records')">📁 查看我的记录</n-button>
    </div>

    <!-- 个人概览 -->
    <div class="stat-row">
      <div class="glass-card stat-card">
        <div class="stat-label">我的图片</div>
        <div class="stat-value">{{ total }}</div>
        <div class="stat-unit">张</div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-label">单次上限</div>
        <div class="stat-value">9</div>
        <div class="stat-unit">张 / 次</div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-label">单张限制</div>
        <div class="stat-value">20</div>
        <div class="stat-unit">MB</div>
      </div>
    </div>

    <!-- 广告轮播位：轮播管理里启用中的图片会在这里展示 -->
    <AdCarousel />

    <UploadPanel :top-offset="62" @uploaded="onUploaded" />

    <!-- 上传结果：最近上传的图片，可直接预览 / 复制 / 分享 / 删除 -->
    <div class="result-section">
      <div class="result-head">
        <div>
          <h3 class="result-title">🖼️ 最近上传</h3>
          <p class="result-sub">刚上传的图片会出现在这里，可直接复制链接、分享或删除</p>
        </div>
        <n-button secondary size="small" :loading="recordsLoading" @click="loadRecords">🔄 刷新</n-button>
      </div>

      <n-spin :show="recordsLoading">
        <n-empty v-if="!recordsLoading && !records.length" description="还没有上传过图片，上传后结果会展示在这里" class="empty" />

        <div v-else class="gallery">
          <div v-for="(r, idx) in records" :key="r.id" class="glass-card card">
            <div class="img-wrap" @click="preview(idx)">
              <img :src="r.cdnUrl" :alt="r.originName" loading="lazy" />
              <div class="zoom-mask"><span>🔍 点击预览</span></div>
              <span v-if="sharedMap[r.id]" class="shared-badge" title="已生成分享链接">🔗 已分享</span>
            </div>
            <div class="card-body">
              <div class="name" :title="r.originName">{{ r.originName }}</div>
              <div class="info">{{ formatSize(r.size) }} · {{ fmtTime(r.createTime) }}</div>
              <div class="actions">
                <n-button size="tiny" secondary type="primary" @click="preview(idx)">预览</n-button>
                <n-button size="tiny" secondary type="info" @click="openShare(r)">🔗 分享</n-button>
                <n-button size="tiny" secondary @click="copy(r.cdnUrl)">链接</n-button>
                <n-button size="tiny" secondary @click="copy(`![${r.originName}](${r.cdnUrl})`)">MD</n-button>
                <n-button size="tiny" secondary type="error" @click="remove(r)">删除</n-button>
              </div>
            </div>
          </div>
        </div>
      </n-spin>
    </div>

    <ShareModal v-model:show="shareShow" :record="shareRecord" @canceled="onShareCanceled" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import UploadPanel from '../components/UploadPanel.vue'
import AdCarousel from '../components/AdCarousel.vue'
import ShareModal from '../components/ShareModal.vue'
import api from '../api'
import { openPreview } from '../utils/previewer'
import { copyText, fetchShare } from '../utils/share'

const message = useMessage()
const dialog = useDialog()

const total = ref(0)
const records = ref([])
const recordsLoading = ref(false)
const sharedMap = ref({})

const shareShow = ref(false)
const shareRecord = ref(null)

onMounted(() => {
  fetchTotal()
  loadRecords()
})

async function fetchTotal() {
  try {
    const res = await api.get('/records', { params: { page: 1, size: 1 } })
    total.value = res.data?.total ?? 0
  } catch { /* 统计失败不影响上传 */ }
}

/** 拉最近 9 张上传记录作为「上传结果」展示 */
async function loadRecords() {
  recordsLoading.value = true
  try {
    const res = await api.get('/records', { params: { page: 1, size: 9 } })
    records.value = res.data.records || []
    markShared()
  } catch (e) {
    message.error(e.message)
  } finally {
    recordsLoading.value = false
  }
}

/** 后台静默拉取每条记录是否已有分享链接（失败不影响主流程） */
async function markShared() {
  const map = { ...sharedMap.value }
  await Promise.all(records.value.map(async (r) => {
    const info = await fetchShare(r.id)
    if (info) map[r.id] = info
    else delete map[r.id]
  }))
  sharedMap.value = map
}

function onUploaded() {
  fetchTotal()
  loadRecords()
}

function preview(idx) {
  openPreview(
    records.value.map(r => ({
      src: r.cdnUrl,
      cdnUrl: r.cdnUrl,
      name: r.originName,
      size: r.size,
      recordId: r.id
    })),
    idx
  )
}

function remove(r) {
  dialog.warning({
    title: '确认删除',
    content: `将同时从 GitHub 仓库删除「${r.originName}」，删除后 CDN 链接将失效，确定继续吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await api.delete(`/records/${r.id}`)
        message.success('已删除')
        fetchTotal()
        loadRecords()
      } catch (e) {
        message.error(e.message)
      }
    }
  })
}

function openShare(r) {
  shareRecord.value = r
  shareShow.value = true
}

function onShareCanceled(recordId) {
  const map = { ...sharedMap.value }
  delete map[recordId]
  sharedMap.value = map
}

async function copy(text) {
  const ok = await copyText(text)
  ok ? message.success('已复制到剪贴板') : message.error('复制失败，请手动复制')
}

function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-head h2 { margin: 0 0 6px; font-size: 26px; }
.desc { color: var(--text-dim); font-size: 13px; margin: 0; }
.desc code { background: var(--bg-soft); padding: 2px 6px; border-radius: 6px; color: var(--accent2); }

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}
.stat-card { padding: 16px 18px; display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
.stat-label { width: 100%; font-size: 12px; color: var(--text-dim); margin-bottom: 4px; }
.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  background: linear-gradient(120deg, #ff7a45, #ffb26b);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  font-variant-numeric: tabular-nums;
}
.stat-value.mini { font-size: 15px; background: none; -webkit-text-fill-color: currentColor; color: var(--text); }
.stat-unit { font-size: 12px; color: var(--text-dim); }

/* 上传结果区 */
.result-section { display: flex; flex-direction: column; gap: 14px; }
.result-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; }
.result-title { margin: 0 0 4px; font-size: 18px; }
.result-sub { margin: 0; font-size: 12px; color: var(--text-dim); }
.empty { padding: 56px 0; }

:deep(.n-spin-container), :deep(.n-spin-content) { width: 100%; }
.gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 16px;
}
.card { overflow: hidden; transition: transform 0.2s, box-shadow 0.2s; }
.card:hover { transform: translateY(-4px); box-shadow: 0 12px 40px rgba(124, 92, 255, 0.18); }
.img-wrap {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  cursor: zoom-in;
  background: rgba(0, 0, 0, 0.3);
}
.zoom-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  background: rgba(10, 8, 24, 0.5);
  opacity: 0;
  transition: opacity 0.18s;
}
.img-wrap:hover .zoom-mask { opacity: 1; }
.img-wrap img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.3s; display: block; }
.card:hover .img-wrap img { transform: scale(1.05); }
.shared-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  font-size: 10.5px;
  padding: 2px 8px;
  border-radius: 999px;
  color: #8ee8d6;
  background: rgba(34, 211, 238, 0.16);
  border: 1px solid rgba(34, 211, 238, 0.4);
}
.card-body { padding: 12px 14px; display: flex; flex-direction: column; gap: 6px; }
.name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.info { color: var(--text-dim); font-size: 11px; }
.actions { display: flex; gap: 6px; flex-wrap: wrap; }

@media (max-width: 720px) {
  .page-head { flex-direction: column; }
}
</style>
