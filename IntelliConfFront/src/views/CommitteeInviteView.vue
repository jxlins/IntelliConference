<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import http from '../utils/http'

interface InviteTokenInfo {
  conferenceId: number
  conferenceName: string
  conferenceShortName?: string
  inviteeEmail: string
  inviteeName?: string
  roleName: string
  committeeName: string
  invitationStatus: string
  expired: boolean
  expiredAt?: string
}

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const accepting = ref(false)
const declining = ref(false)
const invite = ref<InviteTokenInfo | null>(null)

function isSuccess(resp: any) {
  return resp?.success || resp?.code === '0'
}

async function loadInvite() {
  const token = String(route.query.token || '')
  if (!token) {
    ElMessage.error('邀请链接无效，缺少邀请凭证')
    return
  }
  loading.value = true
  try {
    const response = await axios.get(`/api/committee-invitations/token/${token}`)
    if (isSuccess(response.data)) {
      invite.value = response.data.data
    } else {
      ElMessage.error(response.data?.message || '加载邀请信息失败')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载邀请信息失败')
  } finally {
    loading.value = false
  }
}

async function acceptInvite() {
  const token = String(route.query.token || '')
  if (!token) return
  if (!localStorage.getItem('token')) {
    router.push(`/auth?token=${encodeURIComponent(token)}&mode=accept`)
    return
  }
  accepting.value = true
  try {
    const response = await http.post(`/api/committee-invitations/token/${token}/accept`)
    if (!isSuccess(response.data)) {
      if (String(response.data?.message || '').includes('Token')) {
        localStorage.removeItem('token')
        localStorage.removeItem('username')
        router.push(`/auth?token=${encodeURIComponent(token)}&mode=accept`)
        return
      }
      ElMessage.error(response.data?.message || '接受邀请失败')
      return
    }
    ElMessage.success('已接受邀请，您已正式加入会议委员会')
    const data = response.data.data as InviteTokenInfo
    if (data?.conferenceShortName) {
      router.push(`/dashboard?confId=${encodeURIComponent(data.conferenceShortName)}`)
      return
    }
    router.push('/portal')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '接受邀请失败，请确认当前账号邮箱与受邀邮箱一致')
  } finally {
    accepting.value = false
  }
}

async function declineInvite() {
  const token = String(route.query.token || '')
  if (!token) return
  declining.value = true
  try {
    const response = await axios.post(`/api/committee-invitations/token/${token}/decline`, {})
    if (isSuccess(response.data)) {
      ElMessage.success('已拒绝本次邀请')
      await loadInvite()
      return
    }
    ElMessage.error(response.data?.message || 'Failed to decline invitation')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '拒绝邀请失败')
  } finally {
    declining.value = false
  }
}

onMounted(loadInvite)
</script>

<template>
  <div class="invite-page" v-loading="loading">
    <section class="invite-card">
      <p class="eyebrow">COMMITTEE INVITATION</p>
      <h1>{{ invite?.conferenceName || '会议委员会邀请' }}</h1>
      <div v-if="invite" class="invite-meta">
        <span>{{ invite.roleName }}</span>
        <span>{{ invite.committeeName }}</span>
        <span>{{ invite.inviteeEmail }}</span>
      </div>
      <div v-if="invite" class="invite-notice">
        您受邀担任<strong>{{ invite.roleName }}</strong>。接受后系统才会将您加入委员会，并向您展示该角色需要处理的会议事务。
      </div>
      <p class="invite-desc">请使用受邀邮箱 <strong>{{ invite?.inviteeEmail }}</strong> 登录或注册后确认。</p>
      <div class="actions">
        <el-button type="primary" :loading="accepting" :disabled="invite?.expired || invite?.invitationStatus !== 'SENT'" @click="acceptInvite">同意并加入委员会</el-button>
        <el-button :loading="declining" :disabled="invite?.expired || invite?.invitationStatus !== 'SENT'" @click="declineInvite">拒绝邀请</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.invite-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #f4f7fb;
}

.invite-card {
  width: min(560px, 100%);
  padding: 32px;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
}

.eyebrow {
  margin: 0 0 12px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.invite-card h1 {
  margin: 0;
  color: #0f172a;
  font-size: 28px;
}

.invite-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
  color: #475569;
}

.invite-desc {
  margin: 18px 0 0;
  color: #64748b;
  line-height: 1.7;
}

.invite-notice {
  margin-top: 22px;
  padding: 16px;
  border: 1px solid #dbe6fb;
  border-radius: 12px;
  color: #45536d;
  background: #f3f7ff;
  line-height: 1.7;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}
</style>
