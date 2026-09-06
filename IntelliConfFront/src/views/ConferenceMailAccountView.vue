<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import http from '../utils/http'

interface MailAccountResp {
  id: number
  conferenceId: number
  providerType: string
  fromEmail: string
  fromName?: string
  replyTo?: string
  smtpHost?: string
  smtpPort?: number
  sslEnabled?: boolean
  starttlsEnabled?: boolean
  enabled?: boolean
  lastTestStatus?: string
  lastTestTime?: string
}

const route = useRoute()
const router = useRouter()

const confShortName = computed(() => String(route.query.confId || ''))
const conferenceId = ref<number | null>(null)
const conferenceTitle = ref('')

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)

const formRef = ref<FormInstance>()
const form = ref({
  providerType: 'TENCENT_EXMAIL',
  fromEmail: '',
  fromName: '',
  replyTo: '',
  password: '',
  smtpHost: '',
  smtpPort: 465,
  sslEnabled: true,
  starttlsEnabled: false,
})

const boundAccount = ref<MailAccountResp | null>(null)
const testResult = ref<{ success: boolean; message: string } | null>(null)

const providerOptions = [
  { label: '腾讯企业邮箱', value: 'TENCENT_EXMAIL', host: 'smtp.exmail.qq.com', port: 465, ssl: true, starttls: false },
  { label: 'QQ 邮箱', value: 'QQ', host: 'smtp.qq.com', port: 465, ssl: true, starttls: false },
  { label: '网易 163 邮箱', value: 'NETEASE_163', host: 'smtp.163.com', port: 465, ssl: true, starttls: false },
  { label: 'Gmail', value: 'GMAIL', host: 'smtp.gmail.com', port: 587, ssl: false, starttls: true },
  { label: 'Microsoft Outlook / 365', value: 'OUTLOOK', host: 'smtp.office365.com', port: 587, ssl: false, starttls: true },
  { label: '其他邮箱（自定义 SMTP）', value: 'SMTP', host: '', port: 465, ssl: true, starttls: false }
]

const providerLabelMap: Record<string, string> = {
  TENCENT_EXMAIL: '腾讯企业邮箱',
  SMTP: 'SMTP',
  QQ: 'QQ 邮箱',
  NETEASE_163: '网易 163 邮箱',
  GMAIL: 'Gmail',
  OUTLOOK: 'Microsoft Outlook / 365'
}

const isSupportedProvider = computed(() => true)
const showCustomSmtp = computed(() => form.value.providerType === 'SMTP')

const rules: FormRules = {
  providerType: [{ required: true, message: '请选择邮箱类型', trigger: 'change' }],
  fromEmail: [
    { required: true, message: '请输入发件邮箱', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value || ''))
        callback(ok ? undefined : new Error('请输入正确的邮箱地址'))
      },
      trigger: 'blur'
    }
  ],
  password: [{ required: true, message: '请输入邮箱密码或客户端专用密码', trigger: 'blur' }]
}

async function apiGetConferenceByShortName(shortName: string) {
  const res = await http.get(`/api/intelli-conf/v1/conference/${shortName}`)
  return res.data.data
}

async function apiBindMailAccount(confId: number, payload: any) {
  const res = await http.post(`/api/conferences/${confId}/mail/account`, payload, { timeout: 30000 })
  if (!res.data?.success && res.data?.code !== '0') throw new Error(res.data?.message || '绑定失败')
  return res.data.data
}

async function apiGetMailAccount(confId: number) {
  const res = await http.get(`/api/conferences/${confId}/mail/account`)
  return res.data.data
}

async function apiTestMailAccount(confId: number) {
  const res = await http.post(`/api/conferences/${confId}/mail/account/test`, {}, { timeout: 30000 })
  if (!res.data?.success && res.data?.code !== '0') throw new Error(res.data?.message || '验证失败')
  return res.data.data
}

