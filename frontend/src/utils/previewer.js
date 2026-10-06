import { reactive } from 'vue'

/**
 * 全局图片预览器状态。
 * 用法：
 *   openPreview([{ src, name, size, cdnUrl }], index)
 *   openPreview(list, index) 会做一次归一化，字符串数组也支持。
 */
export const previewState = reactive({
  show: false,
  items: [],
  index: 0
})

export function openPreview(items, index = 0) {
  const list = (Array.isArray(items) ? items : [items])
    .map(normalize)
    .filter(it => it && it.src)
  if (!list.length) return
  previewState.items = list
  previewState.index = Math.min(Math.max(Number(index) || 0, 0), list.length - 1)
  previewState.show = true
  document.body.style.overflow = 'hidden'
}

export function closePreview() {
  previewState.show = false
  previewState.index = 0
  previewState.items = []
  document.body.style.overflow = ''
}

function normalize(it) {
  if (!it) return null
  if (typeof it === 'string') {
    return { src: it, name: guessName(it), size: null, cdnUrl: /^https?:/.test(it) ? it : '' }
  }
  const src = it.src || it.url || it.cdnUrl || it.preview || ''
  const cdnUrl = it.cdnUrl && /^https?:/.test(it.cdnUrl) ? it.cdnUrl : (/^https?:/.test(src) ? src : '')
  return {
    src,
    name: it.name || it.originName || guessName(src),
    size: typeof it.size === 'number' ? it.size : null,
    cdnUrl,
    // 上传记录 id：有值说明这张图已入库，可以生成分享链接
    recordId: it.recordId ?? it.id ?? null
  }
}

function guessName(url) {
  if (!url) return '未命名'
  try {
    const clean = url.split('?')[0].split('#')[0]
    return decodeURIComponent(clean.substring(clean.lastIndexOf('/') + 1)) || '未命名'
  } catch {
    return '未命名'
  }
}
