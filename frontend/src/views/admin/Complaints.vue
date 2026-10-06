<template>
  <div class="page">
    <el-card>
      <div class="card-title">投诉与申诉处理</div>
      <el-radio-group v-model="filter" class="mb">
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
        <el-table-column label="食品/购买时间" width="170">
          <template #default="{ row }">
            <div v-if="row.foodName || row.purchaseTime">
              <div>{{ row.foodName || '-' }}<el-tag v-if="row.batchNo" size="small" type="info" class="ml4">批次{{ row.batchNo }}</el-tag></div>
              <div class="text-muted" style="font-size:12px">{{ fmtTime(row.purchaseTime) }}</div>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="{ SUBMITTED: 'warning', PROCESSING: 'primary', RESOLVED: 'success', REJECTED: 'info' }[row.status]">
              {{ { SUBMITTED: '待处理', PROCESSING: '处理中', RESOLVED: '已办结', REJECTED: '已驳回' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" type="warning" @click="openCase(row)">案件关联</el-button>
            <el-button size="small" :type="['RESOLVED','REJECTED'].includes(row.status) ? '' : 'primary'"
              @click="openHandle(row)">{{ ['RESOLVED','REJECTED'].includes(row.status) ? '查看' : '处理' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 案件关联抽屉 -->
    <el-drawer v-model="caseVisible" size="72%" title="投诉案件关联与留样送检">
      <template v-if="caseData">
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="投诉编号">#{{ caseData.complaint.id }}</el-descriptions-item>
          <el-descriptions-item label="投诉类型">{{ typeText(caseData.complaint.type) }}</el-descriptions-item>
          <el-descriptions-item label="投诉人">{{ caseData.complaint.reporterName }} {{ caseData.complaint.reporterPhone }}</el-descriptions-item>
          <el-descriptions-item label="涉及摊主/摊位">{{ caseData.vendor?.name }} / {{ caseData.result?.stallNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="食品名称">{{ caseData.complaint.foodName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="购买时间">{{ fmtTime(caseData.complaint.purchaseTime) }}</el-descriptions-item>
          <el-descriptions-item label="批次号">{{ caseData.complaint.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ fmtTime(caseData.complaint.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="情况说明" :span="2">{{ caseData.complaint.description }}</el-descriptions-item>
          <el-descriptions-item label="支付凭证" :span="2">
            <template v-if="caseData.complaint.paymentProof">
              <el-image v-for="u in caseData.complaint.paymentProof.split(',')" :key="u" :src="u"
                :preview-src-list="caseData.complaint.paymentProof.split(',')" class="img-thumb mr4" fit="cover"
                preview-teleported />
            </template>
            <span v-else class="text-muted">消费者未提供</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert type="info" :closable="false" class="mb"
          title="平台自动关联：摊主留样、同批次其他消费者投诉、巡查记录、入场核验与缴费（保证金）记录" />

        <div class="section-title">摊主留样（可选择发起送检）</div>
        <el-table :data="caseData.samples" size="small" class="mb">
          <el-table-column prop="productName" label="食品" width="100" />
          <el-table-column prop="batchNo" label="批次" width="130" />
          <el-table-column prop="storageLocation" label="保存位置" min-width="120" />
          <el-table-column label="留样状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="sampleStatusType(row.status)">{{ sampleStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="在售管控" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="SELLING_STATUS[row.sellingStatus]?.type">{{ SELLING_STATUS[row.sellingStatus]?.text || '在售' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="留样时间" width="140">
            <template #default="{ row }">{{ fmtTime(row.retainedAt) }}</template>
          </el-table-column>
        </el-table>
        <div class="mb">
          <el-button type="danger" @click="openCreateLab(null)">发起留样送检（现场抽样/无留样）</el-button>
          <el-button v-for="s in caseData.samples.filter(x => ['KEPT','ABNORMAL'].includes(x.status))" :key="s.id"
            size="small" type="danger" plain @click="openCreateLab(s)">送检「{{ s.productName }} / {{ s.batchNo }}」</el-button>
        </div>

        <div class="section-title">同批次/同食品其他消费者投诉（{{ caseData.relatedConsumers.length }}）</div>
        <el-table :data="caseData.relatedConsumers" size="small" class="mb">
          <el-table-column label="投诉编号" width="90">
            <template #default="{ row }">#{{ row.complaint.id }}</template>
          </el-table-column>
          <el-table-column label="投诉人" width="120">
            <template #default="{ row }">{{ row.complaint.reporterName }} {{ row.complaint.reporterPhone }}</template>
          </el-table-column>
          <el-table-column label="食品/批次" width="160">
            <template #default="{ row }">{{ row.complaint.foodName || '-' }} / {{ row.complaint.batchNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="购买时间" width="140">
            <template #default="{ row }">{{ fmtTime(row.complaint.purchaseTime) }}</template>
          </el-table-column>
          <el-table-column label="关联依据" min-width="160">
            <template #default="{ row }">
              <el-tag v-for="r in row.matchReasons" :key="r" size="small" type="warning" class="mr4">{{ r }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="section-title">巡查记录（{{ caseData.patrols.length }}）</div>
        <el-table :data="caseData.patrols" size="small" class="mb">
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="hygieneScore" label="卫生分" width="70" />
          <el-table-column label="问题项" min-width="200">
            <template #default="{ row }">
              <el-tag v-if="!row.priceMarked" size="small" type="danger" class="mr4">未明码标价</el-tag>
              <el-tag v-if="row.shortWeight" size="small" type="danger" class="mr4">缺斤少两</el-tag>
              <el-tag v-if="row.occupyingLane" size="small" type="danger" class="mr4">占道</el-tag>
              <el-tag v-if="!row.electricSafe" size="small" type="danger" class="mr4">用电隐患</el-tag>
              <span v-if="row.priceMarked && !row.shortWeight && !row.occupyingLane && row.electricSafe" class="text-muted">无</span>
            </template>
          </el-table-column>
          <el-table-column prop="notes" label="备注" min-width="120" show-overflow-tooltip />
        </el-table>

        <div class="section-title">入场核验</div>
        <el-descriptions v-if="caseData.entryCheck" :column="2" border size="small" class="mb">
          <el-descriptions-item label="核验人">{{ caseData.entryCheck.staff?.name }}</el-descriptions-item>
          <el-descriptions-item label="核验时间">{{ fmtTime(caseData.entryCheck.checkedAt) }}</el-descriptions-item>
          <el-descriptions-item label="实际品类">{{ catName(caseData.entryCheck.actualCategory) }}</el-descriptions-item>
          <el-descriptions-item label="按位摆摊">{{ caseData.entryCheck.atAssignedStall ? '是' : '否' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-else type="info" :closable="false" class="mb" title="该摊位尚无入场核验记录" />

        <div class="section-title">缴费记录（保证金扣罚依据）</div>
        <el-table :data="caseData.payments" size="small" class="mb">
          <el-table-column prop="itemName" label="项目" min-width="140" />
          <el-table-column prop="amount" label="金额" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="PAY_STATUS[row.status]?.type">{{ PAY_STATUS[row.status]?.text }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="section-title">关联送检单（{{ caseData.labTests.length }}）</div>
        <el-table :data="caseData.labTests" size="small" class="mb">
          <el-table-column label="编号" width="70"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
          <el-table-column prop="productName" label="食品" width="100" />
          <el-table-column prop="batchNo" label="批次" width="120" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="LAB_TEST_STATUS[row.status]?.type">{{ LAB_TEST_STATUS[row.status]?.text }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="管控">
            <template #default="{ row }">{{ BATCH_CONTROL[row.batchControl] }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="$router.push(`/admin/lab-tests?id=${row.id}`)">打开送检单</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 发起送检对话框 -->
    <el-dialog v-model="createVisible" title="发起留样送检" width="600px">
      <el-alert type="warning" :closable="false" class="mb"
        title="送检期间，同批次食品若仍在售卖，必须要求摊主下架或留存销售名单；检测结果将联动保证金、信用与后续熟食抽签资格" />
      <el-form :model="labForm" label-width="120px">
        <el-form-item label="受检食品" required>
          <el-input v-model="labForm.productName" placeholder="如：凉拌黄瓜" />
        </el-form-item>
        <el-form-item label="批次号" required>
          <el-input v-model="labForm.batchNo" placeholder="如：20261015-03" />
        </el-form-item>
        <el-form-item label="送检机构">
          <el-input v-model="labForm.labName" placeholder="如：县市场监管检验检测中心" />
        </el-form-item>
        <el-form-item label="检测项目">
          <el-input v-model="labForm.testItems" placeholder="如：菌落总数、大肠菌群、沙门氏菌" />
        </el-form-item>
        <el-form-item label="同批次管控" required>
          <el-radio-group v-model="labForm.batchControl">
            <el-radio value="REMOVE">要求立即下架</el-radio>
            <el-radio value="KEEP_LIST">继续售卖但留存购买者名单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="管控通知">
          <el-input v-model="labForm.controlNotice" type="textarea" :rows="2"
            placeholder="向摊主展示的通知内容（可空，使用默认通知）" />
        </el-form-item>
        <el-form-item label="暂停熟食售卖">
          <el-switch v-model="labForm.suspendNow" />
          <span class="text-muted" style="margin-left:8px">调查期间同步暂停该摊主熟食售卖</span>
        </el-form-item>
        <template v-if="labForm.suspendNow">
          <el-form-item label="暂停天数">
            <el-input-number v-model="labForm.suspendDays" :min="1" :max="30" />
          </el-form-item>
          <el-form-item label="暂停原因">
            <el-input v-model="labForm.suspendReason" placeholder="默认：食品安全调查，送检期间暂停熟食售卖" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="danger" :loading="creating" @click="submitCreate">发起送检</el-button>
      </template>
    </el-dialog>

    <!-- 处理对话框 -->
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
            <span class="text-muted" style="margin-left:8px">负值扣分，0 不变（送检不合格可在送检单中联动）</span>
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">提交处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import api from '../../api'
import { fmtTime, catName, PAY_STATUS, LAB_TEST_STATUS, BATCH_CONTROL, SELLING_STATUS } from '../../utils/dict'

const router = useRouter()
const complaints = ref([])
const filter = ref('')
const handleVisible = ref(false)
const handling = ref(false)
const current = ref(null)
const handleForm = ref({ status: 'RESOLVED', resolution: '', creditDelta: 0 })

const caseVisible = ref(false)
const caseData = ref(null)
const createVisible = ref(false)
const creating = ref(false)
const labForm = ref({})

const filtered = computed(() => filter.value ? complaints.value.filter(c => c.status === filter.value) : complaints.value)

function typeText(t) {
  return { DIARRHEA: '腹泻/食品安全', PRICE: '价格问题', SHORT_WEIGHT: '缺斤少两', HYGIENE: '卫生问题', QUALITY: '质量问题', BOUNDARY: '边界纠纷', FEE_DISPUTE: '费用争议', DEPOSIT_DISPUTE: '扣罚争议', OTHER: '其他' }[t] || t
}
function sampleStatusText(s) {
  return { KEPT: '已留样', DISPOSED: '已处置', ABNORMAL: '异常', LAB_TESTING: '送检中', LAB_SENT: '已送机构', DESTROYED: '已销毁' }[s] || s
}
function sampleStatusType(s) {
  return s === 'KEPT' ? 'success' : s === 'LAB_TESTING' || s === 'LAB_SENT' ? 'warning' : s === 'ABNORMAL' ? 'danger' : 'info'
}

async function load() { complaints.value = await api.get('/admin/complaints') }

async function openCase(row) {
  caseData.value = await api.get(`/admin/complaints/${row.id}/case`)
  caseVisible.value = true
}

function openCreateLab(sample) {
  const c = caseData.value.complaint
  labForm.value = {
    complaintId: c.id,
    sampleId: sample?.id || null,
    productName: sample?.productName || c.foodName || '',
    batchNo: sample?.batchNo || c.batchNo || '',
    labName: '', testItems: '菌落总数,大肠菌群,沙门氏菌',
    batchControl: 'REMOVE', controlNotice: '',
    suspendNow: false, suspendDays: 3, suspendReason: ''
  }
  createVisible.value = true
}

async function submitCreate() {
  const f = labForm.value
  if (!f.productName || !f.batchNo) return ElMessage.warning('请填写受检食品与批次号')
  creating.value = true
  try {
    const t = await api.post('/admin/lab-tests', f)
    ElMessage.success(`送检单 #${t.id} 已发起，摊主端将收到${f.batchControl === 'REMOVE' ? '下架' : '留存销售名单'}通知`)
    createVisible.value = false
    caseVisible.value = false
    router.push(`/admin/lab-tests?id=${t.id}`)
    await load()
  } finally { creating.value = false }
}

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

onMounted(load)
</script>

<style scoped>
.section-title { font-weight: 600; margin: 12px 0 8px; border-left: 3px solid #e6a23c; padding-left: 8px; }
.mr4 { margin-right: 4px; }
.ml4 { margin-left: 4px; }
</style>
