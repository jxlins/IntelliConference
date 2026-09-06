<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CircleCheck, Clock, EditPen, Message, Promotion, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../utils/http'

interface Milestone {
  id: number
  nodeCode: string
  nodeName: string
  startDate?: string
  targetEndDate?: string
  status: number
  current?: boolean
}

interface EmailContent {
  taskLogId: number
  subject?: string
  contentBody?: string
  plainBody?: string
  targetRole?: string
  sendTime?: string
  contentStatus?: number
  selectedEmails?: string[]
}

interface BatchStat {
  totalCount?: number
  successCount?: number
  failCount?: number
}

interface MailTask {
  id: number
  planId?: number
  sourceTaskId?: number
  milestoneId: number
  milestoneName: string
  milestoneDate?: string
  taskName: string
  params?: string
  executionStatus?: number
  errorMsg?: string
  createTime?: string
  emailContent?: EmailContent
  batchStat?: BatchStat
  mailPlanStatus?: string
  recipients?: Array<{ name?: string; email: string }>
}

const route = useRoute()
const router = useRouter()
const confKey = computed(() => String(route.query.confId || ''))
const conferenceId = ref<number | null>(null)
const conferenceTitle = ref('')
const conferenceShortName = ref('')
const loading = ref(false)
const refreshing = ref(false)
const tasks = ref<MailTask[]>([])
const activeFilter = ref('ALL')
watch(() => route.query.filter, filter => {
  activeFilter.value = ['WAITING_REVIEW', 'QUEUED', 'SENT', 'FAILED', 'PLANNED', 'REJECTED'].includes(String(filter))
    ? String(filter) : 'ALL'
}, { immediate: true })
const drawerVisible = ref(false)
const activeTask = ref<MailTask | null>(null)
const detailLoading = ref(false)
const approving = ref(false)
const rejecting = ref(false)
const regenerating = ref(false)
const mailForm = ref({ subject: '', contentBody: '', sendTime: '', targetRole: '' })
/** 审核时勾选的收件人邮箱集合；打开抽屉时默认全选（或回填已保存的选择） */
const selectedEmails = ref<Set<string>>(new Set())

const filteredTasks = computed(() => {
  if (activeFilter.value === 'ALL') return tasks.value
  return tasks.value.filter(item => getTaskState(item).key === activeFilter.value)
})

const summary = computed(() => {
  const result = { WAITING_REVIEW: 0, QUEUED: 0, SENT: 0, FAILED: 0, PLANNED: 0, REJECTED: 0 }
  tasks.value.forEach(item => {
    const key = getTaskState(item).key as keyof typeof result
    if (key in result) result[key] += 1
  })
  return result
})

function isSuccess(resp: any) {
  return resp?.success || resp?.code === '0'
}

function formatDateTime(value?: string) {
  if (!value) return '待流程节点触发'
  return value.replace('T', ' ').slice(0, 16)
}

