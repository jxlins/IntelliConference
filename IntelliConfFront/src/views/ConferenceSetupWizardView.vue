<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, Refresh, Check } from '@element-plus/icons-vue'
import http from '../utils/http'

interface StageItem {
  id?: number
  stageDefId: number
  stageCode: string
  stageName: string
  stageOrder: number
  plannedStartTime: string
  plannedEndTime: string
  stageStatus?: string
  progress?: number
  isCurrent?: number
}

interface TaskItem {
  taskId?: number
  conferenceId?: number
  stageId: number
  stageCode: string
  taskCode: string
  taskName: string
  taskDesc?: string
  taskType?: string
  principalRole?: string
  plannedStartTime?: string
  plannedEndTime?: string
  taskStatus?: string
  priority?: string
  riskLevel?: string
  isCore?: number
  needReview?: number
  sortOrder?: number
}

const route = useRoute()
const router = useRouter()

const conferenceKey = computed(() => String(route.query.confId || ''))
const loading = ref(false)
const savingDates = ref(false)
const previewLoading = ref(false)
const generating = ref(false)
const taskPreviewLoading = ref(false)
const taskGenerating = ref(false)
const formRef = ref<FormInstance>()
const stages = ref<StageItem[]>([])
const taskPreview = ref<TaskItem[]>([])

const setupStatus = ref({
  conferenceId: 0,
  shortName: '',
  title: '',
  setupStatus: 'BASIC_CREATED',
  datesCompleted: false,
  stagesGenerated: false,
  nextAction: 'COMPLETE_DATES',
})

const dateForm = reactive({
  paperSubmissionDeadline: '',
  notificationOfAcceptance: '',
  cameraReadySubmission: '',
  earlyBirdRegistration: '',
  conferenceStartDate: '',
  conferenceEndDate: '',
})

const rules: FormRules = {
  paperSubmissionDeadline: [{ required: true, message: '请选择投稿截止时间', trigger: 'change' }],
  notificationOfAcceptance: [{ required: true, message: '请选择录用通知时间', trigger: 'change' }],
  cameraReadySubmission: [{ required: true, message: '请选择终稿截止时间', trigger: 'change' }],
  earlyBirdRegistration: [{ required: true, message: '请选择早鸟注册截止时间', trigger: 'change' }],
  conferenceStartDate: [{ required: true, message: '请选择会议开始时间', trigger: 'change' }],
  conferenceEndDate: [{ required: true, message: '请选择会议结束时间', trigger: 'change' }],
}

function normalizeDateTime(value: string) {
  if (!value) return ''
  return value.length === 10 ? `${value} 00:00:00` : value
}

async function loadSetupStatus() {
  if (!conferenceKey.value) {
    ElMessage.error('缺少会议标识')
    return
  }
  loading.value = true
  try {
    const [statusResp, confResp] = await Promise.all([
      http.get(`/api/intelli-conf/v1/conference/${conferenceKey.value}/setup-status`),
      http.get(`/api/intelli-conf/v1/conference/${conferenceKey.value}`),
    ])
    setupStatus.value = statusResp.data.data || setupStatus.value
    const conf = confResp.data.data || {}
    dateForm.paperSubmissionDeadline = conf.paperSubmissionDeadline || ''
    dateForm.notificationOfAcceptance = conf.notificationOfAcceptance || ''
    dateForm.cameraReadySubmission = conf.cameraReadySubmission || ''
    dateForm.earlyBirdRegistration = conf.earlyBirdRegistration || ''
    dateForm.conferenceStartDate = conf.conferenceStartDate || ''
    dateForm.conferenceEndDate = conf.conferenceEndDate || ''
    if (setupStatus.value.stagesGenerated) {
      await loadStages()
    }
    if (setupStatus.value.setupStatus === 'STAGE_GENERATED') {
      await generateTaskPreview(true)
    }
    if (setupStatus.value.setupStatus === 'TASK_GENERATED') {
      await loadGeneratedTasks()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载初始化状态失败')
  } finally {
    loading.value = false
  }
}

async function loadStages() {
  const response = await http.get(`/api/intelli-conf/v1/conference/${conferenceKey.value}/stages`)
  stages.value = response.data.data || []
}

async function loadGeneratedTasks() {
  if (!setupStatus.value.conferenceId) return
  const response = await http.get(`/api/conferences/${setupStatus.value.conferenceId}/tasks/generated`)
  taskPreview.value = response.data.data || []
}

async function saveImportantDates() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    savingDates.value = true
    try {
      const payload = Object.fromEntries(
        Object.entries(dateForm).map(([key, value]) => [key, normalizeDateTime(String(value))])
      )
      await http.put(`/api/intelli-conf/v1/conference/${conferenceKey.value}/important-dates`, payload)
      ElMessage.success('关键日期已保存')
      await loadSetupStatus()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '保存关键日期失败')
    } finally {
      savingDates.value = false
    }
  })
}

async function generatePreview() {
  previewLoading.value = true
  try {
    const response = await http.get(`/api/intelli-conf/v1/conference/${conferenceKey.value}/stages/preview`)
    stages.value = response.data.data || []
    ElMessage.success('已根据关键日期生成阶段预览')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '生成阶段预览失败')
  } finally {
    previewLoading.value = false
  }
}

async function confirmGenerateStages() {
  if (stages.value.length !== 7) {
    ElMessage.warning('请先生成完整的七阶段流程')
    return
  }
  generating.value = true
  try {
    const payload = stages.value.map(item => ({
      stageDefId: item.stageDefId,
      stageCode: item.stageCode,
      stageName: item.stageName,
      stageOrder: Number(item.stageOrder),
      plannedStartTime: normalizeDateTime(item.plannedStartTime),
      plannedEndTime: normalizeDateTime(item.plannedEndTime),
    }))
    await http.post(`/api/intelli-conf/v1/conference/${conferenceKey.value}/stages/generate`, payload)
    await loadSetupStatus()
    await generateTaskPreview()
    ElMessage.success('流程阶段已确认，事务方案已生成')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '确认流程阶段失败')
  } finally {
    generating.value = false
  }
}

