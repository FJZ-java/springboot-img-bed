import api from '../api'

/**
 * 分享链接工具：为上传记录生成 / 取消公开分享短链。
 * 后端返回的 url 可能是相对路径（/share/xxxx），这里统一补全成可复制的绝对地址。
 */

/** 为某条上传记录生成分享链接（已存在则直接复用） */
export async function createShare(recordId) {
  const res = await api.post(`/share/record/${recordId}`)
  return normalizeShare(res.data)
}

/** 查询某条记录已有的分享链接（没有则返回 null） */
export async function fetchShare(recordId) {
  try {
    const res = await api.get(`/share/record/${recordId}`)
    return res.data ? normalizeShare(res.data) : null
  } catch {
    return null
  }
}

/** 取消分享 */
export async function cancelShare(recordId) {
  await api.delete(`/share/record/${recordId}`)
}

function normalizeShare(info) {
  if (!info) return null
  return { ...info, fullUrl: toAbsolute(info) }
}

/** 把相对路径补全为浏览器可访问的绝对地址 */
export function toAbsolute(info) {
  const url = info?.url || (info?.code ? `/share/${info.code}` : '')
  if (!url) return ''
  return /^https?:/i.test(url) ? url : window.location.origin + url
}

/** 复制到剪贴板（失败时回退到 execCommand，兼容 http 环境） */
export async function copyText(text) {
  if (!text) return false
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch { /* 继续走回退方案 */ }
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}