function currentLocalDateTime() {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

function roleLabel(role?: string) {
  const labels: Record<string, string> = {
    PROSPECT: '潜在投稿者',
    AUTHOR: '投稿作者',
    ACCEPTED_AUTHOR: '录用作者',
    REGISTERED: '已注册参会者',
    REVIEWER: '审稿人',
    SPEAKER: '演讲嘉宾',
    ORGANIZER: '组委会成员',
  }
  return labels[role || ''] || role || '由邮件场景自动确定'
}

function getTaskState(task: MailTask) {
  if (task.mailPlanStatus === 'SENT') return { key: 'SENT', label: '已发送', type: 'success' as const }
  if (task.mailPlanStatus === 'FAILED') return { key: 'FAILED', label: '发送异常', type: 'danger' as const }
  if (task.mailPlanStatus === 'REJECTED') return { key: 'REJECTED', label: '已拒绝', type: 'info' as const }
  if (['APPROVED', 'SENDING'].includes(task.mailPlanStatus || '')) return { key: 'QUEUED', label: task.mailPlanStatus === 'SENDING' ? '发送中' : '已审核待发送', type: 'warning' as const }
  if (task.mailPlanStatus === 'PENDING_REVIEW') return { key: 'WAITING_REVIEW', label: '待负责人审核', type: 'primary' as const }
  if (task.id < 0) return { key: 'PLANNED', label: '计划中', type: 'info' as const }
  if (task.executionStatus === 2 || task.emailContent?.contentStatus === 2) {
    return { key: 'SENT', label: '已发送', type: 'success' as const }
  }
  if (task.executionStatus === 3) return { key: 'FAILED', label: '发送异常', type: 'danger' as const }
  if (task.emailContent?.contentStatus === 1 || task.emailContent?.contentStatus === 3) {
    return { key: 'QUEUED', label: '发送队列中', type: 'warning' as const }
  }
  return { key: 'WAITING_REVIEW', label: '待审核', type: 'primary' as const }
}

function parseEmailScene(task: MailTask) {
  let params: any = {}
  try {
    params = task.params ? JSON.parse(task.params) : {}
  } catch {
    params = {}
  }
  const email = params.email || {}
  return String(email.email_type || params.email_type || email.type || params.type || task.taskName || '').toUpperCase()
}

function inferTargetRole(task: MailTask) {
  if (task.emailContent?.targetRole) return task.emailContent.targetRole
  const scene = parseEmailScene(task)
  if (scene.includes('REVIEW')) return 'REVIEWER'
  if (scene.includes('RESULT') || scene.includes('DECISION') || scene.includes('SUBMISSION')) return 'AUTHOR'
  if (scene.includes('REGISTRATION')) return 'ACCEPTED_AUTHOR'
  return 'PROSPECT'
}

function buildSuggestedMail(task: MailTask) {
  const scene = parseEmailScene(task)
  const conference = conferenceTitle.value || conferenceShortName.value || '本次会议'
  if (scene.includes('REVIEW')) {
    return {
      subject: `【${conferenceShortName.value}】审稿工作提醒`,
      contentBody: `尊敬的审稿专家：\n\n感谢您参与 ${conference} 的论文评审工作。当前评审节点已开始，请登录会议系统查看待评审稿件，并在规定时间内提交评审意见。\n\n如有任何问题，请直接回复本邮件与组委会联系。\n\n${conference} 组委会`,
    }
  }
  if (scene.includes('RESULT') || scene.includes('DECISION')) {
    return {
      subject: `【${conferenceShortName.value}】论文评审结果通知`,
      contentBody: `尊敬的作者：\n\n您投稿至 ${conference} 的论文评审工作已经完成。请登录会议系统查看评审结果及后续安排。\n\n感谢您对本次会议的关注与支持。\n\n${conference} 组委会`,
    }
  }
  if (scene.includes('REGISTRATION')) {
    return {
      subject: `【${conferenceShortName.value}】会议注册提醒`,
      contentBody: `尊敬的作者：\n\n${conference} 的注册通道现已开放。请在注册截止时间前完成注册，并留意会议日程与参会说明。\n\n期待与您相聚。\n\n${conference} 组委会`,
    }
  }
  return {
    subject: `【${conferenceShortName.value}】Call for Papers 征稿通知`,
    contentBody: `尊敬的学者：\n\n${conference} 现面向全球研究者征集高质量原创论文。诚邀您围绕会议主题提交最新研究成果，并欢迎将本通知分享给相关领域的同事。\n\n重要日期与投稿方式请见会议官方网站。\n\n${conference} 组委会`,
  }
}

async function loadTasks(showSuccess = false) {
  if (!conferenceId.value) return
  refreshing.value = true
  try {
    const planResp = await http.get(`/api/conferences/${conferenceId.value}/task-mail-plans`)
    const plans = planResp.data?.data || []
    tasks.value = plans.map((plan: any) => ({
      id: plan.planId,
      planId: plan.planId,
      sourceTaskId: plan.taskId,
      milestoneId: 0,
      milestoneName: stageLabel(plan.stageCode),
      milestoneDate: plan.plannedSendTime,
      taskName: plan.taskName,
      executionStatus: plan.status === 'SENT' ? 2 : plan.status === 'FAILED' ? 3 : 1,
      mailPlanStatus: plan.status,
      emailContent: {
        taskLogId: plan.taskId,
        subject: plan.subject,
        contentBody: plan.contentBody,
        plainBody: plan.plainBody,
        targetRole: plan.targetRole,
        sendTime: plan.plannedSendTime,
        contentStatus: plan.status === 'SENT' ? 2 : plan.status === 'APPROVED' ? 1 : 0,
        selectedEmails: plan.selectedEmails,
      },
      batchStat: { totalCount: plan.recipientCount, successCount: plan.successCount, failCount: plan.failCount },
      recipients: plan.recipients || [],
      errorMsg: plan.errorMessage,
    }))
    if (showSuccess) ElMessage.success('邮件流程状态已更新')
    return
    /* legacy milestone mail tasks remain supported by the fallback implementation below */
    // eslint-disable-next-line no-unreachable
    const timelineResp = await http.get(`/api/conferences/${conferenceId.value}/timeline`)
    const milestones: Milestone[] = timelineResp.data?.data?.milestones || []
    const logResults = await Promise.all(milestones.map(async milestone => {
      const response = await http.get(`/api/task/logs/${milestone.id}`)
      return (response.data?.data || [])
        .filter((item: any) => item.handlerBean === 'emailTaskHandler')
        .map((item: any) => ({
          ...item,
          milestoneId: milestone.id,
          milestoneName: milestone.nodeName,
          milestoneDate: milestone.targetEndDate || milestone.startDate,
        } as MailTask))
    }))
    const mailTasks = logResults.flat()
    await Promise.all(mailTasks.filter(item => item.id > 0).map(async item => {
      try {
        const detailResp = await http.get(`/api/task/detail/${item.id}`)
        const detail = detailResp.data?.data || {}
        item.emailContent = detail.emailContent || undefined
        item.batchStat = detail.batchStat || undefined
      } catch {
        // 单条详情加载失败不影响整个邮件中心。
      }
    }))
    tasks.value = mailTasks.sort((a, b) => new Date(a.milestoneDate || 0).getTime() - new Date(b.milestoneDate || 0).getTime())
    if (showSuccess) ElMessage.success('邮件流程状态已更新')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载邮件流程失败')
  } finally {
    refreshing.value = false
  }
}

function stageLabel(code?: string) {
  const labels: Record<string, string> = {
    CALL_FOR_PAPERS: '征文投稿阶段', REVIEW: '审稿阶段', REGISTRATION: '注册阶段',
    CONFERENCE_PREPARATION: '会务准备阶段', CONFERENCE_DAYS: '会议阶段', POST_CONFERENCE: '会后整理阶段',
  }
  return labels[code || ''] || code || '会议流程'
}

async function loadPage() {
  if (!confKey.value) {
    ElMessage.error('缺少会议标识')
    router.replace('/portal')
    return
  }
  loading.value = true
  try {
    const conferenceResp = await http.get(`/api/intelli-conf/v1/conference/${confKey.value}`)
    if (!isSuccess(conferenceResp.data)) throw new Error(conferenceResp.data?.message || '会议信息加载失败')
    const conference = conferenceResp.data.data || {}
    conferenceId.value = conference.id
    conferenceTitle.value = conference.title || conference.shortName
    conferenceShortName.value = conference.shortName || confKey.value
    await loadTasks()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || error.message || '加载邮件自动化中心失败')
  } finally {
    loading.value = false
  }
}

