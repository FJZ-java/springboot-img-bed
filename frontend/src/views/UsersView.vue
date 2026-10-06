<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="gradient-text">用户管理</h2>
        <p class="desc">管理图床注册用户：新增账号、重置密码、封禁账号（封禁后无法登录）</p>
      </div>
      <div class="head-actions">
        <n-button secondary :loading="loading" @click="load">🔄 刷新</n-button>
        <n-button type="primary" @click="openCreate">➕ 新增用户</n-button>
      </div>
    </div>

    <!-- 总览 -->
    <div class="stat-row">
      <div class="glass-card stat-card">
        <div class="stat-label">用户总数</div>
        <div class="stat-value">{{ users.length }}</div>
        <div class="stat-unit">个</div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-label">管理员</div>
        <div class="stat-value ok">{{ adminCount }}</div>
        <div class="stat-unit">个</div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-label">托管图片</div>
        <div class="stat-value cyan">{{ totalImages }}</div>
        <div class="stat-unit">张</div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-label">已封禁</div>
        <div class="stat-value danger">{{ bannedCount }}</div>
        <div class="stat-unit">个</div>
      </div>
    </div>

    <div class="glass-card table-card">
      <n-spin :show="loading">
        <n-empty v-if="!loading && users.length === 0" description="暂无用户" style="padding: 48px 0;" />
        <table v-else class="u-table">
          <thead>
            <tr>
              <th style="width: 56px;">ID</th>
              <th>用户名</th>
              <th>昵称</th>
              <th style="width: 90px;">角色</th>
              <th style="width: 80px;">状态</th>
              <th style="width: 90px;">图片数</th>
              <th style="width: 170px;">注册时间</th>
              <th style="width: 230px;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td class="dim">{{ u.id }}</td>
              <td>
                <div class="u-name">
                  <span class="u-avatar">{{ (u.nickname || u.username).charAt(0).toUpperCase() }}</span>
                  <span class="mono">{{ u.username }}</span>
                </div>
              </td>
              <td>{{ u.nickname || '—' }}</td>
              <td>
                <span class="u-badge" :class="u.admin ? 'admin' : 'user'">
                  {{ u.admin ? '管理员' : '用户' }}
                </span>
              </td>
              <td>
                <span class="u-badge" :class="u.banned ? 'banned' : 'normal'">
                  {{ u.banned ? '已封禁' : '正常' }}
                </span>
              </td>
              <td class="dim num">{{ u.imageCount }}</td>
              <td class="dim">{{ fmtTime(u.createTime) }}</td>
              <td>
                <div class="u-ops">
                  <n-button
                    size="tiny"
                    :secondary="!u.banned"
                    :type="u.banned ? 'primary' : 'default'"
                    :disabled="u.admin || u.id === auth.user?.id"
                    @click="toggleBan(u)"
                  >{{ u.banned ? '解封' : '封禁' }}</n-button>
                  <n-button size="tiny" secondary type="primary" @click="openReset(u)">重置密码</n-button>
                  <n-button size="tiny" secondary @click="openRename(u)">改昵称</n-button>
                  <n-button
                    size="tiny"
                    secondary
                    type="error"
                    :disabled="u.admin || u.id === auth.user?.id"
                    @click="removeUser(u)"
                  >删除</n-button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </n-spin>
    </div>

    <!-- 新增用户 -->
    <n-modal v-model:show="createShow" preset="card" title="新增用户" style="width: 420px; max-width: 92vw;">
      <n-form label-placement="top">
        <n-form-item label="用户名">
          <n-input v-model:value="createForm.username" placeholder="3-20 位字母、数字或下划线" :maxlength="20" />
        </n-form-item>
        <n-form-item label="昵称（可选）">
          <n-input v-model:value="createForm.nickname" placeholder="留空则与用户名一致" :maxlength="20" />
        </n-form-item>
        <n-form-item label="初始密码">
          <n-input v-model:value="createForm.password" type="password" show-password-on="click" placeholder="6-32 位" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="foot-btns">
          <n-button quaternary @click="createShow = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="submitCreate">创建</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 重置密码 -->
    <n-modal v-model:show="resetShow" preset="card" :title="`重置密码：${current?.username || ''}`" style="width: 400px; max-width: 92vw;">
      <n-form label-placement="top">
        <n-form-item label="新密码">
          <n-input v-model:value="resetPassword" type="password" show-password-on="click" placeholder="6-32 位" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="foot-btns">
          <n-button quaternary @click="resetShow = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="submitReset">确认重置</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 修改昵称 -->
    <n-modal v-model:show="renameShow" preset="card" :title="`修改昵称：${current?.username || ''}`" style="width: 400px; max-width: 92vw;">
      <n-form label-placement="top">
        <n-form-item label="新昵称">
          <n-input v-model:value="renameValue" placeholder="请输入新昵称" :maxlength="20" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="foot-btns">
          <n-button quaternary @click="renameShow = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="submitRename">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import api from '../api'
import { useAuthStore } from '../stores/auth'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const users = ref([])

const createShow = ref(false)
const resetShow = ref(false)
const renameShow = ref(false)
const current = ref(null)
const resetPassword = ref('')
const renameValue = ref('')
const createForm = reactive({ username: '', nickname: '', password: '' })

