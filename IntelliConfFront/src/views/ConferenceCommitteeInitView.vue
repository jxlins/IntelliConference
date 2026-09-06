<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import http from '../utils/http'

interface ConferenceInfo {
  id: number
  title: string
  shortName: string
}

interface CommitteeRole {
  roleDefId: number
  roleCode: string
  roleName: string
  committeeType?: string
  committeeName?: string
  roleDesc?: string
  isRequired?: number
  canAssignTask?: number
}

interface CommitteeInvitation {
  invitationId: number
  sourceTaskId: number
  roleDefId: number
  inviteeName?: string
  inviteeEmail: string
  inviteeAffiliation?: string
  roleCode: string
  roleName: string
  committeeType?: string
  committeeName?: string
  invitationStatus: string
  sendCount?: number
  sentAt?: string
  acceptedAt?: string
  declinedAt?: string
  expiredAt?: string
}

interface CommitteeSummary {
  requiredRoleCount: number
  acceptedRequiredRoleCount: number
  totalInvitationCount: number
  sentCount: number
  acceptedCount: number
  declinedCount: number
  expiredCount: number
  canCompleteCommitteeTask: boolean
  missingRequiredRoles: Array<{ roleCode: string; roleName: string }>
}

const route = useRoute()
const router = useRouter()

const confShortName = computed(() => String(route.query.confId || ''))
const sourceTaskId = computed(() => Number(route.query.taskId || 0))

const loading = ref(false)
const saving = ref(false)
const conference = ref<ConferenceInfo | null>(null)
const roles = ref<CommitteeRole[]>([])
const invitations = ref<CommitteeInvitation[]>([])
const summary = ref<CommitteeSummary | null>(null)

const form = ref({
  inviteeName: '',
  inviteeEmail: '',
  inviteeAffiliation: '',
  roleDefId: undefined as number | undefined,
})

function isSuccess(resp: any) {
  return resp?.success || resp?.code === '0'
}

async function loadConference() {
  const response = await http.get(`/api/intelli-conf/v1/conference/${confShortName.value}`)
  if (!isSuccess(response.data)) {
    throw new Error(response.data?.message || 'Failed to load conference')
  }
  conference.value = response.data.data
}

async function loadRoles() {
  const response = await http.get(`/api/conferences/${conference.value?.id}/committee/roles`)
  roles.value = response.data.data || []
  if (!form.value.roleDefId && roles.value.length > 0) {
    form.value.roleDefId = roles.value[0]?.roleDefId
  }
}

async function loadInvitations() {
  const response = await http.get(`/api/conferences/${conference.value?.id}/committee/invitations`, {
    params: { sourceTaskId: sourceTaskId.value || undefined },
  })
  invitations.value = response.data.data || []
}

async function loadSummary() {
  const response = await http.get(`/api/conferences/${conference.value?.id}/committee/setup-summary`)
  summary.value = response.data.data || null
}

async function loadPage() {
  if (!confShortName.value) {
    ElMessage.error('Missing conference id')
    return
  }
  loading.value = true
  try {
    await loadConference()
    await Promise.all([loadRoles(), loadInvitations(), loadSummary()])
  } catch (error: any) {
    ElMessage.error(error.message || error.response?.data?.message || 'Failed to load committee setup page')
  } finally {
    loading.value = false
  }
}

async function saveInvitation() {
  if (!conference.value?.id) return
  if (!sourceTaskId.value) {
    ElMessage.error('Missing committee task id')
    return
  }
  saving.value = true
  try {
    const response = await http.post(`/api/conferences/${conference.value.id}/committee/invitations`, {
      sourceTaskId: sourceTaskId.value,
      inviteeName: form.value.inviteeName,
      inviteeEmail: form.value.inviteeEmail,
      inviteeAffiliation: form.value.inviteeAffiliation,
      roleDefId: form.value.roleDefId,
    })
    if (!isSuccess(response.data)) {
      ElMessage.error(response.data?.message || 'Failed to save invitation')
      return
    }
    ElMessage.success('Invitation draft saved')
    form.value.inviteeName = ''
    form.value.inviteeEmail = ''
    form.value.inviteeAffiliation = ''
    await Promise.all([loadInvitations(), loadSummary()])
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to save invitation')
  } finally {
    saving.value = false
  }
}

async function sendInvitation(invitationId: number) {
  if (!conference.value?.id) return
  try {
    const response = await http.post(`/api/conferences/${conference.value.id}/committee/invitations/${invitationId}/send`)
    if (isSuccess(response.data)) {
      ElMessage.success('Invitation sent')
      await Promise.all([loadInvitations(), loadSummary()])
      return
    }
    ElMessage.error(response.data?.message || 'Failed to send invitation')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to send invitation')
  }
}

