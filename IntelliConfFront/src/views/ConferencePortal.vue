<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, Delete, Location, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import http from '../utils/http'

interface Conference {
  shortName: string
  title: string
  startTime?: string
  endTime?: string
  currentState?: string
  setupStatus?: string
  province?: string
  city?: string
  country?: string
  address?: string
}

const router = useRouter()
const conferences = ref<Conference[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const deletingMap = ref<Record<string, boolean>>({})
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const setupGuidePrefix = 'conference-setup-guided:'

const conferenceForm = reactive({
  title: '',
  shortName: '',
  description: '',
  startTime: '',
  endTime: '',
  country: '中国',
  province: '',
  city: '',
  address: '',
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入会议名称', trigger: 'blur' },
    { min: 2, max: 100, message: '会议名称长度为 2-100 个字符', trigger: 'blur' },
  ],
  shortName: [
    { required: true, message: '请输入会议简称', trigger: 'blur' },
    { min: 2, max: 30, message: '会议简称长度为 2-30 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9-]+$/, message: '仅支持字母、数字和短横线', trigger: 'blur' },
  ],
  startTime: [{ required: true, message: '请选择会议开始日期', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择会议结束日期', trigger: 'change' }],
  city: [{ required: true, message: '请输入举办城市', trigger: 'blur' }],
}

function isSuccess(resp: any) {
  return resp?.success || resp?.code === '0'
}

function getSetupLabel(status?: string) {
  if (status === 'TASK_GENERATED') return '可执行'
  if (status === 'STAGE_GENERATED') return '已初始化'
  if (status === 'DATES_COMPLETED') return '待生成阶段'
  return '待完善日期'
}

function getSetupType(status?: string): 'success' | 'warning' | 'info' {
  if (status === 'TASK_GENERATED') return 'success'
  if (status === 'STAGE_GENERATED') return 'success'
  if (status === 'DATES_COMPLETED') return 'warning'
  return 'info'
}

function formatDateRange(conf: Conference) {
  if (!conf.startTime && !conf.endTime) return '日期未设置'
  if (!conf.endTime) return conf.startTime || ''
  return `${conf.startTime || ''} - ${conf.endTime}`
}

function formatLocation(conf: Conference) {
  const parts = [conf.country, conf.province, conf.city].filter(Boolean)
  return parts.join(' / ') || conf.address || '地点未设置'
}

async function loadConferences() {
  loading.value = true
  try {
    const response = await http.get('/api/intelli-conf/v1/conference/page', {
      params: {
        current: currentPage.value,
        size: pageSize.value,
      },
    })
    if (isSuccess(response.data)) {
      const pageData = response.data.data || {}
      conferences.value = pageData.records || []
      total.value = pageData.total || 0
      return
    }
    ElMessage.error(response.data?.message || '加载会议列表失败')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载会议列表失败')
  } finally {
    loading.value = false
  }
}

function enterConference(conf: Conference) {
  const setupGuided = localStorage.getItem(`${setupGuidePrefix}${conf.shortName}`) === '1'
  if (conf.setupStatus !== 'TASK_GENERATED' && !setupGuided) {
    localStorage.setItem(`${setupGuidePrefix}${conf.shortName}`, '1')
    router.push(`/conference-setup?confId=${encodeURIComponent(conf.shortName)}`)
    return
  }
  router.push(`/dashboard?confId=${encodeURIComponent(conf.shortName)}`)
}

function openCreateDialog() {
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(conferenceForm, {
    title: '',
    shortName: '',
    description: '',
    startTime: '',
    endTime: '',
    country: '中国',
    province: '',
    city: '',
    address: '',
  })
}

async function submitConference() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (new Date(conferenceForm.endTime) < new Date(conferenceForm.startTime)) {
      ElMessage.error('会议结束日期不能早于开始日期')
      return
    }

    submitting.value = true
    try {
      const response = await http.post('/api/intelli-conf/v1/conference', {
        ...conferenceForm,
        title: conferenceForm.title.trim(),
        shortName: conferenceForm.shortName.trim(),
        description: conferenceForm.description.trim() || undefined,
      })
      if (isSuccess(response.data)) {
        ElMessage.success('会议创建成功，请继续完善重要日期')
        dialogVisible.value = false
        const shortName = conferenceForm.shortName.trim()
        localStorage.setItem(`${setupGuidePrefix}${shortName}`, '1')
        await loadConferences()
        router.push(`/conference-setup?confId=${encodeURIComponent(shortName)}`)
        return
      }
      ElMessage.error(response.data?.message || '创建会议失败')
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '创建会议失败')
    } finally {
      submitting.value = false
    }
  })
}

