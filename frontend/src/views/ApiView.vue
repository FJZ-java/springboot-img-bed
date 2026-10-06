<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="gradient-text">API 接口</h2>
        <p class="desc">用 API Key 在脚本 / 第三方工具里直接上传与管理你的图片</p>
      </div>
    </div>

    <!-- API Key 卡片 -->
    <div class="glass-card key-card">
      <div class="key-head">
        <span class="key-title">🔑 我的 API Key</span>
        <span class="key-warn">等同密码，请勿泄露给他人或提交到公开仓库</span>
      </div>
      <n-spin :show="loading">
        <!-- 未生成：引导手动生成 -->
        <div v-if="info && !info.hasKey" class="key-empty">
          <p class="empty-text">你还没有 API Key，生成后仅显示一次，请立即保存。</p>
          <n-button type="primary" :loading="generating" @click="generate">🔑 生成 API Key</n-button>
        </div>
        <!-- 已生成：只显示掩码，完整值无法再次查看 -->
        <div v-else-if="info" class="key-body">
          <code class="key-value">{{ info.masked }}</code>
          <div class="key-actions">
            <n-button size="small" secondary type="warning" :loading="generating" @click="confirmReset">
              🔄 重置密钥
            </n-button>
          </div>
          <p class="key-note">完整密钥仅在生成时显示一次，此后只能看到掩码；泄露后请立即重置。</p>
        </div>
      </n-spin>
    </div>

    <!-- 一次性展示完整密钥：关闭后无法再查看 -->
    <n-modal v-model:show="showGenerated" preset="card" :mask-closable="false" style="max-width: 560px">
      <template #header>🔑 已生成新的 API Key</template>
      <div class="generated-box">
        <p class="generated-warn">⚠️ 此密钥只显示这一次，关闭后将无法再次查看，请立即复制保存。</p>
        <code class="key-value">{{ generatedKey }}</code>
        <div class="generated-actions">
          <n-button type="primary" @click="copyGenerated">📋 复制密钥</n-button>
          <n-button secondary @click="closeGenerated">我已保存</n-button>
        </div>
      </div>
    </n-modal>

    <!-- 调用文档 -->
    <div class="glass-card doc-card">
      <h3 class="doc-title">📖 调用方式</h3>
      <p class="doc-text">
        所有需要登录的接口都支持 API Key 认证：在请求头中带上
        <code class="inline">X-API-Key: 你的密钥</code> 即可（与登录令牌二选一）。接口地址前缀为
        <code class="inline">{{ baseUrl }}/api</code>。
      </p>

      <div class="doc-section">
        <div class="doc-label"><span class="method post">POST</span> 上传图片 <code class="inline">/api/records/upload</code></div>
        <pre class="code-block">curl -X POST {{ baseUrl }}/api/records/upload \
  -H "X-API-Key: {{ sampleKey }}" \
  -F "file=@/path/to/image.png"</pre>
      </div>

      <div class="doc-section">
        <div class="doc-label"><span class="method get">GET</span> 查询上传记录 <code class="inline">/api/records</code></div>
        <pre class="code-block">curl "{{ baseUrl }}/api/records?page=1&size=20" \
  -H "X-API-Key: {{ sampleKey }}"</pre>
      </div>

      <div class="doc-section">
        <div class="doc-label"><span class="method delete">DELETE</span> 删除图片 <code class="inline">/api/records/{id}</code></div>
        <pre class="code-block">curl -X DELETE {{ baseUrl }}/api/records/123 \
  -H "X-API-Key: {{ sampleKey }}"</pre>
      </div>

      <div class="doc-section">
        <div class="doc-label">📦 统一返回结构</div>
        <pre class="code-block">{
  "code": 0,          // 0 = 成功；400 参数错误 / 401 未认证 / 403 无权限 / 429 触发限流
  "message": "ok",
  "data": { ... }     // 上传成功时含 cdnUrl（CDN 直链）、githubPath、size 等
}</pre>
      </div>

      <p class="doc-tip">💡 提示：上传接口限流为每用户每小时 200 张；密钥泄露后请立即点「重置密钥」，旧密钥会即刻失效。</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import api from '../api'
