<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CircleCheck, EditPen, MagicStick, Message, Plus, Promotion, Refresh, Search, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import http from '../utils/http'

interface CommitteeMember {
  confId: number
  email: string
  name?: string
  institution?: string
  role?: string
  inviteStatus?: string
  tokenExpireTime?: string
}

interface Participant {
  id: number | string
  contactId?: number
  name: string
  email: string
  institution?: string
  role?: string
  createTime?: string
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
  expiredAt?: string
}

interface AuthorDiscoveryJob {
  jobId: number
  status: string
  totalPapers?: number
  totalCandidates?: number
  highConfidenceEmails?: number
  errorMessage?: string
}

interface PotentialAuthor {
  id: number
  authorName: string
  email?: string
  organization?: string
  countryRegion?: string
  topicSimilarity?: number
  emailConfidence?: number
  overallScore?: number
  reviewStatus?: string
  contactStatus?: string
  sourcePlatform?: string
  sourceUrl?: string
  researchKeywords?: string
  representativePapers?: string
}

const route = useRoute()
const router = useRouter()

const confShortName = computed(() => String(route.query.confId || ''))
const conferenceId = ref<number | null>(null)
const conferenceTitle = ref('')

const loading = ref(false)
const activeTab = ref('committee')

const committeeLoading = ref(false)
const committeeList = ref<CommitteeMember[]>([])
const committeeRoles = ref<CommitteeRole[]>([])
const committeeInvitations = ref<CommitteeInvitation[]>([])
const committeeTaskId = ref<number | null>(null)
const inviteDialogVisible = ref(false)
const inviteSaving = ref(false)
const inviteSendingId = ref<number | null>(null)
const selectedInviteRole = ref<CommitteeRole | null>(null)
const inviteFormRef = ref<FormInstance>()
const inviteForm = ref({ name: '', email: '', affiliation: '' })

const participantLoading = ref(false)
const participantRecords = ref<Participant[]>([])
const participantKeyword = ref('')
const participantPage = ref(1)
const participantSize = ref(10)
const participantTotal = ref(0)
const participantUseLocalPaging = ref(false)
const roleFilter = ref('ALL')
const participantUpdatingId = ref<number | string | null>(null)

const addDialogVisible = ref(false)
const addSaving = ref(false)
const addFormRef = ref<FormInstance>()
const addForm = ref({
  name: '',
  email: '',
  institution: '',
  role: 'PROSPECT',
})

const editDialogVisible = ref(false)
const editSaving = ref(false)
const editForm = ref({
  memberId: 0 as number | string,
  name: '',
  email: '',
  institution: '',
  role: 'PROSPECT',
})

const discoveryDialogVisible = ref(false)
const discoveryRunning = ref(false)
const discoveryLoadingCandidates = ref(false)
const discoveryReviewingId = ref<number | null>(null)
const discoveryLastJob = ref<AuthorDiscoveryJob | null>(null)
const discoveryCancelling = ref(false)
// 任务状态标签与文案（含"已停止"终态）
const discoveryStatusTagType = computed(() => {
  const s = discoveryLastJob.value?.status
  if (s === 'COMPLETED') return 'success'
  if (s === 'RUNNING' || s === 'PENDING') return 'warning'
  if (s === 'FAILED') return 'danger'
  return 'info'
})
const discoveryStatusText = computed(() => {
  const s = discoveryLastJob.value?.status
  if (s === 'RUNNING') return '检索中'
  if (s === 'PENDING') return '排队中'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'PARTIAL') return '部分完成'
  if (s === 'CANCELLED') return '已停止'
  if (s === 'FAILED') return '失败'
  return s || ''
})

async function handleCancelDiscovery() {
  const job = discoveryLastJob.value
  if (!job || discoveryCancelling.value) return
  discoveryCancelling.value = true
  try {
    const updated = await apiCancelAuthorDiscoveryJob(job.jobId)
    discoveryLastJob.value = { ...job, ...updated }
    ElMessage.success('已停止任务，已保存的候选结果会保留')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '停止任务失败')
  } finally {
    discoveryCancelling.value = false
  }
}
const discoveryCandidates = ref<PotentialAuthor[]>([])
const discoveryFormRef = ref<FormInstance>()
const discoveryForm = ref({
  topicKeywords: [''],
  yearFrom: new Date().getFullYear() - 5,
  yearTo: new Date().getFullYear(),
  maxAuthors: 200,
  enableCrossref: true,
  enableEmailExtraction: true,
})

const addRules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
}

