<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="gradient-text">轮播管理</h2>
        <p class="desc">配置广告轮播图：启用中的图片会展示在上传页面的广告位，支持跳转链接与排序</p>
      </div>
      <div class="head-actions">
        <n-button secondary :loading="loading" @click="load">🔄 刷新</n-button>
        <n-button type="primary" @click="openCreate">➕ 新增轮播图</n-button>
      </div>
    </div>

    <n-spin :show="loading">
      <n-empty v-if="!loading && !slides.length" description="还没有轮播图，添加后会在上传页展示" class="empty">
        <template #extra>
          <n-button type="primary" @click="openCreate">新增轮播图</n-button>
        </template>
      </n-empty>

      <div v-else class="slide-grid">
        <div v-for="s in slides" :key="s.id" class="glass-card slide-card">
          <div class="slide-img" @click="openPreviewOne(s)">
            <img :src="s.imageUrl" :alt="s.title || '轮播图'" loading="lazy" />
            <span class="slide-badge" :class="s.enabled ? 'on' : 'off'">{{ s.enabled ? '展示中' : '已停用' }}</span>
            <div class="zoom-mask"><span>🔍 预览</span></div>
          </div>
          <div class="slide-body">
            <div class="slide-title" :title="s.title || ''">{{ s.title || '未命名' }}</div>
            <div class="slide-link" :title="s.linkUrl || ''">
              {{ s.linkUrl || '无跳转链接' }}
            </div>
            <div class="slide-foot">
              <span class="slide-sort">排序 {{ s.sortOrder }}</span>
              <n-switch
                size="small"
                :value="!!s.enabled"
                :loading="togglingId === s.id"
                @update:value="(v) => toggleEnabled(s, v)"
              >
                <template #checked>启用</template>
                <template #unchecked>停用</template>
              </n-switch>
            </div>
            <div class="slide-ops">
              <n-button size="tiny" secondary type="primary" @click="openEdit(s)">✏️ 编辑</n-button>
              <n-button size="tiny" secondary type="error" @click="removeSlide(s)">🗑️ 删除</n-button>
            </div>
          </div>
        </div>
      </div>
    </n-spin>

    <!-- 新增 / 编辑弹窗 -->
    <n-modal v-model:show="editShow" preset="card" :title="editing ? '编辑轮播图' : '新增轮播图'" style="width: 480px; max-width: 92vw;">
      <n-form label-placement="top">
        <n-form-item label="轮播图片">
          <div class="img-edit">
            <div v-if="form.imageUrl" class="img-now" @click="openPreviewOne({ imageUrl: form.imageUrl, title: form.title })">
              <img :src="form.imageUrl" alt="预览" />
            </div>
            <div class="img-inputs">
              <n-input v-model:value="form.imageUrl" placeholder="粘贴图片地址（建议先用下方按钮上传）" size="small" />
              <n-button size="small" secondary type="primary" :loading="uploading" @click="pickImage">
                ⬆️ 上传图片
              </n-button>
              <p class="img-hint">上传到图床后会自动填入 CDN 链接</p>
            </div>
          </div>
        </n-form-item>
        <n-form-item label="标题（可选，叠加在图片左下角）">
          <n-input v-model:value="form.title" placeholder="如：火星图床夏季大促" :maxlength="40" />
        </n-form-item>
        <n-form-item label="跳转链接（可选）">
          <n-input v-model:value="form.linkUrl" placeholder="点击图片后跳转，如 https://example.com" />
        </n-form-item>
        <div class="two-col">
          <n-form-item label="排序（小的在前）">
            <n-input-number v-model:value="form.sortOrder" :min="0" :max="999" style="width: 100%;" />
          </n-form-item>
          <n-form-item label="启用展示">
            <n-switch v-model:value="form.enabled" size="large">
              <template #checked>启用</template>
              <template #unchecked>停用</template>
            </n-switch>
          </n-form-item>
        </div>
      </n-form>
      <template #footer>
        <div class="foot-btns">
          <n-button quaternary @click="editShow = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="submit">保存</n-button>
        </div>
      </template>
    </n-modal>

    <input ref="imgInput" type="file" accept="image/*" hidden @change="onPick" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import api from '../api'
import { openPreview } from '../utils/previewer'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const slides = ref([])
const togglingId = ref(null)

const editShow = ref(false)
const editing = ref(null)
const saving = ref(false)
const uploading = ref(false)
const imgInput = ref(null)

const form = reactive({ imageUrl: '', title: '', linkUrl: '', sortOrder: 0, enabled: true })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await api.get('/admin/carousel')
    slides.value = res.data || []
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { imageUrl: '', title: '', linkUrl: '', sortOrder: 0, enabled: true })
  editShow.value = true
}

