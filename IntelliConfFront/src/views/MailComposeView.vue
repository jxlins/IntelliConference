<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import http from '../utils/http'

interface Participant {
  id: string | number
  name: string
  email: string
  institution?: string
  role: string
}

interface RecipientPayload {
  name: string
  email: string
}

interface MailTemplate {
  id: number
  conferenceId: number
  sceneCode: string
  templateName?: string
  subjectTemplate: string
  htmlTemplate?: string
  textTemplate?: string
  enabled?: boolean
}

const route = useRoute()
const router = useRouter()

const confShortName = computed(() => String(route.query.confId || ''))
const conferenceTitle = ref('')
const conferenceId = ref<number | null>(null)

const loading = ref(false)
const sending = ref(false)
const participants = ref<Participant[]>([])
const selectedRows = ref<Participant[]>([])
const keyword = ref('')

const templateLoading = ref(false)
const templateSaving = ref(false)
const templateDeletingIds = ref<Set<number>>(new Set())
const templates = ref<MailTemplate[]>([])
const templateForm = ref({
  sceneCode: '',
  templateName: '',
  subjectTemplate: '',
  htmlTemplate: '',
  textTemplate: '',
  enabled: true,
})

const form = ref({
  subject: '',
  contentBody: '',
})

const filteredParticipants = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return participants.value
  return participants.value.filter((item) => {
    return (
      (item.name || '').toLowerCase().includes(kw) ||
      (item.email || '').toLowerCase().includes(kw) ||
      (item.role || '').toLowerCase().includes(kw) ||
      (item.institution || '').toLowerCase().includes(kw)
    )
  })
})

async function apiGetConferenceByShortName(shortName: string) {
  const res = await http.get(`/api/intelli-conf/v1/conference/${shortName}`)
  return res.data.data
}

async function apiGetParticipantList(confId: number, current: number, size: number) {
  const res = await http.get('/api/intelli-conf/v1/participant/list', {
    params: { confId, current, size }
  })
  return res.data.data
}

async function apiSendBasicMail(payload: {
  confId: number | null
  subject: string
  contentBody: string
  recipients: RecipientPayload[]
}) {
  const res = await http.post('/api/mail/basic-send', payload)
  return res.data.data
}

async function apiListTemplates(confId: number) {
  const res = await http.get(`/api/conferences/${confId}/mail/templates`)
  return res.data.data
}

async function apiCreateTemplate(confId: number, payload: any) {
  const res = await http.post(`/api/conferences/${confId}/mail/templates`, payload)
  return res.data.data
}

async function apiDeleteTemplate(confId: number, templateId: number) {
  const res = await http.delete(`/api/conferences/${confId}/mail/templates/${templateId}`)
  return res.data.data
}

async function loadParticipants() {
  if (!conferenceId.value) return
  const size = 100
  let current = 1
  let pages = 1
  const rows: Participant[] = []

  do {
    const page = await apiGetParticipantList(conferenceId.value, current, size)
    rows.push(...(page.records || []))
    pages = Number(page.pages || 1)
    current += 1
  } while (current <= pages)

  participants.value = rows
}

async function loadTemplates() {
  if (!conferenceId.value) return
  templateLoading.value = true
  try {
    const result = await apiListTemplates(conferenceId.value)
    templates.value = result || []
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载邮件模板失败')
  } finally {
    templateLoading.value = false
  }
}

async function loadData() {
  if (!confShortName.value) {
    ElMessage.error('缺少会议标识，无法加载邮件页面')
    return
  }

  loading.value = true
  try {
    const conf = await apiGetConferenceByShortName(confShortName.value)
    conferenceId.value = conf.id
    conferenceTitle.value = conf.title || conf.shortName || confShortName.value
    await loadParticipants()
    await loadTemplates()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载邮件页面失败')
  } finally {
    loading.value = false
  }
}

function resetTemplateForm() {
  templateForm.value = {
    sceneCode: '',
    templateName: '',
    subjectTemplate: '',
    htmlTemplate: '',
    textTemplate: '',
    enabled: true,
  }
}

async function handleCreateTemplate() {
  if (!conferenceId.value) {
    ElMessage.error('会议信息未加载完成')
    return
  }

  const sceneCode = templateForm.value.sceneCode.trim()
  const subjectTemplate = templateForm.value.subjectTemplate.trim()

  if (!sceneCode) {
    ElMessage.warning('请输入邮件场景')
    return
  }
  if (!subjectTemplate) {
    ElMessage.warning('请输入邮件主题模板')
    return
  }

  templateSaving.value = true
  try {
    await apiCreateTemplate(conferenceId.value, {
      sceneCode,
      templateName: templateForm.value.templateName.trim() || undefined,
      subjectTemplate,
      htmlTemplate: templateForm.value.htmlTemplate.trim() || undefined,
      textTemplate: templateForm.value.textTemplate.trim() || undefined,
      enabled: templateForm.value.enabled,
    })
    ElMessage.success('模板已保存')
    resetTemplateForm()
    await loadTemplates()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存模板失败')
  } finally {
    templateSaving.value = false
  }
}

