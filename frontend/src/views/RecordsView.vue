<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="gradient-text">上传记录</h2>
        <p class="desc">共 {{ total }} 张图片 · 点击缩略图可预览大图，可勾选后批量删除</p>
      </div>
      <div class="head-actions">
        <n-button secondary :loading="loading" @click="load(1)">🔄 刷新</n-button>
      </div>
    </div>

    <!-- 批量操作条（有勾选时浮现） -->
    <transition name="fade">
      <div v-if="selected.length" class="glass-card toolbar">
        <div class="tb-left">
          <span class="tb-count">已选 {{ selected.length }} 张</span>
          <n-button quaternary size="tiny" @click="selected = []">取消选择</n-button>
        </div>
        <n-button type="error" secondary size="small" :loading="deleting" @click="batchRemove">
          🗑️ 批量删除 ({{ selected.length }})
        </n-button>
      </div>
    </transition>

    <n-spin :show="loading">
      <n-empty v-if="!loading && records.length === 0" description="还没有上传过图片，去上传第一张吧～" class="empty">
        <template #extra>
          <n-button type="primary" @click="$router.push('/upload')">去上传</n-button>
        </template>
      </n-empty>

      <!-- 表格式记录列表 -->
      <div v-else class="glass-card table-card">
        <div class="t-row t-head">
          <div class="t-col c-check">
            <n-checkbox
              :checked="allChecked"
              :indeterminate="someChecked && !allChecked"
              @update:checked="toggleAll"
            />
          </div>
          <div class="t-col c-thumb">预览</div>
          <div class="t-col c-name">文件名</div>
          <div class="t-col c-size">大小</div>
          <div class="t-col c-time">时间</div>
          <div class="t-col c-actions">操作</div>
        </div>

        <div v-for="(r, idx) in records" :key="r.id" class="t-row t-body" :class="{ picked: isChecked(r.id) }">
          <div class="t-col c-check">
            <n-checkbox :checked="isChecked(r.id)" @update:checked="(v) => toggle(r.id, v)" />
          </div>
          <div class="t-col c-thumb">
            <div class="thumb" @click="preview(idx)">
              <img :src="r.cdnUrl" :alt="r.originName" loading="lazy" />
            </div>
          </div>
          <div class="t-col c-name">
            <div class="name-line">
              <span class="name" :title="r.originName">{{ r.originName }}</span>
              <span v-if="sharedMap[r.id]" class="badge shared" title="已生成分享链接">🔗 已分享</span>
            </div>
            <div class="sub-line">
              <span v-if="r.repoName" class="badge repo" :title="'存储仓库 ' + r.repoName">🗄️ {{ r.repoName }}</span>
            </div>
          </div>
          <div class="t-col c-size">{{ formatSize(r.size) }}</div>
          <div class="t-col c-time">{{ r.createTime }}</div>
          <div class="t-col c-actions">
            <n-button size="tiny" secondary class="pill" @click="copy(r.cdnUrl)">复制链接</n-button>
            <n-button size="tiny" secondary class="pill" :loading="sharingId === r.id" @click="openShare(r)">分享</n-button>
            <n-button size="tiny" quaternary type="error" class="del-btn" @click="remove(r)">删除记录</n-button>
          </div>
        </div>

        <div v-if="total > pageSize" class="pager">
          <n-pagination
            v-model:page="page"
            :item-count="total"
            :page-size="pageSize"
            @update:page="load"
          />
        </div>
      </div>
    </n-spin>

    <!-- 分享链接弹窗 -->
    <n-modal v-model:show="shareModal" preset="card" style="width: 560px; max-width: 92vw;" title="分享这张图片">
      <div v-if="shareInfo" class="share-box">
        <div class="share-preview">
          <img :src="shareInfo.cdnUrl" :alt="shareInfo.originName" />
        </div>
        <p class="share-tip">任何人打开下面的链接都可以查看这张图片，无需登录。</p>
        <n-input :value="shareInfo.fullUrl" readonly size="small" class="share-input" />
        <div class="share-actions">
          <n-button type="primary" size="small" @click="copyShare">复制链接</n-button>
          <n-button size="small" secondary @click="openSharePage">打开看看</n-button>
          <n-button size="small" secondary @click="copyShareMarkdown">复制 Markdown</n-button>
          <n-button size="small" quaternary type="error" :loading="canceling" @click="cancelCurrentShare">
            取消分享
          </n-button>
        </div>
        <div class="share-meta">
          已访问 {{ shareInfo.viewCount }} 次 · 生成于 {{ shareInfo.shareTime }}
        </div>
      </div>
      <div v-else class="share-loading"><n-spin size="medium" /></div>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import api from '../api'
