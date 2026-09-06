<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Download, Picture, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import http from '../utils/http'

type PageMode = 'last' | 'all' | 'number'
type Position = 'bottom-right' | 'bottom-left' | 'top-right' | 'top-left' | 'center' | 'manual'

const router = useRouter()
const pdfFile = ref<File | null>(null)
const customSealFile = ref<File | null>(null)
const previewUrl = ref('')
const previewLoading = ref(false)
const stamping = ref(false)

const form = reactive({
  name: '',
  centerText: '电子专用章',
  pageMode: 'last' as PageMode,
  pageNumber: 1,
  position: 'bottom-right' as Position,
  x: 36,
  y: 36,
  size: 120,
  opacity: 82,
})

const canStamp = computed(() => Boolean(
  pdfFile.value && (customSealFile.value || form.name.trim()) && !stamping.value,
))

function revokePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function choosePdf(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] || null
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.pdf') || file.type && file.type !== 'application/pdf') {
    ElMessage.error('请选择 PDF 文件')
    input.value = ''
    return
  }
  if (file.size > 25 * 1024 * 1024) {
    ElMessage.error('PDF 文件不能超过 25 MB')
    input.value = ''
    return
  }
  pdfFile.value = file
}

function chooseCustomSeal(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] || null
  if (!file) return
  if (file.type !== 'image/png' || !file.name.toLowerCase().endsWith('.png')) {
    ElMessage.error('自定义印章仅支持透明 PNG 图片')
    input.value = ''
    return
  }
  if (file.size > 4 * 1024 * 1024) {
    ElMessage.error('自定义印章图片不能超过 4 MB')
    input.value = ''
    return
  }
  customSealFile.value = file
  revokePreview()
  previewUrl.value = URL.createObjectURL(file)
}

function clearCustomSeal() {
  customSealFile.value = null
  revokePreview()
}

async function messageFromBlob(blob: Blob, fallback: string) {
  try {
    const payload = JSON.parse(await blob.text())
    return payload?.message || fallback
  } catch {
    return fallback
  }
}

async function previewSeal() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入印章环形名称')
    return
  }
  previewLoading.value = true
  try {
    const data = new FormData()
    data.append('name', form.name.trim())
    data.append('centerText', form.centerText.trim() || '电子专用章')
    const response = await http.post('/api/intelli-conf/v1/electronic-seal/preview', data, {
      responseType: 'blob',
      timeout: 30000,
    })
    const contentType = String(response.headers['content-type'] || '')
    if (!contentType.includes('image/png')) {
      throw new Error(await messageFromBlob(response.data, '生成印章预览失败'))
    }
    revokePreview()
    previewUrl.value = URL.createObjectURL(response.data)
  } catch (error: any) {
    const message = error.response?.data instanceof Blob
      ? await messageFromBlob(error.response.data, '生成印章预览失败')
      : error.message || '生成印章预览失败'
    ElMessage.error(message)
  } finally {
    previewLoading.value = false
  }
}