async function openTask(task: MailTask) {
  if (task.id < 0) {
    ElMessage.info('该邮件将在对应流程阶段开始后自动生成草稿')
    return
  }
  activeTask.value = task
  detailLoading.value = true
  drawerVisible.value = true
  try {
    if (task.planId && task.emailContent) {
      const content = task.emailContent
      mailForm.value = {
        subject: content.subject || '',
        contentBody: content.plainBody || content.contentBody || '',
        sendTime: content.sendTime ? content.sendTime.replace(' ', 'T').slice(0, 16) : currentLocalDateTime(),
        targetRole: content.targetRole || '',
      }
      initRecipientSelection(task, content.selectedEmails)
      return
    }
    const response = await http.get(`/api/task/email-content/${task.id}`)
    const content: EmailContent = response.data?.data || { taskLogId: task.id }
    const suggested = buildSuggestedMail(task)
    task.emailContent = content
    mailForm.value = {
      subject: content.subject || suggested.subject,
      contentBody: content.plainBody || content.contentBody || suggested.contentBody,
      sendTime: content.sendTime ? content.sendTime.replace(' ', 'T').slice(0, 16) : currentLocalDateTime(),
      targetRole: content.targetRole || inferTargetRole(task),
    }
    initRecipientSelection(task, content.selectedEmails)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载邮件草稿失败')
  } finally {
    detailLoading.value = false
  }
}