import { openPreview } from '../utils/previewer'
import { cancelShare, copyText, createShare, fetchShare } from '../utils/share'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const deleting = ref(false)
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12

/** 当前页勾选的记录 id */
const selected = ref([])
/** 已生成分享链接的记录 id（用于角标，懒加载） */
const sharedMap = ref({})

const shareModal = ref(false)
const shareInfo = ref(null)
const sharingId = ref(null)
const canceling = ref(false)

const allChecked = computed(
  () => records.value.length > 0 && selected.value.length === records.value.length
)
const someChecked = computed(() => selected.value.length > 0)

onMounted(() => load(1))

async function load(p) {
  page.value = p
  loading.value = true
  selected.value = []
  try {
    const res = await api.get('/records', { params: { page: p, size: pageSize } })
    records.value = res.data.records
    total.value = Number(res.data.total)
    markShared()
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

/** 后台静默拉取每条记录是否已有分享链接（失败不影响主流程） */
async function markShared() {
  const ids = records.value.map(r => r.id)
  const map = { ...sharedMap.value }
  await Promise.all(ids.map(async (id) => {
    const info = await fetchShare(id)
    if (info) map[id] = info
    else delete map[id]
  }))
  sharedMap.value = map
}

function isChecked(id) {
  return selected.value.includes(id)
}

function toggle(id, checked) {
  if (checked) {
    if (!selected.value.includes(id)) selected.value.push(id)
  } else {
    selected.value = selected.value.filter(x => x !== id)
  }
}

function toggleAll(checked) {
  selected.value = checked ? records.value.map(r => r.id) : []
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
        const next = records.value.length === 1 && page.value > 1 ? page.value - 1 : page.value
        load(next)
      } catch (e) {
        message.error(e.message)
      }
    }
  })
}

function batchRemove() {
  const count = selected.value.length
  if (!count) return
  dialog.warning({
    title: '批量删除',
    content: `将从 GitHub 仓库删除选中的 ${count} 张图片，删除后 CDN 链接全部失效，确定继续吗？`,
    positiveText: `删除 ${count} 张`,
    negativeText: '取消',
    onPositiveClick: async () => {
      deleting.value = true
      try {
        const res = await api.post('/records/batch-delete', { ids: selected.value })
        const { deleted, failed } = res.data
        if (failed && failed.length) {
          const names = failed.map(f => f.name).slice(0, 3).join('、')
          message.warning(`成功 ${deleted} 张，失败 ${failed.length} 张（${names}${failed.length > 3 ? ' 等' : ''}）`)
        } else {
          message.success(`已删除 ${deleted} 张图片`)
        }
        const next = records.value.length === deleted && page.value > 1 ? page.value - 1 : page.value
        load(next)
      } catch (e) {
        message.error(e.message)
      } finally {
        deleting.value = false
      }
    }
  })
}

/* ---------------- 分享 ---------------- */

async function openShare(r) {
  sharingId.value = r.id
  shareInfo.value = null
  shareModal.value = true
  try {
    const info = sharedMap.value[r.id] || (await createShare(r.id))
    shareInfo.value = info
    sharedMap.value = { ...sharedMap.value, [r.id]: info }
  } catch (e) {
    shareModal.value = false
    message.error(e.message)
  } finally {
    sharingId.value = null
  }
}

async function copyShare() {
  const ok = await copyText(shareInfo.value?.fullUrl)
  ok ? message.success('分享链接已复制') : message.error('复制失败，请手动复制')
}

async function copyShareMarkdown() {
  const info = shareInfo.value
  if (!info) return
  const md = `[![${info.originName}](${info.cdnUrl})](${info.fullUrl})`
  const ok = await copyText(md)
  ok ? message.success('Markdown 已复制') : message.error('复制失败，请手动复制')
}

