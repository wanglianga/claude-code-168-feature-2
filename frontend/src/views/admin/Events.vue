<template>
  <div class="page">
    <el-card>
      <div style="display:flex;justify-content:space-between;align-items:center" class="mb">
        <div class="card-title" style="margin:0">集市管理</div>
        <el-button type="primary" @click="createVisible = true">新建集市</el-button>
      </div>
      <el-table :data="events" size="small">
        <el-table-column prop="name" label="集市名称" min-width="130" />
        <el-table-column prop="marketDate" label="赶集日" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="eventStatus[row.status]?.type">{{ eventStatus[row.status]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="380">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" size="small" type="success" @click="setStatus(row, 'OPEN')">开放报名</el-button>
            <el-button v-if="row.status === 'OPEN'" size="small" type="warning" @click="setStatus(row, 'CLOSED')">截止报名</el-button>
            <el-button v-if="row.status === 'LOTTERY_DONE'" size="small" type="primary" @click="setStatus(row, 'IN_PROGRESS')">开市</el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" size="small" type="info" @click="setStatus(row, 'FINISHED')">结束</el-button>
            <el-button size="small" @click="viewApplications(row)">报名审核</el-button>
            <el-button v-if="row.status === 'CLOSED'" size="small" type="danger" @click="openLottery(row)">组织抽签</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建集市 -->
    <el-dialog v-model="createVisible" title="新建集市" width="440px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="名称" required><el-input v-model="createForm.name" placeholder="如：冬月初八大集" /></el-form-item>
        <el-form-item label="赶集日" required>
          <el-date-picker v-model="createForm.marketDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="createForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createEvent">创建</el-button>
      </template>
    </el-dialog>

    <!-- 报名审核 -->
    <el-drawer v-model="appsVisible" :title="`报名审核 - ${currentEvent?.name}`" size="70%">
      <el-table :data="applications" size="small">
        <el-table-column label="摊主" width="130">
          <template #default="{ row }">
            {{ row.vendor?.name }}
            <el-tag v-if="row.veteran" type="success" size="small">老摊主</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="品类" width="100">
          <template #default="{ row }">{{ categories[row.category] }}</template>
        </el-table-column>
        <el-table-column label="车辆(米)" width="100">
          <template #default="{ row }">{{ row.vehicleLength || '-' }}×{{ row.vehicleWidth || '-' }}</template>
        </el-table-column>
        <el-table-column label="需求" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.needsPower" size="small" class="mr">用电</el-tag>
            <el-tag v-if="row.sellsCookedFood" size="small" type="warning" class="mr">熟食</el-tag>
            <el-tag v-if="row.sellsLivePoultry" size="small" type="danger" class="mr">活禽</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="证照" width="90">
          <template #default="{ row }">
            <el-image v-if="firstPhoto(row.licensePhotos)" :src="firstPhoto(row.licensePhotos)"
              :preview-src-list="splitPhotos(row.licensePhotos)" class="img-thumb" style="width:40px;height:40px"
              fit="cover" preview-teleported />
            <span v-else class="text-muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="historyRecord" label="历史经营" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 'BLOCKED'" :content="row.blockReason || '食品安全资格受限'">
              <el-tag type="danger" size="small">资格受限</el-tag>
            </el-tooltip>
            <el-tag v-else :type="{ PENDING: 'warning', APPROVED: 'primary', REJECTED: 'danger', WON: 'success', LOST: 'info' }[row.status]" size="small">
              {{ { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', WON: '已中签', LOST: '未中签' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button size="small" type="success" @click="review(row, true)">通过</el-button>
              <el-button size="small" type="danger" @click="review(row, false)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 抽签 -->
    <el-dialog v-model="lotteryVisible" :title="`组织抽签 - ${currentEvent?.name}`" width="480px">
      <el-alert type="info" :closable="false" class="mb"
        title="抽签规则：老摊主优先；熟食→冷链区；活禽→隔离区；用电→带电源摊位；车辆超限排除；消防通道不参与分配；送检不合格处于熟食售卖暂停/抽签资格冻结期的摊主不参与熟食类摊位抽签（标记为资格受限）" />
      <el-form label-width="110px">
        <el-form-item label="随机种子" required>
          <el-input-number v-model="lotterySeed" :min="1" style="width: 100%" />
          <div class="text-muted">种子将公示并留档，任何人可凭种子+快照复核抽签结果</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="lotteryRemark" placeholder="如：现场见证人、抽签地点" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lotteryVisible = false">取消</el-button>
        <el-button type="danger" :loading="lotteryRunning" @click="runLottery">执行抽签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { CATEGORIES, EVENT_STATUS } from '../../utils/dict'

const categories = CATEGORIES
const eventStatus = EVENT_STATUS
const events = ref([])
const applications = ref([])
const createVisible = ref(false)
const appsVisible = ref(false)
const lotteryVisible = ref(false)
const lotteryRunning = ref(false)
const currentEvent = ref(null)
const lotterySeed = ref(Math.floor(Math.random() * 90000000) + 10000000)
const lotteryRemark = ref('')
const createForm = ref({ name: '', marketDate: '', remark: '' })

function firstPhoto(s) { return splitPhotos(s)[0] }
function splitPhotos(s) { return s ? s.split(',').filter(Boolean) : [] }

async function load() { events.value = await api.get('/admin/events') }

async function createEvent() {
  if (!createForm.value.name || !createForm.value.marketDate) return ElMessage.warning('请填写名称和赶集日')
  await api.post('/admin/events', createForm.value)
  ElMessage.success('创建成功')
  createVisible.value = false
  createForm.value = { name: '', marketDate: '', remark: '' }
  await load()
}

async function setStatus(row, status) {
  await ElMessageBox.confirm(`确认将「${row.name}」状态变更为 ${eventStatus[status]?.text}？`, '状态变更')
  await api.post(`/admin/events/${row.id}/status`, { status })
  ElMessage.success('已变更')
  await load()
}

async function viewApplications(row) {
  currentEvent.value = row
  applications.value = await api.get(`/admin/events/${row.id}/applications`)
  appsVisible.value = true
}

async function review(row, approve) {
  const note = approve ? '审核通过' : (await ElMessageBox.prompt('驳回原因', '驳回报名', { inputValue: '证照不全' })).value
  await api.post(`/admin/applications/${row.id}/review`, { approve, note })
  ElMessage.success('已处理')
  applications.value = await api.get(`/admin/events/${currentEvent.value.id}/applications`)
}

function openLottery(row) {
  currentEvent.value = row
  lotterySeed.value = Math.floor(Math.random() * 90000000) + 10000000
  lotteryVisible.value = true
}

async function runLottery() {
  lotteryRunning.value = true
  try {
    const run = await api.post(`/admin/events/${currentEvent.value.id}/lottery`, {
      seed: lotterySeed.value, remark: lotteryRemark.value
    })
    ElMessage.success(`抽签完成：${run.remark}`)
    lotteryVisible.value = false
    await load()
  } finally { lotteryRunning.value = false }
}

onMounted(load)
</script>

<style scoped>
.mr { margin-right: 4px; }
</style>
