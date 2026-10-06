import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: sessionStorage.getItem('imgbed_token') || '',
    user: JSON.parse(sessionStorage.getItem('imgbed_user') || 'null')
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    /** 是否为管理员（由后端在用户信息里返回 admin 标记） */
    isAdmin: (s) => !!s.user?.admin
  },
  actions: {
    setAuth(token, user) {
      this.token = token
      this.user = user
      sessionStorage.setItem('imgbed_token', token)
      sessionStorage.setItem('imgbed_user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      sessionStorage.removeItem('imgbed_token')
      sessionStorage.removeItem('imgbed_user')
    }
  }
})