async function cancelInvitation(invitationId: number) {
  if (!conference.value?.id) return
  try {
    const response = await http.put(`/api/conferences/${conference.value.id}/committee/invitations/${invitationId}/cancel`)
    if (isSuccess(response.data)) {
      ElMessage.success('Invitation cancelled')
      await Promise.all([loadInvitations(), loadSummary()])
      return
    }
    ElMessage.error(response.data?.message || 'Failed to cancel invitation')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to cancel invitation')
  }
}

function getStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'DECLINED' || status === 'CANCELLED') return 'danger'
  if (status === 'SENT') return 'warning'
  return 'info'
}

function goBack() {
  router.push(`/dashboard?confId=${encodeURIComponent(confShortName.value)}`)
}

onMounted(loadPage)
</script>

<template>
  <div class="committee-page" v-loading="loading">
    <div class="top-bar">
      <el-button :icon="ArrowLeft" text @click="goBack">Back</el-button>
      <div>
        <h1>Committee Invitation Management</h1>
        <p>{{ conference?.title || conference?.shortName }}</p>
      </div>
    </div>

    <section class="summary-grid">
      <div class="summary-card">
        <strong>{{ summary?.acceptedRequiredRoleCount || 0 }}/{{ summary?.requiredRoleCount || 0 }}</strong>
        <span>Required roles confirmed</span>
      </div>
      <div class="summary-card">
        <strong>{{ summary?.totalInvitationCount || 0 }}</strong>
        <span>Total invitations</span>
      </div>
      <div class="summary-card">
        <strong>{{ summary?.sentCount || 0 }}</strong>
        <span>Sent</span>
      </div>
      <div class="summary-card">
        <strong>{{ summary?.acceptedCount || 0 }}</strong>
        <span>Accepted</span>
      </div>
    </section>

    <section v-if="summary?.missingRequiredRoles?.length" class="missing-panel">
      <h3>Missing required roles</h3>
      <el-tag
        v-for="role in summary.missingRequiredRoles"
        :key="role.roleCode"
        type="danger"
        effect="plain"
      >
        {{ role.roleName }}
      </el-tag>
    </section>

    <section class="content-grid">
      <div class="panel">
        <h2>Create invitation</h2>
        <el-form label-width="110px">
          <el-form-item label="Name">
            <el-input v-model="form.inviteeName" />
          </el-form-item>
          <el-form-item label="Email">
            <el-input v-model="form.inviteeEmail" />
          </el-form-item>
          <el-form-item label="Affiliation">
            <el-input v-model="form.inviteeAffiliation" />
          </el-form-item>
          <el-form-item label="Role">
            <el-select v-model="form.roleDefId" style="width: 100%">
              <el-option
                v-for="role in roles"
                :key="role.roleDefId"
                :label="`${role.roleName} (${role.committeeName || role.roleCode})`"
                :value="role.roleDefId"
              />
            </el-select>
          </el-form-item>
          <el-button type="primary" :loading="saving" @click="saveInvitation">Save Draft</el-button>
        </el-form>
      </div>

      <div class="panel">
        <div class="panel-head">
          <h2>Invitations</h2>
          <el-button @click="loadInvitations">Refresh</el-button>
        </div>
        <el-table :data="invitations" border stripe>
          <el-table-column prop="inviteeName" label="Name" min-width="120" />
          <el-table-column prop="inviteeEmail" label="Email" min-width="200" />
          <el-table-column prop="inviteeAffiliation" label="Affiliation" min-width="160" />
          <el-table-column prop="roleName" label="Role" min-width="150" />
          <el-table-column label="Status" width="130">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.invitationStatus)" effect="plain">{{ row.invitationStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Actions" width="180" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="['DRAFT', 'SENT', 'EXPIRED'].includes(row.invitationStatus)"
                text
                type="primary"
                @click="sendInvitation(row.invitationId)"
              >
                {{ row.invitationStatus === 'DRAFT' ? 'Send' : 'Resend' }}
              </el-button>
              <el-button
                v-if="['DRAFT', 'SENT', 'EXPIRED'].includes(row.invitationStatus)"
                text
                type="danger"
                @click="cancelInvitation(row.invitationId)"
              >
                Cancel
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.committee-page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.top-bar h1 {
  margin: 0;
  font-size: 24px;
}

.top-bar p {
  margin: 4px 0 0;
  color: #64748b;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.summary-card {
  display: grid;
  gap: 6px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}

.summary-card strong {
  font-size: 24px;
  color: #0f172a;
}

.summary-card span {
  color: #64748b;
}

.missing-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid #fecaca;
  border-radius: 12px;
  background: #fff1f2;
}

.missing-panel h3 {
  margin: 0;
  font-size: 15px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.5fr);
  gap: 18px;
}

.panel {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}

.panel h2 {
  margin: 0 0 14px;
  font-size: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

@media (max-width: 1080px) {
  .summary-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
