<template>
  <n-modal
    :show="show"
    preset="card"
    style="width: 560px; max-width: 92vw;"
    title="分享这张图片"
    @update:show="emit('update:show', $event)"
  >
    <div v-if="info" class="share-box">
      <div class="share-preview">
        <img :src="record.cdnUrl" :alt="record.originName" />
      </div>
      <p class="share-tip">任何人打开下面的链接都可以查看这张图片，无需登录。</p>
      <n-input :value="info.fullUrl" readonly size="small" class="share-input" />
      <div class="share-actions">
        <n-button type="primary" size="small" @click="copyUrl">复制链接</n-button>
        <n-button size="small" secondary @click="openPage">打开看看</n-button>
        <n-button size="small" secondary @click="copyMd">复制 Markdown</n-button>
        <n-button size="small" quaternary type="error" :loading="canceling" @click="cancel">取消分享</n-button>
      </div>
      <div class="share-meta">已访问 {{ info.viewCount }} 次 · 生成于 {{ info.shareTime }}</div>
    </div>
    <div v-else class="share-loading"><n-spin size="medium" /></div>
  </n-modal>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { cancelShare, copyText, createShare, fetchShare } from '../utils/share'

const props = defineProps({
  show: { type: Boolean, default: false },
  record: { type: Object, default: null }
})
const emit = defineEmits(['update:show', 'canceled'])

const message = useMessage()
const info = ref(null)
const canceling = ref(false)

watch(
  () => [props.show, props.record?.id],
  async ([show]) => {
    if (!show || !props.record) return
    info.value = null
    try {
      info.value = (await fetchShare(props.record.id)) || (await createShare(props.record.id))
    } catch (e) {
      emit('update:show', false)
      message.error(e.message)
    }
  },
  { immediate: true }
)

async function copyUrl() {
  const ok = await copyText(info.value?.fullUrl)
  ok ? message.success('分享链接已复制') : message.error('复制失败，请手动复制')
}

async function copyMd() {
  if (!info.value) return
  const md = `[![${props.record.originName}](${props.record.cdnUrl})](${info.value.fullUrl})`
  const ok = await copyText(md)
  ok ? message.success('Markdown 已复制') : message.error('复制失败，请手动复制')
}

function openPage() {
  if (info.value?.fullUrl) window.open(info.value.fullUrl, '_blank', 'noopener')
}

async function cancel() {
  if (!info.value) return
  canceling.value = true
  try {
    await cancelShare(props.record.id)
    message.success('已取消分享，原链接立即失效')
    emit('canceled', props.record.id)
    emit('update:show', false)
    info.value = null
  } catch (e) {
    message.error(e.message)
  } finally {
    canceling.value = false
  }
}
</script>

<style scoped>
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
</style>
