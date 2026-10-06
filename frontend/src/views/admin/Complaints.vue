<template>
  <div class="page">
    <el-card>
      <div class="card-title">投诉与申诉处理</div>
      <el-radio-group v-model="filter" class="mb" @change="load">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="SUBMITTED">待处理</el-radio-button>
        <el-radio-button value="PROCESSING">处理中</el-radio-button>
        <el-radio-button value="RESOLVED">已办结</el-radio-button>
      </el-radio-group>
      <el-table :data="filtered" size="small">
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reporterType === 'VENDOR' ? 'primary' : 'danger'">
              {{ row.reporterType === 'VENDOR' ? '摊主申诉' : '消费者投诉' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="事项" width="120">
          <template #default="{ row }">{{ typeText(row.type) }}</template>
        </el-table-column>
        <el-table-column label="涉及摊位/摊主" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.result" size="small">{{ row.result.stallNo }}</el-tag>
            {{ row.vendor?.name || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="reporterName" label="投诉人" width="100" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="{ SUBMITTED: 'warning', PROCESSING: 'primary', RESOLVED: 'success', REJECTED: 'info' }[row.status]">
              {{ { SUBMITTED: '待处理', PROCESSING: '处理中', RESOLVED: '已办结', REJECTED: '已驳回' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190">
          <template #default="{ row }">
            <el-button v-if="!['RESOLVED', 'REJECTED'].includes(row.status)" size="small" type="primary" @click="openHandle(row)">处理</el-button>
            <el-button v-else size="small" @click="openHandle(row)">查看</el-button>
            <el-button v-if="row.reporterType === 'CONSUMER' && row.vendor" size="small" type="warning"
              @click="openTrace(row)">溯源送检</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="handleVisible" title="处理投诉/申诉" width="520px">
      <template v-if="current">
        <el-descriptions :column="1" border class="mb">
          <el-descriptions-item label="类型">{{ typeText(current.type) }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ current.description }}</el-descriptions-item>
          <el-descriptions-item label="投诉人">{{ current.reporterName }} {{ current.reporterPhone }}</el-descriptions-item>
        </el-descriptions>
        <el-form :model="handleForm" label-width="100px">
          <el-form-item label="处理状态">
            <el-radio-group v-model="handleForm.status">
              <el-radio value="PROCESSING">处理中</el-radio>
              <el-radio value="RESOLVED">办结</el-radio>
              <el-radio value="REJECTED">驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="处理说明" required>
            <el-input v-model="handleForm.resolution" type="textarea" :rows="3"
              placeholder="处理结果将向投诉人与摊主公开" />
          </el-form-item>
          <el-form-item label="信用联动" v-if="current.vendor">
            <el-input-number v-model="handleForm.creditDelta" :min="-30" :max="10" />
            <span class="text-muted" style="margin-left:8px">负值扣分，0 不变</span>
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">提交处理</el-button>
      </template>
    </el-dialog>

    <!-- 食品投诉溯源与发起送检 -->
    <el-drawer v-model="traceVisible" size="64%" title="食品投诉溯源 · 关联留样/购买/支付/同批次/巡查">
      <div v-if="trace" class="trace-wrap">
        <el-alert type="warning" :closable="false" class="mb"
          :title="`被投诉摊主：${trace.vendor?.name}（${trace.vendor?.phone || '无电话'}） 当前信用 ${trace.vendor?.creditScore} 分`"
          :description="restrictionDesc(trace.restriction)" />

        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="投诉人">{{ trace.complaint.reporterName }} {{ trace.complaint.reporterPhone }}</el-descriptions-item>
          <el-descriptions-item label="购买时间">
            <span :class="{ 'text-danger': !trace.complaint.purchaseTime }">
              {{ trace.complaint.purchaseTime ? fmtTime(trace.complaint.purchaseTime) : '未填写（无法精确锁定批次）' }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="投诉类型" :span="2">{{ typeText(trace.complaint.type) }}</el-descriptions-item>
          <el-descriptions-item label="情况说明" :span="2">{{ trace.complaint.description }}</el-descriptions-item>
          <el-descriptions-item label="支付凭证" :span="2">
            <template v-if="trace.complaint.paymentProof">
              <el-image v-for="u in trace.complaint.paymentProof.split(',')" :key="u" :src="u"
                :preview-src-list="trace.complaint.paymentProof.split(',')" class="img-thumb" fit="cover" preview-teleported />
            </template>
            <span v-else class="text-muted">消费者未上传支付凭证</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">摊主留样（按购买时间 {{ trace.retainHours }} 小时留样规定圈定相关批次）</div>
        <el-table :data="trace.samples" size="small" class="mb">
          <el-table-column width="55">
            <template #header>送检</template>
            <template #default="{ row }">
              <el-radio v-model="pickedSampleId" :label="row.sample.id" :disabled="!!trace.inspection"><span></span></el-radio>
            </template>
          </el-table-column>
          <el-table-column label="相关性" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.relevant ? 'danger' : 'info'">{{ row.relevant ? '同批次可疑' : '其他留样' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="食品" width="100">
            <template #default="{ row }">{{ row.sample.productName }}</template>
          </el-table-column>
          <el-table-column label="批次" width="130">
            <template #default="{ row }">{{ row.sample.batchNo }}</template>
          </el-table-column>
          <el-table-column label="照片" width="90">
            <template #default="{ row }">
              <el-image v-if="row.sample.photo" :src="row.sample.photo.split(',')[0]"
                :preview-src-list="row.sample.photo.split(',')" class="img-thumb" fit="cover" preview-teleported />
            </template>
          </el-table-column>
          <el-table-column label="保存位置" min-width="120">
            <template #default="{ row }">{{ row.sample.storageLocation }}</template>
          </el-table-column>
          <el-table-column label="留样时间" width="140">
            <template #default="{ row }">{{ fmtTime(row.sample.retainedAt) }}</template>
          </el-table-column>
        </el-table>

        <el-row :gutter="12" class="mb">
          <el-col :span="12">
            <div class="section-title">同批次其他消费者（{{ trace.relatedConsumers.filter(c => c.sameBatch).length }} 人同批次）</div>
            <div class="list-box">
              <div v-for="c in trace.relatedConsumers" :key="c.complaint.id" class="list-item">
                <el-tag size="small" :type="c.sameBatch ? 'danger' : 'info'">{{ c.sameBatch ? '同批次' : '其他' }}</el-tag>
                <b style="margin:0 6px">{{ typeText(c.complaint.type) }}</b>
                {{ c.complaint.reporterName }}
                <span class="text-muted">{{ c.complaint.purchaseTime ? fmtTime(c.complaint.purchaseTime) : '' }}</span>
                <div class="text-muted">{{ c.complaint.description }}</div>
              </div>
              <el-empty v-if="!trace.relatedConsumers.length" description="暂无其他消费者投诉" :image-size="50" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="section-title">巡查记录（{{ trace.patrols.length }} 条）</div>
            <div class="list-box">
              <div v-for="p in trace.patrols" :key="p.id" class="list-item">
                卫生 {{ p.hygieneScore }} 分
                <el-tag v-if="p.shortWeight" size="small" type="danger" style="margin-left:4px">缺斤少两</el-tag>
                <el-tag v-if="p.occupyingLane" size="small" type="warning" style="margin-left:4px">占道</el-tag>
                <el-tag v-if="!p.priceMarked" size="small" type="warning" style="margin-left:4px">未明码标价</el-tag>
                <el-tag v-if="!p.electricSafe" size="small" type="danger" style="margin-left:4px">用电隐患</el-tag>
                <div class="text-muted">{{ fmtTime(p.createdAt) }} {{ p.notes || '' }}</div>
              </div>
              <el-empty v-if="!trace.patrols.length" description="暂无巡查记录" :image-size="50" />
            </div>
          </el-col>
        </el-row>

        <div class="section-title">保证金账单（检测不合格可扣罚）</div>
        <el-table :data="trace.deposits" size="small" class="mb">
          <el-table-column prop="itemName" label="项目" />
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="{ PAID: 'success', DEDUCTED: 'danger', UNPAID: 'warning' }[row.status]">{{ { PAID: '已缴', DEDUCTED: '已扣罚', UNPAID: '未缴' }[row.status] }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-alert v-if="trace.inspection" type="success" :closable="false" class="mb"
          :title="`已发起送检单 #${trace.inspection.id}（${inspStatus(trace.inspection.status).text}）批次 ${trace.inspection.batchNo} → ${trace.inspection.labName}`"
          :description="trace.inspection.resultSummary || '检测结果尚未登记，请在「留样送检」页跟进'" />

        <el-card v-if="!trace.inspection" shadow="never" class="mb">
          <div class="section-title">发起留样送检</div>
          <el-form :model="inspForm" label-width="150px">
            <el-form-item label="承检机构" required>
              <el-input v-model="inspForm.labName" placeholder="如：县市场监管局食品快检中心" />
            </el-form-item>
            <el-form-item label="检测项目">
              <el-input v-model="inspForm.testItems" placeholder="如：菌落总数、大肠菌群、沙门氏菌" />
            </el-form-item>
            <el-form-item label="同批次食品处置" required>
              <el-radio-group v-model="inspForm.batchAction">
                <el-radio value="REMOVE">要求立即下架停售</el-radio>
                <el-radio value="KEEP_LIST">留存同批次销售名单（继续观察）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="暂停熟食售卖">
              <el-switch v-model="inspForm.suspendCooked" active-text="送检期间暂停该摊主熟食售卖" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="creatingInsp" @click="createInspection">发起送检</el-button>
              <span class="text-muted" style="margin-left:8px">需先在上方留样列表选择送检批次</span>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { fmtTime, INSPECTION_STATUS } from '../../utils/dict'

const complaints = ref([])
const filter = ref('')
const handleVisible = ref(false)
const handling = ref(false)
const current = ref(null)
const handleForm = ref({ status: 'RESOLVED', resolution: '', creditDelta: 0 })

const filtered = computed(() => filter.value ? complaints.value.filter(c => c.status === filter.value) : complaints.value)

function typeText(t) {
  return { DIARRHEA: '腹泻/食品安全', PRICE: '价格问题', SHORT_WEIGHT: '缺斤少两', HYGIENE: '卫生问题', QUALITY: '质量问题', BOUNDARY: '边界纠纷', FEE_DISPUTE: '费用争议', DEPOSIT_DISPUTE: '扣罚争议', OTHER: '其他' }[t] || t
}
function inspStatus(s) { return INSPECTION_STATUS[s] || { text: s, type: 'info' } }

async function load() { complaints.value = await api.get('/admin/complaints') }

function openHandle(row) {
  current.value = row
  handleForm.value = { status: 'RESOLVED', resolution: row.resolution || '', creditDelta: 0 }
  handleVisible.value = true
}

async function submitHandle() {
  if (!handleForm.value.resolution) return ElMessage.warning('请填写处理说明')
  handling.value = true
  try {
    await api.post(`/admin/complaints/${current.value.id}/handle`, handleForm.value)
    ElMessage.success('已处理')
    handleVisible.value = false
    await load()
  } finally { handling.value = false }
}

// ===== 食品溯源与送检 =====
const traceVisible = ref(false)
const trace = ref(null)
const pickedSampleId = ref(null)
const creatingInsp = ref(false)
const inspForm = ref({ labName: '', testItems: '', batchAction: 'REMOVE', suspendCooked: true })

function restrictionDesc(r) {
  if (!r) return '当前无熟食售卖/抽签资格限制'
  const parts = []
  if (r.cookedSalesSuspended) parts.push('熟食售卖已暂停' + (r.suspensionReason ? `（${r.suspensionReason}）` : ''))
  if (r.cookedLotteryBanned) parts.push('已取消熟食摊位抽签资格' + (r.banUntil ? `至 ${r.banUntil}` : '（永久）'))
  return parts.length ? parts.join('；') : '当前无熟食售卖/抽签资格限制'
}

async function openTrace(row) {
  trace.value = await api.get(`/admin/complaints/${row.id}/trace`)
  const relevant = trace.value.samples.find(s => s.relevant)
  pickedSampleId.value = relevant ? relevant.sample.id : (trace.value.samples[0]?.sample.id || null)
  inspForm.value = { labName: '', testItems: '', batchAction: 'REMOVE', suspendCooked: true }
  traceVisible.value = true
}

async function createInspection() {
  if (!pickedSampleId.value) return ElMessage.warning('请先选择要送检的留样批次')
  if (!inspForm.value.labName) return ElMessage.warning('请填写承检机构')
  await ElMessageBox.confirm(
    '送检后将按所选方式要求摊主处置同批次食品，并可暂停其熟食售卖，全程留痕。确认发起？',
    '确认发起留样送检', { type: 'warning', confirmButtonText: '确认发起', cancelButtonText: '取消' })
  creatingInsp.value = true
  try {
    await api.post('/admin/inspections', {
      complaintId: trace.value.complaint.id,
      sampleId: pickedSampleId.value,
      ...inspForm.value
    })
    ElMessage.success('送检已发起，已通知摊主处置')
    trace.value = await api.get(`/admin/complaints/${trace.value.complaint.id}/trace`)
    await load()
  } finally { creatingInsp.value = false }
}

onMounted(load)
</script>

<style scoped>
.trace-wrap :deep(.el-descriptions) { margin-bottom: 4px; }
.section-title { font-weight: 600; font-size: 14px; margin: 14px 0 8px; padding-left: 8px; border-left: 3px solid #e6a23c; }
.list-box { max-height: 220px; overflow-y: auto; border: 1px solid #ebeef5; border-radius: 4px; padding: 6px 10px; }
.list-item { padding: 8px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
.text-danger { color: #f56c6c; font-weight: 600; }
</style>
