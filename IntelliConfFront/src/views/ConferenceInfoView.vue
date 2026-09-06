<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import http from '../utils/http'

const route = useRoute()
const router = useRouter()
const confId = computed(() => String(route.query.confId || ''))
const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  title: '',
  shortName: '',
  description: '',
  websiteUrl: '',
  contactEmail: '',
  host: '',
  coOriganizer: '',
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
  shortName: [{ required: true, message: '会议简称不能为空', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择会议开始日期', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择会议结束日期', trigger: 'change' }],
  city: [{ required: true, message: '请输入举办城市', trigger: 'blur' }],
  contactEmail: [
    { required: true, message: '请输入联系邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
  websiteUrl: [{ required: true, message: '请输入会议网站', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主办单位', trigger: 'blur' }],
}

function isSuccess(resp: any) {
  return resp?.success || resp?.code === '0'
}

async function loadConferenceDetail() {
  if (!confId.value) {
    ElMessage.error('缺少会议标识')
    router.push('/portal')
    return
  }

  loading.value = true
  try {
    const response = await http.get(`/api/intelli-conf/v1/conference/${confId.value}`)
    if (!isSuccess(response.data)) {
      ElMessage.error(response.data?.message || '加载会议信息失败')
      return
    }

    const data = response.data.data || {}
    form.title = data.title || ''
    form.shortName = data.shortName || confId.value
    form.description = data.description || ''
    form.websiteUrl = data.websiteUrl || ''
    form.contactEmail = data.contactEmail || ''
    form.host = data.host || ''
    form.coOriganizer = data.coOriganizer || ''
    form.startTime = data.startTime || ''
    form.endTime = data.endTime || ''
    form.country = data.country || '中国'
    form.province = data.province || ''
    form.city = data.city || ''
    form.address = data.address || ''
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载会议信息失败')
  } finally {
    loading.value = false
  }
}

async function saveConference() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (new Date(form.endTime) < new Date(form.startTime)) {
      ElMessage.error('会议结束日期不能早于开始日期')
      return
    }

    saving.value = true
    try {
      const response = await http.put('/api/intelli-conf/v1/conference', {
        title: form.title.trim(),
        shortName: form.shortName,
        description: form.description.trim() || undefined,
        websiteUrl: form.websiteUrl.trim(),
        contactEmail: form.contactEmail.trim(),
        host: form.host.trim(),
        coOriganizer: form.coOriganizer.trim() || undefined,
        startTime: form.startTime,
        endTime: form.endTime,
        country: form.country.trim() || undefined,
        province: form.province.trim() || undefined,
        city: form.city.trim(),
        address: form.address.trim() || undefined,
      })
      if (isSuccess(response.data)) {
        ElMessage.success('会议信息已保存')
        return
      }
      ElMessage.error(response.data?.message || '保存会议信息失败')
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '保存会议信息失败')
    } finally {
      saving.value = false
    }
  })
}

function goBack() {
  router.push(`/dashboard?confId=${encodeURIComponent(confId.value)}`)
}

onMounted(loadConferenceDetail)
</script>

<template>
  <div class="info-page" v-loading="loading">
    <header class="top-bar">
      <el-button :icon="ArrowLeft" text @click="goBack">返回控制台</el-button>
      <div>
        <h1>会议信息</h1>
        <p>维护会议官网、主办方、地点与基础日期</p>
      </div>
    </header>

    <section class="form-panel">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
        <el-row :gutter="18">
          <el-col :xs="24" :md="12">
            <el-form-item label="会议名称" prop="title">
              <el-input v-model="form.title" placeholder="会议名称" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="会议简称" prop="shortName">
              <el-input v-model="form.shortName" disabled />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="开始日期" prop="startTime">
              <el-date-picker v-model="form.startTime" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="结束日期" prop="endTime">
              <el-date-picker v-model="form.endTime" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="联系邮箱" prop="contactEmail">
              <el-input v-model="form.contactEmail" placeholder="contact@example.com" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="会议网站" prop="websiteUrl">
              <el-input v-model="form.websiteUrl" placeholder="https://example.com" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="主办单位" prop="host">
              <el-input v-model="form.host" placeholder="主办单位" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="协办单位">
              <el-input v-model="form.coOriganizer" placeholder="可选" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="国家/地区">
              <el-input v-model="form.country" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="省份">
              <el-input v-model="form.province" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="城市" prop="city">
              <el-input v-model="form.city" />
            </el-form-item>
          </el-col>
          <el-col :xs="24">
            <el-form-item label="详细地址">
              <el-input v-model="form.address" />
            </el-form-item>
          </el-col>
          <el-col :xs="24">
            <el-form-item label="会议简介">
              <el-input v-model="form.description" type="textarea" :rows="5" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="actions">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveConference">保存</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.info-page {
  min-height: 100vh;
  padding: 28px;
  background: #f4f7fb;
}

.top-bar {
  max-width: 1180px;
  margin: 0 auto 18px;
  display: flex;
  align-items: center;
  gap: 18px;
}

.top-bar h1 {
  margin: 0;
  font-size: 24px;
}

.top-bar p {
  margin: 5px 0 0;
  color: #64748b;
}

.form-panel {
  max-width: 1180px;
  margin: 0 auto;
  padding: 22px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.06);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
}
</style>
