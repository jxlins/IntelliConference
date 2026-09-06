<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import axios from 'axios'
import http from '../utils/http'

interface InviteTokenInfo {
  conferenceId: number
  conferenceName: string
  conferenceShortName?: string
  inviteeEmail: string
  roleName: string
  committeeName: string
  invitationStatus: string
  expired: boolean
}

const router = useRouter()
const route = useRoute()

const activeTab = ref<'login' | 'register'>('login')
const loginLoading = ref(false)
const registerLoading = ref(false)
const inviteTokenInfo = ref<InviteTokenInfo | null>(null)
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

const inviteToken = computed(() => String(route.query.token || ''))

const loginForm = reactive({
  username: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  rePassword: '',
})

const registerEmailReadonly = computed(() => !!inviteTokenInfo.value?.inviteeEmail)

const validateRePassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('Please confirm your password'))
    return
  }
  if (value !== registerForm.password) {
    callback(new Error('Passwords do not match'))
    return
  }
  callback()
}

const loginRules: FormRules = {
  username: [{ required: true, message: 'Please enter username', trigger: 'blur' }],
  password: [{ required: true, message: 'Please enter password', trigger: 'blur' }],
}

const registerRules: FormRules = {
  username: [
    { required: true, message: 'Please enter username', trigger: 'blur' },
    { min: 4, max: 20, message: 'Username must be 4-20 characters', trigger: 'blur' },
  ],
  email: [
    { required: true, message: 'Please enter email', trigger: 'blur' },
    { type: 'email', message: 'Please enter a valid email', trigger: 'blur' },
  ],
  password: [
    { required: true, message: 'Please enter password', trigger: 'blur' },
    { min: 6, message: 'Password must be at least 6 characters', trigger: 'blur' },
  ],
  rePassword: [
    { required: true, message: 'Please confirm your password', trigger: 'blur' },
    { validator: validateRePassword, trigger: 'blur' },
  ],
}

function isSuccess(resp: any) {
  return resp?.success || resp?.code === '0'
}

async function loadInviteTokenInfo() {
  if (!inviteToken.value) {
    return
  }
  try {
    const response = await axios.get(`/api/committee-invitations/token/${inviteToken.value}`)
    if (isSuccess(response.data)) {
      inviteTokenInfo.value = response.data.data
      registerForm.email = response.data.data?.inviteeEmail || ''
      if (route.query.mode === 'register') {
        activeTab.value = 'register'
      }
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to load invitation info')
  }
}

async function finishInviteAcceptance() {
  if (!inviteToken.value) {
    router.push('/portal')
    return
  }
  const response = await http.post(`/api/committee-invitations/token/${inviteToken.value}/accept`)
  if (!isSuccess(response.data)) {
    throw new Error(response.data?.message || 'Failed to accept invitation')
  }
  const data = response.data.data as InviteTokenInfo
  ElMessage.success('Invitation accepted successfully')
  if (data?.conferenceShortName) {
    router.push(`/dashboard?confId=${encodeURIComponent(data.conferenceShortName)}`)
    return
  }
  router.push('/portal')
}

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loginLoading.value = true
    try {
      const response = await axios.post('/api/intelli-conf/v1/user/login', loginForm)
      if (!isSuccess(response.data)) {
        ElMessage.error(response.data?.message || 'Login failed')
        return
      }
      const token = response.data.data?.token
      if (token) {
        localStorage.setItem('token', token)
        localStorage.setItem('username', loginForm.username)
      }
      if (inviteToken.value) {
        await finishInviteAcceptance()
        return
      }
      ElMessage.success('Login successful')
      router.push('/portal')
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || error.message || 'Login failed')
    } finally {
      loginLoading.value = false
    }
  })
}

async function handleRegister() {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (inviteTokenInfo.value?.inviteeEmail && registerForm.email.trim().toLowerCase() !== inviteTokenInfo.value.inviteeEmail.toLowerCase()) {
      ElMessage.error('Please register with the invited email address')
      return
    }
    registerLoading.value = true
    try {
      const response = await axios.post('/api/intelli-conf/v1/user', {
        username: registerForm.username,
        email: registerForm.email,
        password: registerForm.password,
      })
      if (!isSuccess(response.data)) {
        ElMessage.error(response.data?.message || 'Registration failed')
        return
      }
      if (inviteToken.value) {
        loginForm.username = registerForm.username
        loginForm.password = registerForm.password
        activeTab.value = 'login'
        await handleLogin()
        return
      }
      ElMessage.success('Registration successful, please log in')
      activeTab.value = 'login'
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Registration failed')
    } finally {
      registerLoading.value = false
    }
  })
}

onMounted(loadInviteTokenInfo)
</script>

<template>
  <div class="auth-page">
    <section class="brand-panel">
      <p class="eyebrow">IntelliConf</p>
      <h1>Conference workflow, with the sharp edges sanded down.</h1>
      <p>
        Create conferences, manage stages and tasks, invite committee members,
        and keep the whole process visible from setup to wrap-up.
      </p>
      <div v-if="inviteTokenInfo" class="invite-card">
        <strong>{{ inviteTokenInfo.conferenceName }}</strong>
        <span>{{ inviteTokenInfo.roleName }} · {{ inviteTokenInfo.committeeName }}</span>
        <span>{{ inviteTokenInfo.inviteeEmail }}</span>
      </div>
    </section>

    <section class="auth-card">
      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="Login" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" size="large" class="auth-form">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="Username" clearable />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="Password" show-password />
            </el-form-item>
            <el-button type="primary" size="large" :loading="loginLoading" class="submit-btn" @click="handleLogin">
              Login
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="Register" name="register">
          <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" size="large" class="auth-form">
            <el-form-item prop="username">
              <el-input v-model="registerForm.username" placeholder="Username" clearable />
            </el-form-item>
            <el-form-item prop="email">
              <el-input v-model="registerForm.email" placeholder="Email" clearable :readonly="registerEmailReadonly" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="Password" show-password />
            </el-form-item>
            <el-form-item prop="rePassword">
              <el-input v-model="registerForm.rePassword" type="password" placeholder="Confirm password" show-password />
            </el-form-item>
            <el-button type="primary" size="large" :loading="registerLoading" class="submit-btn" @click="handleRegister">
              Register
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 40px;
  align-items: center;
  padding: 56px;
  background: #f4f7fb;
}

.brand-panel {
  max-width: 680px;
}

.eyebrow {
  margin: 0 0 12px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.brand-panel h1 {
  margin: 0;
  font-size: 44px;
  line-height: 1.15;
  color: #0f172a;
}

.brand-panel p:last-child {
  margin: 18px 0 0;
  color: #475569;
  font-size: 17px;
  line-height: 1.8;
}

.invite-card {
  display: grid;
  gap: 6px;
  margin-top: 24px;
  padding: 18px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #eff6ff;
  color: #1e3a8a;
}

.auth-card {
  padding: 30px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
}

.auth-form {
  padding-top: 16px;
}

.submit-btn {
  width: 100%;
}

@media (max-width: 900px) {
  .auth-page {
    grid-template-columns: 1fr;
    padding: 28px;
  }
}
</style>
