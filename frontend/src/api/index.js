import axios from 'axios'
import { openAuthModal } from '../utils/authModal'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// 令牌自动附加（sessionStorage，关闭标签页即失效）
api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('imgbed_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 统一错误处理：401 唤起登录弹窗，其余提取后端 message
api.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => {
    if (err.response) {
      const { status, data } = err.response
      if (status === 401) {
        sessionStorage.removeItem('imgbed_token')
        sessionStorage.removeItem('imgbed_user')
        // 不跳页，直接弹出登录框；记录/仓库等需登录页面的守卫会再兜底
        openAuthModal('login')
        return Promise.reject(new Error('登录已过期，请重新登录'))
      }
      const msg = data?.message || `请求失败 (${status})`
      // 4xx 不重试，5xx 由调用方决定是否重试
      return Promise.reject(new Error(msg))
    }
    return Promise.reject(new Error('网络异常，请检查后端服务是否启动'))
  }
)

export default api