function openSharePage() {
  const url = shareInfo.value?.fullUrl
  if (url) window.open(url, '_blank', 'noopener')
}

async function cancelCurrentShare() {
  if (!shareInfo.value) return
  canceling.value = true
  try {
    await cancelShare(shareInfo.value.recordId)
    const id = shareInfo.value.recordId
    const map = { ...sharedMap.value }
    delete map[id]
    sharedMap.value = map
    message.success('已取消分享，原链接立即失效')
    shareModal.value = false
    shareInfo.value = null
  } catch (e) {
    message.error(e.message)
  } finally {
    canceling.value = false
  }
}

async function copy(text) {
  const ok = await copyText(text)
  ok ? message.success('已复制到剪贴板') : message.error('复制失败，请手动复制')
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 20px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-head h2 { margin: 0 0 6px; font-size: 26px; }
.desc { color: var(--text-dim); font-size: 13px; margin: 0; }
.empty { padding: 80px 0; }

/* n-spin 的内容容器默认不撑满，会让里面的块级元素塌掉 */
:deep(.n-spin-container), :deep(.n-spin-content) { width: 100%; }

/* 批量操作条 */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
}
.tb-left { display: flex; align-items: center; gap: 10px; }
.tb-count {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  color: #c9b8ff;
  background: rgba(124, 92, 255, 0.18);
  border: 1px solid rgba(124, 92, 255, 0.4);
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.18s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ===== 表格 ===== */
.table-card { overflow: hidden; padding: 0; }
.t-row {
  display: grid;
  grid-template-columns: 40px 64px 1fr 110px 190px 240px;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
}
.t-head {
  font-size: 12.5px;
  color: var(--text-dim);
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.02);
  padding-top: 14px;
  padding-bottom: 14px;
}
.t-body {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  transition: background 0.15s;
}
.t-body:last-of-type { border-bottom: none; }
.t-body:hover { background: rgba(124, 92, 255, 0.06); }
.t-body.picked { background: rgba(124, 92, 255, 0.12); }

.c-check { display: flex; justify-content: center; }

.thumb {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  overflow: hidden;
  cursor: zoom-in;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border);
  transition: transform 0.18s, box-shadow 0.18s;
}
.thumb:hover { transform: scale(1.06); box-shadow: 0 4px 16px rgba(124, 92, 255, 0.3); }
.thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }

.c-name { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.name-line { display: flex; align-items: center; gap: 8px; min-width: 0; }
.name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sub-line { display: flex; gap: 6px; }
.badge {
  flex-shrink: 0;
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 999px;
}
.badge.shared {
  color: #8ee8d6;
  background: rgba(34, 211, 238, 0.14);
  border: 1px solid rgba(34, 211, 238, 0.35);
}
.badge.repo {
  color: var(--text-dim);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border);
}

.c-size { font-size: 12.5px; color: var(--text-dim); }
.c-time { font-size: 12.5px; color: var(--text-dim); font-variant-numeric: tabular-nums; }

.c-actions { display: flex; align-items: center; gap: 8px; justify-content: flex-end; }
.pill { border-radius: 999px; }
.del-btn { font-size: 12px; }

.pager { display: flex; justify-content: flex-end; padding: 14px 18px; border-top: 1px solid var(--border); }

/* 分享弹窗 */
.share-box { display: flex; flex-direction: column; gap: 12px; }
.share-preview {
  height: 180px;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border);
}
.share-preview img { width: 100%; height: 100%; object-fit: contain; display: block; }
.share-tip { margin: 0; font-size: 12px; color: var(--text-dim); }
.share-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.share-meta { font-size: 11.5px; color: var(--text-dim); }
.share-loading { display: grid; place-items: center; padding: 40px 0; }

@media (max-width: 900px) {
  .t-row { grid-template-columns: 36px 56px 1fr 90px 150px; }
  .c-time { display: none; }
  .t-head .c-time { display: none; }
  .c-actions { grid-column: 1 / -1; justify-content: flex-end; }
}
@media (max-width: 720px) {
  .page-head { flex-direction: column; align-items: flex-start; gap: 10px; }
  .t-row { grid-template-columns: 36px 56px 1fr; }
  .c-size { display: none; }
  .t-head .c-size { display: none; }
}
</style>
