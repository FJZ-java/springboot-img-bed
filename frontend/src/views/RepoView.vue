<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="gradient-text">仓库管理</h2>
        <p class="desc" v-if="shownRepo">
          <a :href="`https://github.com/${shownRepo.owner}/${shownRepo.repo}`" target="_blank" class="repo-link">
            {{ shownRepo.owner }}/{{ shownRepo.repo }}
          </a>
          · 分支 {{ shownRepo.branch || 'main' }} · 存储目录 /{{ shownRepo.dirPrefix || 'images' }}
          <span v-if="repos.length > 1" class="multi-tip">· 共 {{ repos.length }} 个仓库，可切换查看</span>
        </p>
      </div>
      <div class="head-actions">
        <n-select
          v-if="repos.length > 1"
          v-model:value="repoId"
          :options="repoOptions"
          size="small"
          style="width: 220px;"
          @update:value="onRepoChange"
        />
        <n-button secondary :loading="loading" @click="load(currentPath)">🔄 刷新</n-button>
      </div>
    </div>

    <div class="glass-card browser">
      <div class="breadcrumb">
        <span class="crumb" @click="navigate('')">🏠 {{ info.dirPrefix || '根目录' }}</span>
        <template v-for="(seg, i) in segments" :key="i">
          <span class="sep">/</span>
          <span class="crumb" @click="navigate(segments.slice(0, i + 1).join('/'))">{{ seg }}</span>
        </template>
      </div>

      <n-spin :show="loading">
        <n-empty v-if="!loading && items.length === 0" description="该目录为空" style="padding: 48px 0;" />
        <div v-else class="file-list">
          <div v-for="item in items" :key="item.path" class="file-row" @click="onRowClick(item)">
            <span class="icon">{{ item.type === 'dir' ? '📁' : '🖼️' }}</span>
            <span class="file-name" :title="item.name">{{ item.name }}</span>
            <span class="file-size">{{ item.type === 'dir' ? '—' : formatSize(item.size) }}</span>
            <div class="row-actions" @click.stop>
              <template v-if="item.type === 'file'">
                <n-button v-if="item.cdnUrl" size="tiny" quaternary type="primary" @click="onRowClick(item)">预览</n-button>
                <n-button size="tiny" quaternary type="primary" @click="copy(item.cdnUrl)">复制 CDN</n-button>
                <n-button size="tiny" quaternary type="error" @click="remove(item)">删除</n-button>
              </template>
            </div>
          </div>
        </div>
      </n-spin>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import api from '../api'
import { openPreview } from '../utils/previewer'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const info = ref({})
const items = ref([])
const currentPath = ref('')
const repos = ref([])
const repoId = ref(null)

const segments = computed(() => currentPath.value ? currentPath.value.split('/') : [])
const repoOptions = computed(() => repos.value.map(r => ({
  label: `${r.name} (${r.owner}/${r.repo})`,
  value: r.id
})))
/** 当前正在浏览的仓库：有选中用选中的，否则用默认仓库信息 */
const shownRepo = computed(() => {
  const picked = repos.value.find(r => r.id === repoId.value)
  return picked || info.value
})
/** 当前目录内的图片文件（用于预览时左右切换） */
const imageFiles = computed(() => items.value.filter(i => i.type === 'file' && i.cdnUrl))

onMounted(async () => {
  try {
    const res = await api.get('/github/info')
    info.value = res.data
  } catch (e) {
    message.error(e.message)
  }
  try {
    const res = await api.get('/admin/repos')
    repos.value = res.data.repos || []
    if (repos.value.length) repoId.value = repos.value[0].id
  } catch { /* 拿不到列表就按默认仓库浏览 */ }
  load('')
})

async function onRepoChange() {
  load('')
}

async function load(path) {
  currentPath.value = path
  loading.value = true
  try {
    const params = { path }
    if (repoId.value) params.repoId = repoId.value
    const res = await api.get('/github/files', { params })
    items.value = res.data
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

function navigate(path) {
  load(path)
}

function onRowClick(item) {
  if (item.type === 'dir') {
    load(item.path.replace(new RegExp(`^${info.value.dirPrefix}/?`), ''))
    return
  }
  if (!item.cdnUrl) return
  const list = imageFiles.value
  const idx = list.findIndex(f => f.path === item.path)
  if (idx < 0) {
    openPreview([{ src: item.cdnUrl, cdnUrl: item.cdnUrl, name: item.name, size: item.size }], 0)
    return
  }
  openPreview(
    list.map(f => ({ src: f.cdnUrl, cdnUrl: f.cdnUrl, name: f.name, size: f.size })),
    idx
  )
}

function remove(item) {
  dialog.warning({
    title: '确认删除',
    content: `将从 GitHub 仓库删除「${item.name}」，确定继续吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await api.delete('/github/file', { data: { path: item.path, sha: item.sha } })
        message.success('已删除')
        load(currentPath.value)
      } catch (e) {
        message.error(e.message)
      }
    }
  })
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('CDN 链接已复制')
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
.page { display: flex; flex-direction: column; gap: 20px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-head h2 { margin: 0 0 6px; font-size: 26px; }
.desc { color: var(--text-dim); font-size: 13px; margin: 0; }
.repo-link { color: var(--accent2); text-decoration: none; }
.repo-link:hover { text-decoration: underline; }
.browser { padding: 8px 0; min-height: 300px; }
.breadcrumb {
  padding: 10px 18px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.crumb { cursor: pointer; color: var(--accent2); }
.crumb:hover { text-decoration: underline; }
.sep { color: var(--text-dim); }
.file-list { display: flex; flex-direction: column; }
.file-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 18px;
  cursor: pointer;
  transition: background 0.15s;
}
.file-row:hover { background: var(--bg-soft); }
.icon { font-size: 18px; }
.file-name { flex: 1; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-size { color: var(--text-dim); font-size: 12px; width: 90px; text-align: right; }
.row-actions { display: flex; gap: 4px; }
</style>