async function handleDeleteTemplate(template: MailTemplate) {
  if (!conferenceId.value) {
    ElMessage.error('会议信息未加载完成')
    return
  }

  try {
    await ElMessageBox.confirm(`确认删除模板「${template.templateName || template.sceneCode}」？`, '删除模板', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  templateDeletingIds.value.add(template.id)
  try {
    await apiDeleteTemplate(conferenceId.value, template.id)
    ElMessage.success('模板已删除')
    await loadTemplates()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除模板失败')
  } finally {
    templateDeletingIds.value.delete(template.id)
  }
}

function handleSelectionChange(rows: Participant[]) {
  selectedRows.value = rows
}

function handleBack() {
  router.push(`/dashboard?confId=${encodeURIComponent(confShortName.value)}`)
}

async function handleSend() {
  if (!conferenceId.value) {
    ElMessage.error('会议信息未加载完成')
    return
  }
  if (!form.value.subject.trim()) {
    ElMessage.warning('请输入邮件主题')
    return
  }
  if (!form.value.contentBody.trim()) {
    ElMessage.warning('请输入邮件内容')
    return
  }
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请至少勾选一个发送对象')
    return
  }

  sending.value = true
  try {
    const payload = {
      confId: conferenceId.value,
      subject: form.value.subject.trim(),
      contentBody: form.value.contentBody.trim(),
      recipients: selectedRows.value.map((item) => ({
        name: item.name,
        email: item.email,
      })),
    }

    const result = await apiSendBasicMail(payload)
    ElMessage.success(`发送完成：成功 ${result.successCount || 0}，失败 ${result.failCount || 0}`)
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="mail-compose-page" v-loading="loading">
    <div class="top-bar">
      <el-button :icon="ArrowLeft" text @click="handleBack">返回控制台</el-button>
      <div class="title-wrap">
        <h2>基础邮件发送</h2>
        <p>{{ conferenceTitle }}</p>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="14">
        <div class="panel">
          <h3>邮件内容</h3>
          <el-form label-width="80px">
            <el-form-item label="主题" required>
              <el-input v-model="form.subject" placeholder="请输入邮件主题" />
            </el-form-item>
            <el-form-item label="内容" required>
              <el-input
                v-model="form.contentBody"
                type="textarea"
                :rows="14"
                placeholder="请输入邮件内容（支持 HTML）"
              />
            </el-form-item>
          </el-form>
        </div>
      </el-col>

      <el-col :xs="24" :lg="10">
        <div class="panel">
          <div class="recipient-head">
            <h3>发送对象</h3>
            <el-tag type="info">已选 {{ selectedRows.length }}</el-tag>
          </div>
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索姓名/邮箱/角色"
            class="mb"
          />

          <el-table
            :data="filteredParticipants"
            height="420"
            border
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="50" />
            <el-table-column prop="name" label="姓名" width="120" />
            <el-table-column prop="email" label="邮箱" min-width="220" />
            <el-table-column prop="role" label="角色" width="110" />
          </el-table>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="template-row">
      <el-col :xs="24" :lg="14">
        <div class="panel">
          <div class="panel-head">
            <h3>邮件模板</h3>
            <el-button size="small" :loading="templateLoading" @click="loadTemplates">刷新</el-button>
          </div>
          <el-form label-width="96px">
            <el-form-item label="场景编码" required>
              <el-input v-model="templateForm.sceneCode" placeholder="如 REVIEW_INVITE" />
            </el-form-item>
            <el-form-item label="模板名称">
              <el-input v-model="templateForm.templateName" placeholder="可选：用于识别模板" />
            </el-form-item>
            <el-form-item label="主题模板" required>
              <el-input v-model="templateForm.subjectTemplate" placeholder="如 评审邀请：{{conferenceName}}" />
            </el-form-item>
            <el-form-item label="HTML 正文">
              <el-input
                v-model="templateForm.htmlTemplate"
                type="textarea"
                :rows="6"
                placeholder="可选：HTML 正文模板"
              />
            </el-form-item>
            <el-form-item label="纯文本">
              <el-input
                v-model="templateForm.textTemplate"
                type="textarea"
                :rows="4"
                placeholder="可选：纯文本模板"
              />
            </el-form-item>
            <el-form-item label="是否启用">
              <el-switch v-model="templateForm.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="templateSaving" @click="handleCreateTemplate">保存模板</el-button>
              <el-button @click="resetTemplateForm">清空</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-col>
      <el-col :xs="24" :lg="10">
        <div class="panel">
          <div class="panel-head">
            <h3>模板列表</h3>
          </div>
          <el-table :data="templates" border height="360" v-loading="templateLoading">
            <el-table-column prop="templateName" label="名称" min-width="160" />
            <el-table-column prop="sceneCode" label="场景" width="140" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button
                  type="danger"
                  link
                  :loading="templateDeletingIds.has(row.id)"
                  @click="handleDeleteTemplate(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!templateLoading && templates.length === 0" description="暂无模板" />
        </div>
      </el-col>
    </el-row>

    <div class="actions">
      <el-button @click="handleBack">取消</el-button>
      <el-button type="primary" :loading="sending" @click="handleSend">发送邮件</el-button>
    </div>
  </div>
</template>

<style scoped>
.mail-compose-page {
  max-width: 1360px;
  margin: 0 auto;
  padding: 24px;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.title-wrap h2 {
  margin: 0;
  font-size: 22px;
  color: #0f3d8f;
}

.title-wrap p {
  margin: 3px 0 0;
  color: #64748b;
  font-size: 13px;
}

.panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 16px;
}

.panel h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.recipient-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.template-row {
  margin-top: 20px;
}

.actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.mb {
  margin-bottom: 12px;
}
</style>