import { copyText } from '../utils/share'

const message = useMessage()
const dialog = useDialog()

const info = ref(null)
const loading = ref(false)
const generating = ref(false)
const generatedKey = ref('')
const showGenerated = ref(false)

const baseUrl = computed(() => window.location.origin)
const sampleKey = computed(() => (info.value && info.value.masked ? info.value.masked : 'img_sk_••••••••'))

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await api.get('/apikey')
    info.value = res.data
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

// 首次生成：没有旧 key 需要失效，直接生成
function generate() {
  doGenerate()
}

// 重置：先确认旧 key 会失效
function confirmReset() {
  dialog.warning({
    title: '重置 API Key',
    content: '重置后旧密钥立即失效，正在使用旧密钥的脚本 / 工具都会调用失败，确定继续吗？',
    positiveText: '重置',
    negativeText: '取消',
    onPositiveClick: () => doGenerate()
  })
}

async function doGenerate() {
  generating.value = true
  try {
    const res = await api.post('/apikey/generate')
    generatedKey.value = res.data.apiKey
    showGenerated.value = true
    info.value = { hasKey: true, masked: res.data.masked }
  } catch (e) {
    message.error(e.message)
  } finally {
    generating.value = false
  }
}

function copyGenerated() {
  copy(generatedKey.value)
}

function closeGenerated() {
  showGenerated.value = false
  generatedKey.value = ''
}

async function copy(text) {
  if (!text) return
  const ok = await copyText(text)
  ok ? message.success('API Key 已复制') : message.error('复制失败，请手动复制')
}
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 20px; }
.page-head h2 { margin: 0 0 6px; font-size: 26px; }
.desc { color: var(--text-dim); font-size: 13px; margin: 0; }

.key-card { padding: 18px 20px; display: flex; flex-direction: column; gap: 14px; }
.key-head { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.key-title { font-size: 15px; font-weight: 600; }
.key-warn { font-size: 12px; color: #f0b429; }
.key-body { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.key-value {
  flex: 1;
  min-width: 260px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  letter-spacing: 0.5px;
  color: #8ee8d6;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid var(--border);
  word-break: break-all;
}
.key-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.key-empty { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; }
.empty-text { margin: 0; font-size: 13px; color: var(--text-dim); }
.key-note { margin: 4px 0 0; font-size: 12px; color: var(--text-dim); }
.generated-box { display: flex; flex-direction: column; gap: 12px; }
.generated-warn { margin: 0; font-size: 13px; color: #f0b429; line-height: 1.7; }
.generated-actions { display: flex; gap: 10px; }

.doc-card { padding: 18px 20px; display: flex; flex-direction: column; gap: 14px; }
.doc-title { margin: 0; font-size: 16px; }
.doc-text { margin: 0; font-size: 13px; color: var(--text-dim); line-height: 1.8; }
.inline {
  padding: 1px 7px;
  border-radius: 6px;
  font-size: 12px;
  color: #8ee8d6;
  background: rgba(34, 211, 238, 0.1);
  border: 1px solid rgba(34, 211, 238, 0.25);
}
.doc-section { display: flex; flex-direction: column; gap: 8px; }
.doc-label { font-size: 13px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.method {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
}
.method.post { color: #ffd29d; background: rgba(251, 146, 60, 0.15); border: 1px solid rgba(251, 146, 60, 0.35); }
.method.get { color: #8ee8d6; background: rgba(34, 211, 238, 0.12); border: 1px solid rgba(34, 211, 238, 0.35); }
.method.delete { color: #ff9d9d; background: rgba(255, 99, 99, 0.12); border: 1px solid rgba(255, 99, 99, 0.35); }
.code-block {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #d7dbff;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid var(--border);
  overflow-x: auto;
  white-space: pre;
}
.doc-tip { margin: 0; font-size: 12px; color: var(--text-dim); }
</style>
