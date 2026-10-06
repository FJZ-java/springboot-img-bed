<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="gradient-text">存储配置</h2>
        <p class="desc">配置多个 GitHub 仓库，上传时按权重随机分流，避免单仓库体积膨胀</p>
      </div>
      <div class="head-actions">
        <n-button secondary :loading="loading" @click="load">🔄 刷新</n-button>
        <n-button type="primary" @click="openCreate">➕ 新增仓库</n-button>
      </div>
    </div>

    <!-- 总览 -->
    <div class="stat-row">
      <div class="glass-card stat-card">
        <div class="stat-label">仓库总数</div>
        <div class="stat-value">{{ summary.total || 0 }}</div>
        <div class="stat-unit">个</div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-label">启用中</div>
        <div class="stat-value ok">{{ summary.enabled || 0 }}</div>
        <div class="stat-unit">个参与分流</div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-label">托管文件</div>
        <div class="stat-value cyan">{{ summary.files || 0 }}</div>
        <div class="stat-unit">个</div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-label">占用体积</div>
        <div class="stat-value mini">{{ formatSize(summary.size || 0) }}</div>
        <div class="stat-unit">仓库磁盘 {{ formatSize((summary.diskSize || 0) * 1024) }}</div>
      </div>
    </div>

    <n-spin :show="loading" style="width: 100%;">
      <n-empty v-if="!loading && repos.length === 0" description="还没有配置存储仓库，先添加一个吧" class="empty">
        <template #extra>
          <n-button type="primary" @click="openCreate">➕ 新增仓库</n-button>
        </template>
      </n-empty>

      <div v-else class="repo-grid">
        <div v-for="r in repos" :key="r.id" class="glass-card repo-card" :class="{ off: r.enabled !== 1 }">
          <div class="rc-head">
            <div class="rc-title">
              <span class="rc-name">{{ r.name }}</span>
              <span class="rc-badge" :class="r.enabled === 1 ? 'on' : 'off'">
                {{ r.enabled === 1 ? '启用中' : '已停用' }}
              </span>
              <span v-if="r.hasToken" class="rc-badge key" :title="r.tokenMasked">独立 Token</span>
            </div>
            <n-switch :value="r.enabled === 1" size="small" @update:value="(v) => toggleEnabled(r, v)">
              <template #checked>启用</template>
              <template #unchecked>停用</template>
            </n-switch>
          </div>

          <div class="rc-path">
            <span class="mono">{{ r.fullName }}</span>
            <span class="dot">·</span>
            <span class="mono">{{ r.branch || 'main' }}</span>
            <span class="dot">·</span>
            <span class="mono">/{{ r.dirPrefix || 'images' }}</span>
          </div>

          <div class="rc-stats">
            <div class="rc-stat">
              <span class="k">文件数</span>
              <span class="v">{{ (r.fileCount || 0).toLocaleString() }}</span>
            </div>
            <div class="rc-stat">
              <span class="k">文件体积</span>
              <span class="v">{{ formatSize(r.totalSize || 0) }}</span>
            </div>
            <div class="rc-stat">
              <span class="k">仓库占用</span>
              <span class="v">{{ formatSize((r.diskSize || 0) * 1024) }}</span>
            </div>
            <div class="rc-stat">
              <span class="k">权重</span>
              <span class="v">{{ r.weight || 1 }}</span>
            </div>
          </div>

          <div class="rc-foot">
            <span class="sync">上次同步：{{ fmtTime(r.lastSyncTime) }}</span>
            <div class="rc-ops">
              <n-button size="tiny" secondary type="primary" :loading="syncingId === r.id" @click="syncRepo(r)">
                同步
              </n-button>
              <n-button size="tiny" secondary @click="openEdit(r)">编辑</n-button>
              <n-button size="tiny" secondary type="error" @click="removeRepo(r)">删除</n-button>
            </div>
          </div>
          <div v-if="r.remark" class="rc-remark">{{ r.remark }}</div>
        </div>
      </div>
    </n-spin>

    <!-- 新增 / 编辑 弹窗 -->
    <n-modal
      v-model:show="formShow"
      preset="card"
      :title="editing ? '编辑仓库配置' : '新增存储仓库'"
      style="width: 560px; max-width: 92vw;"
    >
      <n-form label-placement="top">
        <n-form-item label="仓库备注名">
          <n-input v-model:value="form.name" placeholder="例如：主仓库 / 备份仓库" />
        </n-form-item>
        <div class="form-row">
          <n-form-item label="Owner（仓库拥有者）" class="flex1">
            <n-input v-model:value="form.owner" :disabled="editing" placeholder="例如：FJZ-java" />
          </n-form-item>
          <n-form-item label="Repo（仓库名）" class="flex1">
            <n-input v-model:value="form.repo" :disabled="editing" placeholder="例如：boot-img-bed" />
          </n-form-item>
        </div>
        <div class="form-row">
          <n-form-item label="分支" class="flex1">
            <n-input v-model:value="form.branch" placeholder="留空自动使用默认分支" />
          </n-form-item>
          <n-form-item label="存储目录" class="flex1">
            <n-input v-model:value="form.dirPrefix" placeholder="images" />
          </n-form-item>
        </div>
        <div class="form-row">
          <n-form-item label="权重（越大越容易被选中）" class="flex1">
            <n-input-number v-model:value="form.weight" :min="1" :max="100" class="full" />
          </n-form-item>
          <n-form-item label="状态" class="flex1">
            <n-switch v-model:value="enabledSwitch">
              <template #checked>启用</template>
              <template #unchecked>停用</template>
            </n-switch>
          </n-form-item>
        </div>
        <n-form-item label="备注">
          <n-input v-model:value="form.remark" placeholder="选填" />
        </n-form-item>
      </n-form>

      <template #footer>
        <div class="modal-foot">
          <span v-if="!editing" class="tip">保存时会向 GitHub 校验仓库可访问性并自动同步统计</span>
          <div class="foot-btns">
            <n-button quaternary @click="formShow = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="submit">保存</n-button>
          </div>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import api from '../api'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const saving = ref(false)
