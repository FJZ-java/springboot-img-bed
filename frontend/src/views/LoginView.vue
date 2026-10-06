<template>
  <AuthShell title="欢迎回来 👋" subtitle="登录后即可继续管理你的图片">
    <AuthPanel initial-mode="login" @success="onSuccess" />
  </AuthShell>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AuthShell from '../components/AuthShell.vue'
import AuthPanel from '../components/AuthPanel.vue'

const router = useRouter()

function onSuccess() {
  const target = sessionStorage.getItem('imgbed_redirect')
  sessionStorage.removeItem('imgbed_redirect')
  router.replace(target || '/upload')
}
</script>