const adminCount = computed(() => users.value.filter(u => u.admin).length)
const bannedCount = computed(() => users.value.filter(u => u.banned).length)
const totalImages = computed(() => users.value.reduce((s, u) => s + (u.imageCount || 0), 0))

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await api.get('/admin/users')
    users.value = res.data || []
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, { username: '', nickname: '', password: '' })
  createShow.value = true
}

async function submitCreate() {
  if (!createForm.username.trim() || !createForm.password.trim()) {
    message.warning('请填写用户名与初始密码')
    return
  }
  saving.value = true
  try {
    await api.post('/admin/users', {
      username: createForm.username.trim(),
      nickname: createForm.nickname.trim(),
      password: createForm.password.trim()
    })
    message.success('用户已创建')
    createShow.value = false
    load()
  } catch (e) {
    message.error(e.message)
  } finally {
    saving.value = false
  }
}

function openReset(u) {
  current.value = u
  resetPassword.value = ''
  resetShow.value = true
}

async function submitReset() {
  if (!resetPassword.value.trim()) {
    message.warning('请输入新密码')
    return
  }
  saving.value = true
  try {
    await api.put(`/admin/users/${current.value.id}`, { password: resetPassword.value.trim() })
    message.success(`已重置「${current.value.username}」的密码`)
    resetShow.value = false
  } catch (e) {
    message.error(e.message)
  } finally {
    saving.value = false
  }
}

function openRename(u) {
  current.value = u
  renameValue.value = u.nickname || ''
  renameShow.value = true
}

async function submitRename() {
  if (!renameValue.value.trim()) {
    message.warning('请输入新昵称')
    return
  }
  saving.value = true
  try {
    await api.put(`/admin/users/${current.value.id}`, { nickname: renameValue.value.trim() })
    message.success('昵称已更新')
    renameShow.value = false
    load()
  } catch (e) {
    message.error(e.message)
  } finally {
    saving.value = false
  }
}

/** 封禁 / 解封：封禁后该用户无法登录，已登录的会话也会立即失效 */
function toggleBan(u) {
  const ban = !u.banned
  dialog.warning({
    title: ban ? '封禁账号' : '解封账号',
    content: ban
      ? `确定封禁「${u.username}」吗？封禁后该账号无法登录，已登录的会话也会立即失效。`
      : `确定解封「${u.username}」吗？解封后该账号可正常登录。`,
    positiveText: ban ? '封禁' : '解封',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await api.put(`/admin/users/${u.id}/status`, { status: ban ? 0 : 1 })
        message.success(ban ? `已封禁「${u.username}」` : `已解封「${u.username}」`)
        load()
      } catch (e) {
        message.error(e.message)
      }
    }
  })
}

function removeUser(u) {
  dialog.warning({
    title: '删除用户',
    content: `确定删除用户「${u.username}」吗？其上传记录将一并清除（GitHub 上的图片文件不受影响），该操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await api.delete(`/admin/users/${u.id}`)
        message.success('已删除')
        load()
      } catch (e) {
        message.error(e.message)
      }
    }
  })
}

function fmtTime(t) {
  if (!t) return '—'
  return String(t).slice(0, 16)
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
.stat-value.danger { background: linear-gradient(120deg, #f87171, #fca5a5); -webkit-background-clip: text; background-clip: text; }
.stat-unit { font-size: 12px; color: var(--text-dim); }

.table-card { padding: 8px 4px; overflow-x: auto; }
.u-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.u-table th {
  text-align: left;
  padding: 12px 14px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-dim);
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
.u-table td { padding: 11px 14px; border-bottom: 1px solid rgba(255, 255, 255, 0.05); vertical-align: middle; }
.u-table tbody tr:last-child td { border-bottom: none; }
.u-table tbody tr:hover { background: rgba(255, 255, 255, 0.03); }
.dim { color: var(--text-dim); }
.num { font-variant-numeric: tabular-nums; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }

.u-name { display: flex; align-items: center; gap: 9px; }
.u-avatar {
  width: 26px; height: 26px; border-radius: 50%;
  display: grid; place-items: center;
  font-size: 12px; font-weight: 700; color: #fff;
  background: linear-gradient(135deg, #7c5cff, #22d3ee);
  flex-shrink: 0;
}
.u-badge {
  font-size: 10.5px; padding: 1px 8px; border-radius: 999px;
  color: #7dd3fc; background: rgba(125, 211, 252, 0.1); border: 1px solid rgba(125, 211, 252, 0.3);
}
.u-badge.admin { color: #c9b8ff; background: rgba(124, 92, 255, 0.16); border-color: rgba(124, 92, 255, 0.4); }
.u-badge.normal { color: #6ee7b7; background: rgba(52, 211, 153, 0.12); border-color: rgba(52, 211, 153, 0.34); }
.u-badge.banned { color: #ff9d9d; background: rgba(248, 113, 113, 0.14); border-color: rgba(248, 113, 113, 0.4); }
.u-ops { display: flex; gap: 6px; flex-wrap: wrap; }

.foot-btns { display: flex; gap: 8px; justify-content: flex-end; }

@media (max-width: 720px) {
  .page-head { flex-direction: column; align-items: flex-start; }
}
</style>