async function generateTaskPreview(silent = false) {
  if (!setupStatus.value.conferenceId) {
    ElMessage.warning('缺少会议标识')
    return
  }
  taskPreviewLoading.value = true
  try {
    const response = await http.get(`/api/conferences/${setupStatus.value.conferenceId}/tasks/preview`)
    taskPreview.value = response.data.data || []
    if (!silent) {
      ElMessage.success('任务与角色方案已生成')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '生成任务方案失败')
  } finally {
    taskPreviewLoading.value = false
  }
}

async function confirmGenerateTasks() {
  if (!setupStatus.value.conferenceId) {
    ElMessage.warning('缺少会议标识')
    return
  }
  if (taskPreview.value.length === 0) {
    ElMessage.warning('请先生成任务方案')
    return
  }
  taskGenerating.value = true
  try {
    await http.post(`/api/conferences/${setupStatus.value.conferenceId}/tasks/generate`)
    ElMessage.success('任务与负责角色已确认')
    router.push(`/dashboard?confId=${encodeURIComponent(conferenceKey.value)}`)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '确认任务方案失败')
  } finally {
    taskGenerating.value = false
  }
}

function goBack() {
  router.push(`/dashboard?confId=${encodeURIComponent(conferenceKey.value)}`)
}

onMounted(loadSetupStatus)
</script>

<template>
  <div class="setup-page" v-loading="loading">
    <div class="top-bar">
      <el-button :icon="ArrowLeft" text @click="goBack">返回工作台</el-button>
      <div>
        <h2>{{ setupStatus.title || '会议流程初始化' }}</h2>
        <p>定义关键日期，系统将自动生成全流程时间轴与任务负责角色</p>
      </div>
    </div>

    <section class="section">
      <div class="section-head">
        <h3>第一步 · 确认关键日期</h3>
        <el-button type="primary" :loading="savingDates" @click="saveImportantDates">保存关键日期</el-button>
      </div>
      <el-form ref="formRef" :model="dateForm" :rules="rules" label-width="210px">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="投稿截止时间" prop="paperSubmissionDeadline">
              <el-date-picker v-model="dateForm.paperSubmissionDeadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="录用通知时间" prop="notificationOfAcceptance">
              <el-date-picker v-model="dateForm.notificationOfAcceptance" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="终稿提交截止时间" prop="cameraReadySubmission">
              <el-date-picker v-model="dateForm.cameraReadySubmission" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="早鸟注册截止时间" prop="earlyBirdRegistration">
              <el-date-picker v-model="dateForm.earlyBirdRegistration" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="会议开始时间" prop="conferenceStartDate">
              <el-date-picker v-model="dateForm.conferenceStartDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="会议结束时间" prop="conferenceEndDate">
              <el-date-picker v-model="dateForm.conferenceEndDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </section>

    <section class="section">
      <div class="section-head">
        <h3>第二步 · 审核全流程时间轴</h3>
        <div class="actions">
          <el-button :icon="Refresh" :loading="previewLoading" @click="generatePreview">重新生成</el-button>
          <el-button type="success" :icon="Check" :loading="generating" @click="confirmGenerateStages">确认时间轴</el-button>
        </div>
      </div>
      <el-table :data="stages" border stripe>
        <el-table-column label="顺序" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.stageOrder" :min="1" :max="99" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column prop="stageCode" label="阶段编码" min-width="190" />
        <el-table-column label="阶段名称" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.stageName" />
          </template>
        </el-table-column>
        <el-table-column label="计划开始" min-width="210">
          <template #default="{ row }">
            <el-date-picker v-model="row.plannedStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="计划结束" min-width="210">
          <template #default="{ row }">
            <el-date-picker v-model="row.plannedEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column prop="stageStatus" label="状态" width="130" />
      </el-table>
    </section>

    <section class="section">
      <div class="section-head">
        <h3>第三步 · 审核任务与负责角色</h3>
        <div class="actions">
          <el-button :icon="Refresh" :loading="taskPreviewLoading" @click="generateTaskPreview">重新生成方案</el-button>
          <el-button type="success" :icon="Check" :loading="taskGenerating" @click="confirmGenerateTasks">确认并开始执行</el-button>
        </div>
      </div>
      <el-table :data="taskPreview" border stripe>
        <el-table-column prop="stageCode" label="所属阶段" min-width="170" />
        <el-table-column prop="taskCode" label="任务编码" min-width="190" />
        <el-table-column prop="taskName" label="任务名称" min-width="220" />
        <el-table-column prop="principalRole" label="建议负责角色" min-width="150" />
        <el-table-column prop="plannedStartTime" label="计划开始" min-width="180" />
        <el-table-column prop="plannedEndTime" label="计划完成" min-width="180" />
        <el-table-column prop="taskStatus" label="预览状态" min-width="140" />
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.setup-page {
  max-width: 1320px;
  margin: 0 auto;
  padding: 24px;
  background: #f6f8fb;
  min-height: 100vh;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.top-bar h2 {
  margin: 0;
  font-size: 22px;
  color: #1d4ed8;
}

.top-bar p {
  margin: 4px 0 0;
  color: #64748b;
}

.section {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
  margin-bottom: 18px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.section-head h3 {
  margin: 0;
  font-size: 17px;
  color: #111827;
}

.actions {
  display: flex;
  gap: 10px;
}
</style>