const syncingId = ref(null)
const repos = ref([])
const summary = ref({})

const formShow = ref(false)
const editing = ref(null)
const enabledSwitch = ref(true)
const form = reactive({
  name: '', owner: '', repo: '', branch: '', dirPrefix: 'images',
  weight: 1, remark: ''
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await api.get('/admin/repos')
    repos.value = res.data.repos || []
    summary.value = res.data.summary || {}
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    name: '', owner: '', repo: '', branch: '', dirPrefix: 'images',
    weight: 1, remark: ''
  })
  enabledSwitch.value = true
  formShow.value = true
}

function openEdit(r) {
  editing.value = r
  Object.assign(form, {
    name: r.name || '',
    owner: r.owner,
    repo: r.repo,
    branch: r.branch || '',
    dirPrefix: r.dirPrefix || 'images',
    weight: r.weight || 1,
    remark: r.remark || ''
  })
  enabledSwitch.value = r.enabled === 1
  formShow.value = true
}

async function submit() {
  if (!editing.value && (!form.owner.trim() || !form.repo.trim())) {
    message.warning('请填写 Owner 与 Repo')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      owner: form.owner.trim(),
      repo: form.repo.trim(),
      branch: form.branch.trim(),
      dirPrefix: form.dirPrefix.trim() || 'images',
      weight: form.weight || 1,
      remark: form.remark.trim(),
      enabled: enabledSwitch.value ? 1 : 0
    }

    if (editing.value) {
      await api.put(`/admin/repos/${editing.value.id}`, payload)
      message.success('已保存')
    } else {
      await api.post('/admin/repos', payload)
      message.success('仓库已添加，并完成首次同步')
    }
    formShow.value = false
    load()
  } catch (e) {
    message.error(e.message)
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(r, v) {
  try {
    await api.put(`/admin/repos/${r.id}`, { enabled: v ? 1 : 0 })
    message.success(v ? '已启用，开始参与上传分流' : '已停用，不再接收新图片')
    load()
  } catch (e) {
    message.error(e.message)
  }
}

async function syncRepo(r) {
  syncingId.value = r.id
  try {
    await api.post(`/admin/repos/${r.id}/sync`)
    message.success('同步完成')
    load()
  } catch (e) {
    message.error(e.message)
  } finally {
    syncingId.value = null
  }
}

function removeRepo(r) {
  dialog.warning({
    title: '移除仓库配置',
    content: `确定移除「${r.name}」（${r.fullName}）吗？仅删除本站配置，GitHub 上的文件不会受影响。`,
    positiveText: '移除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await api.delete(`/admin/repos/${r.id}`)
        message.success('已移除')
        load()
      } catch (e) {
        message.error(e.message)
      }
    }
  })
}

