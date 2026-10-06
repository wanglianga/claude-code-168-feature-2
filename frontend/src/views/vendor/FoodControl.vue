<template>
  <div class="page">
    <!-- 待响应的同批次管控 -->
    <el-card v-if="pending.length">
      <div class="card-title"><el-tag type="danger" effect="dark">待处理</el-tag>
        同批次食品管控要求（{{ pending.length }}）
      </div>
      <el-alert type="error" :closable="false" class="mb"
        title="市场管理方已就食品安全投诉发起留样送检。送检期间，同批次食品须按要求立即下架，或继续售卖时完整留存购买者名单，否则将从重处理。" />
      <div v-for="item in pending" :key="item.labTest.id" class="control-box">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="送检单">#{{ item.labTest.id }}</el-descriptions-item>
          <el-descriptions-item label="食品/批次">{{ item.labTest.productName }} / {{ item.labTest.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="管控要求" :span="2">
            <el-tag :type="item.labTest.batchControl === 'REMOVE' ? 'danger' : 'warning'">
              {{ item.labTest.batchControl === 'REMOVE' ? '立即下架同批次食品' : '继续售卖须留存购买者名单' }}
            </el-tag>
            <span class="text-muted" style="margin-left:8px">{{ item.labTest.controlNotice }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源投诉">#{{ item.labTest.complaint?.id }} {{ item.labTest.complaint?.description }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ fmtTime(item.labTest.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <div class="respond-bar">
          <template v-if="item.labTest.batchControl === 'REMOVE'">
            <el-button type="danger" @click="quickRespond(item, 'REMOVED')">确认已下架</el-button>
            <el-button @click="quickRespond(item, 'SOLD_OUT')">已售罄（无剩余）</el-button>
          </template>
          <el-button :type="item.labTest.batchControl === 'KEEP_LIST' ? 'warning' : ''"
            @click="openList(item)">{{ item.labTest.batchControl === 'KEEP_LIST' ? '填写销售名单' : '同时留存销售名单' }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- 生效中的暂停/冻结 -->
    <el-card v-if="activeSuspensions.length" class="mt">
      <div class="card-title">我的熟食售卖/抽签资格限制（生效中 {{ activeSuspensions.length }}）</div>
      <el-table :data="activeSuspensions" size="small">
        <el-table-column label="类型" width="180">
          <template #default="{ row }">{{ SUSPENSION_TYPE[row.type] }}</template>
        </el-table-column>
        <el-table-column label="起止" width="210">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate || '长期（至人工解除）' }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="260">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reasonType === 'LAB_UNQUALIFIED' ? 'danger' : 'warning'">{{ SUSPENSION_REASON[row.reasonType] }}</el-tag>
            {{ row.reason }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 送检单记录 -->
    <el-card class="mt">
      <div class="card-title">我的留样送检记录</div>
      <el-empty v-if="!labTests.length" description="暂无送检记录" />
      <el-table v-else :data="labTests" size="small">
        <el-table-column label="编号" width="70"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
        <el-table-column label="集市" min-width="120">
          <template #default="{ row }">{{ row.event?.name }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="食品" width="100" />
        <el-table-column prop="batchNo" label="批次" width="120" />
        <el-table-column label="检测机构" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.labName || '-' }}</template>
        </el-table-column>
        <el-table-column label="管控要求" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.batchControl === 'REMOVE' ? 'danger' : 'warning'">{{ BATCH_CONTROL[row.batchControl] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="LAB_TEST_STATUS[row.status]?.type">{{ LAB_TEST_STATUS[row.status]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检测结果" min-width="200">
          <template #default="{ row }">
            <span v-if="row.status === 'UNQUALIFIED'" class="unq">
              不合格：扣保证金¥{{ row.depositPenalty }}、信用{{ row.creditDelta }}、暂停售卖{{ row.suspendDays }}天、冻结抽签{{ row.lotteryBanDays }}天
            </span>
            <span v-else-if="row.status === 'QUALIFIED'" class="text-muted">合格，已解除调查管控</span>
            <span v-else class="text-muted">结果待出</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 销售名单编辑 -->
    <el-dialog v-model="listVisible" title="同批次购买者销售名单" width="720px">
      <el-alert type="warning" :closable="false" class="mb"
        title="请如实登记同批次食品购买者（姓名/联系方式/数量/时间），用于出现食品安全问题时协查通知；登记情况将同步市场管理方。" />
      <el-table :data="listRows" size="small">
        <el-table-column label="购买者" width="120">
          <template #default="{ row }"><el-input v-model="row.buyer" size="small" placeholder="姓名/称呼" /></template>
        </el-table-column>
        <el-table-column label="联系电话" width="140">
          <template #default="{ row }"><el-input v-model="row.phone" size="small" /></template>
        </el-table-column>
        <el-table-column label="数量" width="100">
          <template #default="{ row }"><el-input v-model="row.qty" size="small" placeholder="如1份" /></template>
        </el-table-column>
        <el-table-column label="购买时间" min-width="170">
          <template #default="{ row }">
            <el-date-picker v-model="row.soldAt" size="small" type="datetime" value-format="YYYY-MM-DD HH:mm"
              placeholder="时间" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="100">
          <template #default="{ row }"><el-input v-model="row.note" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="60">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" @click="listRows.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button size="small" class="mt" @click="listRows.push({ buyer: '', phone: '', qty: '', soldAt: '', note: '' })">+ 添加购买者</el-button>
      <el-input v-model="listNote" type="textarea" :rows="2" class="mt" placeholder="补充说明（如：未留联系方式的散客数量、剩余同批次食品去向）" />
      <template #footer>
        <el-button @click="listVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingList" @click="submitList">提交名单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { fmtTime, LAB_TEST_STATUS, BATCH_CONTROL, SUSPENSION_TYPE, SUSPENSION_REASON } from '../../utils/dict'

const pending = ref([])
const labTests = ref([])
const suspensions = ref([])
const listVisible = ref(false)
const listRows = ref([])
const listNote = ref('')
const currentItem = ref(null)
const submittingList = ref(false)

const activeSuspensions = computed(() => suspensions.value.filter(s => s.status === 'ACTIVE'))

async function load() {
  const [p, t, s] = await Promise.all([
    api.get('/vendor/batch-controls'),
    api.get('/vendor/lab-tests'),
    api.get('/vendor/suspensions')
  ])
  pending.value = p
  labTests.value = t
  suspensions.value = s
}

async function quickRespond(item, status) {
  const text = status === 'REMOVED' ? '已立即下架同批次食品' : '同批次食品已售罄、无剩余'
  try {
    await ElMessageBox.confirm(`确认「${text}」？确认后将同步市场管理方并记录留痕。`, '管控响应', { type: 'warning' })
  } catch { return }
  await api.post(`/vendor/samples/${item.sample.id}/sales-control`, {
    sellingStatus: status, salesList: [], note: text
  })
  ElMessage.success('已响应管控要求')
  await load()
}

function openList(item) {
  currentItem.value = item
  try {
    listRows.value = item.sample.salesList ? JSON.parse(item.sample.salesList) : []
  } catch { listRows.value = [] }
  if (!listRows.value.length) listRows.value = [{ buyer: '', phone: '', qty: '', soldAt: '', note: '' }]
  listNote.value = item.sample.salesListNote || ''
  listVisible.value = true
}

async function submitList() {
  const rows = listRows.value.filter(r => r.buyer || r.phone)
  if (!rows.length) return ElMessage.warning('请至少填写一位购买者信息')
  submittingList.value = true
  try {
    await api.post(`/vendor/samples/${currentItem.value.sample.id}/sales-control`, {
      sellingStatus: 'KEPT_LIST', salesList: rows, note: listNote.value
    })
    ElMessage.success('销售名单已提交市场管理方')
    listVisible.value = false
    await load()
  } finally { submittingList.value = false }
}

onMounted(load)
</script>

<style scoped>
.control-box { border: 1px solid #f56c6c55; border-radius: 6px; padding: 10px; margin-bottom: 12px; background: #fef0f011; }
.respond-bar { margin-top: 10px; display: flex; gap: 8px; }
.unq { color: #f56c6c; font-size: 12px; }
</style>
