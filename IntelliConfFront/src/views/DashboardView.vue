<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Bell, ChatLineRound, EditPen, MagicStick, Message, Setting, User, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, type UploadRequestOptions } from 'element-plus'
import http from '../utils/http'

interface ConferenceInfo {
  id: number
  title: string
  shortName: string
  currentState: string
  setupStatus?: string
  city?: string
  country?: string
  createUser?: string
}

interface StageInfo {
  id: number
  stageCode: string
  stageName: string
  stageOrder: number
  plannedStartTime: string
  plannedEndTime: string
  stageStatus: string
  progress?: number
}

interface TaskInfo {
  taskId: number
  conferenceId: number
  stageId: number
  stageCode: string
  taskCode: string
  taskName: string
  taskDesc?: string
  taskType?: string
  completionType?: string
  principalRole?: string
  principalUserId?: number
  principalName?: string
  plannedStartTime?: string
  plannedEndTime?: string
  actualStartTime?: string
  actualEndTime?: string
  taskStatus?: string
  priority?: string
  riskLevel?: string
  isCore?: number
  needReview?: number
  sortOrder?: number
  completionDesc?: string
  completionUrl?: string
  completedBy?: number
  completedByName?: string
  submittedAt?: string
  createdAt?: string
  updatedAt?: string
}

interface TaskAttachment {
  attachmentId: number
  fileName: string
  fileUrl: string
  fileType?: string
  fileSize?: number
  uploadedBy?: number
  uploadedByName?: string
  uploadedAt?: string
  processStatus?: string
  processType?: string
  relatedBatchId?: number
  processMessage?: string
  processResult?: string
  totalCount?: number
  successCount?: number
  failedCount?: number
  duplicateCount?: number
}

interface MissingRole {
  roleCode: string
  roleName: string
}

interface TaskAssigneeCandidate {
  userId: number
  memberName: string
  memberEmail: string
  roleCode: string
  roleName: string
  committeeType?: string
  committeeName?: string
}

interface TaskSystemCheck {
  taskCode: string
  checkType: string
  canComplete: boolean
  requiredRoleCount: number
  acceptedRequiredRoleCount: number
  missingRequiredRoles: MissingRole[]
  message?: string
}

const router = useRouter()
const route = useRoute()
const confId = computed(() => String(route.query.confId || ''))

const conferenceInfo = ref<ConferenceInfo | null>(null)
const stages = ref<StageInfo[]>([])
const tasks = ref<TaskInfo[]>([])
const loading = ref(false)
const selectedStageId = ref<number | null>(null)
const taskDrawerVisible = ref(false)
const taskDetailLoading = ref(false)
const attachmentLoading = ref(false)
const completingTask = ref(false)
const activeTask = ref<TaskInfo | null>(null)
const taskAttachments = ref<TaskAttachment[]>([])
const taskSystemCheck = ref<TaskSystemCheck | null>(null)
const assigneeCandidates = ref<TaskAssigneeCandidate[]>([])
const selectedAssigneeUserId = ref<number | null>(null)
const assigningTask = ref(false)
const canManageAssignments = ref(false)
const completionDesc = ref('')
const completionUrl = ref('')

const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
  PREPARING: { label: '筹备中', type: 'info' },
  LIVE: { label: '进行中', type: 'success' },
  CONCLUDING: { label: '收尾中', type: 'warning' },
  ARCHIVED: { label: '已归档', type: 'info' },
}

const now = ref(Date.now())
let clockTimer: ReturnType<typeof setInterval> | undefined
const taskSection = ref<HTMLElement>()
const stageStrip = ref<HTMLElement>()
const taskKeyword = ref('')
const assigneeFilter = ref('')
type TaskFilter = 'CURRENT' | 'OVERDUE' | 'UNASSIGNED' | 'FUTURE' | 'COMPLETED' | 'ALL'
const taskFilter = ref<TaskFilter>('CURRENT')
const filterLabels: Record<TaskFilter, string> = {
  CURRENT: '当前待办', OVERDUE: '逾期事务', UNASSIGNED: '待指派', FUTURE: '后续计划', COMPLETED: '已完成', ALL: '全部事务',
}
const roleLabels: Record<string, string> = {
  GENERAL_CHAIR: '大会主席', PROGRAM_CHAIR: '程序委员会主席', PUBLICATION_CHAIR: '出版主席',
  PUBLICITY_CHAIR: '宣传主席', SECRETARY: '会议秘书', SECRETARIAT: '会议秘书',
  REGISTRATION_CHAIR: '注册主席', FINANCE_OFFICER: '财务负责人', LOCAL_CHAIR: '会务主席',
  PROGRAM_COMMITTEE_MEMBER: '程序委员会委员', ADVISORY_MEMBER: '顾问委员会委员', ORGANIZER: '会议组织者',
}
function roleLabel(value?: string) { return value ? roleLabels[value] || value : '待确定角色' }
function completionLabel(value?: string) {
  return ({ MANUAL_CONFIRM: '人工确认', FILE_UPLOAD: '提交附件', SYSTEM_CHECK: '系统检查' } as Record<string, string>)[value || ''] || '人工确认'
}
function timestamp(value?: string) {
  if (!value) return NaN
  return new Date(value.replace(' ', 'T')).getTime()
}
function isOpen(task: TaskInfo) { return !['COMPLETED', 'CANCELLED'].includes(task.taskStatus || '') }
function isOverdue(task: TaskInfo) { return isOpen(task) && timestamp(task.plannedEndTime) < now.value }
function isFuture(task: TaskInfo) {
  return isOpen(task) && !['IN_PROGRESS', 'PROCESSING', 'REJECTED', 'FAILED'].includes(task.taskStatus || '')
    && timestamp(task.plannedStartTime) > now.value
}
const activeStages = computed(() => stages.value.filter(s => s.stageStatus === 'IN_PROGRESS'))
const stageSummary = computed(() => activeStages.value.map(s => s.stageName).join('、')
  || (stages.value.length && stages.value.every(s => ['COMPLETED', 'CANCELLED'].includes(s.stageStatus))
    ? '全部阶段已结束' : '暂无进行中的阶段'))