const discoveryRules: FormRules = {
  topicKeywords: [
    {
      validator: (_rule, value: string[], callback) => {
        const hasTopic = Array.isArray(value) && value.some(item => item && item.trim())
        if (!hasTopic) {
          callback(new Error('请至少填写一个主题关键词'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  yearFrom: [{ required: true, message: '请选择起始年份', trigger: 'change' }],
  yearTo: [{ required: true, message: '请选择结束年份', trigger: 'change' }],
  maxAuthors: [{ required: true, message: '请输入最大候选人数', trigger: 'change' }],
}

const inviteRules: FormRules = {
  name: [{ required: true, message: '请输入受邀人姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入受邀人邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
}

const roleOptions = [
  { label: '潜在投稿者', value: 'PROSPECT' },
  { label: '投稿作者', value: 'AUTHOR' },
  { label: '已录用作者', value: 'ACCEPTED_AUTHOR' },
  { label: '已注册参会者', value: 'REGISTERED' },
  { label: '已签到参会者', value: 'ATTENDEE' },
  { label: '审稿人', value: 'REVIEWER' },
  { label: '演讲嘉宾', value: 'SPEAKER' },
  { label: '组织者', value: 'ORGANIZER' },
]

const lifecycleSteps = computed(() => {
  const roles = participantRecords.value.reduce<Record<string, number>>((result, item) => {
    const key = (item.role || 'PROSPECT').toUpperCase()
    result[key] = (result[key] || 0) + 1
    return result
  }, {})
  return [
    { key: 'PROSPECT', label: '潜在投稿者', hint: '发现或导入', count: roles.PROSPECT || 0 },
    { key: 'AUTHOR', label: '投稿作者', hint: '已产生投稿', count: roles.AUTHOR || 0 },
    { key: 'ACCEPTED_AUTHOR', label: '录用作者', hint: '论文已录用', count: roles.ACCEPTED_AUTHOR || 0 },
    { key: 'REGISTERED', label: '已注册', hint: '完成缴费注册', count: roles.REGISTERED || 0 },
    { key: 'ATTENDEE', label: '已参会', hint: '完成签到', count: roles.ATTENDEE || 0 },
  ]
})

const committeeCount = computed(() => {
  const acceptedEmails = committeeInvitations.value
    .filter(item => item.invitationStatus === 'ACCEPTED')
    .map(item => item.inviteeEmail.toLowerCase())
  committeeList.value
    .filter(item => item.inviteStatus === 'ACCEPTED')
    .forEach(item => acceptedEmails.push(item.email.toLowerCase()))
  return new Set(acceptedEmails).size
})

const committeeGroups = computed(() => {
  const groups = new Map<string, { key: string; name: string; roles: Array<CommitteeRole & { accepted: CommitteeInvitation[]; pending: CommitteeInvitation[] }> }>()
  committeeRoles.value.forEach(role => {
    const key = role.committeeType || 'OTHER_COMMITTEE'
    if (!groups.has(key)) {
      groups.set(key, { key, name: role.committeeName || '其他委员会', roles: [] })
    }
    const roleInvitations = committeeInvitations.value.filter(item => item.roleCode === role.roleCode)
    const legacyRole = role.roleCode === 'GENERAL_CHAIR'
      ? 'CHAIR'
      : role.roleCode === 'PROGRAM_COMMITTEE_MEMBER' ? 'REVIEWER' : role.roleCode
    const legacyAccepted: CommitteeInvitation[] = committeeList.value
      .filter(item => item.role === legacyRole && item.inviteStatus === 'ACCEPTED')
      .filter(item => !roleInvitations.some(invitation => invitation.inviteeEmail.toLowerCase() === item.email.toLowerCase()))
      .map((item, index) => ({
        invitationId: -(role.roleDefId * 1000 + index + 1),
        sourceTaskId: 0,
        roleDefId: role.roleDefId,
        inviteeName: item.name,
        inviteeEmail: item.email,
        inviteeAffiliation: item.institution,
        roleCode: role.roleCode,
        roleName: role.roleName,
        committeeType: role.committeeType,
        committeeName: role.committeeName,
        invitationStatus: 'ACCEPTED',
      }))
    groups.get(key)?.roles.push({
      ...role,
      accepted: [...roleInvitations.filter(item => item.invitationStatus === 'ACCEPTED'), ...legacyAccepted],
      pending: roleInvitations.filter(item => item.invitationStatus !== 'ACCEPTED'),
    })
  })
  return Array.from(groups.values())
})
const participantTotalDisplay = computed(() => {
  if (roleFilter.value !== 'ALL') {
    return participantRecords.value.filter(item => item.role === roleFilter.value).length
  }
  if (participantTotal.value > 0) return participantTotal.value
  return participantRecords.value.length
})

const participantCount = computed(() => participantTotal.value || participantRecords.value.length)

const participants = computed(() => {
  const source = roleFilter.value === 'ALL'
    ? participantRecords.value
    : participantRecords.value.filter(item => item.role === roleFilter.value)
  if (!participantUseLocalPaging.value) return source
  const start = (participantPage.value - 1) * participantSize.value
  return source.slice(start, start + participantSize.value)
})

async function apiGetConferenceByShortName(shortName: string) {
  const res = await http.get(`/api/intelli-conf/v1/conference/${shortName}`)
  return res.data.data
}

async function apiGetCommitteeList(confId: number) {
  const res = await http.get(`/api/intelli-conf/v1/conference/${confId}/committee/list`)
  return res.data.data || []
}

async function apiGetParticipantList(confId: number, current: number, size: number, keyword?: string) {
  const res = await http.get('/api/intelli-conf/v1/participant/list', {
    params: { confId, current, size, keyword }
  })
  return res.data.data
}

async function apiAddParticipant(payload: {
  confId: number
  name: string
  email: string
  institution?: string
  role?: string
}) {
  const res = await http.post('/api/intelli-conf/v1/participant/add', payload)
  return res.data.data
}

async function loadCommitteeSetup() {
  if (!conferenceId.value) return
  const [roleResp, invitationResp, taskResp] = await Promise.all([
    http.get(`/api/conferences/${conferenceId.value}/committee/roles`),
    http.get(`/api/conferences/${conferenceId.value}/committee/invitations`),
    http.get(`/api/conferences/${conferenceId.value}/tasks/generated`),
  ])
  committeeRoles.value = roleResp.data?.data || []
  committeeInvitations.value = invitationResp.data?.data || []
  const committeeTask = (taskResp.data?.data || []).find((item: any) => item.taskCode === 'CONFIRM_CONFERENCE_COMMITTEE')
  committeeTaskId.value = committeeTask?.taskId || committeeTask?.taskInstanceId || null
}

async function apiCreateAuthorDiscoveryJob(confId: number, payload: {
  topicKeywords: string[]
  yearFrom: number
  yearTo: number
  maxAuthors: number
  enableCrossref: boolean
  enableEmailExtraction: boolean
}) {
  const res = await http.post(`/api/conferences/${confId}/author-discovery/jobs`, payload)
  return res.data.data as AuthorDiscoveryJob
}

async function apiRunAuthorDiscoveryJob(jobId: number, payload: {
  enableCrossref: boolean
  enableEmailExtraction: boolean
}) {
  // 异步启动：大目标（如200人）任务可能运行数十分钟，改为启动后轮询进度
  const res = await http.post(`/api/author-discovery/jobs/${jobId}/start`, payload, {
    timeout: 30000,
  })
  return res.data.data as AuthorDiscoveryJob
}

async function apiGetAuthorDiscoveryJob(jobId: number) {
  const res = await http.get(`/api/author-discovery/jobs/${jobId}`)
  return res.data.data as AuthorDiscoveryJob
}

async function apiGetLatestAuthorDiscoveryJob(confId: number) {
  const res = await http.get(`/api/conferences/${confId}/author-discovery/latest`)
  return res.data.data as AuthorDiscoveryJob | null
}

async function apiCancelAuthorDiscoveryJob(jobId: number) {
  const res = await http.post(`/api/author-discovery/jobs/${jobId}/cancel`, null, { timeout: 30000 })
  return res.data.data as AuthorDiscoveryJob
}

async function apiListPotentialAuthors(confId: number) {
  const res = await http.get(`/api/conferences/${confId}/potential-authors`, {
    params: {
      reviewStatus: 'PENDING',
      sourcePlatform: 'OPENALEX',
    },
  })
  return (res.data.data || []) as PotentialAuthor[]
}

async function apiApprovePotentialAuthor(authorId: number) {
  await http.post(`/api/potential-authors/${authorId}/approve`)
}

async function apiRejectPotentialAuthor(authorId: number) {
  await http.post(`/api/potential-authors/${authorId}/reject`)
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function getCommitteeRoleLabel(role?: string) {
  if (role === 'CHAIR') return '主席'
  if (role === 'REVIEWER') return '委员'
  return role || '-'
}

function getCommitteeRoleTag(role?: string): 'success' | 'warning' | 'info' {
  if (role === 'CHAIR') return 'success'
  if (role === 'REVIEWER') return 'warning'
  return 'info'
}

function getInviteStatusLabel(status?: string) {
  if (status === 'ACCEPTED') return '已同意'
  if (status === 'DECLINED') return '已拒绝'
  if (status === 'INVITED') return '待确认'
  return status || '-'
}

function getInviteStatusTag(status?: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'DECLINED') return 'danger'
  if (status === 'INVITED') return 'warning'
  return 'info'
}

async function loadCommittee() {
  if (!conferenceId.value) return
  committeeLoading.value = true
  try {
    const [legacyMembers] = await Promise.all([
      apiGetCommitteeList(conferenceId.value),
      loadCommitteeSetup(),
    ])
    committeeList.value = legacyMembers
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载委员会列表失败')
  } finally {
    committeeLoading.value = false
  }
}

async function loadParticipants() {
  if (!conferenceId.value) return
  participantLoading.value = true
  try {
    const apiPageSize = 100
    const keyword = participantKeyword.value.trim() || undefined
    const firstPage = await apiGetParticipantList(conferenceId.value, 1, apiPageSize, keyword)
    const records: Participant[] = [...(firstPage?.records || [])]
    const pages = Math.max(1, Number(firstPage?.pages || 1))
    if (pages > 1) {
      const remaining = await Promise.all(
        Array.from({ length: pages - 1 }, (_, index) =>
          apiGetParticipantList(conferenceId.value as number, index + 2, apiPageSize, keyword)
        )
      )
      remaining.forEach(page => records.push(...(page?.records || [])))
    }
    participantUseLocalPaging.value = true
    participantRecords.value = records
    participantTotal.value = Number(firstPage?.total ?? records.length)
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载参会者列表失败')
  } finally {
    participantLoading.value = false
  }
}

async function loadData() {
  if (!confShortName.value) {
    ElMessage.error('缺少会议标识，无法加载用户管理')
    return
  }

  loading.value = true
  try {
    const conf = await apiGetConferenceByShortName(confShortName.value)
    conferenceId.value = conf.id
    conferenceTitle.value = conf.title || conf.shortName || confShortName.value
    await Promise.all([loadCommittee(), loadParticipants()])
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载会议数据失败')
  } finally {
    loading.value = false
  }
}

function handleBack() {
  router.push(`/dashboard?confId=${encodeURIComponent(confShortName.value)}`)
}

function handleSearch() {
  participantPage.value = 1
  loadParticipants()
}

function handleReset() {
  participantKeyword.value = ''
  roleFilter.value = 'ALL'
  participantPage.value = 1
  loadParticipants()
}

function openInviteDialog(role: CommitteeRole) {
  selectedInviteRole.value = role
  inviteForm.value = { name: '', email: '', affiliation: '' }
  inviteDialogVisible.value = true
}

async function submitCommitteeInvitation() {
  if (!conferenceId.value || !selectedInviteRole.value) return
  if (!committeeTaskId.value) {
    ElMessage.error('未找到“确立会议委员会”任务，请先生成会议任务方案')
    return
  }
  if (!inviteFormRef.value) return
  await inviteFormRef.value.validate(async valid => {
    if (!valid || !conferenceId.value || !selectedInviteRole.value || !committeeTaskId.value) return
    inviteSaving.value = true
    try {
      const createResp = await http.post(`/api/conferences/${conferenceId.value}/committee/invitations`, {
        sourceTaskId: committeeTaskId.value,
        roleDefId: selectedInviteRole.value.roleDefId,
        inviteeName: inviteForm.value.name.trim(),
        inviteeEmail: inviteForm.value.email.trim().toLowerCase(),
        inviteeAffiliation: inviteForm.value.affiliation.trim() || undefined,
      })
      const invitation = createResp.data?.data
      if (!invitation?.invitationId) throw new Error(createResp.data?.message || '邀请创建失败')
      await http.post(`/api/conferences/${conferenceId.value}/committee/invitations/${invitation.invitationId}/send`)
      ElMessage.success('邀请邮件已发送；对方同意后才会正式加入委员会')
      inviteDialogVisible.value = false
      await loadCommittee()
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || err.message || '委员会邀请发送失败')
    } finally {
      inviteSaving.value = false
    }
  })
}

async function resendCommitteeInvitation(invitation: CommitteeInvitation) {
  if (!conferenceId.value) return
  inviteSendingId.value = invitation.invitationId
  try {
    await http.post(`/api/conferences/${conferenceId.value}/committee/invitations/${invitation.invitationId}/send`)
    ElMessage.success('邀请邮件已重新发送，有效期延长14天')
    await loadCommittee()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '重新发送邀请失败')
  } finally {
    inviteSendingId.value = null
  }
}

async function deleteCommitteeInvitation(invitation: CommitteeInvitation) {
  if (!conferenceId.value || invitation.invitationId < 0) return
  try {
    await ElMessageBox.confirm(
      invitation.invitationStatus === 'ACCEPTED'
        ? `确定移除“${invitation.inviteeName || invitation.inviteeEmail}”吗？其成员角色、邀请记录及该角色下的个人任务指派会一并移除。`
        : `确定删除“${invitation.inviteeName || invitation.inviteeEmail}”的邀请记录吗？`,
      invitation.invitationStatus === 'ACCEPTED' ? '移除委员会成员' : '删除邀请记录',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  inviteSendingId.value = invitation.invitationId
  try {
    await http.delete(`/api/conferences/${conferenceId.value}/committee/invitations/${invitation.invitationId}`)
    ElMessage.success(invitation.invitationStatus === 'ACCEPTED' ? '委员会成员已移除' : '邀请记录已删除')
    await loadCommittee()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  } finally {
    inviteSendingId.value = null
  }
}

function getInvitationStatusLabel(status?: string) {
  if (status === 'ACCEPTED') return '已加入'
  if (status === 'SENT') return '等待对方同意'
  if (status === 'DRAFT') return '待发送'
  if (status === 'DECLINED') return '已拒绝'
  if (status === 'EXPIRED') return '已过期'
  if (status === 'CANCELLED') return '已取消'
  return status || '未知'
}

function getInvitationStatusType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'SENT' || status === 'DRAFT') return 'warning'
  if (status === 'DECLINED' || status === 'EXPIRED' || status === 'CANCELLED') return 'danger'
  return 'info'
}

function selectRoleFilter(role: string) {
  roleFilter.value = roleFilter.value === role ? 'ALL' : role
  participantPage.value = 1
}

function getParticipantRoleLabel(role?: string) {
  return roleOptions.find(item => item.value === role)?.label || role || '未分类'
}

function getParticipantRoleType(role?: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (role === 'ATTENDEE' || role === 'REGISTERED') return 'success'
  if (role === 'ACCEPTED_AUTHOR' || role === 'AUTHOR') return 'primary'
  if (role === 'PROSPECT') return 'info'
  if (role === 'REVIEWER' || role === 'SPEAKER') return 'warning'
  return 'info'
}

function getNextLifecycleRole(role?: string) {
  const order = ['PROSPECT', 'AUTHOR', 'ACCEPTED_AUTHOR', 'REGISTERED', 'ATTENDEE']
  const index = order.indexOf(role || '')
  return index >= 0 && index < order.length - 1 ? order[index + 1] : ''
}

function openEditDialog(participant: Participant) {
  editForm.value = {
    memberId: participant.id,
    name: participant.name || '',
    email: participant.email || '',
    institution: participant.institution || '',
    role: participant.role || 'PROSPECT',
  }
  editDialogVisible.value = true
}

async function updateParticipant(payload: typeof editForm.value) {
  await http.put('/api/intelli-conf/v1/participant/update', {
    memberId: payload.memberId,
    name: payload.name.trim(),
    email: payload.email.trim().toLowerCase(),
    institution: payload.institution.trim() || undefined,
    role: payload.role,
  })
}

async function handleSaveParticipant() {
  if (!editForm.value.name.trim() || !editForm.value.email.trim()) {
    ElMessage.warning('请填写姓名和邮箱')
    return
  }
  editSaving.value = true
  try {
    await updateParticipant(editForm.value)
    ElMessage.success('人员信息已更新')
    editDialogVisible.value = false
    await loadParticipants()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '更新人员信息失败')
  } finally {
    editSaving.value = false
  }
}

async function promoteParticipant(participant: Participant) {
  const nextRole = getNextLifecycleRole(participant.role)
  if (!nextRole) return
  participantUpdatingId.value = participant.id
  try {
    await updateParticipant({
      memberId: participant.id,
      name: participant.name,
      email: participant.email,
      institution: participant.institution || '',
      role: nextRole,
    })
    ElMessage.success(`${participant.name} 已升级为${getParticipantRoleLabel(nextRole)}`)
    await loadParticipants()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '人员阶段升级失败')
  } finally {
    participantUpdatingId.value = null
  }
}

function handlePageChange(page: number) {
  participantPage.value = page
  if (!participantUseLocalPaging.value) {
    loadParticipants()
  }
}

function handleSizeChange(size: number) {
  participantSize.value = size
  participantPage.value = 1
  if (!participantUseLocalPaging.value) {
    loadParticipants()
  }
}

function openAddDialog() {
  addForm.value = {
    name: '',
    email: '',
    institution: '',
    role: 'PROSPECT',
  }
  addDialogVisible.value = true
}

function openDiscoveryDialog() {
  router.push({ path: '/author-discovery', query: { confId: confShortName.value } })
}
function addTopicKeyword() {
  discoveryForm.value.topicKeywords.push('')
}

function removeTopicKeyword(index: number) {
  if (discoveryForm.value.topicKeywords.length === 1) {
    discoveryForm.value.topicKeywords = ['']
    return
  }
  discoveryForm.value.topicKeywords.splice(index, 1)
}

function scorePercent(value?: number) {
  if (value == null) return '-'
  return `${Math.round(Number(value) * 100)}%`
}

function parseAuthorKeywords(value?: string) {
  if (!value) return []
  try { return (JSON.parse(value) || []).slice(0, 12) } catch { return [] }
}

function parseRepresentativePapers(value?: string) {
  if (!value) return []
  try { return (JSON.parse(value) || []).slice(0, 5) } catch { return [] }
}

async function loadPotentialAuthors() {
  if (!conferenceId.value) return
  discoveryLoadingCandidates.value = true
  try {
    discoveryCandidates.value = await apiListPotentialAuthors(conferenceId.value)
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载潜在投稿者失败')
  } finally {
    discoveryLoadingCandidates.value = false
  }
}

let discoveryPollTimer: ReturnType<typeof setInterval> | null = null

function stopDiscoveryPolling() {
  if (discoveryPollTimer !== null) {
    clearInterval(discoveryPollTimer)
    discoveryPollTimer = null
  }
}

// 后台轮询任务进度直到终态（最长跟踪 60 分钟）；大目标检索可能持续数十分钟，
// 期间按钮不再阻塞，进度实时显示在任务摘要区，可随时离开本页稍后再看
function startDiscoveryPolling(job: AuthorDiscoveryJob) {
  stopDiscoveryPolling()
  let elapsedSeconds = 0
  const maxSeconds = 60 * 60
  discoveryPollTimer = setInterval(async () => {
    elapsedSeconds += 5
    try {
      const current = await apiGetAuthorDiscoveryJob(job.jobId)
      discoveryLastJob.value = { ...job, ...current }
      if (elapsedSeconds % 30 === 0) {
        await loadPotentialAuthors()
      }
      if (!['PENDING', 'RUNNING'].includes(current.status || '')) {
        stopDiscoveryPolling()
        await loadPotentialAuthors()
        if (current.status === 'COMPLETED') {
          ElMessage.success(`发现任务完成，候选作者 ${current.totalCandidates ?? 0} 人`)
        } else if (current.status === 'PARTIAL') {
          ElMessage.warning(`本轮检索结束，累计候选作者 ${current.totalCandidates ?? 0} 人；可再次发起任务继续补充`)
        } else if (current.status === 'CANCELLED') {
          ElMessage.info(`任务已停止，已保存候选作者 ${current.totalCandidates ?? 0} 人；可再次发起任务继续补充`)
        } else {
          ElMessage.error(current.errorMessage || '发现任务失败')
        }
      } else if (elapsedSeconds >= maxSeconds) {
        stopDiscoveryPolling()
        ElMessage.info('任务仍在后台检索中，可稍后在「潜在投稿者发现」页面查看进度')
      }
    } catch {
      // 单次轮询失败（网络抖动）不中断后续轮询
      if (elapsedSeconds >= maxSeconds) stopDiscoveryPolling()
    }
  }, 5000)
}

async function handleRunAuthorDiscovery() {
  if (!conferenceId.value) {
    ElMessage.error('会议信息未加载完成')
    return
  }
  if (!discoveryFormRef.value) return

  await discoveryFormRef.value.validate(async (valid) => {
    if (!valid) return
    const topics = discoveryForm.value.topicKeywords
      .map(item => item.trim())
      .filter(Boolean)
    if (discoveryForm.value.yearFrom > discoveryForm.value.yearTo) {
      ElMessage.error('起始年份不能晚于结束年份')
      return
    }

    discoveryRunning.value = true
    discoveryLastJob.value = null
    try {
      const payload = {
        topicKeywords: topics,
        yearFrom: discoveryForm.value.yearFrom,
        yearTo: discoveryForm.value.yearTo,
        maxAuthors: discoveryForm.value.maxAuthors,
        enableCrossref: discoveryForm.value.enableCrossref,
        enableEmailExtraction: discoveryForm.value.enableEmailExtraction,
      }
      const job = await apiCreateAuthorDiscoveryJob(conferenceId.value as number, payload)
      discoveryLastJob.value = job
      await apiRunAuthorDiscoveryJob(job.jobId, {
        enableCrossref: payload.enableCrossref,
        enableEmailExtraction: payload.enableEmailExtraction,
      })
      // 后台轮询任务进度直到终态；按钮随即释放，进度在任务摘要区实时刷新
      startDiscoveryPolling(job)
      ElMessage.info('任务已启动，进度将实时刷新；大目标检索可能持续数十分钟，可稍后再看')
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '潜在投稿者发现任务失败')
    } finally {
      discoveryRunning.value = false
    }
  })
}

async function handleReviewPotentialAuthor(author: PotentialAuthor, approved: boolean) {
  discoveryReviewingId.value = author.id
  try {
    if (approved) {
      await apiApprovePotentialAuthor(author.id)
      ElMessage.success('已通过该潜在投稿者')
      await loadParticipants()
    } else {
      await apiRejectPotentialAuthor(author.id)
      ElMessage.success('已拒绝该潜在投稿者')
    }
    discoveryCandidates.value = discoveryCandidates.value.filter(item => item.id !== author.id)
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '审核潜在投稿者失败')
  } finally {
    discoveryReviewingId.value = null
  }
}

async function handleAddParticipant() {
  if (!conferenceId.value) {
    ElMessage.error('会议信息未加载完成')
    return
  }
  if (!addFormRef.value) return

  await addFormRef.value.validate(async (valid) => {
    if (!valid) return
    addSaving.value = true
    try {
      await apiAddParticipant({
        confId: conferenceId.value as number,
        name: addForm.value.name.trim(),
        email: addForm.value.email.trim().toLowerCase(),
        institution: addForm.value.institution.trim() || undefined,
        role: addForm.value.role || undefined,
      })
      ElMessage.success('参会者已添加')
      addDialogVisible.value = false
      participantPage.value = 1
      await loadParticipants()
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '添加参会者失败')
    } finally {
      addSaving.value = false
    }
  })
}

onMounted(loadData)

// 页面加载时若存在仍在运行的发现任务，恢复进度展示与轮询
onMounted(async () => {
  if (!conferenceId.value) return
  try {
    const latest = await apiGetLatestAuthorDiscoveryJob(conferenceId.value as number)
    if (latest) {
      discoveryLastJob.value = latest
      if (['PENDING', 'RUNNING'].includes(latest.status || '')) {
        startDiscoveryPolling(latest)
      }
    }
  } catch {
    // 无历史任务或加载失败时静默忽略
  }
})

onBeforeUnmount(stopDiscoveryPolling)
</script>

<template>
  <div class="user-mgmt-page" v-loading="loading">
    <header class="top-bar">
      <div class="title-row">
        <el-button :icon="ArrowLeft" text @click="handleBack">返回控制台</el-button>
        <div>
          <p class="eyebrow">Conference Console</p>
          <h1>会议用户管理</h1>
          <span>{{ conferenceTitle }}</span>
        </div>
      </div>
      <div class="headline-actions">
        <div class="headline-card">
          <el-icon><UserFilled /></el-icon>
          <div>
            <strong>{{ committeeCount }}</strong>
            <p>委员会成员</p>
          </div>
        </div>
        <div class="headline-card">
          <el-icon><UserFilled /></el-icon>
          <div>
            <strong>{{ participantCount }}</strong>
            <p>参会者</p>
          </div>
        </div>
      </div>
    </header>

    <section class="content-panel">
      <el-tabs v-model="activeTab" class="user-tabs">
        <el-tab-pane label="委员会" name="committee">
          <div class="panel-head">
            <div>
              <h3>按委员会角色管理成员</h3>
              <p>发出邮件邀请不会直接添加成员；受邀人通过邮件确认后才会正式加入。</p>
            </div>
            <el-button :icon="Refresh" :loading="committeeLoading" @click="loadCommittee">刷新状态</el-button>
          </div>

          <el-alert
            title="邀请闭环：填写受邀人 → 发送邀请邮件 → 对方使用被邀请邮箱登录并同意 → 正式成为委员会成员"
            type="info"
            :closable="false"
            show-icon
            class="committee-alert"
          />

          <div class="committee-groups" v-loading="committeeLoading">
            <section v-for="group in committeeGroups" :key="group.key" class="committee-group">
              <div class="committee-group-head">
                <div>
                  <span>{{ group.key.replace(/_/g, ' ') }}</span>
                  <h4>{{ group.name }}</h4>
                </div>
                <small>{{ group.roles.length }} 个角色</small>
              </div>

              <div class="role-list">
                <article v-for="role in group.roles" :key="role.roleCode" class="role-card">
                  <div class="role-card-head">
                    <div class="role-title">
                      <span class="role-icon"><el-icon><UserFilled /></el-icon></span>
                      <div>
                        <div class="role-name-row">
                          <strong>{{ role.roleName }}</strong>
                          <el-tag v-if="role.isRequired" size="small" type="danger" effect="plain">必需角色</el-tag>
                        </div>
                        <p>{{ role.roleDesc || '负责该角色对应的会议事务' }}</p>
                      </div>
                    </div>
                    <el-button :icon="Plus" type="primary" plain @click="openInviteDialog(role)">添加成员</el-button>
                  </div>

                  <div class="role-members">
                    <div v-for="member in role.accepted" :key="member.invitationId" class="member-chip accepted">
                      <span class="member-state"><el-icon><CircleCheck /></el-icon></span>
                      <div><strong>{{ member.inviteeName || member.inviteeEmail }}</strong><small>{{ member.inviteeEmail }} · 已正式加入</small></div>
                      <el-button v-if="member.invitationId > 0" text type="danger" @click="deleteCommitteeInvitation(member)">删除</el-button>
                    </div>
                    <div v-for="invitation in role.pending" :key="invitation.invitationId" class="member-chip pending">
                      <span class="member-state"><el-icon><Message /></el-icon></span>
                      <div>
                        <strong>{{ invitation.inviteeName || invitation.inviteeEmail }}</strong>
                        <small>{{ invitation.inviteeEmail }}</small>
                      </div>
                      <el-tag :type="getInvitationStatusType(invitation.invitationStatus)" size="small" effect="plain">
                        {{ getInvitationStatusLabel(invitation.invitationStatus) }}
                      </el-tag>
                      <el-button
                        v-if="['DRAFT', 'SENT', 'EXPIRED'].includes(invitation.invitationStatus)"
                        text
                        type="primary"
                        :loading="inviteSendingId === invitation.invitationId"
                        @click="resendCommitteeInvitation(invitation)"
                      >
                        {{ invitation.invitationStatus === 'DRAFT' ? '发送' : '重发' }}
                      </el-button>
                      <el-button text type="danger" @click="deleteCommitteeInvitation(invitation)">删除</el-button>
                    </div>
                    <div v-if="role.accepted.length === 0 && role.pending.length === 0" class="role-empty">
                      暂无成员或待处理邀请
                    </div>
                  </div>
                </article>
              </div>
            </section>
          </div>
          <el-empty v-if="!committeeLoading && committeeGroups.length === 0" description="暂无可用委员会角色" />
        </el-tab-pane>

        <el-tab-pane label="参会者" name="participants">
          <div class="lifecycle-panel">
            <div class="lifecycle-heading">
              <div>
                <span>人员转化路径</span>
                <strong>从潜在投稿到实际参会</strong>
              </div>
              <p>系统按投稿、录用、注册与签到节点持续更新人员阶段。</p>
            </div>
            <div class="lifecycle-flow">
              <button
                v-for="(step, index) in lifecycleSteps"
                :key="step.key"
                type="button"
                class="lifecycle-step"
                :class="{ active: roleFilter === step.key }"
                @click="selectRoleFilter(step.key)"
              >
                <span class="step-index">{{ index + 1 }}</span>
                <div>
                  <strong>{{ step.count }}</strong>
                  <span>{{ step.label }}</span>
                  <small>{{ step.hint }}</small>
                </div>
              </button>
            </div>
          </div>

          <div class="panel-head">
            <div>
              <h3>参会者列表</h3>
              <p>支持按姓名、邮箱、单位或角色检索。</p>
            </div>
            <div class="search-row">
              <el-button :icon="MagicStick" type="success" @click="openDiscoveryDialog">发现潜在投稿者</el-button>
              <el-button :icon="Plus" type="primary" @click="openAddDialog">添加参会者</el-button>
              <el-select v-model="roleFilter" style="width: 150px" aria-label="人员阶段筛选" @change="participantPage = 1">
                <el-option label="全部阶段" value="ALL" />
                <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input
                v-model="participantKeyword"
                clearable
                placeholder="搜索参会者"
                class="search-input"
                @keyup.enter="handleSearch"
              />
              <el-button :icon="Search" type="primary" @click="handleSearch">搜索</el-button>
              <el-button @click="handleReset">重置</el-button>
            </div>
          </div>

          <el-table :data="participants" border class="table" v-loading="participantLoading">
            <el-table-column prop="name" label="姓名" min-width="120" />
            <el-table-column prop="email" label="邮箱" min-width="220" />
            <el-table-column prop="institution" label="单位" min-width="160" />
            <el-table-column label="当前阶段" min-width="140">
              <template #default="scope">
                <el-tag :type="getParticipantRoleType(scope.row.role)" effect="light" round>
                  {{ getParticipantRoleLabel(scope.row.role) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="加入时间" min-width="160">
              <template #default="scope">
                {{ formatDateTime(scope.row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="220" fixed="right">
              <template #default="scope">
                <el-button :icon="EditPen" link @click="openEditDialog(scope.row)">编辑</el-button>
                <el-button
                  v-if="getNextLifecycleRole(scope.row.role)"
                  :icon="Promotion"
                  type="primary"
                  link
                  :loading="participantUpdatingId === scope.row.id"
                  @click="promoteParticipant(scope.row)"
                >
                  升级为{{ getParticipantRoleLabel(getNextLifecycleRole(scope.row.role)) }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!participantLoading && participants.length === 0" description="暂无参会者" />

          <div class="pager">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next, jumper"
              :total="participantTotalDisplay"
              :page-size="participantSize"
              :current-page="participantPage"
              :page-sizes="[10, 20, 50, 100]"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="inviteDialogVisible" :title="`邀请${selectedInviteRole?.roleName || '委员会成员'}`" width="540px">
      <el-alert
        :title="`邀请发出后，对方需要使用受邀邮箱登录并确认；确认前不会计入${selectedInviteRole?.roleName || '该角色'}成员。`"
        type="warning"
        :closable="false"
        show-icon
        class="invite-dialog-alert"
      />
      <el-form ref="inviteFormRef" :model="inviteForm" :rules="inviteRules" label-width="88px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="inviteForm.name" placeholder="受邀人姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="inviteForm.email" placeholder="name@university.edu" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="inviteForm.affiliation" placeholder="学校或研究机构（可选）" />
        </el-form-item>
        <el-form-item label="邀请角色">
          <el-input :model-value="selectedInviteRole?.roleName" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteDialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Message" :loading="inviteSaving" @click="submitCommitteeInvitation">创建并发送邀请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addDialogVisible" title="添加参会者" width="520px">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="88px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="addForm.name" placeholder="参会者姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="addForm.email" placeholder="name@example.com" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="addForm.institution" placeholder="可选" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="addForm.role" placeholder="选择角色" style="width: 100%">
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSaving" @click="handleAddParticipant">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="编辑人员与会议阶段" width="520px">
      <el-form :model="editForm" label-width="88px">
        <el-form-item label="姓名" required>
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="邮箱" required>
          <el-input v-model="editForm.email" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="editForm.institution" />
        </el-form-item>
        <el-form-item label="当前阶段">
          <el-select v-model="editForm.role" style="width: 100%">
            <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleSaveParticipant">保存变更</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="discoveryDialogVisible"
      title="潜在投稿者自动发现"
      width="860px"
      class="discovery-dialog"
    >
      <el-alert
        title="系统将分页检索多个主题关键词，优先通信作者，并从公开论文页面和PDF提取邮箱。结果默认进入待审核状态，不会自动发送邮件。"
        type="info"
        show-icon
        :closable="false"
        class="discovery-alert"
      />

      <el-form
        ref="discoveryFormRef"
        :model="discoveryForm"
        :rules="discoveryRules"
        label-width="112px"
        class="discovery-form"
      >
        <el-form-item label="主题关键词" prop="topicKeywords">
          <div class="topic-list">
            <div
              v-for="(_topic, index) in discoveryForm.topicKeywords"
              :key="index"
              class="topic-row"
            >
              <el-input
                v-model="discoveryForm.topicKeywords[index]"
                placeholder="例如：Artificial Intelligence in Education"
                clearable
              />
              <el-button @click="removeTopicKeyword(index)">删除</el-button>
            </div>
            <el-button :icon="Plus" plain @click="addTopicKeyword">添加主题</el-button>
          </div>
        </el-form-item>

        <div class="discovery-grid">
          <el-form-item label="起始年份" prop="yearFrom">
            <el-input-number
              v-model="discoveryForm.yearFrom"
              :min="1900"
              :max="discoveryForm.yearTo"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="结束年份" prop="yearTo">
            <el-input-number
              v-model="discoveryForm.yearTo"
              :min="discoveryForm.yearFrom"
              :max="new Date().getFullYear()"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="最大候选数" prop="maxAuthors">
            <el-input-number
              v-model="discoveryForm.maxAuthors"
              :min="1"
              :max="500"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <div class="discovery-options">
          <el-switch
            v-model="discoveryForm.enableCrossref"
            active-text="补充 Crossref"
          />
          <el-switch
            v-model="discoveryForm.enableEmailExtraction"
            active-text="提取公开邮箱"
          />
        </div>

        <div class="score-rules">
          <h4>评分说明</h4>
          <p>
            综合得分 = 主题相关性 × 55% + 邮箱可信度 × 30% + 近五年论文活跃度 × 10% + 引用或论文数量 × 5%。
          </p>
          <p>
            邮箱可信度按来源和身份匹配计算：PDF 首页且为通信作者约 95%，PDF 首页且作者姓名匹配约 85%，开放获取页面匹配作者或机构约 80%，普通论文落地页匹配机构约 65%，仅发现邮箱但身份依据不足约 30%。
          </p>
        </div>
      </el-form>

      <div v-if="discoveryLastJob" class="job-summary">
        <el-tag :type="discoveryStatusTagType" effect="plain">{{ discoveryStatusText }}</el-tag>
        <span>任务 ID：{{ discoveryLastJob.jobId }}</span>
        <span>候选作者：{{ discoveryLastJob.totalCandidates ?? 0 }}</span>
        <span>高可信邮箱：{{ discoveryLastJob.highConfidenceEmails ?? 0 }}</span>
        <span v-if="discoveryLastJob.errorMessage" class="job-progress">{{ discoveryLastJob.errorMessage }}</span>
        <el-button
          v-if="['PENDING', 'RUNNING'].includes(discoveryLastJob.status || '')"
          size="small"
          type="danger"
          plain
          :loading="discoveryCancelling"
          @click="handleCancelDiscovery"
        >停止任务</el-button>
      </div>

      <el-table
        v-if="discoveryCandidates.length > 0 || discoveryLoadingCandidates"
        :data="discoveryCandidates"
        border
        class="table discovery-table"
        v-loading="discoveryLoadingCandidates"
        max-height="320"
      >
        <el-table-column type="expand">
          <template #default="scope">
            <div class="author-detail">
              <div>
                <strong>研究关键词</strong>
                <div class="author-keywords">
                  <el-tag v-for="keyword in parseAuthorKeywords(scope.row.researchKeywords)" :key="keyword" size="small" effect="plain">{{ keyword }}</el-tag>
                  <span v-if="parseAuthorKeywords(scope.row.researchKeywords).length === 0">暂无</span>
                </div>
              </div>
              <div>
                <strong>代表论文</strong>
                <ul>
                  <li v-for="paper in parseRepresentativePapers(scope.row.representativePapers)" :key="paper.id || paper.title">
                    {{ paper.title }} <span v-if="paper.publicationYear">({{ paper.publicationYear }})</span>
                  </li>
                </ul>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="authorName" label="作者" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200">
          <template #default="scope">
            {{ scope.row.email || '未提取到可信邮箱' }}
          </template>
        </el-table-column>
        <el-table-column prop="organization" label="机构" min-width="180" />
        <el-table-column prop="countryRegion" label="国家/地区" min-width="100" />
        <el-table-column label="来源" min-width="100">
          <template #default="scope">
            <a v-if="scope.row.sourceUrl" :href="scope.row.sourceUrl" target="_blank" rel="noreferrer">{{ scope.row.sourcePlatform || '学术来源' }}</a>
            <span v-else>{{ scope.row.sourcePlatform || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="综合分" min-width="90">
          <template #default="scope">
            {{ scorePercent(scope.row.overallScore) }}
          </template>
        </el-table-column>
        <el-table-column label="邮箱可信度" min-width="110">
          <template #default="scope">
            {{ scorePercent(scope.row.emailConfidence) }}
          </template>
        </el-table-column>
        <el-table-column label="审核状态" min-width="100">
          <template #default="scope">
            <el-tag type="warning" effect="plain">{{ scope.row.reviewStatus || 'PENDING' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="150" fixed="right">
          <template #default="scope">
            <div class="review-actions">
              <el-button
                type="success"
                size="small"
                :loading="discoveryReviewingId === scope.row.id"
                @click="handleReviewPotentialAuthor(scope.row, true)"
              >
                通过
              </el-button>
              <el-button
                type="danger"
                size="small"
                plain
                :loading="discoveryReviewingId === scope.row.id"
                @click="handleReviewPotentialAuthor(scope.row, false)"
              >
                拒绝
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="discoveryDialogVisible = false">关闭</el-button>
        <el-button :loading="discoveryLoadingCandidates" @click="loadPotentialAuthors">刷新候选</el-button>
        <el-button
          type="success"
          :icon="MagicStick"
          :loading="discoveryRunning"
          @click="handleRunAuthorDiscovery"
        >
          开始发现
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-mgmt-page {
  min-height: 100vh;
  padding: 24px 28px 48px;
  background: radial-gradient(circle at top left, #fff4e6 0%, #f8f9ff 40%, #eef6ff 100%);
  color: #1a2033;
  font-family: "Sora", "Noto Sans SC", sans-serif;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.title-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.title-row h1 {
  margin: 6px 0 4px;
  font-size: 28px;
  letter-spacing: 0.4px;
}

.title-row span {
  color: #5b647a;
  font-size: 14px;
}

.eyebrow {
  text-transform: uppercase;
  letter-spacing: 2px;
  font-size: 11px;
  color: #9aa4b2;
  margin: 0;
}

.headline-actions {
  display: grid;
  grid-auto-flow: column;
  gap: 12px;
}

.headline-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 12px 30px rgba(23, 32, 62, 0.08);
  backdrop-filter: blur(6px);
}

.headline-card strong {
  font-size: 20px;
}

.headline-card p {
  margin: 0;
  font-size: 12px;
  color: #637083;
}

.content-panel {
  background: #ffffff;
  border-radius: 18px;
  padding: 16px 18px 24px;
  box-shadow: 0 18px 40px rgba(23, 32, 62, 0.08);
  animation: riseIn 320ms ease-out;
}

.committee-alert {
  margin-bottom: 18px;
}

.committee-groups {
  display: grid;
  gap: 20px;
}

.committee-group {
  overflow: hidden;
  border: 1px solid #e3e8f1;
  border-radius: 16px;
  background: #f8faff;
}

.committee-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 18px;
  border-bottom: 1px solid #e3e8f1;
  background: linear-gradient(100deg, #f3f7ff, #fff);
}

.committee-group-head span {
  color: #7a89a5;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .1em;
}

.committee-group-head h4 {
  margin: 3px 0 0;
  font-size: 16px;
}

.committee-group-head small {
  color: #7d889a;
}

.role-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 14px;
}

.role-card {
  padding: 16px;
  border: 1px solid #e1e7f0;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(34, 53, 90, .04);
}

.role-card-head,
.role-title,
.role-name-row,
.member-chip {
  display: flex;
  align-items: center;
}

.role-card-head {
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 13px;
  border-bottom: 1px solid #eef1f5;
}

.role-title {
  min-width: 0;
  gap: 11px;
}

.role-icon {
  display: grid;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 11px;
  color: #3f68ca;
  background: #edf3ff;
}

.role-name-row {
  gap: 8px;
  flex-wrap: wrap;
}

.role-title p {
  margin: 4px 0 0;
  color: #7c8799;
  font-size: 11px;
}

.role-members {
  display: grid;
  gap: 8px;
  padding-top: 12px;
}

.member-chip {
  min-width: 0;
  gap: 9px;
  padding: 10px;
  border: 1px solid #e9edf3;
  border-radius: 10px;
  background: #fafbfd;
}

.member-chip.accepted {
  border-color: #d5eee4;
  background: #f4fbf8;
}

.member-state {
  display: grid;
  flex: 0 0 28px;
  height: 28px;
  place-items: center;
  border-radius: 8px;
  color: #3f6bc9;
  background: #edf3ff;
}

.accepted .member-state {
  color: #23805d;
  background: #e3f6ee;
}

.member-chip > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.member-chip strong,
.member-chip small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-chip strong {
  font-size: 13px;
}

.member-chip small {
  margin-top: 3px;
  color: #7e8898;
  font-size: 11px;
}

.role-empty {
  padding: 12px;
  border: 1px dashed #dce2ec;
  border-radius: 9px;
  color: #9aa3b2;
  font-size: 12px;
  text-align: center;
}

.invite-dialog-alert {
  margin-bottom: 18px;
}

.lifecycle-panel {
  margin: 2px 0 22px;
  padding: 20px;
  border: 1px solid #e4eaf2;
  border-radius: 16px;
  background: linear-gradient(135deg, #f8fbff 0%, #fffdf8 100%);
}

.lifecycle-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.lifecycle-heading div,
.lifecycle-step div {
  display: flex;
  flex-direction: column;
}

.lifecycle-heading span {
  color: #6b7589;
  font-size: 12px;
}

.lifecycle-heading strong {
  margin-top: 3px;
  font-size: 17px;
}

.lifecycle-heading p {
  margin: 0;
  color: #7a8495;
  font-size: 12px;
}

.lifecycle-flow {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 12px;
}

.lifecycle-step {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 15px;
  border: 1px solid #e4eaf2;
  border-radius: 13px;
  color: #233047;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: 180ms ease;
}

.lifecycle-step:not(:last-child)::after {
  content: '›';
  position: absolute;
  right: -10px;
  z-index: 2;
  color: #a8b4c6;
  font-size: 24px;
}

.lifecycle-step:hover,
.lifecycle-step.active {
  border-color: #4779ee;
  box-shadow: 0 10px 26px rgba(71, 121, 238, 0.12);
  transform: translateY(-2px);
}

.step-index {
  display: grid;
  flex: 0 0 29px;
  width: 29px;
  height: 29px;
  place-items: center;
  border-radius: 9px;
  color: #315cc6;
  background: #edf3ff;
  font-size: 12px;
  font-weight: 700;
}

.lifecycle-step strong {
  font-size: 22px;
  line-height: 1;
}

.lifecycle-step div > span {
  margin-top: 5px;
  font-size: 13px;
  font-weight: 600;
}

.lifecycle-step small {
  margin-top: 2px;
  color: #8b95a5;
  font-size: 11px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.panel-head h3 {
  margin: 0 0 4px;
  font-size: 18px;
}

.panel-head p {
  margin: 0;
  color: #6b7589;
  font-size: 13px;
}

.search-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.search-input {
  min-width: 220px;
}

.table {
  border-radius: 12px;
  overflow: hidden;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.discovery-alert {
  margin-bottom: 16px;
}

.discovery-form {
  margin-top: 4px;
}

.topic-list {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 10px;
}

.topic-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.discovery-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.discovery-options {
  display: flex;
  gap: 24px;
  padding-left: 112px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.score-rules {
  margin: 4px 0 14px 112px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f7f9fc;
  border: 1px solid #e4e9f2;
  color: #526071;
  font-size: 13px;
  line-height: 1.6;
}

.score-rules h4 {
  margin: 0 0 6px;
  color: #1f2d3d;
  font-size: 14px;
}

.score-rules p {
  margin: 4px 0;
}

.job-summary {
  display: flex;
  gap: 14px;
  align-items: center;
  flex-wrap: wrap;
  padding: 12px 14px;
  margin: 8px 0 14px;
  border-radius: 8px;
  background: #f6fbf7;
  color: #315442;
  font-size: 13px;
}

.job-progress {
  flex-basis: 100%;
  color: #6b8a78;
  font-size: 12px;
}

.discovery-table {
  margin-top: 12px;
}

.review-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: nowrap;
}

.author-detail {
  display: grid;
  gap: 16px;
  padding: 14px 34px;
  color: #4d596c;
}

.author-detail strong { display: block; margin-bottom: 8px; color: #253047; }
.author-keywords { display: flex; gap: 6px; flex-wrap: wrap; }
.author-detail ul { margin: 0; padding-left: 18px; line-height: 1.8; }

@keyframes riseIn {
  from {
    opacity: 0.75;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .user-mgmt-page {
    padding: 20px 16px 36px;
  }

  .headline-actions {
    grid-auto-flow: row;
  }

  .lifecycle-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .lifecycle-flow {
    grid-template-columns: 1fr 1fr;
  }

  .role-list {
    grid-template-columns: 1fr;
  }

  .role-card-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .panel-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .search-row {
    width: 100%;
  }

  .search-input {
    flex: 1;
    min-width: 180px;
  }

  .discovery-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .discovery-options {
    padding-left: 0;
  }

  .score-rules {
    margin-left: 0;
  }

  .topic-row {
    grid-template-columns: 1fr;
  }
}
</style>