async function loadData() {
  if (!confShortName.value) {
    ElMessage.error('缺少会议标识，无法加载邮箱配置')
    return
  }

  loading.value = true
  try {
    const conf = await apiGetConferenceByShortName(confShortName.value)
    conferenceId.value = conf.id
    conferenceTitle.value = conf.title || conf.shortName || confShortName.value
    const account = await apiGetMailAccount(conf.id)
    boundAccount.value = account || null
    if (account) {
      form.value.providerType = inferProvider(account)
      form.value.fromEmail = account.fromEmail || ''
      form.value.fromName = account.fromName || ''
      form.value.replyTo = account.replyTo || ''
      form.value.password = ''
      form.value.smtpHost = account.smtpHost || ''
      form.value.smtpPort = account.smtpPort || 465
      form.value.sslEnabled = account.sslEnabled !== false
      form.value.starttlsEnabled = account.starttlsEnabled === true
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载会议信息失败')
  } finally {
    loading.value = false
  }
}

function inferProvider(account: MailAccountResp) {
  if (account.providerType === 'TENCENT_EXMAIL') return 'TENCENT_EXMAIL'
  const option = providerOptions.find(item => item.host && item.host === account.smtpHost)
  return option?.value || 'SMTP'
}

function applyProviderPreset(provider: string) {
  const option = providerOptions.find(item => item.value === provider)
  if (!option || provider === 'SMTP') return
  form.value.smtpHost = option.host
  form.value.smtpPort = option.port
  form.value.sslEnabled = option.ssl
  form.value.starttlsEnabled = option.starttls
  // 切换邮箱服务商时清理上一次绑定的邮箱地址与密码，避免不同服务商账号串用（如163邮箱被带到腾讯企业邮）
  form.value.fromEmail = ''
  form.value.password = ''
}

// 按服务商区分密码输入框提示：腾讯企业邮用登录密码/客户端专用密码，其余用授权码/应用专用密码
const passwordPlaceholder = computed(() => {
  switch (form.value.providerType) {
    case 'TENCENT_EXMAIL':
      return '邮箱登录密码 / 客户端专用密码'
    case 'QQ':
    case 'NETEASE_163':
    case 'GMAIL':
    case 'OUTLOOK':
      return 'SMTP授权码 / 应用专用密码'
    default:
      return 'SMTP授权码 / 应用专用密码 / 登录密码'
  }
})

function resolveProviderLabel(providerType?: string) {
  if (!providerType) {
    return '-'
  }
  return providerLabelMap[providerType] || providerType
}

function connectionStatusLabel(status?: string) {
  if (status === 'SUCCESS') return '验证通过'
  if (status === 'NOT_TESTED') return '新配置尚未验证'
  if (status === 'AUTH_FAILED') return '认证失败'
  if (status === 'CONNECTION_FAILED') return '连接失败'
  if (status === 'TIMEOUT') return '连接超时'
  return status || '尚未测试'
}

function connectionStatusType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'NOT_TESTED' || !status) return 'warning'
  return 'danger'
}

async function handleBind() {
  if (!conferenceId.value) {
    ElMessage.error('会议信息未加载完成')
    return
  }
  if (!formRef.value) {
    return
  }
  if (form.value.providerType !== 'TENCENT_EXMAIL' && !form.value.smtpHost.trim()) {
    ElMessage.warning('请填写SMTP服务器地址')
    return
  }

  await formRef.value.validate(async (valid) => {
    if (!valid) {
      return
    }

    saving.value = true
    try {
      const payload = {
        providerType: form.value.providerType === 'TENCENT_EXMAIL' ? 'TENCENT_EXMAIL' : 'SMTP',
        fromEmail: form.value.fromEmail.trim(),
        username: form.value.fromEmail.trim(),
        fromName: form.value.fromName.trim() || undefined,
        replyTo: form.value.replyTo.trim() || undefined,
        // 授权码复制时常带空格/换行，先清理再提交
        password: form.value.password.replace(/\s+/g, ''),
        smtpHost: form.value.smtpHost.trim(),
        smtpPort: form.value.smtpPort,
        sslEnabled: form.value.sslEnabled,
        starttlsEnabled: form.value.starttlsEnabled,
      }
      boundAccount.value = await apiBindMailAccount(conferenceId.value!, payload)
      form.value.providerType = boundAccount.value ? inferProvider(boundAccount.value) : form.value.providerType
      testResult.value = { success: true, message: '新配置通过SMTP认证并已保存；实际投递以发送结果为准' }
      form.value.password = ''
      ElMessage.success('会议邮箱已绑定并验证成功')
    } catch (err: any) {
      testResult.value = { success: false, message: err.response?.data?.message || err.message || '绑定失败' }
      ElMessage.error(testResult.value.message)
    } finally {
      saving.value = false
    }
  })
}

