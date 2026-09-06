<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../utils/http'

interface Candidate { id: string; authorName: string; email?: string; organization?: string; countryRegion?: string; overallScore: number; reviewStatus: string; sourceUrl?: string; representativePapers?: string }
interface Job { jobId: string; status: string; totalPapers: number; totalCandidates: number; highConfidenceEmails: number; errorMessage?: string }
const route = useRoute()
const router = useRouter()
const conferenceId = ref('')
const title = ref('')
const keywords = ref('')
const target = ref(200)
const yearFrom = ref(new Date().getFullYear() - 5)
const yearTo = ref(new Date().getFullYear())
const rows = ref<Candidate[]>([])
const job = ref<Job | null>(null)
const starting = ref(false)
const loading = ref(false)
const cancelling = ref(false)
const problem = ref('')
const selected = ref('PENDING')
const busy = computed(() => starting.value || ['PENDING', 'RUNNING'].includes(job.value?.status || ''))
const visibleRows = computed(() => selected.value ? rows.value.filter(r => r.reviewStatus === selected.value) : rows.value)
const storageKey = computed(() => 'author-discovery-job:' + conferenceId.value)
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false
function data(response: any) {
  if (!response.data?.success && response.data?.code !== '0') throw new Error(response.data?.message || '请求失败')
  return response.data.data
}
function errorText(e: any) { return e.response?.data?.message || e.message || '请求失败' }
function safeLink(value?: string) {
  try { const url = new URL(value || ''); return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined } catch { return undefined }
}
function papers(value?: string): Array<{title: string; publicationYear?: number}> {
  try { return JSON.parse(value || '[]').slice(0, 5) } catch { return [] }
}
async function refreshRows() {
  rows.value = data(await http.get('/api/conferences/' + conferenceId.value + '/potential-authors'))
}
async function poll() {
  clearTimeout(timer)
  if (!job.value || disposed) return
  try {
    job.value = data(await http.get('/api/author-discovery/jobs/' + job.value.jobId))
    await refreshRows()
    problem.value = ''
  } catch (e) { problem.value = errorText(e) }
  if (!disposed && busy.value) timer = setTimeout(poll, 5000)
}
async function start() {
  if (busy.value) return
  const topics = keywords.value.split(/[\n;；]+/).map(s => s.trim()).filter(Boolean)
  if (!topics.length || yearFrom.value > yearTo.value) { ElMessage.warning('请填写研究主题和有效年份范围'); return }
  starting.value = true
  try {
    const payload = { topicKeywords: topics, yearFrom: yearFrom.value, yearTo: yearTo.value, maxAuthors: target.value, enableCrossref: true, enableEmailExtraction: true }
    job.value = data(await http.post('/api/conferences/' + conferenceId.value + '/author-discovery/jobs', payload))
    localStorage.setItem(storageKey.value, JSON.stringify({ jobId: job.value!.jobId, target: target.value }))
    job.value = data(await http.post('/api/author-discovery/jobs/' + job.value!.jobId + '/start', payload))
    ElMessage.success('发现任务已提交，您可以离开页面，稍后回来查看结果')
  } catch (e) { problem.value = errorText(e) }
  finally { starting.value = false }
  await poll()
}
async function stop() {
  if (!job.value || cancelling.value) return
  cancelling.value = true
  try {
    job.value = data(await http.post('/api/author-discovery/jobs/' + job.value.jobId + '/cancel'))
    ElMessage.success('已停止任务，已保存的结果会保留')
  } catch (e) { problem.value = errorText(e) }
  finally { cancelling.value = false }
  await poll()
}
async function review(row: Candidate, action: string) {
  try {
    data(await http.post('/api/potential-authors/' + row.id + '/' + action))
    await refreshRows()
    ElMessage.success(action === 'approve' ? '已加入潜在投稿者名单' : '已拒绝')
  } catch (e) { ElMessage.error(errorText(e)) }
}
onMounted(async () => {
  loading.value = true
  try {
    const conf = data(await http.get('/api/intelli-conf/v1/conference/' + encodeURIComponent(String(route.query.confId || ''))))
    conferenceId.value = String(conf.id)
    title.value = conf.title
    keywords.value = /education|教育/i.test(conf.title) && /intelligen|智能/i.test(conf.title)
      ? 'artificial intelligence in education\nintelligent tutoring systems\nlearning analytics\neducational data mining'
      : conf.title
    const saved = localStorage.getItem(storageKey.value)
    if (saved) {
      try {
        const value = JSON.parse(saved)
        target.value = value.target || 200
        job.value = data(await http.get('/api/author-discovery/jobs/' + value.jobId))
      } catch { localStorage.removeItem(storageKey.value) }
    }
    const latest = data(await http.get('/api/conferences/' + conferenceId.value + '/author-discovery/latest'))
    if (latest) job.value = latest
    await refreshRows()
    if (busy.value) await poll()
  } catch (e) { problem.value = errorText(e) }
  finally { loading.value = false }
})
onBeforeUnmount(() => { disposed = true; clearTimeout(timer) })
</script>