/** 后端返回的是 ISO 字符串（带纳秒），这里显示成 "2026-10-02 02:46" */
function fmtTime(t) {
  if (!t) return '未同步'
  const s = String(t).replace('T', ' ')
  return s.slice(0, 16)
}

function formatSize(bytes) {
  const n = Number(bytes) || 0
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(2) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 12px; }
.page-head h2 { margin: 0 0 6px; font-size: 26px; }
.desc { color: var(--text-dim); font-size: 13px; margin: 0; }
.head-actions { display: flex; gap: 8px; flex-shrink: 0; }

.stat-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; }
.stat-card { padding: 16px 18px; display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
.stat-label { width: 100%; font-size: 12px; color: var(--text-dim); margin-bottom: 4px; }
.stat-value {
  font-size: 26px; font-weight: 700; line-height: 1.1;
  background: linear-gradient(120deg, #ff7a45, #ffb26b);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
  font-variant-numeric: tabular-nums;
}
.stat-value.ok { background: linear-gradient(120deg, #34d399, #6ee7b7); -webkit-background-clip: text; background-clip: text; }
.stat-value.cyan { background: linear-gradient(120deg, #22d3ee, #7dd3fc); -webkit-background-clip: text; background-clip: text; }
.stat-value.mini { font-size: 20px; background: none; -webkit-text-fill-color: currentColor; color: var(--text); }
.stat-unit { font-size: 12px; color: var(--text-dim); }

/* n-spin 的内容容器默认不撑满，会让上面的栅格塌成一列 */
:deep(.n-spin-container), :deep(.n-spin-content) { width: 100%; }
.repo-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 14px; }
.repo-card { padding: 16px 18px; display: flex; flex-direction: column; gap: 10px; transition: border-color 0.2s, transform 0.2s; }
.repo-card:hover { transform: translateY(-3px); }
.repo-card.off { opacity: 0.72; }

.rc-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.rc-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; min-width: 0; }
.rc-name { font-size: 15px; font-weight: 600; }
.rc-badge {
  font-size: 10.5px; padding: 1px 8px; border-radius: 999px;
  color: #34d399; background: rgba(52, 211, 153, 0.12); border: 1px solid rgba(52, 211, 153, 0.35);
}
.rc-badge.off { color: #fbbf24; background: rgba(251, 191, 36, 0.12); border-color: rgba(251, 191, 36, 0.35); }
.rc-badge.key { color: #c9b8ff; background: rgba(124, 92, 255, 0.16); border-color: rgba(124, 92, 255, 0.4); }

.rc-path { font-size: 12px; color: var(--text-dim); display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; color: #a5b4fc; }
.dot { opacity: 0.4; }

.rc-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.rc-stat { display: flex; flex-direction: column; gap: 2px; padding: 8px 10px; border-radius: 10px; background: rgba(255, 255, 255, 0.035); border: 1px solid var(--border); }
.rc-stat .k { font-size: 11px; color: var(--text-dim); }
.rc-stat .v { font-size: 13px; font-weight: 600; font-variant-numeric: tabular-nums; }

.rc-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.sync { font-size: 11.5px; color: var(--text-dim); }
.rc-ops { display: flex; gap: 6px; }
.rc-remark { font-size: 11.5px; color: var(--text-dim); }

.form-row { display: flex; gap: 12px; }
.flex1 { flex: 1; min-width: 0; }
.full { width: 100%; }
.modal-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.tip { font-size: 11.5px; color: var(--text-dim); }
.foot-btns { display: flex; gap: 8px; margin-left: auto; }
.empty { padding: 60px 0; }

@media (max-width: 720px) {
  .page-head { flex-direction: column; align-items: flex-start; }
  .form-row { flex-direction: column; gap: 0; }
  .rc-stats { grid-template-columns: repeat(2, 1fr); }
}
</style>