const selectedStage = computed(() => stages.value.find(s => s.id === selectedStageId.value) || null)
const allCompletedTaskCount = computed(() => tasks.value.filter(t => t.taskStatus === 'COMPLETED').length)
const currentTaskCount = computed(() => tasks.value.filter(t => isOpen(t) && !isFuture(t)).length)
const futureTaskCount = computed(() => tasks.value.filter(isFuture).length)
const unassignedTaskCount = computed(() => tasks.value.filter(t => isOpen(t) && !t.principalUserId).length)
const overdueTaskCount = computed(() => tasks.value.filter(isOverdue).length)
const overallProgress = computed(() => {
  const count = tasks.value.filter(t => t.taskStatus !== 'CANCELLED').length
  return count ? Math.round(allCompletedTaskCount.value / count * 100) : 0
})
const nextDueTask = computed(() => tasks.value.filter(t => isOpen(t) && timestamp(t.plannedEndTime) >= now.value)
  .slice().sort((a, b) => timestamp(a.plannedEndTime) - timestamp(b.plannedEndTime))[0] || null)
const oldestOverdueTask = computed(() => tasks.value.filter(isOverdue)
  .slice().sort((a, b) => timestamp(a.plannedEndTime) - timestamp(b.plannedEndTime))[0] || null)
const assigneeOptions = computed(() => Array.from(new Map(tasks.value.filter(t => t.principalUserId)
  .map(t => [String(t.principalUserId), { value: String(t.principalUserId), label: t.principalName || '未命名负责人' }])).values()))
const canAssignFromList = computed(() => String(conferenceInfo.value?.createUser || '') === localStorage.getItem('username'))
const visibleTasks = computed(() => tasks.value.filter(t => {
  if (selectedStageId.value != null && t.stageId !== selectedStageId.value) return false
  if (assigneeFilter.value === 'UNASSIGNED' ? !!t.principalUserId
    : assigneeFilter.value && String(t.principalUserId) !== assigneeFilter.value) return false
  const keyword = taskKeyword.value.trim().toLowerCase()
  if (keyword && ![t.taskName, t.taskDesc, t.principalName, roleLabel(t.principalRole)]
    .join(' ').toLowerCase().includes(keyword)) return false
  switch (taskFilter.value) {
    case 'CURRENT': return isOpen(t) && !isFuture(t)
    case 'OVERDUE': return isOverdue(t)
    case 'UNASSIGNED': return isOpen(t) && !t.principalUserId
    case 'FUTURE': return isFuture(t)
    case 'COMPLETED': return t.taskStatus === 'COMPLETED'
    default: return true
  }
}).slice().sort((a, b) => Number(isOverdue(b)) - Number(isOverdue(a))
  || Number(!isOpen(a)) - Number(!isOpen(b))
  || (Number.isFinite(timestamp(a.plannedEndTime)) ? timestamp(a.plannedEndTime) : Infinity)
    - (Number.isFinite(timestamp(b.plannedEndTime)) ? timestamp(b.plannedEndTime) : Infinity)
  || (a.sortOrder || 0) - (b.sortOrder || 0)))
const moduleCards = [
  { key: 'mail', title: '邮件中心', desc: '审核邮件、查看发送结果', icon: Bell, tone: 'violet' },
  { key: 'discovery', title: '投稿者发现', desc: '检索学者与公开邮箱', icon: MagicStick, tone: 'orange' },
  { key: 'people', title: '人员中心', desc: '委员会与参会人员', icon: UserFilled, tone: 'green' },
]
const taskActivityLogs = computed(() => tasks.value
  .filter(t => ['COMPLETED', 'FAILED', 'REJECTED', 'CANCELLED'].includes(t.taskStatus || ''))
  .slice().sort((a, b) => (timestamp(b.actualEndTime || b.updatedAt) || 0) - (timestamp(a.actualEndTime || a.updatedAt) || 0))
  .slice(0, 6).map(t => ({ id: t.taskId, title: t.taskName, status: t.taskStatus,
    time: t.actualEndTime || t.updatedAt, stageName: stages.value.find(s => s.id === t.stageId)?.stageName })))
function applyTaskFilter(filter: TaskFilter) {
  taskFilter.value = filter
  selectedStageId.value = null
  taskKeyword.value = ''
  assigneeFilter.value = ''
  taskSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function resetTaskFilters() { applyTaskFilter('ALL') }
function goMailReview() {
  router.push({ path: '/mail-workflow', query: { confId: confId.value, filter: 'WAITING_REVIEW' } })
}
function stageTaskCount(id: number) { return tasks.value.filter(t => t.stageId === id && isOpen(t)).length }
async function revealCurrentStage() {
  await nextTick()
  const current = stageStrip.value?.querySelector<HTMLElement>('.stage-card.active')
  if (current && stageStrip.value) stageStrip.value.scrollLeft = Math.max(0, current.offsetLeft - stageStrip.value.offsetLeft - 16)
}

function isSuccess(resp: any) {
  return resp?.success || resp?.code === '0'
}

function getStatusInfo(status?: string) {
  return statusMap[status || ''] || { label: status || 'Unknown', type: 'info' as const }
}

function getStageStatusLabel(status: string) {
  if (status === 'IN_PROGRESS') return '进行中'
  if (status === 'COMPLETED') return '已完成'
  if (status === 'DELAYED') return '已延期'
  if (status === 'CANCELLED') return '已取消'
  return '计划中'
}

function getTaskStatusLabel(status?: string) {
  if (status === 'COMPLETED') return '已完成'
  if (status === 'FAILED') return '失败'
  if (status === 'REJECTED') return '已驳回'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'IN_PROGRESS' || status === 'PROCESSING') return '处理中'
  return '待开始'
}

function getTaskStatusType(status?: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'REJECTED' || status === 'CANCELLED') return 'danger'
  if (status === 'IN_PROGRESS' || status === 'PROCESSING') return 'warning'
  return 'info'
}

function getStageStepStatus(stage: StageInfo) {
  if (stage.stageStatus === 'COMPLETED') return 'success'
  if (stage.stageStatus === 'IN_PROGRESS') return 'process'
  return 'wait'
}

