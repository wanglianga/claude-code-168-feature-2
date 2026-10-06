<template>
  <div class="page">
    <el-card>
      <div style="display:flex;justify-content:space-between;align-items:center" class="mb">
        <div class="card-title" style="margin:0">留样送检（送检期间暂停熟食、结果联动保证金/信用/抽签资格）</div>
        <el-button @click="load">刷新</el-button>
      </div>
      <el-radio-group v-model="statusFilter" class="mb" @change="load">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="IN_LAB">检测中</el-radio-button>
        <el-radio-button value="QUALIFIED">合格</el-radio-button>
        <el-radio-button value="UNQUALIFIED">不合格</el-radio-button>
      </el-radio-group>
      <el-table :data="inspections" size="small">
        <el-table-column prop="id" label="单号" width="70" />
        <el-table-column label="集市" min-width="130">
          <template #default="{ row }">{{ row.event?.name }}</template>
        </el-table-column>
        <el-table-column label="摊主" width="120">
          <template #default="{ row }">{{ row.vendor?.name }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="130" />
        <el-table-column prop="labName" label="承检机构" min-width="140" show-overflow-tooltip />
        <el-table-column label="同批次处置" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.batchAction === 'REMOVE' ? 'danger' : 'warning'">
              {{ { REMOVE: '要求下架', KEEP_LIST: '留存名单' }[row.batchAction] }}
            </el-tag>
            <div v-if="row.batchReceiptStatus" class="text-muted">
              回执：{{ { REMOVED: '已下架', LIST_KEPT: '已留存名单' }[row.batchReceiptStatus] }}
            </div>
            <div v-else class="text-danger">摊主未回执</div>
          </template>
        </el-table-column>
        <el-table-column label="暂停熟食" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.cookedSuspended ? 'danger' : 'success'">{{ row.cookedSuspended ? '暂停中' : '已恢复' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="inspStatus(row.status).type">{{ inspStatus(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button v-if="['PENDING','IN_LAB'].includes(row.status)" size="small" type="primary" @click="openResult(row)">登记结果</el-button>
            <el-button v-else size="small" @click="openResult(row)">查看</el-button>
            <el-button v-if="row.cookedSuspended" size="small" type="warning" @click="lift(row)">解除暂停</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!inspections.length" description="暂无送检单，可在「投诉处理 → 溯源送检」发起" />
    </el-card>

    <!-- 登记/查看检测结果 -->
    <el-drawer v-model="resultVisible" size="56%" :title="`送检单 #${current?.id} · 检测结果处置`">
      <div v-if="current">
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="摊主">{{ current.vendor?.name }}（{{ current.vendor?.phone }}）</el-descriptions-item>
          <el-descriptions-item label="批次">{{ current.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="留样食品">{{ current.sample?.productName }}</el-descriptions-item>
          <el-descriptions-item label="承检机构">{{ current.labName }}</el-descriptions-item>
          <el-descriptions-item label="检测项目" :span="2">{{ current.testItems || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源投诉" :span="2">#{{ current.complaint?.id }} {{ current.complaint?.description }}</el-descriptions-item>
          <el-descriptions-item label="摊主回执" :span="2">
            <template v-if="current.batchReceiptStatus">
              {{ { REMOVED: '同批次已下架', LIST_KEPT: '已留存销售名单' }[current.batchReceiptStatus] }}
              <span class="text-muted">（{{ fmtTime(current.batchReceiptAt) }}）{{ current.batchReceipt || '' }}</span>
            </template>
            <span v-else class="text-danger">摊主尚未提交同批次处置回执</span>
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="['PENDING','IN_LAB'].includes(current.status)">
          <el-card shadow="never" class="mb">
            <div class="card-title">登记检测结果（提交后联动处置，全程留痕并向摊主公开）</div>
            <el-form :model="form" label-width="170px">
              <el-form-item label="检测结果" required>
                <el-radio-group v-model="form.status">
                  <el-radio-button value="QUALIFIED">检测合格</el-radio-button>
                  <el-radio-button value="UNQUALIFIED">检测不合格</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="结果说明" required>
                <el-input v-model="form.resultSummary" type="textarea" :rows="3" placeholder="如：大肠菌群超标，判定不合格" />
              </el-form-item>
              <el-form-item label="检测报告照片">
                <ImageUpload v-model="form.reportPhoto" />
              </el-form-item>

              <template v-if="form.status === 'UNQUALIFIED'">
                <el-divider content-position="left">不合格处置联动</el-divider>
                <el-form-item label="扣罚保证金">
                  <el-switch v-model="form.deductDeposit" active-text="扣罚该摊主本场已缴经营保证金（生成扣罚调整留痕）" />
                </el-form-item>
                <el-form-item label="信用扣分">
                  <el-input-number v-model="form.creditDelta" :min="-50" :max="0" />
                  <span class="text-muted" style="margin-left:8px">0 表示不调整</span>
                </el-form-item>
                <el-form-item label="取消熟食抽签资格">
                  <el-switch v-model="form.disqualifyCooked" active-text="取消该摊主后续熟食冷链摊位抽签资格" />
                </el-form-item>
                <el-form-item v-if="form.disqualifyCooked" label="资格限制截止">
                  <el-date-picker v-model="form.disqualifyUntil" type="date" placeholder="留空=永久取消"
                    value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
                <el-alert type="error" :closable="false" class="mb"
                  title="不合格后继续暂停该摊主熟食售卖，待整改后由管理员在列表中手动「解除暂停」。" />
              </template>
              <el-alert v-else type="success" :closable="false" class="mb"
                title="检测合格：自动解除送检期间的熟食售卖暂停，不扣保证金/信用，不影响抽签资格。" />

              <el-form-item>
                <el-button type="primary" :loading="saving" @click="submitResult">提交检测结果</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </template>

        <template v-else>
          <el-descriptions :column="1" border size="small" class="mb">
            <el-descriptions-item label="结果">
              <el-tag size="small" :type="inspStatus(current.status).type">{{ inspStatus(current.status).text }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="结果说明">{{ current.resultSummary }}</el-descriptions-item>
            <el-descriptions-item label="检测报告">
              <template v-if="current.reportPhoto">
                <el-image v-for="u in current.reportPhoto.split(',')" :key="u" :src="u"
                  :preview-src-list="current.reportPhoto.split(',')" class="img-thumb" fit="cover" preview-teleported />
              </template>
              <span v-else class="text-muted">无</span>
            </el-descriptions-item>
            <el-descriptions-item label="保证金处置">
              {{ current.depositDeducted && Number(current.depositDeducted) > 0 ? `已扣罚 ¥${current.depositDeducted}` : '未扣罚' }}
            </el-descriptions-item>
            <el-descriptions-item label="信用变动">{{ current.creditDelta || 0 }}</el-descriptions-item>
            <el-descriptions-item label="熟食抽签资格">
              <el-tag v-if="current.disqualifyCooked" size="small" type="danger">
                已取消{{ current.disqualifyUntil ? '至 ' + current.disqualifyUntil : '（永久）' }}
              </el-tag>
              <span v-else>不受影响</span>
            </el-descriptions-item>
            <el-descriptions-item label="登记人/时间">
              {{ current.resultBy?.name }} {{ fmtTime(current.resultAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { fmtTime, INSPECTION_STATUS } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'

const inspections = ref([])
const statusFilter = ref('')
const resultVisible = ref(false)
const saving = ref(false)
const current = ref(null)
const form = ref({})

function inspStatus(s) { return INSPECTION_STATUS[s] || { text: s, type: 'info' } }

async function load() {
  inspections.value = await api.get('/admin/inspections', {
    params: statusFilter.value ? { status: statusFilter.value } : {}
  })
}

function openResult(row) {
  current.value = row
  form.value = {
    status: 'UNQUALIFIED', resultSummary: '', reportPhoto: '',
    deductDeposit: true, creditDelta: -10, disqualifyCooked: true, disqualifyUntil: ''
  }
  resultVisible.value = true
}

async function submitResult() {
  if (!form.value.resultSummary) return ElMessage.warning('请填写检测结果说明')
  await ElMessageBox.confirm('确认提交检测结果？保证金扣罚、信用扣分与抽签资格限制将立即生效并留痕。',
    '确认处置', { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' })
  saving.value = true
  try {
    await api.post(`/admin/inspections/${current.value.id}/result`, form.value)
    ElMessage.success('检测结果已登记并完成联动处置')
    resultVisible.value = false
    await load()
  } finally { saving.value = false }
}

async function lift(row) {
  const { value } = await ElMessageBox.prompt('解除该摊主熟食售卖暂停（整改完成确认），可填写备注：', '解除暂停', {
    confirmButtonText: '确认解除', cancelButtonText: '取消', inputType: 'textarea', inputValue: ''
  }).catch(() => ({ value: null }))
  if (value === null || value === undefined) return
  await api.post(`/admin/inspections/${row.id}/lift`, { note: value || '' })
  ElMessage.success('已解除熟食售卖暂停')
  await load()
}

onMounted(load)
</script>
