<template>
  <div class="page">
    <el-card>
      <div class="card-title">留样送检与熟食管控</div>
      <el-radio-group v-model="statusFilter" class="mb">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="INITIATED">待抽样</el-radio-button>
        <el-radio-button value="SENT">检测中</el-radio-button>
        <el-radio-button value="QUALIFIED">合格</el-radio-button>
        <el-radio-button value="UNQUALIFIED">不合格</el-radio-button>
      </el-radio-group>
      <el-table :data="filtered" size="small">
        <el-table-column label="编号" width="70"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
        <el-table-column label="集市" min-width="120">
          <template #default="{ row }">{{ row.event?.name }}</template>
        </el-table-column>
        <el-table-column label="摊主" width="120">
          <template #default="{ row }">{{ row.vendor?.name }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="食品" width="100" />
        <el-table-column prop="batchNo" label="批次" width="120" />
        <el-table-column prop="labName" label="检测机构" min-width="130" show-overflow-tooltip />
        <el-table-column label="管控" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.batchControl === 'REMOVE' ? 'danger' : 'warning'">
              {{ BATCH_CONTROL[row.batchControl] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="LAB_TEST_STATUS[row.status]?.type">{{ LAB_TEST_STATUS[row.status]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="open(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="mt">
      <div class="card-title">熟食售卖暂停 / 抽签资格冻结记录</div>
      <el-radio-group v-model="suspFilter" class="mb" @change="loadSuspensions">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="ACTIVE">生效中</el-radio-button>
        <el-radio-button value="REVOKED">已解除</el-radio-button>
        <el-radio-button value="EXPIRED">已到期</el-radio-button>
      </el-radio-group>
      <el-table :data="suspensions" size="small">
        <el-table-column label="摊主" width="120">
          <template #default="{ row }">{{ row.vendor?.name }}</template>
        </el-table-column>
        <el-table-column label="类型" width="170">
          <template #default="{ row }">{{ SUSPENSION_TYPE[row.type] }}</template>
        </el-table-column>
        <el-table-column label="起止" width="200">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate || '长期' }}</template>
        </el-table-column>
        <el-table-column label="原因类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reasonType === 'LAB_UNQUALIFIED' ? 'danger' : 'warning'">
              {{ SUSPENSION_REASON[row.reasonType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因（向摊主公开）" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="SUSPENSION_STATUS[row.status]?.type">{{ SUSPENSION_STATUS[row.status]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" size="small" type="warning" @click="revoke(row)">解除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 送检单处理抽屉 -->
    <el-drawer v-model="detailVisible" size="70%" title="留样送检单处理">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="送检编号">#{{ detail.labTest.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="LAB_TEST_STATUS[detail.labTest.status]?.type">{{ LAB_TEST_STATUS[detail.labTest.status]?.text }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="摊主">{{ detail.labTest.vendor?.name }}</el-descriptions-item>
          <el-descriptions-item label="来源投诉">#{{ detail.labTest.complaint?.id }}（{{ detail.labTest.complaint?.reporterName }}）</el-descriptions-item>
          <el-descriptions-item label="食品/批次">{{ detail.labTest.productName }} / {{ detail.labTest.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="检测机构">{{ detail.labTest.labName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检测项目" :span="2">{{ detail.labTest.testItems || '-' }}</el-descriptions-item>
          <el-descriptions-item label="同批次管控" :span="2">
            <el-tag :type="detail.labTest.batchControl === 'REMOVE' ? 'danger' : 'warning'">
              {{ BATCH_CONTROL[detail.labTest.batchControl] }}
            </el-tag>
            <span class="text-muted" style="margin-left:8px">{{ detail.labTest.controlNotice }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 流程操作 -->
        <el-space wrap class="mb">
          <el-button v-if="['INITIATED'].includes(detail.labTest.status)" type="primary" @click="openSend">登记抽样并送检测机构</el-button>
          <el-button v-if="['INITIATED','SENT'].includes(detail.labTest.status)" type="success" @click="openResult('QUALIFIED')">录入：检测合格</el-button>
          <el-button v-if="['INITIATED','SENT'].includes(detail.labTest.status)" type="danger" @click="openResult('UNQUALIFIED')">录入：检测不合格</el-button>
          <el-button @click="manualSuspend = true">补充暂停/冻结措施</el-button>
        </el-space>

        <!-- 摊主管控响应 -->
        <div class="section-title">摊主同批次管控响应</div>
        <el-table v-if="detail.case.samples.length" :data="detail.case.samples" size="small" class="mb">
            <el-table-column prop="productName" label="食品" width="100" />
            <el-table-column prop="batchNo" label="批次" width="120" />
            <el-table-column label="在售状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="SELLING_STATUS[row.sellingStatus]?.type">{{ SELLING_STATUS[row.sellingStatus]?.text }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="响应时间" width="140">
              <template #default="{ row }">{{ fmtTime(row.salesListUpdatedAt) }}</template>
            </el-table-column>
            <el-table-column prop="salesListNote" label="说明" min-width="120" show-overflow-tooltip />
            <el-table-column label="销售名单" width="90">
              <template #default="{ row }">
                <el-button size="small" :disabled="!parseList(row.salesList).length"
                  @click="showSalesList(row)">查看（{{ parseList(row.salesList).length }}）</el-button>
              </template>
            </el-table-column>
          </el-table>
        <el-alert v-else type="info" :closable="false" class="mb" title="该送检单未关联留样（现场抽样送检）" />

        <!-- 结果区 -->
        <template v-if="['QUALIFIED','UNQUALIFIED'].includes(detail.labTest.status)">
          <div class="section-title">检测结果与联动处理</div>
          <el-descriptions :column="2" border size="small" class="mb">
            <el-descriptions-item label="检测结论">
              <el-tag :type="detail.labTest.status === 'QUALIFIED' ? 'success' : 'danger'">
                {{ detail.labTest.status === 'QUALIFIED' ? '合格' : '不合格' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="录入时间">{{ fmtTime(detail.labTest.reportedAt) }}</el-descriptions-item>
            <el-descriptions-item label="结果说明" :span="2">{{ detail.labTest.resultSummary }}</el-descriptions-item>
            <el-descriptions-item label="保证金扣罚">¥{{ detail.labTest.depositPenalty }}</el-descriptions-item>
            <el-descriptions-item label="信用变动">{{ detail.labTest.creditDelta || 0 }}</el-descriptions-item>
            <el-descriptions-item label="暂停熟食售卖">{{ detail.labTest.suspendDays }} 天</el-descriptions-item>
            <el-descriptions-item label="冻结熟食抽签资格">{{ detail.labTest.lotteryBanDays }} 天</el-descriptions-item>
            <el-descriptions-item v-if="detail.labTest.reportPhotos" label="检测报告" :span="2">
              <el-image v-for="u in detail.labTest.reportPhotos.split(',')" :key="u" :src="u"
                :preview-src-list="detail.labTest.reportPhotos.split(',')" class="img-thumb mr4" fit="cover"
                preview-teleported />
            </el-descriptions-item>
          </el-descriptions>
        </template>

        <div class="section-title">该摊主管控记录</div>
        <el-table :data="detail.suspensions" size="small" class="mb">
          <el-table-column label="类型" width="170">
            <template #default="{ row }">{{ SUSPENSION_TYPE[row.type] }}</template>
          </el-table-column>
          <el-table-column label="起止" width="200">
            <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate || '长期' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="SUSPENSION_STATUS[row.status]?.type">{{ SUSPENSION_STATUS[row.status]?.text }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>

    <!-- 送样对话框 -->
    <el-dialog v-model="sendVisible" title="登记抽样并送检测机构" width="480px">
      <el-form :model="sendForm" label-width="100px">
        <el-form-item label="检测机构">
          <el-input v-model="sendForm.labName" placeholder="如：县市场监管检验检测中心" />
        </el-form-item>
        <el-form-item label="检测项目">
          <el-input v-model="sendForm.testItems" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="机构收样日">
          <el-date-picker v-model="sendForm.receivedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="sendForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSend">确认送样</el-button>
      </template>
    </el-dialog>

    <!-- 结果录入对话框 -->
    <el-dialog v-model="resultVisible" :title="resultForm.result === 'UNQUALIFIED' ? '录入检测不合格结果' : '录入检测合格结果'" width="560px">
      <el-form :model="resultForm" label-width="140px">
        <el-form-item label="结果说明" required>
          <el-input v-model="resultForm.resultSummary" type="textarea" :rows="3"
            :placeholder="resultForm.result === 'UNQUALIFIED' ? '如：凉拌黄瓜菌落总数、大肠菌群超标，检出沙门氏菌' : '如：所检项目均符合食品安全标准'" />
        </el-form-item>
        <el-form-item label="检测报告照片">
          <ImageUpload v-model="resultForm.reportPhotos" button-text="上传检测报告" />
        </el-form-item>
        <template v-if="resultForm.result === 'UNQUALIFIED'">
          <el-divider content-position="left">联动处理（生成调整记录，摊主端可见）</el-divider>
          <el-form-item label="保证金扣罚(元)">
            <el-input-number v-model="resultForm.depositPenalty" :min="0" :max="2000" :step="50" />
          </el-form-item>
          <el-form-item label="信用扣分">
            <el-input-number v-model="resultForm.creditDelta" :min="-60" :max="0" />
          </el-form-item>
          <el-form-item label="暂停熟食售卖(天)">
            <el-input-number v-model="resultForm.suspendDays" :min="0" :max="365" />
          </el-form-item>
          <el-form-item label="冻结熟食抽签(天)">
            <el-input-number v-model="resultForm.lotteryBanDays" :min="0" :max="730" />
            <span class="text-muted" style="margin-left:8px">期内后续集市熟食摊位抽签将被拦截</span>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="resultVisible = false">取消</el-button>
        <el-button :type="resultForm.result === 'UNQUALIFIED' ? 'danger' : 'success'" @click="submitResult">提交结果并执行联动</el-button>
      </template>
    </el-dialog>

    <!-- 销售名单 -->
    <el-dialog v-model="listVisible" title="同批次购买者销售名单" width="640px">
      <el-table :data="salesList" size="small">
        <el-table-column prop="buyer" label="购买者" width="120" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="qty" label="数量" width="100" />
        <el-table-column prop="soldAt" label="购买时间" min-width="140" />
        <el-table-column prop="note" label="备注" min-width="120" />
      </el-table>
    </el-dialog>

    <!-- 补充暂停/冻结 -->
    <el-dialog v-model="manualSuspend" title="补充暂停熟食售卖/冻结抽签资格" width="500px">
      <el-form :model="suspendForm" label-width="120px" v-if="detail">
        <el-form-item label="措施类型">
          <el-radio-group v-model="suspendForm.type">
            <el-radio value="SALES_SUSPEND">暂停熟食售卖</el-radio>
            <el-radio value="LOTTERY_BAN">冻结熟食抽签资格</el-radio>
            <el-radio value="BOTH">两者并处</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="期限(天,0长期)">
          <el-input-number v-model="suspendForm.days" :min="0" :max="730" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="suspendForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualSuspend = false">取消</el-button>
        <el-button type="warning" @click="submitSuspend">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import api from '../../api'
import { fmtTime, LAB_TEST_STATUS, BATCH_CONTROL, SELLING_STATUS, SUSPENSION_TYPE, SUSPENSION_STATUS, SUSPENSION_REASON } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'

const route = useRoute()
const tests = ref([])
const statusFilter = ref('')
const filtered = computed(() => statusFilter.value ? tests.value.filter(t => t.status === statusFilter.value) : tests.value)

const suspensions = ref([])
const suspFilter = ref('')

const detailVisible = ref(false)
const detail = ref(null)
const sendVisible = ref(false)
const sendForm = ref({})
const resultVisible = ref(false)
const resultForm = ref({})
const listVisible = ref(false)
const salesList = ref([])
const manualSuspend = ref(false)
const suspendForm = ref({ type: 'SALES_SUSPEND', days: 7, reason: '' })

function parseList(s) {
  if (!s) return []
  try { return JSON.parse(s) } catch { return [] }
}

async function load() { tests.value = await api.get('/admin/lab-tests') }
async function loadSuspensions() {
  suspensions.value = await api.get('/admin/suspensions', { params: suspFilter.value ? { status: suspFilter.value } : {} })
}

async function open(row) {
  detail.value = await api.get(`/admin/lab-tests/${row.id}`)
  detailVisible.value = true
}

function openSend() {
  sendForm.value = {
    labName: detail.value.labTest.labName || '县市场监管检验检测中心',
    testItems: detail.value.labTest.testItems || '菌落总数,大肠菌群,沙门氏菌',
    receivedDate: '', remark: ''
  }
  sendVisible.value = true
}

async function submitSend() {
  await api.post(`/admin/lab-tests/${detail.value.labTest.id}/send`, sendForm.value)
  ElMessage.success('已登记送样')
  sendVisible.value = false
  await load()
  await open(await findRow())
}

async function findRow() {
  return tests.value.find(t => t.id === detail.value.labTest.id)
}

function openResult(result) {
  resultForm.value = {
    result, resultSummary: '', reportPhotos: '',
    depositPenalty: result === 'UNQUALIFIED' ? 200 : 0,
    creditDelta: result === 'UNQUALIFIED' ? -20 : 0,
    suspendDays: result === 'UNQUALIFIED' ? 7 : 0,
    lotteryBanDays: result === 'UNQUALIFIED' ? 90 : 0
  }
  resultVisible.value = true
}

async function submitResult() {
  if (!resultForm.value.resultSummary) return ElMessage.warning('请填写结果说明')
  try {
    await ElMessageBox.confirm('提交后将自动执行保证金/信用/暂停/抽签资格联动并生成调整记录，是否继续？', '确认检测结果',
      { type: 'warning' })
  } catch { return }
  await api.post(`/admin/lab-tests/${detail.value.labTest.id}/result`, resultForm.value)
  ElMessage.success('结果已录入，联动处理已执行')
  resultVisible.value = false
  detailVisible.value = false
  await Promise.all([load(), loadSuspensions()])
}

function showSalesList(sample) {
  salesList.value = parseList(sample.salesList)
  listVisible.value = true
}

async function revoke(row) {
  const { value } = await ElMessageBox.prompt('解除原因（向摊主公开）', '解除管控', {
    confirmButtonText: '确认解除', cancelButtonText: '取消', inputValue: '整改复查合格，解除管控'
  }).catch(() => ({}))
  if (value === undefined) return
  await api.post(`/admin/suspensions/${row.id}/revoke`, { reason: value })
  ElMessage.success('已解除')
  await loadSuspensions()
  if (detail.value) await open(tests.value.find(t => t.id === detail.value.labTest.id))
}

async function submitSuspend() {
  if (!suspendForm.value.reason) return ElMessage.warning('请填写原因')
  await api.post('/admin/suspensions', {
    vendorId: detail.value.labTest.vendor.id,
    eventId: detail.value.labTest.event.id,
    labTestId: detail.value.labTest.id,
    ...suspendForm.value
  })
  ElMessage.success('措施已执行并生成调整记录')
  manualSuspend.value = false
  await loadSuspensions()
  await open(tests.value.find(t => t.id === detail.value.labTest.id))
}

onMounted(async () => {
  await Promise.all([load(), loadSuspensions()])
  if (route.query.id) {
    const row = tests.value.find(t => t.id === Number(route.query.id))
    if (row) open(row)
  }
})
</script>

<style scoped>
.section-title { font-weight: 600; margin: 12px 0 8px; border-left: 3px solid #f56c6c; padding-left: 8px; }
.mr4 { margin-right: 4px; }
</style>
