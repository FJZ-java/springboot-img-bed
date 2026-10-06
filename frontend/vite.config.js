import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 后端地址走环境变量，默认 127.0.0.1（避免 Node 将 localhost 解析为 IPv6 导致代理失败）
  const apiTarget = env.VITE_API_TARGET || 'http://127.0.0.1:8080'

  return {
    plugins: [vue()],
    server: {
      // 显式监听 IPv4 回环地址，避免 Vite 只绑定 ::1 导致 localhost 解析到 IPv4 时访问不了（ERR_EMPTY_RESPONSE）
      host: '127.0.0.1',
      port: 5173,
      strictPort: false,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          // 重写 Origin 为后端地址：代理场景下浏览器仍会带上 localhost:517x，
          // 若后端 CORS 白名单未覆盖该端口会直接返回 403 Invalid CORS request
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => {
              proxyReq.setHeader('origin', apiTarget)
              proxyReq.setHeader('referer', apiTarget)
            })
          }
        }
      }
    }
  }
})
