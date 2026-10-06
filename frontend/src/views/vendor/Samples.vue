<template>
  <div class="page">
    <el-card>
      <div class="card-title">食品留样登记（卤味、糕点、凉菜等食品类摊主必传）</div>
      <el-form :model="form" label-width="100px" style="max-width: 560px">
        <el-form-item label="所属集市" required>
          <el-select v-model="form.eventId" style="width: 100%" @change="onEventChange">
            <el-option v-for="e in myEvents" :key="e.id" :label="`${e.name}（${e.marketDate}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="食品名称" required>
          <el-input v-model="form.productName" placeholder="如：卤牛肉" />
        </el-form-item>
        <el-form-item label="批次" required>
          <el-input v-model="form.batchNo" placeholder="如：20261015-01" />
        </el-form-item>
        <el-form-item label="留样照片" required>
          <ImageUpload v-model="form.photo" />
        </el-form-item>
        <el-form-item label="保存位置" required>
          <el-input v-model="form.storageLocation" placeholder="如：1号冷藏柜第2层" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">提交留样</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="mt">
      <div class="card-title">我的留样记录</div>
      <el-table :data="samples" size="small">
        <el-table-column label="集市" min-width="130">
          <template #default="{ row }">{{ row.event?.name }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="食品" width="110" />
        <el-table-column prop="batchNo" label="批次" width="130" />
        <el-table-column label="照片" width="100">
          <template #default="{ row }">
            <el-image v-if="row.photo" :src="row.photo.split(',')[0]" :preview-src-list="row.photo.split(',')"
              class="img-thumb" fit="cover" preview-teleported />
          </template>
        </el-table-column>
        <el-table-column prop="storageLocation" label="保存位置" min-width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'KEPT' ? 'success' : 'warning'" size="small">
              {{ { KEPT: '已留样', DISPOSED: '已处置', ABNORMAL: '异常' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="留样时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.retainedAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="mt">
      <div class="card-title">我的留样送检单（食品安全投诉处置）</div>
      <el-empty v-if="!inspections.length" description="暂无送检单" :image-size="60" />
      <div v-for="insp in inspections" :key="insp.id" class="insp-item">
        <div class="insp-head">
          <el-tag size="small" type="info">#{{ insp.id }}</el-tag>
          <el-tag size="small" type="warning" style="margin-left:6px">批次 {{ insp.batchNo }}</el-tag>
          <el-tag size="small" :type="inspStatus(insp.status).type" style="margin-left:6px">{{ inspStatus(insp.status).text }}</el-tag>
          <el-tag v-if="insp.cookedSuspended" size="small" type="danger" style="margin-left:6px">熟食售卖暂停中</el-tag>
          <span class="text-muted" style="margin-left:8px">{{ insp.event?.name }} · {{ insp.labName }}</span>
        </div>

        <el-alert v-if="insp.cookedSuspended" type="error" :closable="false" class="mt"
          title="您的熟食售卖已被暂停，检测结果出具/整改通过前不得售卖熟食" />

        <div v-if="['PENDING','IN_LAB'].includes(insp.status)" class="mt">
          <p>
            管理方要求同批次食品：
            <el-tag size="small" :type="insp.batchAction === 'REMOVE' ? 'danger' : 'warning'">
              {{ { REMOVE: '立即下架停售', KEEP_LIST: '留存同批次销售名单' }[insp.batchAction] }}
            </el-tag>
          </p>
          <div v-if="insp.batchReceiptStatus">
            您已于 {{ fmtTime(insp.batchReceiptAt) }} 回执：
            <el-tag size="small" :type="insp.batchReceiptStatus === 'REMOVED' ? 'danger' : 'warning'">
              {{ { REMOVED: '同批次已下架', LIST_KEPT: '已留存销售名单' }[insp.batchReceiptStatus] }}
            </el-tag>
            <span class="text-muted"> {{ insp.batchReceipt || '' }}</span>
          </div>
          <el-button v-else type="primary" size="small" class="mt" @click="openReceipt(insp)">提交处置回执</el-button>
        </div>

        <div v-else class="mt">
          <p><b>检测结果：</b><el-tag size="small" :type="inspStatus(insp.status).type">{{ inspStatus(insp.status).text }}</el-tag></p>
          <p class="text-muted">{{ insp.resultSummary }}</p>
          <p v-if="insp.depositDeducted && Number(insp.depositDeducted) > 0" style="color:#f56c6c">保证金已扣罚 ¥{{ insp.depositDeducted }}（详见「信用与追溯」）</p>
          <p v-if="insp.creditDelta" style="color:#f56c6c">信用变动 {{ insp.creditDelta }} 分（详见「信用与追溯」）</p>
          <p v-if="insp.disqualifyCooked" style="color:#f56c6c;font-weight:600">
            已被取消后续熟食摊位抽签资格{{ insp.disqualifyUntil ? '至 ' + insp.disqualifyUntil : '（永久）' }}，将影响后续熟食摊位抽签
          </p>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="receiptVisible" title="同批次食品处置回执" width="480px">
      <el-form label-width="90px">
        <el-form-item label="处置方式" required>
          <el-radio-group v-model="receiptForm.receiptStatus">
            <el-radio v-if="receiptAction === 'REMOVE'" value="REMOVED">同批次已下架</el-radio>
            <el-radio v-if="receiptAction === 'KEEP_LIST'" value="LIST_KEPT">已留存销售名单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="情况说明">
          <el-input v-model="receiptForm.receipt" type="textarea" :rows="3"
            :placeholder="receiptForm.receiptStatus === 'REMOVED' ? '如下架数量、下架时间、现场照片已交工作人员' : '如已登记同批次购买顾客名单与联系方式'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiptVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiptSaving" @click="submitReceipt">提交回执</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { fmtTime, INSPECTION_STATUS } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'

const results = ref([])
const samples = ref([])
const inspections = ref([])
const saving = ref(false)
const form = ref({ eventId: null, resultId: null, productName: '', batchNo: '', photo: '', storageLocation: '', remark: '' })

const myEvents = computed(() => {
  const seen = new Map()
  results.value.forEach(r => seen.set(r.application.event.id, r.application.event))
  return [...seen.values()]
})

function onEventChange(eventId) {
  const r = results.value.find(x => x.application.event.id === eventId)
  form.value.resultId = r?.id || null
}

async function submit() {
  const f = form.value
  if (!f.eventId || !f.productName || !f.batchNo || !f.photo || !f.storageLocation) {
    return ElMessage.warning('请完整填写留样信息并上传照片')
  }
  saving.value = true
  try {
    await api.post('/vendor/samples', f)
    ElMessage.success('留样登记成功')
    form.value = { ...f, productName: '', batchNo: '', photo: '', storageLocation: '', remark: '' }
    samples.value = await api.get('/vendor/samples')
  } finally { saving.value = false }
}

function inspStatus(s) { return INSPECTION_STATUS[s] || { text: s, type: 'info' } }

// ===== 送检同批次处置回执 =====
const receiptVisible = ref(false)
const receiptSaving = ref(false)
const receiptInspectionId = ref(null)
const receiptAction = ref('REMOVE')
const receiptForm = ref({ receiptStatus: 'REMOVED', receipt: '' })

function openReceipt(insp) {
  receiptInspectionId.value = insp.id
  receiptAction.value = insp.batchAction
  receiptForm.value = {
    receiptStatus: insp.batchAction === 'KEEP_LIST' ? 'LIST_KEPT' : 'REMOVED',
    receipt: ''
  }
  receiptVisible.value = true
}

async function submitReceipt() {
  receiptSaving.value = true
  try {
    await api.post(`/vendor/inspections/${receiptInspectionId.value}/receipt`, receiptForm.value)
    ElMessage.success('回执已提交，管理方将跟进检测')
    receiptVisible.value = false
    inspections.value = await api.get('/vendor/inspections')
  } finally { receiptSaving.value = false }
}

onMounted(async () => {
  const [res, sams, insps] = await Promise.all([
    api.get('/vendor/results'), api.get('/vendor/samples'), api.get('/vendor/inspections')
  ])
  results.value = res
  samples.value = sams
  inspections.value = insps
  if (myEvents.value.length) {
    form.value.eventId = myEvents.value[0].id
    onEventChange(form.value.eventId)
  }
})
</script>

<style scoped>
.insp-item { border: 1px solid #ebeef5; border-radius: 6px; padding: 12px 14px; margin-bottom: 12px; }
.insp-head { display: flex; align-items: center; flex-wrap: wrap; }
</style>