async function deleteConference(conf: Conference) {
  try {
    await ElMessageBox.confirm(`确定删除会议「${conf.title}」吗？此操作不可恢复。`, '删除会议', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  deletingMap.value = { ...deletingMap.value, [conf.shortName]: true }
  try {
    const response = await http.delete(`/api/intelli-conf/v1/conference/${conf.shortName}`)
    if (isSuccess(response.data)) {
      ElMessage.success('会议已删除')
      await loadConferences()
      return
    }
    ElMessage.error(response.data?.message || '删除会议失败')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除会议失败')
  } finally {
    deletingMap.value = { ...deletingMap.value, [conf.shortName]: false }
  }
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/auth')
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadConferences()
}

onMounted(loadConferences)
</script>

<template>
  <div class="portal-page">
    <header class="portal-header">
      <div>
        <p class="eyebrow">IntelliConf</p>
        <h1>我的会议</h1>
        <span>管理会议创建、初始化和执行进度</span>
      </div>
      <div class="header-actions">
        <el-button :icon="Plus" type="primary" size="large" @click="openCreateDialog">创建会议</el-button>
        <el-button size="large" @click="logout">退出登录</el-button>
      </div>
    </header>

    <main class="portal-content">
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="conferences.length === 0" description="暂无会议">
        <el-button :icon="Plus" type="primary" @click="openCreateDialog">创建第一个会议</el-button>
      </el-empty>

      <template v-else>
        <div class="conference-grid">
          <article
            v-for="conf in conferences"
            :key="conf.shortName"
            class="conference-card"
            @click="enterConference(conf)"
          >
            <div class="card-head">
              <div class="title-wrap">
                <h2>{{ conf.title }}</h2>
                <span>{{ conf.shortName }}</span>
              </div>
              <el-tag :type="getSetupType(conf.setupStatus)" effect="plain">
                {{ getSetupLabel(conf.setupStatus) }}
              </el-tag>
            </div>

            <div class="card-meta">
              <div>
                <el-icon><Calendar /></el-icon>
                <span>{{ formatDateRange(conf) }}</span>
              </div>
              <div>
                <el-icon><Location /></el-icon>
                <span>{{ formatLocation(conf) }}</span>
              </div>
            </div>

            <div class="card-footer">
              <el-button type="primary" text @click.stop="enterConference(conf)">
                {{ conf.setupStatus === 'TASK_GENERATED' ? '进入工作台' : '继续初始化' }}
              </el-button>
              <el-button
                :icon="Delete"
                type="danger"
                text
                :loading="deletingMap[conf.shortName]"
                @click.stop="deleteConference(conf)"
              >
                删除
              </el-button>
            </div>
          </article>
        </div>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next, jumper"
            @current-change="handlePageChange"
          />
        </div>
      </template>
    </main>

    <el-dialog
      v-model="dialogVisible"
      title="创建会议"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="conferenceForm" :rules="rules" label-width="96px">
        <el-form-item label="会议名称" prop="title">
          <el-input v-model="conferenceForm.title" placeholder="例如：IntelliConf 2026" clearable />
        </el-form-item>
        <el-form-item label="会议简称" prop="shortName">
          <el-input v-model="conferenceForm.shortName" placeholder="例如：ICAI-2026" clearable />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始日期" prop="startTime">
              <el-date-picker v-model="conferenceForm.startTime" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endTime">
              <el-date-picker v-model="conferenceForm.endTime" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="国家/地区">
              <el-input v-model="conferenceForm.country" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="省份">
              <el-input v-model="conferenceForm.province" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="城市" prop="city">
          <el-input v-model="conferenceForm.city" placeholder="会议举办城市" clearable />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="conferenceForm.address" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item label="会议简介">
          <el-input v-model="conferenceForm.description" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConference">创建并初始化</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.portal-page {
  min-height: 100vh;
  background: #f4f7fb;
}

.portal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 34px 32px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.eyebrow {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.portal-header h1 {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
}

.portal-header span {
  display: inline-block;
  margin-top: 6px;
  color: #64748b;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.portal-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 28px 24px;
}

.conference-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 18px;
}

.conference-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 230px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.05);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.conference-card:hover {
  transform: translateY(-3px);
  border-color: #bfdbfe;
  box-shadow: 0 18px 42px rgba(37, 99, 235, 0.12);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.title-wrap {
  min-width: 0;
}

.title-wrap h2 {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 18px;
}

.title-wrap span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.card-meta {
  display: grid;
  gap: 10px;
  color: #475569;
  font-size: 13px;
}

.card-meta div {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.card-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 28px;
  padding: 18px;
  border-radius: 12px;
  background: #fff;
}

@media (max-width: 768px) {
  .portal-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
