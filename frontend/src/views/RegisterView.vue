<template>
  <AuthShell title="创建账号 🚀" subtitle="注册即开通，免费托管你的图片">
    <AuthPanel initial-mode="register" @success="onSuccess" />
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