/** 初始化收件人勾选：若已保存选择则回填，否则默认全选 */
function initRecipientSelection(task: MailTask, saved?: string[]) {
  const recipients = task.recipients || []
  const all = recipients.map(r => r.email).filter(Boolean)
  if (saved && saved.length > 0) {
    const savedLower = new Set(saved.map(e => e.trim().toLowerCase()))
    selectedEmails.value = new Set(all.filter(e => savedLower.has(e.toLowerCase())))
  } else {
    selectedEmails.value = new Set(all)
  }
}

const recipientAllChecked = computed(() => {
  const all = (activeTask.value?.recipients || []).map(r => r.email).filter(Boolean)
  return all.length > 0 && all.every(e => selectedEmails.value.has(e))
})
const recipientIndeterminate = computed(() => {
  const size = selectedEmails.value.size
  return size > 0 && !recipientAllChecked.value
})
function toggleAllRecipients(value: any) {
  const all = (activeTask.value?.recipients || []).map(r => r.email).filter(Boolean)
  selectedEmails.value = new Set(value ? all : [])
}
function toggleRecipient(email: string, checked: boolean) {
  const next = new Set(selectedEmails.value)
  if (checked) next.add(email); else next.delete(email)
  selectedEmails.value = next
}

async function approveMail() {
  if (!activeTask.value || !mailForm.value.subject.trim() || !mailForm.value.contentBody.trim()) {
    ElMessage.warning('请完整填写邮件主题和正文')
    return
  }
  const checked = Array.from(selectedEmails.value)
  if (activeTask.value.recipients?.length && checked.length === 0) {
    ElMessage.warning('请至少勾选一名收件人')
    return
  }
  try {
    await ElMessageBox.confirm(
      `邮件将发送给“${roleLabel(mailForm.value.targetRole)}”中勾选的 ${checked.length || (activeTask.value.recipients?.length || 0)} 名收件人，确认审核通过并加入发送队列吗？`,
      '审核并发送',
      { type: 'warning', confirmButtonText: '审核通过', cancelButtonText: '继续编辑' },
    )
  } catch {
    return
  }
  approving.value = true
  try {
    if (activeTask.value.planId) {
      await http.put(`/api/conferences/${conferenceId.value}/task-mail-plans/${activeTask.value.planId}/approve`, {
        subject: mailForm.value.subject.trim(),
        contentBody: mailForm.value.contentBody.trim(),
        plannedSendTime: mailForm.value.sendTime ? mailForm.value.sendTime.replace('T', ' ') + ':00' : undefined,
        selectedEmails: checked,
      })
      ElMessage.success('审核通过，系统将在计划时间自动发送')
      drawerVisible.value = false
      await loadTasks()
      return
    }
    await http.post('/api/task/email-content/save', {
      taskLogId: activeTask.value.id,
      subject: mailForm.value.subject.trim(),
      contentBody: mailForm.value.contentBody.trim(),
      sendTime: mailForm.value.sendTime ? mailForm.value.sendTime.replace('T', ' ') + ':00' : undefined,
    })
    if (activeTask.value.executionStatus === 3) {
      await http.post(`/api/task/email-content/retry/${activeTask.value.id}`)
    }
    ElMessage.success('审核通过，邮件已进入自动发送队列')
    drawerVisible.value = false
    await loadTasks()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '邮件审核提交失败')
  } finally {
    approving.value = false
  }
}

async function rejectMail() {
  if (!activeTask.value || !activeTask.value.planId) {
    ElMessage.info('该邮件为计划生成，暂不支持拒绝操作')
    return
  }
  let reason = ''
  try {
    const ret = await ElMessageBox.prompt('请输入拒绝原因（选填）', '拒绝该邮件', {
      type: 'warning',
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：内容不符合要求，需要重新生成',
    })
    reason = ret.value || ''
  } catch {
    return
  }
  rejecting.value = true
  try {
    await http.post(`/api/conferences/${conferenceId.value}/task-mail-plans/${activeTask.value.planId}/reject`, { reason })
    ElMessage.success('已拒绝该邮件，系统不会再发送')
    drawerVisible.value = false
    await loadTasks()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '拒绝操作失败')
  } finally {
    rejecting.value = false
  }
}