function appendOptions(data: FormData) {
  if (form.name.trim()) data.append('name', form.name.trim())
  data.append('centerText', form.centerText.trim() || '电子专用章')
  data.append('page', form.pageMode === 'number' ? String(form.pageNumber) : form.pageMode)
  data.append('position', form.position)
  data.append('size', String(form.size))
  data.append('opacity', String(form.opacity / 100))
  if (form.position === 'manual') {
    data.append('x', String(form.x))
    data.append('y', String(form.y))
  }
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

async function stampPdf() {
  if (!pdfFile.value) {
    ElMessage.warning('请选择 PDF 文件')
    return
  }
  if (!customSealFile.value && !form.name.trim()) {
    ElMessage.warning('请输入印章环形名称，或上传自定义印章')
    return
  }

  stamping.value = true
  try {
    const data = new FormData()
    data.append('file', pdfFile.value)
    if (customSealFile.value) data.append('sealImage', customSealFile.value)
    appendOptions(data)
    const response = await http.post('/api/intelli-conf/v1/electronic-seal/stamp', data, {
      responseType: 'blob',
      timeout: 120000,
    })
    const contentType = String(response.headers['content-type'] || '')
    if (!contentType.includes('application/pdf')) {
      throw new Error(await messageFromBlob(response.data, '生成盖章 PDF 失败'))
    }
    const baseName = pdfFile.value.name.replace(/\.pdf$/i, '')
    downloadBlob(response.data, `${baseName}-盖章.pdf`)
    ElMessage.success('盖章完成，文件已下载')
  } catch (error: any) {
    const message = error.response?.data instanceof Blob
      ? await messageFromBlob(error.response.data, '生成盖章 PDF 失败')
      : error.message || '生成盖章 PDF 失败'
    ElMessage.error(message)
  } finally {
    stamping.value = false
  }
}

onBeforeUnmount(revokePreview)
</script>

<template>
  <div class="seal-page">
    <header class="seal-header">
      <el-button :icon="ArrowLeft" text @click="router.push('/portal')">我的会议</el-button>
      <div class="header-copy">
        <p>IntelliConf / 文档工具</p>
        <h1>电子印章</h1>
        <span>为 PDF 添加可视化印章图层并下载结果</span>
      </div>
      <div class="header-index">01</div>
    </header>

    <main class="seal-grid">
      <section class="form-panel">
        <div class="section-heading">
          <span>01</span>
          <div><h2>印章内容</h2><p>生成标准圆形红章，或使用已有透明 PNG。</p></div>
        </div>

        <el-form label-position="top" class="seal-form">
          <el-form-item label="印章环形名称">
            <el-input v-model="form.name" maxlength="40" show-word-limit placeholder="例如：智能教育国际会议组委会" />
          </el-form-item>
          <el-form-item label="中心文字">
            <el-input v-model="form.centerText" maxlength="12" show-word-limit />
          </el-form-item>

          <div class="button-row">
            <el-button :icon="Picture" :loading="previewLoading" @click="previewSeal">预览印章</el-button>
            <label class="file-button">
              <input type="file" accept="image/png,.png" @change="chooseCustomSeal" />
              上传自定义印章
            </label>
            <el-button v-if="customSealFile" text type="danger" @click="clearCustomSeal">移除自定义印章</el-button>
          </div>

          <div class="section-heading compact">
            <span>02</span>
            <div><h2>PDF 与位置</h2><p>坐标单位为点，原点位于页面左下角。</p></div>
          </div>

          <label class="pdf-drop">
            <input type="file" accept="application/pdf,.pdf" @change="choosePdf" />
            <el-icon><UploadFilled /></el-icon>
            <strong>{{ pdfFile?.name || '选择 PDF 文件' }}</strong>
            <span>{{ pdfFile ? `${(pdfFile.size / 1024 / 1024).toFixed(2)} MB` : '最大 25 MB' }}</span>
          </label>

          <div class="field-grid">
            <el-form-item label="盖章页">
              <el-select v-model="form.pageMode">
                <el-option label="最后一页" value="last" />
                <el-option label="全部页面" value="all" />
                <el-option label="指定页码" value="number" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.pageMode === 'number'" label="页码">
              <el-input-number v-model="form.pageNumber" :min="1" :max="500" controls-position="right" />
            </el-form-item>
            <el-form-item label="位置">
              <el-select v-model="form.position">
                <el-option label="右下角" value="bottom-right" />
                <el-option label="左下角" value="bottom-left" />
                <el-option label="右上角" value="top-right" />
                <el-option label="左上角" value="top-left" />
                <el-option label="页面中央" value="center" />
                <el-option label="手动坐标" value="manual" />
              </el-select>
            </el-form-item>
            <template v-if="form.position === 'manual'">
              <el-form-item label="X 坐标">
                <el-input-number v-model="form.x" :min="0" :max="2000" controls-position="right" />
              </el-form-item>
              <el-form-item label="Y 坐标">
                <el-input-number v-model="form.y" :min="0" :max="2000" controls-position="right" />
              </el-form-item>
            </template>
          </div>

          <el-form-item :label="`印章大小：${form.size} 点`">
            <el-slider v-model="form.size" :min="36" :max="300" :step="4" />
          </el-form-item>
          <el-form-item :label="`不透明度：${form.opacity}%`">
            <el-slider v-model="form.opacity" :min="10" :max="100" />
          </el-form-item>
        </el-form>

        <div class="action-bar">
          <div><strong>输出 PDF</strong><span>文件仅在本次请求中处理，不保存到服务器。</span></div>
          <el-button :icon="Download" type="primary" size="large" :disabled="!canStamp" :loading="stamping" @click="stampPdf">
            生成盖章 PDF
          </el-button>
        </div>
      </section>

      <aside class="proof-panel">
        <div class="proof-label"><span>印章预览</span><span>800 × 800 PNG</span></div>
        <div class="proof-canvas">
          <img v-if="previewUrl" :src="previewUrl" alt="电子印章预览" />
          <div v-else class="proof-empty">
            <el-icon><Picture /></el-icon>
            <span>填写名称后生成预览</span>
          </div>
        </div>
        <dl>
          <div><dt>类型</dt><dd>PDF 可视化图层</dd></div>
          <div><dt>混合模式</dt><dd>Multiply</dd></div>
          <div><dt>默认位置</dt><dd>右下角，36 点边距</dd></div>
          <div><dt>文件处理</dt><dd>请求完成后不留存</dd></div>
        </dl>
        <p class="scope-note">该功能添加可视化印章，不包含 CA 证书签名、可信时间戳或签名有效性校验。</p>
      </aside>
    </main>
  </div>
</template>

<style scoped>
:global(body) { margin:0; }
.seal-page { min-height:100vh; color:#111827; background:#f7f7f8; font-family:"Helvetica Neue",Arial,"Microsoft YaHei",sans-serif; }
.seal-page *, .seal-page *::before, .seal-page *::after { box-sizing:border-box; }
.seal-header { display:grid; grid-template-columns:160px minmax(0,1fr) 120px; min-height:160px; border-bottom:1px solid #cfd4dc; background:#fff; }
.seal-header > * { padding:28px; border-right:1px solid #cfd4dc; }
.seal-header > :last-child { border-right:0; }
.header-copy p { margin:0 0 12px; color:#e4002b; font-size:12px; font-weight:800; letter-spacing:.08em; text-transform:uppercase; }
.header-copy h1 { margin:0; font-size:42px; line-height:1; letter-spacing:-.04em; }
.header-copy span { display:block; margin-top:14px; color:#566174; font-size:14px; }
.header-index { display:flex; align-items:flex-end; justify-content:flex-end; color:#e4002b; font-size:54px; font-weight:800; line-height:.8; }
.seal-grid { display:grid; grid-template-columns:minmax(0,1fr) 420px; max-width:1440px; margin:0 auto; border-left:1px solid #cfd4dc; border-right:1px solid #cfd4dc; background:#fff; }
.form-panel { min-width:0; }
.form-panel,.proof-panel { padding:32px; }
.proof-panel { border-left:1px solid #cfd4dc; background:#f7f7f8; }
.section-heading { display:grid; grid-template-columns:54px minmax(0,1fr); gap:16px; padding-bottom:22px; border-bottom:1px solid #111827; }
.section-heading.compact { margin-top:34px; }
.section-heading > span { color:#e4002b; font-size:28px; font-weight:800; }
.section-heading h2 { margin:0; font-size:22px; letter-spacing:-.02em; }
.section-heading p { margin:6px 0 0; color:#687386; font-size:13px; }
.seal-form { padding-top:24px; }
.field-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 18px; }
.field-grid :deep(.el-select),.field-grid :deep(.el-input-number) { width:100%; }
.button-row { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
.file-button { height:32px; padding:6px 15px; border:1px solid #dcdfe6; color:#606266; background:#fff; cursor:pointer; font-size:14px; }
.file-button:hover { color:#e4002b; border-color:#e4002b; }
.file-button input,.pdf-drop input { display:none; }
.pdf-drop { display:grid; grid-template-columns:42px minmax(0,1fr) auto; align-items:center; gap:14px; min-height:86px; margin:24px 0; padding:18px; border:1px dashed #8b95a5; cursor:pointer; }
.pdf-drop:hover { border-color:#e4002b; }
.pdf-drop .el-icon { color:#e4002b; font-size:28px; }
.pdf-drop strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:14px; }
.pdf-drop span { color:#687386; font-size:12px; }
.action-bar { display:flex; justify-content:space-between; align-items:center; gap:24px; margin:32px -32px -32px; padding:24px 32px; border-top:1px solid #cfd4dc; background:#f7f7f8; }
.action-bar div { display:grid; gap:4px; }
.action-bar span { color:#687386; font-size:12px; }
.proof-label { display:flex; justify-content:space-between; padding-bottom:12px; border-bottom:1px solid #111827; font-size:12px; font-weight:700; text-transform:uppercase; }
.proof-canvas { display:grid; place-items:center; aspect-ratio:1; margin:28px 0; border:1px solid #cfd4dc; background-color:#fff; background-image:linear-gradient(#eceef2 1px,transparent 1px),linear-gradient(90deg,#eceef2 1px,transparent 1px); background-size:24px 24px; }
.proof-canvas img { width:78%; height:78%; object-fit:contain; }
.proof-empty { display:grid; place-items:center; gap:10px; color:#8b95a5; font-size:13px; }
.proof-empty .el-icon { font-size:40px; }
.proof-panel dl { margin:0; border-top:1px solid #cfd4dc; }
.proof-panel dl div { display:grid; grid-template-columns:110px 1fr; gap:14px; padding:12px 0; border-bottom:1px solid #cfd4dc; font-size:13px; }
.proof-panel dt { color:#687386; }
.proof-panel dd { margin:0; font-weight:600; }
.scope-note { margin:24px 0 0; padding:16px; border-left:4px solid #e4002b; color:#4b5565; background:#fff; font-size:12px; line-height:1.7; }
:deep(.el-button--primary) { --el-button-bg-color:#e4002b; --el-button-border-color:#e4002b; --el-button-hover-bg-color:#bc0024; --el-button-hover-border-color:#bc0024; }
:deep(.el-slider) { --el-slider-main-bg-color:#e4002b; }
@media (max-width:960px) {
  .seal-header { grid-template-columns:120px minmax(0,1fr); }
  .header-index { display:none; }
  .seal-grid { grid-template-columns:1fr; border:0; }
  .proof-panel { border-top:1px solid #cfd4dc; border-left:0; }
  .proof-canvas { max-width:420px; }
}
@media (max-width:600px) {
  .seal-header { grid-template-columns:1fr; }
  .seal-header > * { padding:20px; border-right:0; border-bottom:1px solid #cfd4dc; }
  .header-copy h1 { font-size:34px; }
  .form-panel,.proof-panel { padding:20px; }
  .field-grid { grid-template-columns:1fr; }
  .action-bar { align-items:stretch; flex-direction:column; margin:24px -20px -20px; padding:20px; }
  .action-bar .el-button { width:100%; }
}
</style>