function openEdit(s) {
  editing.value = s
  Object.assign(form, {
    imageUrl: s.imageUrl || '',
    title: s.title || '',
    linkUrl: s.linkUrl || '',
    sortOrder: s.sortOrder ?? 0,
    enabled: !!s.enabled
  })
  editShow.value = true
}

async function submit() {
  if (!form.imageUrl.trim()) {
    message.warning('请先粘贴或上传轮播图片')
    return
  }
  saving.value = true
  try {
    const body = {
      imageUrl: form.imageUrl.trim(),
      title: form.title.trim(),
      linkUrl: form.linkUrl.trim(),
      sortOrder: form.sortOrder ?? 0,
      enabled: form.enabled ? 1 : 0
    }
    if (editing.value) {
      await api.put(`/admin/carousel/${editing.value.id}`, body)
      message.success('轮播图已更新')
    } else {
      await api.post('/admin/carousel', body)
      message.success('轮播图已添加')
    }
    editShow.value = false
    load()
  } catch (e) {
    message.error(e.message)
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(s, enabled) {
  togglingId.value = s.id
  try {
    await api.put(`/admin/carousel/${s.id}`, { enabled: enabled ? 1 : 0 })
    s.enabled = enabled ? 1 : 0
    message.success(enabled ? '已启用，上传页会展示' : '已停用')
  } catch (e) {
    message.error(e.message)
  } finally {
    togglingId.value = null
  }
}

function removeSlide(s) {
  dialog.warning({
    title: '删除轮播图',
    content: '删除后广告位将不再展示这张图片（图床里的图片文件不受影响），确定删除吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await api.delete(`/admin/carousel/${s.id}`)
        message.success('已删除')
        load()
      } catch (e) {
        message.error(e.message)
      }
    }
  })
}

/* ---------- 弹窗内上传图片（走图床上传接口拿 CDN 链接） ---------- */
function pickImage() {
  imgInput.value?.click()
}

async function onPick(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await api.post('/records/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    form.imageUrl = res.data.cdnUrl
    if (!form.title) form.title = file.name.replace(/\.[^.]+$/, '')
    message.success('图片已上传到图床，链接已自动填入')
  } catch (err) {
    message.error(err.message)
  } finally {
    uploading.value = false
  }
}

function openPreviewOne(s) {
  openPreview([{ src: s.imageUrl, cdnUrl: s.imageUrl, name: s.title || '轮播图' }], 0)
}
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 12px; }
.page-head h2 { margin: 0 0 6px; font-size: 26px; }
.desc { color: var(--text-dim); font-size: 13px; margin: 0; }
.head-actions { display: flex; gap: 8px; flex-shrink: 0; }
.empty { padding: 80px 0; }

.slide-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.slide-card { overflow: hidden; transition: transform 0.2s, box-shadow 0.2s; }
.slide-card:hover { transform: translateY(-3px); box-shadow: 0 12px 36px rgba(124, 92, 255, 0.16); }
.slide-img {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  cursor: zoom-in;
  background: rgba(0, 0, 0, 0.3);
}
.slide-img img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 0.3s; }
.slide-card:hover .slide-img img { transform: scale(1.04); }
.zoom-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: #fff;
  background: rgba(10, 8, 24, 0.45);
  opacity: 0;
  transition: opacity 0.18s;
}
.slide-img:hover .zoom-mask { opacity: 1; }
.slide-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  font-size: 10.5px;
  padding: 2px 9px;
  border-radius: 999px;
}
.slide-badge.on { color: #6ee7b7; background: rgba(52, 211, 153, 0.16); border: 1px solid rgba(52, 211, 153, 0.4); }
.slide-badge.off { color: #9aa0ae; background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.18); }

.slide-body { padding: 12px 14px; display: flex; flex-direction: column; gap: 7px; }
.slide-title { font-size: 13.5px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.slide-link { font-size: 11px; color: var(--text-dim); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.slide-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.slide-sort { font-size: 11px; color: var(--text-dim); font-variant-numeric: tabular-nums; }
.slide-ops { display: flex; gap: 6px; }

/* 弹窗 */
.img-edit { display: flex; gap: 12px; width: 100%; }
.img-now {
  width: 120px;
  height: 76px;
  flex-shrink: 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: rgba(0, 0, 0, 0.3);
  cursor: zoom-in;
}
.img-now img { width: 100%; height: 100%; object-fit: cover; display: block; }
.img-inputs { flex: 1; display: flex; flex-direction: column; gap: 7px; align-items: flex-start; }
.img-inputs .n-input { width: 100%; }
.img-hint { margin: 0; font-size: 11px; color: var(--text-dim); }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.foot-btns { display: flex; gap: 8px; justify-content: flex-end; }

@media (max-width: 720px) {
  .page-head { flex-direction: column; align-items: flex-start; }
  .two-col { grid-template-columns: 1fr; }
}
</style>