async function handleTest() {
  if (!conferenceId.value) {
    ElMessage.error('会议信息未加载完成')
    return
  }
  if (!boundAccount.value) {
    ElMessage.warning('请先保存绑定邮箱配置')
    return
  }

  testing.value = true
  try {
    const result = await apiTestMailAccount(conferenceId.value)
    if (result?.success) {
      if (boundAccount.value) {
        boundAccount.value.lastTestStatus = 'SUCCESS'
        boundAccount.value.lastTestTime = new Date().toISOString()
      }
      testResult.value = { success: true, message: '连接成功，邮件系统可正常发送' }
      ElMessage.success('连接成功')
    } else {
      if (boundAccount.value) {
        boundAccount.value.lastTestStatus = result?.errorCode || 'CONNECTION_FAILED'
        boundAccount.value.lastTestTime = new Date().toISOString()
      }
      testResult.value = { success: false, message: result?.errorMessage || '连接失败，请检查邮箱配置' }
      ElMessage.error(testResult.value.message)
    }
  } catch (err: any) {
    const message = err.response?.data?.message || '连接测试失败，请稍后重试'
    testResult.value = { success: false, message }
    ElMessage.error(message)
  } finally {
    testing.value = false
  }
}

function handleBack() {
  router.push(`/dashboard?confId=${encodeURIComponent(confShortName.value)}`)
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="mail-account-page" v-loading="loading">
    <div class="top-bar">
      <el-button :icon="ArrowLeft" text @click="handleBack">返回控制台</el-button>
      <div class="title-wrap">
        <h2>会议邮箱绑定</h2>
        <p>{{ conferenceTitle }}</p>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="14">
        <div class="panel">
          <h3>绑定会议邮箱</h3>
          <el-alert
            type="info"
            show-icon
            :closable="false"
            class="mb"
            title="支持腾讯企业邮箱、QQ、163、Gmail、Outlook及其他标准SMTP邮箱"
          />

          <el-alert v-if="form.providerType === 'TENCENT_EXMAIL'" class="mb" type="warning" :closable="false" title="腾讯企业邮箱：填写该邮箱的登录密码（不是QQ授权码）。若管理后台开启了「客户端专用密码」，请在腾讯企业邮箱管理端生成专用密码后填写。" />
          <el-alert v-else-if="form.providerType === 'QQ'" class="mb" type="warning" :closable="false" title="QQ邮箱：请在网页版「设置→账户」开启SMTP并生成授权码，填写授权码（非QQ登录密码）。" />
          <el-alert v-else-if="form.providerType === 'NETEASE_163'" class="mb" type="warning" :closable="false" title="网易163：请先在网页版设置中开启SMTP并生成客户端授权码；验证失败时不会覆盖原有绑定。" />
          <el-alert v-else-if="form.providerType === 'GMAIL'" class="mb" type="warning" :closable="false" title="Gmail：需先开启两步验证，再生成16位应用专用密码（App Password），去除分组空格后填写。" />
          <el-alert v-else-if="form.providerType === 'OUTLOOK'" class="mb" type="warning" :closable="false" title="Outlook/365：确认管理员已开启SMTP AUTH；开启了两步验证则需使用应用专用密码。" />
          <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
            <el-form-item label="邮箱类型" prop="providerType">
              <el-select v-model="form.providerType" placeholder="请选择邮箱类型" class="w-full" @change="applyProviderPreset">
                <el-option
                  v-for="option in providerOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <template v-if="isSupportedProvider">
              <el-form-item label="发件邮箱" prop="fromEmail">
                <el-input v-model="form.fromEmail" placeholder="例：noreply@yourdomain.com" />
              </el-form-item>
              <el-form-item label="授权码/密码" prop="password">
                <el-input
                  v-model="form.password"
                  type="password"
                  show-password
                  :placeholder="passwordPlaceholder"
                />
              </el-form-item>
              <template v-if="showCustomSmtp">
                <el-form-item label="SMTP服务器" required>
                  <el-input v-model="form.smtpHost" placeholder="例如 smtp.yourdomain.com" />
                </el-form-item>
                <el-form-item label="SMTP端口" required>
                  <el-input-number v-model="form.smtpPort" :min="1" :max="65535" style="width: 100%" />
                </el-form-item>
                <el-form-item label="连接安全">
                  <el-radio-group v-model="form.sslEnabled">
                    <el-radio :value="true" @click="form.starttlsEnabled = false">SSL/TLS</el-radio>
                    <el-radio :value="false" @click="form.starttlsEnabled = true">STARTTLS</el-radio>
                  </el-radio-group>
                </el-form-item>
              </template>
              <el-form-item label="发件人名称">
                <el-input v-model="form.fromName" placeholder="例：会议组委会（可选）" />
              </el-form-item>
              <el-form-item label="回复邮箱">
                <el-input v-model="form.replyTo" placeholder="不填则默认发件邮箱" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="saving" @click="handleBind">保存并验证</el-button>
                <el-button :loading="testing" @click="handleTest">测试连接</el-button>
              </el-form-item>
            </template>
          </el-form>
        </div>
      </el-col>

      <el-col :xs="24" :lg="10">
        <div class="panel">
          <h3>配置提示</h3>
          <ul class="tips">
            <li>邮箱需要开启SMTP服务；多数服务商要求使用“SMTP授权码”或“应用专用密码”，而不是网页登录密码。</li>
            <li>绑定成功后，会议生命周期内邮件将由该邮箱统一发送。</li>
            <li>常见邮箱会自动填写服务器和端口；机构邮箱可选择“自定义SMTP”。</li>
            <li>保存后务必执行连接测试，测试成功才建议用于自动邮件。</li>
          </ul>

          <div class="status-card">
            <div class="status-head">
              <h4>当前绑定状态</h4>
              <el-tag v-if="boundAccount" type="success" effect="plain">已绑定</el-tag>
              <el-tag v-else type="info" effect="plain">未绑定</el-tag>
            </div>

            <div v-if="boundAccount" class="status-body">
              <p><span>发件邮箱：</span>{{ boundAccount.fromEmail }}</p>
              <p><span>邮箱类型：</span>{{ resolveProviderLabel(boundAccount.providerType) }}</p>
              <p><span>回复邮箱：</span>{{ boundAccount.replyTo || '默认发件邮箱' }}</p>
              <p>
                <span>连接状态：</span>
                <el-tag :type="connectionStatusType(boundAccount.lastTestStatus)" size="small" effect="plain">
                  {{ connectionStatusLabel(boundAccount.lastTestStatus) }}
                </el-tag>
              </p>
              <p><span>最近验证：</span>{{ boundAccount.lastTestTime ? boundAccount.lastTestTime.replace('T', ' ').slice(0, 19) : '尚未验证' }}</p>
            </div>
            <el-empty v-else description="尚未绑定会议邮箱" />

            <el-alert
              v-if="testResult"
              class="mt"
              :type="testResult.success ? 'success' : 'error'"
              show-icon
              :closable="false"
              :title="testResult.message"
            />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.mail-account-page {
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

.tips {
  margin: 0 0 16px;
  padding-left: 18px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.7;
}

.status-card {
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px;
}

.status-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.status-body p {
  margin: 6px 0;
  color: #1f2937;
  font-size: 13px;
}

.status-body span {
  color: #64748b;
}

.mb {
  margin-bottom: 12px;
}

.mt {
  margin-top: 12px;
}

.w-full {
  width: 100%;
}
</style>