async function regenerateMail() {
  if (!activeTask.value || !activeTask.value.planId) {
    ElMessage.info('该邮件为计划生成，暂不支持重新生成')
    return
  }
  try {
    await ElMessageBox.confirm(
      '将重新生成邮件主题与正文（会覆盖当前内容），是否继续？',
      '重新生成',
      { type: 'warning', confirmButtonText: '重新生成', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  regenerating.value = true
  try {
    const res = await http.post(`/api/conferences/${conferenceId.value}/task-mail-plans/${activeTask.value.planId}/regenerate`)
    const updated: any = res.data?.data
    if (updated) {
      mailForm.value.subject = updated.subject || ''
      mailForm.value.contentBody = updated.plainBody || updated.contentBody || ''
      if (activeTask.value.emailContent) {
        activeTask.value.emailContent.subject = mailForm.value.subject
        activeTask.value.emailContent.contentBody = mailForm.value.contentBody
      }
      ElMessage.success('已重新生成邮件内容，请审核后通过')
    } else {
      ElMessage.success('已重新生成邮件内容')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '重新生成失败')
  } finally {
    regenerating.value = false
  }
}

function goBack() {
  router.push(`/dashboard?confId=${encodeURIComponent(confKey.value)}`)
}

function goCompose() {
  router.push(`/mail-compose?confId=${encodeURIComponent(confKey.value)}`)
}

function goAccount() {
  router.push(`/conference-mail-account?confId=${encodeURIComponent(confKey.value)}`)
}

onMounted(loadPage)
</script>

<template>
  <div class="mail-workflow-page" v-loading="loading">
    <header class="page-header">
      <div class="header-title">
        <el-button :icon="ArrowLeft" text @click="goBack">返回会议工作台</el-button>
        <div>
          <p>MAIL AUTOMATION</p>
          <h1>邮件自动化中心</h1>
          <span>{{ conferenceTitle }} · 节点触发、内容审核、发送结果全程可追踪</span>
        </div>
      </div>
      <div class="header-actions">
        <el-button @click="goAccount">会议邮箱设置</el-button>
        <el-button @click="goCompose">临时邮件</el-button>
        <el-button :icon="Refresh" :loading="refreshing" type="primary" @click="loadTasks(true)">刷新状态</el-button>
      </div>
    </header>

    <main class="page-main">
      <section class="explain-bar">
        <div class="explain-icon"><el-icon><Promotion /></el-icon></div>
        <div>
          <strong>邮件闭环已启用</strong>
          <p>流程节点到达后系统生成邮件草稿；组织者审核通过后，系统按计划时间使用会议官方邮箱发送。</p>
        </div>
        <div class="flow-mini">
          <span>节点触发</span><i>→</i><span>自动起草</span><i>→</i><span>人工审核</span><i>→</i><span>自动发送</span>
        </div>
      </section>

      <section class="summary-grid">
        <button type="button" :class="{ active: activeFilter === 'WAITING_REVIEW' }" @click="activeFilter = 'WAITING_REVIEW'">
          <el-icon><EditPen /></el-icon><div><strong>{{ summary.WAITING_REVIEW }}</strong><span>待审核</span></div>
        </button>
        <button type="button" :class="{ active: activeFilter === 'QUEUED' }" @click="activeFilter = 'QUEUED'">
          <el-icon><Clock /></el-icon><div><strong>{{ summary.QUEUED }}</strong><span>发送队列</span></div>
        </button>
        <button type="button" :class="{ active: activeFilter === 'SENT' }" @click="activeFilter = 'SENT'">
          <el-icon><CircleCheck /></el-icon><div><strong>{{ summary.SENT }}</strong><span>已发送</span></div>
        </button>
        <button type="button" :class="{ active: activeFilter === 'PLANNED' }" @click="activeFilter = 'PLANNED'">
          <el-icon><Message /></el-icon><div><strong>{{ summary.PLANNED }}</strong><span>后续计划</span></div>
        </button>
        <button type="button" :class="{ active: activeFilter === 'FAILED' }" @click="activeFilter = 'FAILED'">
          <el-icon><Promotion /></el-icon><div><strong>{{ summary.FAILED }}</strong><span>发送异常</span></div>
        </button>
        <button type="button" :class="{ active: activeFilter === 'REJECTED' }" @click="activeFilter = 'REJECTED'">
          <el-icon><CircleCheck /></el-icon><div><strong>{{ summary.REJECTED }}</strong><span>已拒绝</span></div>
        </button>
      </section>

      <section class="workflow-panel">
        <div class="panel-head">
          <div><p>邮件任务</p><h2>{{ activeFilter === 'ALL' ? '全流程邮件计划' : '已筛选的邮件任务' }}</h2></div>
          <el-button v-if="activeFilter !== 'ALL'" text type="primary" @click="activeFilter = 'ALL'">查看全部</el-button>
        </div>
        <el-table :data="filteredTasks" row-key="id" class="mail-table" @row-click="openTask">
          <el-table-column label="流程节点" min-width="170">
            <template #default="scope"><strong>{{ scope.row.milestoneName }}</strong></template>
          </el-table-column>
          <el-table-column prop="taskName" label="邮件事务" min-width="230" />
          <el-table-column label="计划时间" min-width="170">
            <template #default="scope">{{ formatDateTime(scope.row.emailContent?.sendTime || scope.row.milestoneDate) }}</template>
          </el-table-column>
          <el-table-column label="收件人" min-width="150">
            <template #default="scope">{{ roleLabel(scope.row.emailContent?.targetRole || inferTargetRole(scope.row)) }}</template>
          </el-table-column>
          <el-table-column label="发送结果" min-width="150">
            <template #default="scope">
              <span v-if="scope.row.batchStat">{{ scope.row.batchStat.successCount || 0 }}/{{ scope.row.batchStat.totalCount || 0 }} 成功</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120" fixed="right">
            <template #default="scope"><el-tag :type="getTaskState(scope.row).type" effect="light" round>{{ getTaskState(scope.row).label }}</el-tag></template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!refreshing && filteredTasks.length === 0" description="当前暂无邮件任务；任务会随流程阶段自动生成" />
      </section>
    </main>

    <el-drawer v-model="drawerVisible" size="600px" :title="activeTask?.taskName || '邮件审核'">
      <div v-loading="detailLoading" class="review-drawer">
        <template v-if="activeTask">
          <div class="review-context">
            <div><span>流程节点</span><strong>{{ activeTask.milestoneName }}</strong></div>
            <div><span>发送对象</span><strong>{{ roleLabel(mailForm.targetRole) }}</strong></div>
          </div>
          <el-alert title="以下内容由会议场景模板自动生成，请审核并按需修改；正文为纯文本便于查看。" type="info" :closable="false" show-icon />
          <el-form label-position="top" class="review-form">
            <el-form-item label="邮件主题" required><el-input v-model="mailForm.subject" /></el-form-item>
            <el-form-item required>
              <template #label>
                <span>邮件正文（纯文本）</span>
                <el-button text size="small" :loading="regenerating" @click="regenerateMail" style="margin-left: 8px">重新生成</el-button>
              </template>
              <el-input v-model="mailForm.contentBody" type="textarea" :rows="14" resize="vertical" />
            </el-form-item>
          <el-form-item label="计划发送时间">
              <el-date-picker v-model="mailForm.sendTime" type="datetime" value-format="YYYY-MM-DDTHH:mm" style="width: 100%" />
            </el-form-item>
            <el-form-item v-if="activeTask.recipients?.length" :label="`收件人（共 ${activeTask.recipients.length} 人，已选 ${selectedEmails.size}）`">
              <div class="recipient-picker">
                <div class="recipient-picker__toolbar">
                  <el-checkbox :model-value="recipientAllChecked" :indeterminate="recipientIndeterminate" @change="toggleAllRecipients">全选</el-checkbox>
                  <el-button text size="small" @click="selectedEmails = new Set()">清空</el-button>
                </div>
                <div class="recipient-picker__list">
                  <el-checkbox
                    v-for="recipient in activeTask.recipients"
                    :key="recipient.email"
                    :model-value="selectedEmails.has(recipient.email)"
                    @change="(val: any) => toggleRecipient(recipient.email, !!val)"
                  >
                    <span class="recipient-name">{{ recipient.name || '（未命名）' }}</span>
                    <span class="recipient-email">{{ recipient.email }}</span>
                  </el-checkbox>
                </div>
              </div>
            </el-form-item>
          </el-form>
          <div class="drawer-actions">
            <el-button @click="drawerVisible = false">稍后处理</el-button>
            <el-button type="danger" plain :loading="rejecting" @click="rejectMail">拒绝</el-button>
            <el-button type="primary" :loading="approving" @click="approveMail">审核通过并加入发送队列</el-button>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.mail-workflow-page { min-height: 100vh; color: #172033; background: #f3f6fb; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 24px 32px; border-bottom: 1px solid #e2e8f0; background: rgba(255,255,255,.96); }
.header-title { display: flex; align-items: flex-start; gap: 20px; }
.header-title p, .panel-head p { margin: 0 0 4px; color: #4772df; font-size: 11px; font-weight: 800; letter-spacing: .13em; }
.header-title h1 { margin: 0; font-size: 27px; }
.header-title span { display: block; margin-top: 5px; color: #748096; font-size: 13px; }
.header-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.page-main { max-width: 1440px; margin: 0 auto; padding: 26px; }
.explain-bar { display: flex; align-items: center; gap: 16px; padding: 18px 20px; border: 1px solid #dce6fb; border-radius: 16px; background: linear-gradient(100deg,#edf4ff,#fff); box-shadow: 0 12px 32px rgba(42,75,135,.07); }
.explain-icon { display: grid; flex: 0 0 42px; height: 42px; place-items: center; border-radius: 13px; color: #fff; background: #3869dd; font-size: 20px; }
.explain-bar strong { font-size: 15px; }
.explain-bar p { margin: 4px 0 0; color: #69758a; font-size: 13px; }
.flow-mini { display: flex; align-items: center; gap: 8px; margin-left: auto; white-space: nowrap; }
.flow-mini span { padding: 7px 10px; border-radius: 8px; color: #335ca9; background: #fff; font-size: 12px; font-weight: 600; }
.flow-mini i { color: #a6b4cb; font-style: normal; }
.summary-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 14px; margin: 20px 0; }
.summary-grid button { display: flex; align-items: center; gap: 14px; padding: 18px; border: 1px solid #e1e7f0; border-radius: 15px; color: #52627b; background: #fff; cursor: pointer; text-align: left; transition: 160ms ease; }
.summary-grid button:hover, .summary-grid button.active { border-color: #4c76dd; box-shadow: 0 12px 28px rgba(62,101,194,.11); transform: translateY(-2px); }
.summary-grid .el-icon { padding: 10px; border-radius: 11px; color: #315fc4; background: #edf3ff; font-size: 20px; }
.summary-grid button div { display: flex; flex-direction: column; }
.summary-grid strong { color: #15213a; font-size: 25px; line-height: 1; }
.summary-grid span { margin-top: 6px; font-size: 12px; }
.workflow-panel { padding: 22px; border: 1px solid #e1e7ef; border-radius: 18px; background: #fff; box-shadow: 0 16px 40px rgba(31,49,84,.06); }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.panel-head h2 { margin: 0; font-size: 19px; }
.mail-table { cursor: pointer; }
.muted { color: #a1a9b5; }
.review-drawer { padding: 0 4px 24px; }
.review-context { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 16px; }
.review-context div { display: flex; flex-direction: column; gap: 5px; padding: 13px; border-radius: 11px; background: #f6f8fc; }
.review-context span { color: #7a8496; font-size: 11px; }
.review-context strong { font-size: 13px; }
.review-form { margin-top: 18px; }
.recipient-preview { display: flex; gap: 7px; flex-wrap: wrap; }
.recipient-preview > span { color: #7a8496; font-size: 12px; }
.recipient-picker { border: 1px solid #e2e8f0; border-radius: 8px; background: #fbfcfe; }
.recipient-picker__toolbar { display: flex; align-items: center; gap: 14px; padding: 8px 12px; border-bottom: 1px solid #edf0f4; }
.recipient-picker__list { max-height: 240px; overflow-y: auto; padding: 6px 12px; display: flex; flex-direction: column; gap: 4px; }
.recipient-picker__list :deep(.el-checkbox) { margin-right: 0; height: auto; padding: 4px 0; align-items: center; }
.recipient-picker__list :deep(.el-checkbox__label) { white-space: normal; word-break: break-all; }
.recipient-name { font-size: 13px; color: #1f2a3d; margin-right: 8px; }
.recipient-email { font-size: 12px; color: #7a8496; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 15px; border-top: 1px solid #edf0f4; }
@media (max-width: 900px) { .page-header { align-items: flex-start; flex-direction: column; padding: 20px; } .header-title { flex-direction: column; gap: 8px; } .page-main { padding: 18px; } .flow-mini { display: none; } .summary-grid { grid-template-columns: 1fr 1fr; } }
</style>