function isStageSelected(stage: StageInfo) {
  return selectedStageId.value === stage.id
}

function formatDate(value?: string) {
  if (!value) return '待设置'
  return value.slice(0, 10)
}

function formatDateTime(value?: string) {
  if (!value) return '暂无'
  return value.replace('T', ' ').slice(0, 19)
}

function getAttachmentProcessLabel(status?: string) {
  if (status === 'SUCCESS') return 'Success'
  if (status === 'PARTIAL_SUCCESS') return 'Partial Success'
  if (status === 'FAILED') return 'Failed'
  if (status === 'UNPROCESSED') return 'Unprocessed'
  return status || 'Unknown'
}

function getAttachmentProcessType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PARTIAL_SUCCESS') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

async function loadDashboard() {
  if (!confId.value) {
    ElMessage.error('Missing conference id')
    router.replace('/portal')
    return
  }

  loading.value = true
  try {
    const conferenceResp = await http.get(`/api/intelli-conf/v1/conference/${confId.value}`)

    if (!isSuccess(conferenceResp.data)) {
      ElMessage.error(conferenceResp.data?.message || 'Failed to load conference info')
      return
    }

    conferenceInfo.value = conferenceResp.data.data
    stages.value = []
    tasks.value = []
    selectedStageId.value = null
    taskFilter.value = 'CURRENT'
    taskKeyword.value = ''
    assigneeFilter.value = ''

    if (conferenceInfo.value?.setupStatus === 'STAGE_GENERATED' || conferenceInfo.value?.setupStatus === 'TASK_GENERATED') {
      const stageResp = await http.get(`/api/intelli-conf/v1/conference/${confId.value}/stages`)
      if (isSuccess(stageResp.data)) {
        stages.value = stageResp.data.data || []
        void revealCurrentStage()
      }
    }

    if (conferenceInfo.value?.setupStatus === 'TASK_GENERATED') {
      const taskResp = await http.get(`/api/conferences/${conferenceInfo.value.id}/tasks/generated`)
      if (isSuccess(taskResp.data)) {
        tasks.value = taskResp.data.data || []
      }
    }
    void loadReviewCount()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to load dashboard')
  } finally {
    loading.value = false
  }
}

/** 首页审核中心徽标：待审核邮件数量（潜在投稿者审核不计入） */
const pendingReviewCount = ref<number | null>(null)
async function loadReviewCount() {
  if (!conferenceInfo.value?.id) return
  try {
    const resp = await http.get(`/api/conferences/${conferenceInfo.value.id}/review/pending-count`)
    const data = resp.data?.data
    pendingReviewCount.value = isSuccess(resp.data) && typeof data?.mailReviewCount === 'number' ? data.mailReviewCount : null
  } catch {
    pendingReviewCount.value = null
  }
}

async function loadGeneratedTasks() {
  if (!conferenceInfo.value?.id) return
  const taskResp = await http.get(`/api/conferences/${conferenceInfo.value.id}/tasks/generated`)
  if (isSuccess(taskResp.data)) {
    tasks.value = taskResp.data.data || []
  }
}

async function openTaskDetail(task: TaskInfo, assignment = false) {
  if (!assignment && task.taskCode === 'CONFIRM_CONFERENCE_COMMITTEE') {
    router.push(`/conference-committee-init?confId=${encodeURIComponent(confId.value)}&taskId=${task.taskId}`)
    return
  }
  if (!conferenceInfo.value?.id) return
  activeTask.value = null
  taskDrawerVisible.value = true
  taskDetailLoading.value = true
  completionDesc.value = task.completionDesc || ''
  completionUrl.value = task.completionUrl || ''
  taskAttachments.value = []
  taskSystemCheck.value = null
  assigneeCandidates.value = []
  selectedAssigneeUserId.value = null
  canManageAssignments.value = false
  try {
    const detailResp = await http.get(`/api/conferences/${conferenceInfo.value.id}/tasks/${task.taskId}`)
    activeTask.value = detailResp.data.data || task
    completionDesc.value = activeTask.value?.completionDesc || ''
    completionUrl.value = activeTask.value?.completionUrl || ''
    selectedAssigneeUserId.value = activeTask.value?.principalUserId || null
    await Promise.all([
      loadTaskAttachments(),
      loadTaskSystemCheck(),
      loadAssigneeCandidates(),
    ])
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to load task detail')
  } finally {
    taskDetailLoading.value = false
  }
}

async function loadTaskAttachments() {
  if (!conferenceInfo.value?.id || !activeTask.value?.taskId) return
  const response = await http.get(`/api/conferences/${conferenceInfo.value.id}/tasks/${activeTask.value.taskId}/attachments`)
  taskAttachments.value = response.data.data || []
}

async function loadTaskSystemCheck() {
  if (!conferenceInfo.value?.id || !activeTask.value?.taskId || activeTask.value.completionType !== 'SYSTEM_CHECK') return
  const response = await http.get(`/api/conferences/${conferenceInfo.value.id}/tasks/${activeTask.value.taskId}/system-check`)
  taskSystemCheck.value = response.data.data || null
}

async function loadAssigneeCandidates() {
  if (!conferenceInfo.value?.id || !activeTask.value?.taskId) return
  try {
    const response = await http.get(`/api/conferences/${conferenceInfo.value.id}/tasks/${activeTask.value.taskId}/assignee-candidates`)
    assigneeCandidates.value = response.data.data || []
    canManageAssignments.value = true
  } catch {
    assigneeCandidates.value = []
    canManageAssignments.value = false
  }
}

