<template>
  <div class="auth-panel-body">
    <n-form
      ref="formRef"
      :model="form"
      :rules="rules"
      size="large"
      label-placement="top"
      :show-require-mark="true"
      require-mark-placement="right"
      @keyup.enter="submit"
    >
      <n-form-item label="用户名" path="username">
        <n-input v-model:value="form.username" placeholder="请输入用户名" :maxlength="20" />
      </n-form-item>

      <n-form-item v-if="mode === 'register'" label="昵称（可选）" path="nickname">
        <n-input v-model:value="form.nickname" placeholder="请输入昵称" :maxlength="20" />
      </n-form-item>

      <n-form-item label="密码" path="password">
        <n-input
          v-model:value="form.password"
          type="password"
          show-password-on="click"
          placeholder="请输入密码"
        />
      </n-form-item>

      <n-button class="submit-btn" attr-type="button" :loading="loading" @click="submit">
        {{ mode === 'login' ? '登 录' : '注 册' }}
      </n-button>
    </n-form>

    <p class="auth-tip">
      <template v-if="mode === 'login'">
        还没有账号？<a @click="switchMode('register')">立即注册</a>
      </template>
      <template v-else>
        已有账号？<a @click="switchMode('login')">直接登录</a>
      </template>
    </p>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import api from '../api'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  initialMode: { type: String, default: 'login' }
})
const emit = defineEmits(['success', 'mode-change'])

const message = useMessage()
const auth = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const mode = ref(props.initialMode)
const form = ref({ username: '', password: '', nickname: '' })

watch(() => props.initialMode, (v) => { mode.value = v })
watch(mode, (v) => emit('mode-change', v))

const rules = computed(() => ({
  username: mode.value === 'register'
    ? [
        { required: true, message: '请输入用户名', trigger: ['input', 'blur'] },
        { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '需为 3-20 位字母、数字或下划线', trigger: ['input', 'blur'] }
      ]
    : { required: true, message: '请输入用户名', trigger: ['input', 'blur'] },
  password: mode.value === 'register'
    ? [
        { required: true, message: '请输入密码', trigger: ['input', 'blur'] },
        { min: 6, max: 32, message: '密码长度需为 6-32 位', trigger: ['input', 'blur'] }
      ]
    : { required: true, message: '请输入密码', trigger: ['input', 'blur'] }
}))

function switchMode(next) {
  if (mode.value === next) return
  mode.value = next
  formRef.value?.restoreValidation()
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const url = mode.value === 'login' ? '/auth/login' : '/auth/register'
    const payload = mode.value === 'login'
      ? { username: form.value.username, password: form.value.password }
      : { username: form.value.username, password: form.value.password, nickname: form.value.nickname }
    const res = await api.post(url, payload)
    auth.setAuth(res.data.token, res.data.user)
    message.success(mode.value === 'login' ? '登录成功，欢迎回来！' : '注册成功，已自动登录')
    emit('success', res.data.user)
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-panel-body { display: flex; flex-direction: column; }

/* 表单标签 */
:deep(.n-form-item-label) {
  font-size: 13px;
  color: rgba(235, 240, 248, 0.88);
  padding-bottom: 6px;
}
:deep(.n-form-item-label__asterisk) { color: #38bdd0; }
:deep(.n-form-item) { margin-bottom: 4px; }
:deep(.n-form-item-blank) { min-height: 0; }

/* 青色描边输入框 */
:deep(.n-input) {
  --n-border: 1px solid rgba(56, 189, 208, 0.5);
  --n-border-hover: 1px solid rgba(56, 189, 208, 0.85);
  --n-border-focus: 1px solid rgba(56, 189, 208, 1);
  --n-box-shadow-focus: 0 0 0 2px rgba(56, 189, 208, 0.22);
  --n-border-error: 1px solid rgba(248, 113, 113, 0.8);
  --n-border-hover-error: 1px solid rgba(248, 113, 113, 1);
  --n-border-focus-error: 1px solid rgba(248, 113, 113, 1);
  --n-box-shadow-focus-error: 0 0 0 2px rgba(248, 113, 113, 0.2);
  --n-color: rgba(8, 13, 22, 0.55);
  --n-color-focus: rgba(8, 13, 22, 0.7);
  --n-text-color: #e8eef6;
  --n-caret-color: #38bdd0;
  --n-placeholder-color: rgba(230, 236, 245, 0.32);
  --n-border-radius: 8px;
  --n-height-large: 42px;
}

/* 青色主按钮 */
.submit-btn.n-button {
  width: 100%;
  height: 42px;
  margin-top: 14px;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  color: #fff;
  background: linear-gradient(120deg, #1fb6cf, #2dd4bf 160%);
  box-shadow: 0 8px 22px rgba(34, 197, 214, 0.32);
  transition: transform 0.18s ease, box-shadow 0.25s ease, filter 0.25s ease;
}
.submit-btn.n-button:hover {
  filter: brightness(1.08);
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(34, 197, 214, 0.45);
}
.submit-btn.n-button:active { transform: translateY(0) scale(0.995); }

.auth-tip {
  margin: 16px 0 0;
  text-align: center;
  font-size: 12.5px;
  color: rgba(230, 236, 245, 0.45);
}
.auth-tip a { color: #38bdd0; cursor: pointer; }
.auth-tip a:hover { text-decoration: underline; }
</style>
