import { ref } from 'vue'

/** 全局登录弹窗状态：任何地方调用 openAuthModal() 都能唤起登录/注册弹窗 */
export const authModalVisible = ref(false)
export const authModalMode = ref('login') // 'login' | 'register'

export function openAuthModal(mode = 'login') {
  authModalMode.value = mode
  authModalVisible.value = true
}

export function closeAuthModal() {
  authModalVisible.value = false
}