async function uploadTaskAttachment(options: UploadRequestOptions) {
  if (!conferenceInfo.value?.id || !activeTask.value?.taskId) return
  attachmentLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', options.file)
    await http.post(`/api/conferences/${conferenceInfo.value.id}/tasks/${activeTask.value.taskId}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    ElMessage.success('Attachment uploaded')
    await loadTaskAttachments()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to upload attachment')
  } finally {
    attachmentLoading.value = false
  }
}

async function deleteTaskAttachment(attachmentId: number) {
  if (!conferenceInfo.value?.id || !activeTask.value?.taskId) return
  try {
    await http.delete(`/api/conferences/${conferenceInfo.value.id}/tasks/${activeTask.value.taskId}/attachments/${attachmentId}`)
    ElMessage.success('Attachment removed')
    await loadTaskAttachments()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to remove attachment')
  }
}

async function completeCurrentTask() {
  if (!conferenceInfo.value?.id || !activeTask.value?.taskId) return
  completingTask.value = true
  try {
    const response = await http.post(`/api/conferences/${conferenceInfo.value.id}/tasks/${activeTask.value.taskId}/complete`, {
      completionDesc: completionDesc.value,
      completionUrl: completionUrl.value,
    })
    const result = response.data?.data
    if (result) {
      tasks.value = tasks.value.map(item => {
        if (item.taskId !== activeTask.value?.taskId) {
          return item
        }
        return {
          ...item,
          taskStatus: result.taskStatus || 'COMPLETED',
          completionDesc: result.completionDesc,
          actualEndTime: result.actualEndTime,
          submittedAt: result.submittedAt,
          completedBy: result.completedBy,
          completedByName: result.completedByName,
        }
      })
      activeTask.value = {
        ...activeTask.value,
        taskStatus: result.taskStatus || 'COMPLETED',
        completionDesc: result.completionDesc,
        completionUrl: completionUrl.value,
        actualEndTime: result.actualEndTime,
        submittedAt: result.submittedAt,
        completedBy: result.completedBy,
        completedByName: result.completedByName,
      }
      if (result.stageId && result.stageProgress != null) {
        stages.value = stages.value.map(stage => stage.id === result.stageId
          ? { ...stage, progress: Number(result.stageProgress) }
          : stage
        )
      }
    }
    ElMessage.success('Task completed')
    await loadGeneratedTasks()
    taskDrawerVisible.value = false
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to complete task')
  } finally {
    completingTask.value = false
  }
}

async function assignCurrentTask() {
  if (!conferenceInfo.value?.id || !activeTask.value?.taskId || !selectedAssigneeUserId.value) return
  assigningTask.value = true
  try {
    const response = await http.post(`/api/conferences/${conferenceInfo.value.id}/tasks/${activeTask.value.taskId}/assign`, {
      assigneeUserId: selectedAssigneeUserId.value,
    })
    if (!isSuccess(response.data)) {
      ElMessage.error(response.data?.message || 'Failed to assign task')
      return
    }
    const assignee = assigneeCandidates.value.find(item => item.userId === selectedAssigneeUserId.value)
    if (assignee) {
      tasks.value = tasks.value.map(item => item.taskId === activeTask.value?.taskId
        ? { ...item, principalUserId: assignee.userId, principalName: assignee.memberName }
        : item)
      activeTask.value = {
        ...activeTask.value,
        principalUserId: assignee.userId,
        principalName: assignee.memberName,
      }
    }
    ElMessage.success('Task assigned')
    await loadGeneratedTasks()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Failed to assign task')
  } finally {
    assigningTask.value = false
  }
}

const canCompleteTask = computed(() => {
  if (!activeTask.value) return false
  if (activeTask.value.taskStatus === 'COMPLETED' || activeTask.value.taskStatus === 'CANCELLED') return false
  if (activeTask.value.completionType === 'FILE_UPLOAD') {
    if (activeTask.value.taskCode === 'IMPORT_POTENTIAL_AUTHOR_LIST') {
      return taskAttachments.value.some(item =>
        item.processType === 'POTENTIAL_AUTHOR_IMPORT'
        && ['SUCCESS', 'PARTIAL_SUCCESS'].includes(item.processStatus || '')
        && Number(item.successCount || 0) > 0)
    }
    return taskAttachments.value.length > 0
  }
  if (activeTask.value.completionType === 'SYSTEM_CHECK') return taskSystemCheck.value?.canComplete === true
  return true
})

function goPortal() {
  router.push('/portal')
}

function selectStage(stageId: number) {
  selectedStageId.value = stageId
  taskFilter.value = 'ALL'
  taskKeyword.value = ''
  assigneeFilter.value = ''
  taskSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function goSetup() {
  router.push(`/conference-setup?confId=${encodeURIComponent(confId.value)}`)
}

function goInfo() {
  router.push(`/conference-info?confId=${encodeURIComponent(confId.value)}`)
}

function goMailAccount() {
  router.push(`/conference-mail-account?confId=${encodeURIComponent(confId.value)}`)
}

function goMailCompose() {
  router.push(`/mail-compose?confId=${encodeURIComponent(confId.value)}`)
}

function goMailWorkflow() {
  router.push(`/mail-workflow?confId=${encodeURIComponent(confId.value)}`)
}

function openModule(key: string) {
  if (key === 'mail') return goMailWorkflow()
  if (key === 'discovery') return router.push({ path: '/author-discovery', query: { confId: confId.value } })
  if (key === 'people') return goUserManagement()
  document.querySelector('.timeline-panel')?.scrollIntoView({ behavior: 'smooth' })
}

function goUserManagement() {
  router.push(`/conference-users?confId=${encodeURIComponent(confId.value)}`)
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/auth')
}

watch(() => confId.value, loadDashboard, { immediate: true })
onMounted(() => { clockTimer = setInterval(() => { now.value = Date.now() }, 60000) })
onBeforeUnmount(() => { clearInterval(clockTimer) })
</script>

<template>
  <div class="console-page" v-loading="loading">
    <header class="console-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" text @click="goPortal">我的会议</el-button>
        <div class="title-block">
          <h1 :title="conferenceInfo?.title">{{ conferenceInfo?.shortName || '会议工作台' }} · 工作台</h1>
          <p class="conference-fullname">{{ conferenceInfo?.title }}</p>
          <div class="meta-row">
            <el-tag v-if="conferenceInfo" :type="getStatusInfo(conferenceInfo.currentState).type" size="small">{{ getStatusInfo(conferenceInfo.currentState).label }}</el-tag>
            <span>{{ stageSummary }}</span>
          </div>
        </div>
      </div>
      <div class="header-actions">
        <el-button :icon="Message" type="primary" @click="goMailReview">待审核<span v-if="pendingReviewCount">（{{ pendingReviewCount }}）</span></el-button>
        <el-button :icon="Setting" @click="goSetup">流程设置</el-button>
        <el-dropdown trigger="click">
          <el-button :icon="Setting">更多</el-button>
          <template #dropdown><el-dropdown-menu>
            <el-dropdown-item :icon="EditPen" @click="goInfo">会议信息</el-dropdown-item>
            <el-dropdown-item :icon="Message" @click="goMailAccount">会议邮箱</el-dropdown-item>
            <el-dropdown-item :icon="ChatLineRound" @click="goMailCompose">临时邮件</el-dropdown-item>
            <el-dropdown-item divided :icon="User" @click="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </header>

    <main class="console-main">
      <section class="metric-grid" aria-label="事务快捷筛选">
        <button type="button" class="metric-card" :class="{ selected: taskFilter === 'CURRENT' && selectedStageId === null }" @click="applyTaskFilter('CURRENT')">
          <span>当前待办</span><strong>{{ currentTaskCount }}</strong><small>已到开始时间，含逾期事务 →</small>
        </button>
        <button type="button" class="metric-card danger" :class="{ selected: taskFilter === 'OVERDUE' && selectedStageId === null }" @click="applyTaskFilter('OVERDUE')">
          <span>逾期事务</span><strong>{{ overdueTaskCount }}</strong><small>优先处理超期事项 →</small>
        </button>
        <button type="button" class="metric-card review-pending" @click="goMailReview">
          <span>待审核</span><strong>{{ pendingReviewCount ?? '—' }}</strong><small>{{ pendingReviewCount === null ? '数量暂不可用，进入查看' : '审核后按计划发送' }} →</small>
        </button>
        <button type="button" class="metric-card" :class="{ selected: taskFilter === 'UNASSIGNED' && selectedStageId === null }" @click="applyTaskFilter('UNASSIGNED')">
          <span>待指派</span><strong>{{ unassignedTaskCount }}</strong><small>未确认负责人，含后续计划 →</small>
        </button>
      </section>

      <section class="workspace-grid">
        <section ref="taskSection" class="progress-panel task-workspace" aria-label="事务列表">
          <div class="panel-heading">
            <div><h2>{{ selectedStage?.stageName || filterLabels[taskFilter] }}</h2><p class="panel-subtitle">逾期优先，其次按截止时间排列 · 当前可见 {{ visibleTasks.length }} 项</p></div>
            <el-button text @click="loadDashboard">刷新</el-button>
          </div>
          <div class="filter-tabs" aria-label="事务状态">
            <button v-for="(label, key) in filterLabels" :key="key" type="button" :class="{ active: taskFilter === key }" :aria-pressed="taskFilter === key" @click="taskFilter = key">{{ label }}<span v-if="key === 'FUTURE'"> {{ futureTaskCount }}</span></button>
          </div>
          <div class="task-filters">
            <el-input v-model="taskKeyword" clearable placeholder="搜索事务、负责人或角色" aria-label="搜索事务" />
            <el-select v-model="assigneeFilter" placeholder="全部负责人" clearable aria-label="筛选负责人">
              <el-option label="未指派" value="UNASSIGNED" />
              <el-option v-for="person in assigneeOptions" :key="person.value" :label="person.label" :value="person.value" />
            </el-select>
            <el-select v-model="selectedStageId" placeholder="全部阶段" clearable aria-label="筛选阶段" @clear="selectedStageId = null">
              <el-option v-for="stage in stages" :key="stage.id" :label="stage.stageName" :value="stage.id" />
            </el-select>
            <el-button text @click="resetTaskFilters">重置</el-button>
          </div>
          <div class="task-list">
            <article v-for="task in visibleTasks" :key="task.taskId" class="task-row" :class="{ overdue: isOverdue(task), closed: !isOpen(task) }">
              <div class="task-main">
                <div class="task-title-row">
                  <strong>{{ task.taskName }}</strong>
                  <el-tag size="small" :type="getTaskStatusType(task.taskStatus)" effect="light">{{ getTaskStatusLabel(task.taskStatus) }}</el-tag>
                  <el-tag v-if="isOverdue(task)" size="small" type="danger">已逾期</el-tag>
                </div>
                <p>{{ task.taskDesc || '暂无事务说明' }}</p>
                <div class="task-meta">
                  <span class="assignee" :class="{ unassigned: !task.principalUserId }">负责人：{{ task.principalName || '待指派' }}</span>
                  <span>{{ roleLabel(task.principalRole) }}</span>
                  <span :class="{ 'deadline-overdue': isOverdue(task) }">截止 {{ formatDateTime(task.plannedEndTime) }}</span>
                  <span v-if="isFuture(task)">开始 {{ formatDateTime(task.plannedStartTime) }}</span>
                  <span>{{ stages.find(s => s.id === task.stageId)?.stageName }}</span>
                </div>
              </div>
              <div class="task-actions">
                <el-button v-if="canAssignFromList && isOpen(task)" size="small" @click="openTaskDetail(task, true)">{{ task.principalUserId ? '调整指派' : '指派' }}</el-button>
                <el-button :type="isOpen(task) ? 'primary' : 'default'" size="small" @click="openTaskDetail(task)">{{ isOpen(task) ? '处理' : '查看' }}</el-button>
              </div>
            </article>
            <el-empty v-if="!visibleTasks.length" :description="tasks.length ? '当前筛选下没有事务' : '会议任务尚未生成'">
              <el-button v-if="tasks.length" @click="resetTaskFilters">查看全部事务</el-button>
              <el-button v-else type="primary" @click="goSetup">完善会议流程</el-button>
            </el-empty>
          </div>
        </section>

        <aside class="workspace-aside">
          <section class="side-panel">
            <h2>时间提醒</h2>
            <button v-if="nextDueTask" class="deadline-card" type="button" @click="openTaskDetail(nextDueTask)">
              <span>最近截止</span><strong>{{ nextDueTask.taskName }}</strong><time>{{ formatDateTime(nextDueTask.plannedEndTime) }}</time>
            </button>
            <p v-else class="muted">暂无未来截止事项</p>
            <button v-if="oldestOverdueTask" class="deadline-card overdue" type="button" @click="openTaskDetail(oldestOverdueTask)">
              <span>最早逾期</span><strong>{{ oldestOverdueTask.taskName }}</strong><time>{{ formatDateTime(oldestOverdueTask.plannedEndTime) }}</time>
            </button>
          </section>
          <section class="side-panel">
            <div class="panel-heading compact"><h2>全局进度</h2><span>{{ allCompletedTaskCount }}/{{ tasks.filter(t => t.taskStatus !== 'CANCELLED').length }} 项</span></div>
            <el-progress :percentage="overallProgress" color="#315fce" />
            <p class="panel-subtitle">不含已取消事务</p>
          </section>
          <section class="side-panel">
            <h2>功能入口</h2>
            <button v-for="item in moduleCards" :key="item.key" type="button" class="module-card" :class="item.tone" @click="openModule(item.key)">
              <span class="module-icon"><el-icon><component :is="item.icon" /></el-icon></span><div><strong>{{ item.title }}</strong><p>{{ item.desc }}</p></div><i aria-hidden="true">→</i>
            </button>
          </section>
          <section class="side-panel">
            <h2>近期动态</h2>
            <p v-if="!taskActivityLogs.length" class="muted">暂无已结束事务</p>
            <ol v-else class="activity-list"><li v-for="log in taskActivityLogs" :key="log.id">
              <strong>{{ log.title }}</strong><span>{{ getTaskStatusLabel(log.status) }} · {{ formatDateTime(log.time) }}</span>
            </li></ol>
          </section>
        </aside>
      </section>

      <section class="timeline-panel">
        <div class="panel-heading">
          <div><h2>全流程时间轴</h2><p class="panel-subtitle">点击阶段查看对应事务；进行中阶段可并行</p></div>
          <el-button text type="primary" @click="goSetup">调整时间</el-button>
        </div>
        <el-empty v-if="!stages.length" description="尚未生成会议流程阶段" />
        <div v-else ref="stageStrip" class="stage-strip">
          <button v-for="stage in stages" :key="stage.stageCode" type="button" class="stage-card"
            :class="{ selected: isStageSelected(stage), active: stage.stageStatus === 'IN_PROGRESS', completed: stage.stageStatus === 'COMPLETED', delayed: stage.stageStatus === 'DELAYED' }"
            :aria-pressed="isStageSelected(stage)" @click="selectStage(stage.id)">
            <span class="stage-order">{{ stage.stageOrder }} · {{ getStageStatusLabel(stage.stageStatus) }}</span>
            <strong>{{ stage.stageName }}</strong>
            <span class="stage-range">{{ formatDate(stage.plannedStartTime) }} — {{ formatDate(stage.plannedEndTime) }}</span>
            <span>{{ stageTaskCount(stage.id) }} 项未完成</span>
          </button>
        </div>
      </section>
    </main>

    <el-drawer v-model="taskDrawerVisible" size="min(560px, 100vw)" :title="activeTask?.taskName || '事务详情'">
      <div v-loading="taskDetailLoading" class="task-drawer">
        <template v-if="activeTask">
          <div class="drawer-meta">
            <el-tag :type="getTaskStatusType(activeTask.taskStatus)" effect="plain">{{ getTaskStatusLabel(activeTask.taskStatus) }}</el-tag>
            <el-tag effect="plain">{{ completionLabel(activeTask.completionType) }}</el-tag>
          </div>
          <p class="drawer-desc">{{ activeTask.taskDesc || '暂无事务说明' }}</p>
          <div class="drawer-grid">
            <span>建议负责角色：{{ roleLabel(activeTask.principalRole) }}</span>
            <span>计划时间：{{ formatDate(activeTask.plannedStartTime) }} - {{ formatDate(activeTask.plannedEndTime) }}</span>
            <span>完成人：{{ activeTask.completedByName || '尚未完成' }}</span>
            <span>提交时间：{{ formatDateTime(activeTask.submittedAt) }}</span>
          </div>

          <div v-if="canManageAssignments" class="drawer-block">
            <div class="block-head">
              <h3>负责人指派</h3>
            </div>
            <el-select v-model="selectedAssigneeUserId" placeholder="选择匹配该角色的委员会成员" style="width: 100%">
              <el-option
                v-for="candidate in assigneeCandidates"
                :key="candidate.userId"
                :label="`${candidate.memberName} (${candidate.roleName || candidate.roleCode})`"
                :value="candidate.userId"
              />
            </el-select>
            <el-button type="primary" :loading="assigningTask" :disabled="!selectedAssigneeUserId" @click="assignCurrentTask">
              确认指派
            </el-button>
          </div>

          <div v-if="activeTask.completionType === 'FILE_UPLOAD'" class="drawer-block">
            <div class="block-head">
              <h3>任务附件</h3>
              <el-upload :http-request="uploadTaskAttachment" :show-file-list="false">
                <el-button type="primary" :loading="attachmentLoading">上传文件</el-button>
              </el-upload>
            </div>
            <div v-if="taskAttachments.length" class="attachment-list">
              <div v-for="attachment in taskAttachments" :key="attachment.attachmentId" class="attachment-row">
                <div class="attachment-main">
                  <div class="attachment-title-row">
                    <strong>{{ attachment.fileName }}</strong>
                    <el-tag
                      v-if="attachment.processStatus"
                      size="small"
                      :type="getAttachmentProcessType(attachment.processStatus)"
                      effect="plain"
                    >
                      {{ getAttachmentProcessLabel(attachment.processStatus) }}
                    </el-tag>
                  </div>
                  <p>{{ formatDateTime(attachment.uploadedAt) }}</p>
                  <div v-if="attachment.processType" class="attachment-metrics">
                    <span>Type: {{ attachment.processType }}</span>
                    <span v-if="attachment.totalCount != null">Total {{ attachment.totalCount }}</span>
                    <span v-if="attachment.successCount != null">Success {{ attachment.successCount }}</span>
                    <span v-if="attachment.failedCount != null">Failed {{ attachment.failedCount }}</span>
                    <span v-if="attachment.duplicateCount != null">Duplicate {{ attachment.duplicateCount }}</span>
                  </div>
                  <p v-if="attachment.processMessage" class="attachment-message">{{ attachment.processMessage }}</p>
                </div>
                <el-button text type="danger" @click="deleteTaskAttachment(attachment.attachmentId)">删除</el-button>
              </div>
            </div>
            <el-empty v-else description="尚未上传附件" />
          </div>

          <div v-if="activeTask.completionType === 'SYSTEM_CHECK'" class="drawer-block">
            <div class="block-head">
              <h3>系统完成条件</h3>
              <el-button text type="primary" @click="loadTaskSystemCheck">重新检查</el-button>
            </div>
            <div v-if="taskSystemCheck" class="system-check">
              <p>{{ taskSystemCheck.message }}</p>
              <div class="drawer-grid">
                <span>必需角色：{{ taskSystemCheck.requiredRoleCount }}</span>
                <span>已就位角色：{{ taskSystemCheck.acceptedRequiredRoleCount }}</span>
              </div>
              <div v-if="taskSystemCheck.missingRequiredRoles?.length">
                <strong>尚未就位的角色</strong>
                <ul class="missing-role-list">
                  <li v-for="role in taskSystemCheck.missingRequiredRoles" :key="role.roleCode">{{ role.roleName }} ({{ role.roleCode }})</li>
                </ul>
              </div>
            </div>
          </div>

          <div class="drawer-block">
            <h3>完成记录</h3>
            <el-input v-model="completionDesc" type="textarea" :rows="4" placeholder="填写完成说明，便于组织者追踪" />
            <el-input v-model="completionUrl" class="completion-url" placeholder="相关链接（可选）" />
          </div>

          <div class="drawer-actions">
            <el-button @click="taskDrawerVisible = false">关闭</el-button>
            <el-button type="success" :disabled="!canCompleteTask" :loading="completingTask" @click="completeCurrentTask">
              标记为已完成
            </el-button>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.console-page { min-height:100vh; background:#f4f7fb; color:#17243b; font-family:Inter, "Microsoft YaHei", system-ui, sans-serif; }
.console-page *, .console-page *::before, .console-page *::after { box-sizing:border-box; }
.console-header { display:flex; align-items:center; justify-content:space-between; gap:24px; padding:20px 28px; background:#fff; border-bottom:1px solid #e2e8f0; }
.header-left { display:flex; gap:18px; align-items:flex-start; min-width:0; }
.title-block { min-width:0; }
.title-block h1 { margin:0; font-size:22px; letter-spacing:-.02em; }
.conference-fullname { max-width:750px; margin:7px 0; color:#526077; font-size:13px; line-height:1.5; overflow-wrap:anywhere; }
.meta-row { display:flex; align-items:center; gap:8px; color:#526077; font-size:13px; flex-wrap:wrap; }
.header-actions { display:flex; flex-shrink:0; gap:8px; align-items:center; flex-wrap:wrap; }
.header-actions :deep(.el-button + .el-button) { margin-left:0; }
.console-main { max-width:1600px; padding:24px; margin:auto; }
.metric-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:16px; margin-bottom:22px; }
.metric-card { display:flex; flex-direction:column; gap:8px; padding:17px 20px; text-align:left; color:inherit; background:#fff; border:1px solid #dce4ef; border-radius:12px; cursor:pointer; font:inherit; transition:border-color .15s, background .15s; }
.metric-card > span { color:#455671; font-size:14px; font-weight:600; }
.metric-card > strong { font-size:30px; line-height:1.1; font-variant-numeric:tabular-nums; }
.metric-card > small { font-size:12px; color:#596b83; line-height:1.4; }
.metric-card:hover, .metric-card.selected { border-color:#315fce; background:#f0f5ff; }
.metric-card.danger strong { color:#b5323a; }
.metric-card.danger.selected { border-color:#b5323a; background:#fff5f5; }
.metric-card.review-pending > span { color:#b5323a; }
.metric-card.review-pending > strong { color:#b5323a; }
.metric-card.review-pending.selected { border-color:#b5323a; background:#fff5f5; }
button:focus-visible { outline:3px solid #315fce; outline-offset:3px; }
.workspace-grid { display:grid; grid-template-columns:minmax(0,1fr) 310px; gap:22px; align-items:start; margin-bottom:22px; }
.progress-panel, .side-panel, .timeline-panel { background:#fff; border:1px solid #dfe6f0; border-radius:14px; padding:22px; min-width:0; }
.task-workspace { scroll-margin-top:16px; }
.panel-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:16px; }
.panel-heading.compact { margin-bottom:12px; font-size:13px; color:#526077; }
h2 { margin:0; font-size:17px; font-weight:700; }
.panel-subtitle { color:#637087; font-size:13px; margin:6px 0 0; line-height:1.5; }
.filter-tabs { display:flex; gap:5px; flex-wrap:wrap; padding-bottom:14px; }
.filter-tabs button { background:#f4f6fa; border:1px solid transparent; color:#526077; border-radius:7px; padding:8px 11px; font:inherit; font-size:13px; cursor:pointer; }
.filter-tabs button.active { background:#eaf1ff; border-color:#b4c8f5; color:#204eae; font-weight:600; }
.task-filters { display:flex; gap:8px; margin-bottom:16px; flex-wrap:wrap; }
.task-filters > .el-input { flex:1 1 220px; width:auto; }
.task-filters > .el-select { flex:1 1 145px; width:auto; min-width:0; max-width:210px; }
.task-list { display:grid; gap:10px; max-height:590px; overflow-y:auto; overscroll-behavior:contain; padding:3px; }
.task-row { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; padding:16px; border:1px solid #e1e7ef; border-left:3px solid #adc1e6; border-radius:9px; min-width:0; }
.task-row.overdue { border-left-color:#bd3940; background:#fffcfc; }
.task-row.closed { border-left-color:#86a69a; }
.task-main { min-width:0; flex:1; }
.task-title-row { display:flex; align-items:center; flex-wrap:wrap; gap:8px; }
.task-title-row > strong { font-size:15px; line-height:1.5; overflow-wrap:anywhere; }
.task-main > p { color:#59677c; font-size:13px; line-height:1.6; margin:7px 0 9px; }
.task-meta { display:flex; gap:6px 16px; flex-wrap:wrap; font-size:13px; color:#526077; line-height:1.7; }
.task-meta .assignee { color:#243650; font-weight:600; }
.task-meta .unassigned { color:#966300; }
.deadline-overdue { color:#b5323a; font-weight:600; }
.task-actions { display:flex; gap:7px; flex-shrink:0; padding-top:2px; }
.task-actions :deep(.el-button + .el-button) { margin-left:0; }
.workspace-aside { display:grid; gap:16px; }
.side-panel { padding:18px; }
.side-panel > h2 { margin-bottom:14px; }
.deadline-card { display:grid; gap:6px; width:100%; text-align:left; padding:12px; color:#24436d; background:#f3f7ff; border:1px solid #e0e9f8; border-radius:9px; margin-top:10px; font:inherit; cursor:pointer; }
.deadline-card span, .deadline-card time { font-size:13px; }
.deadline-card strong { font-size:14px; line-height:1.5; }
.deadline-card.overdue { background:#fff7f6; border-color:#f0d8d6; color:#a33237; }
.module-card { display:flex; align-items:center; gap:12px; width:100%; text-align:left; background:#fff; border:1px solid #e1e7ef; padding:12px; border-radius:9px; color:inherit; margin-top:10px; cursor:pointer; }
.module-card:hover { background:#f6f9ff; border-color:#abc0ed; }
.module-card > div { flex:1; min-width:0; }
.module-card strong { font-size:14px; }
.module-card p { font-size:12px; line-height:1.5; color:#59677c; margin:4px 0 0; }
.module-card i { font-style:normal; }
.module-icon { display:grid; place-items:center; width:32px; height:32px; border-radius:8px; font-size:18px; flex-shrink:0; }
.violet .module-icon { background:#f0eafb; color:#7353af; }
.orange .module-icon { background:#fff2e5; color:#a75f1e; }
.green .module-icon { background:#e8f5ef; color:#27734e; }
.activity-list { list-style:none; padding:0; margin:0; display:grid; gap:13px; max-height:245px; overflow-y:auto; }
.activity-list li { border-left:2px solid #d8e3f3; padding-left:10px; }
.activity-list strong { display:block; font-size:13px; line-height:1.5; font-weight:500; }
.activity-list span { display:block; font-size:12px; color:#637087; margin-top:4px; line-height:1.5; }
.muted { color:#637087; font-size:13px; }
.stage-strip { display:grid; position:relative; grid-template-columns:repeat(7,minmax(170px,1fr)); gap:10px; overflow-x:auto; padding:4px; scroll-snap-type:x proximity; }
.stage-card { scroll-snap-align:start; display:grid; gap:7px; padding:14px; border:1px solid #dbe3ee; border-radius:9px; text-align:left; color:#526077; background:#f8fafc; font:inherit; font-size:12px; cursor:pointer; }
.stage-card strong { font-size:14px; line-height:1.5; color:#243650; }
.stage-card.active { background:#edf4ff; border-color:#6a8fdb; }
.stage-card.active .stage-order { font-weight:700; color:#204eae; }
.stage-card.completed { border-color:#9cc6b2; }
.stage-card.delayed .stage-order { color:#b5323a; }
.stage-card.selected { box-shadow:inset 0 0 0 2px #315fce; }
.stage-range { line-height:1.5; }
.task-drawer {
  display: grid;
  gap: 18px;
}

.drawer-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.drawer-desc {
  margin: 0;
  color: #475569;
  line-height: 1.6;
}

.drawer-grid {
  display: grid;
  gap: 10px;
  color: #475569;
  font-size: 13px;
}

.drawer-block {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
}

.drawer-block h3 {
  margin: 0;
  font-size: 15px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.attachment-list {
  display: grid;
  gap: 10px;
}

.attachment-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.attachment-main {
  min-width: 0;
}

.attachment-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.attachment-row p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.attachment-metrics {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  color: #475569;
  font-size: 12px;
  flex-wrap: wrap;
}

.attachment-message {
  margin-top: 8px;
}

.system-check p {
  margin: 0;
  color: #475569;
}

.missing-role-list {
  margin: 0;
  padding-left: 18px;
  color: #b91c1c;
}

.completion-url {
  margin-top: 4px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}


@media (prefers-reduced-motion: reduce) { * { scroll-behavior:auto !important; transition:none !important; } }
@media (max-width:1150px) {
  .console-header { flex-direction:column; align-items:stretch; gap:15px; }
  .header-actions { justify-content:flex-end; }
  .workspace-grid { grid-template-columns:minmax(0,1fr) 270px; gap:16px; }
  .task-row { flex-direction:column; }
  .task-actions { align-self:flex-end; }
}
@media (max-width:900px) {
  .workspace-grid { grid-template-columns:1fr; }
  .workspace-aside { grid-template-columns:repeat(2,minmax(0,1fr)); align-items:start; }
  .metric-grid { grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; }
  .task-list { max-height:65vh; }
}
@media (max-width:560px) {
  .console-header,.console-main { padding:16px 12px; }
  .header-left { flex-direction:column; gap:6px; }
  .header-left > .el-button { height:22px; padding:0; }
  .title-block h1 { font-size:20px; }
  .conference-fullname { margin:4px 0; font-size:12px; line-height:1.4; }
  .header-actions { justify-content:flex-start; flex-wrap:nowrap; gap:6px; }
  .header-actions :deep(.el-button) { padding:8px; font-size:12px; }
  .metric-card { padding:12px; gap:5px; }
  .metric-card > strong { font-size:26px; }
  .metric-card > span { font-size:13px; }
  .filter-tabs button { padding:6px 8px; }
  .progress-panel,.timeline-panel { padding:16px 12px; }
  .workspace-aside { grid-template-columns:1fr; }
  .task-filters > .el-select { max-width:none; }
  .task-row { padding:12px; }
  .task-meta { gap:3px 10px; }
  .drawer-actions { flex-wrap:wrap; }
  .drawer-grid { grid-template-columns:1fr; }
}
</style>