<template>
  <main class="discovery-page" v-loading="loading">
    <header>
      <el-button @click="router.push({ path: '/dashboard', query: { confId: route.query.confId } })">返回会议工作台</el-button>
      <h1>潜在投稿者发现</h1>
      <p>{{ title }}</p>
    </header>
    <el-alert v-if="problem" :title="problem" type="error" :closable="false" />
    <section class="search-panel">
      <h2>按研究主题寻找作者</h2>
      <p>每行填写一个具体研究方向。检索公开论文与全文，核验作者和邮箱的对应关系后供您审核。</p>
      <el-input v-model="keywords" type="textarea" :rows="4" :disabled="busy" placeholder="例如：intelligent tutoring systems" />
      <div class="controls">
        <label>目标可信邮箱数 <el-input-number v-model="target" :min="1" :max="500" :disabled="busy" /></label>
        <label>起始年份 <el-input-number v-model="yearFrom" :min="1900" :max="yearTo" :disabled="busy" /></label>
        <label>结束年份 <el-input-number v-model="yearTo" :min="yearFrom" :max="new Date().getFullYear()" :disabled="busy" /></label>
        <el-button type="primary" :loading="busy" @click="start">开始发现</el-button>
      </div>
    </section>
    <section v-if="job" class="progress-panel">
      <div class="metrics">
        <div><strong>{{ job.totalPapers || 0 }}</strong>检索论文</div>
        <div><strong>{{ job.totalCandidates || 0 }} / {{ target }}</strong>去重可信邮箱</div>
        <div><strong>{{ job.highConfidenceEmails || 0 }}</strong>高可信邮箱</div>
      </div>
      <el-progress :percentage="Math.min(100, Math.round((job.totalCandidates || 0) / target * 100))" />
      <p>{{ { PENDING: '排队中', RUNNING: '后台检索中，可稍后回来查看', COMPLETED: '本轮目标已完成', PARTIAL: '本轮检索结束，未达到目标', CANCELLED: '任务已停止，可再次发起继续补充', FAILED: '任务失败' }[job.status] || job.status }}</p>
      <p v-if="job.errorMessage">{{ job.errorMessage }}</p>
      <div>
        <el-button @click="poll">刷新进度</el-button>
        <el-button v-if="['PENDING', 'RUNNING'].includes(job.status)" type="danger" plain :loading="cancelling" @click="stop">停止任务</el-button>
      </div>
    </section>
    <section>
      <div class="table-heading"><h2>候选作者 · {{ visibleRows.length }}</h2>
        <el-select v-model="selected" style="width:160px"><el-option label="待审核" value="PENDING" /><el-option label="已通过" value="APPROVED" /><el-option label="已拒绝" value="REJECTED" /><el-option label="全部结果" value="" /></el-select>
        <el-button @click="refreshRows">刷新候选</el-button>
      </div>
      <el-table :data="visibleRows" max-height="650" row-key="id">
        <el-table-column type="expand"><template #default="{row}"><ul><li v-for="paper in papers(row.representativePapers)" :key="paper.title">{{ paper.title }} ({{ paper.publicationYear }})</li></ul></template></el-table-column>
        <el-table-column prop="authorName" label="作者" min-width="150" />
        <el-table-column prop="email" label="公开邮箱" min-width="220" />
        <el-table-column prop="organization" label="机构" min-width="220" />
        <el-table-column prop="countryRegion" label="地区" width="80" />
        <el-table-column label="综合分" width="85"><template #default="{row}">{{ Math.round(row.overallScore * 100) }}%</template></el-table-column>
        <el-table-column label="证据来源" width="100"><template #default="{row}"><a v-if="safeLink(row.sourceUrl)" :href="safeLink(row.sourceUrl)" target="_blank" rel="noopener noreferrer">查看来源</a></template></el-table-column>
        <el-table-column label="操作" width="160"><template #default="{row}"><template v-if="row.reviewStatus === 'PENDING'"><el-button link type="primary" @click="review(row, 'approve')">通过</el-button><el-button link type="danger" @click="review(row, 'reject')">拒绝</el-button></template><span v-else>{{ row.reviewStatus === 'APPROVED' ? '已通过' : '已拒绝' }}</span></template></el-table-column>
      </el-table>
    </section>
  </main>
</template>

<style scoped>
.discovery-page { max-width:1400px; margin:auto; padding:28px; color:#243047; background:#f5f7fb; min-height:100vh }
header p, section > p { color:#69758a; line-height:1.7 }
h1 { margin-bottom:6px } h2 { font-size:18px }
section { background:white; padding:24px; border:1px solid #e3e8f0; border-radius:16px; margin-top:20px }
.controls,.table-heading,.metrics { display:flex; gap:20px; align-items:center; flex-wrap:wrap; margin:18px 0 }
.controls label { display:flex; flex-direction:column; gap:8px; font-size:13px }
.metrics { justify-content:space-between } .metrics div { display:grid; gap:6px; color:#68748a } .metrics strong { font-size:26px; color:#315fce }
li { line-height:1.8 } a { color:#315fce }
@media(max-width:700px) { .discovery-page { padding:12px } section { padding:16px } }
</style>
