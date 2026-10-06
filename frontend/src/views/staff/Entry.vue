<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card>
          <div class="card-title">扫码 / 输入入场码核验</div>
          <el-input v-model="token" placeholder="扫描或输入摊主入场码" clearable @keyup.enter="scan">
            <template #append><el-button type="primary" @click="scan">核验</el-button></template>
          </el-input>
          <div v-if="scanned" class="mt">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="摊主">{{ scanned.result.application?.vendor?.name }}</el-descriptions-item>
              <el-descriptions-item label="电话">{{ scanned.result.application?.vendor?.phone }}</el-descriptions-item>
              <el-descriptions-item label="摊位号">
                <el-tag type="success" size="large">{{ scanned.result.stallNo }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="入场时间">{{ scanned.result.entryTime }}</el-descriptions-item>
              <el-descriptions-item label="申报品类">{{ categories[scanned.result.application?.category] }}</el-descriptions-item>
              <el-descriptions-item label="费用状态">
                <el-tag :type="allPaid ? 'success' : 'danger'" size="small">{{ allPaid ? '已缴清' : '有未缴费用' }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>
            <el-alert v-if="scanned.restriction?.cookedSalesSuspended" type="error" class="mt" :closable="false"
              :title="`该摊主熟食售卖已暂停${scanned.restriction.suspensionReason ? '：' + scanned.restriction.suspensionReason : ''}，入场时禁止其售卖熟食`" />
            <el-alert v-if="scanned.entryCheck" type="warning" class="mt" :closable="false"
              :title="`已于 ${fmtTime(scanned.entryCheck.checkedAt)} 完成核验（${scanned.entryCheck.staff?.name}）`" />
            <template v-else>
              <el-divider />
              <div class="card-title">入场登记</div>
              <el-form :model="checkForm" label-width="100px">
                <el-form-item label="实际品类">
                  <el-select v-model="checkForm.actualCategory" style="width: 100%">
                    <el-option v-for="(n, c) in categories" :key="c" :label="n" :value="c" />
                  </el-select>
                  <div class="text-muted" v-if="categoryChanged" style="color:#e6a23c">
                    与申报品类不一致，将记录为临时换品类
                  </div>
                </el-form-item>
                <el-form-item label="按位摆摊">
                  <el-switch v-model="checkForm.atAssignedStall" active-text="是" inactive-text="否" />
                </el-form-item>
                <el-form-item label="布置照片">
                  <ImageUpload v-model="checkForm.photos" />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="checkForm.notes" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="saving" @click="submitCheck">确认入场</el-button>
                </el-form-item>
              </el-form>
            </template>
          </div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card>
          <div class="card-title">集市摊位总览</div>
          <el-select v-model="eventId" placeholder="选择集市" class="mb" style="width: 280px" @change="loadAssignments">
            <el-option v-for="e in events" :key="e.id" :label="`${e.name}（${e.marketDate}）`" :value="e.id" />
          </el-select>
          <el-table :data="assignments" size="small" max-height="560">
            <el-table-column label="摊位" width="80">
              <template #default="{ row }"><el-tag size="small">{{ row.result.stallNo }}</el-tag></template>
            </el-table-column>
            <el-table-column label="摊主" width="130">
              <template #default="{ row }">{{ row.result.application?.vendor?.name }}</template>
            </el-table-column>
            <el-table-column label="品类" width="100">
              <template #default="{ row }">{{ categories[row.result.application?.category] }}</template>
            </el-table-column>
            <el-table-column label="熟食限制" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.restriction?.cookedSalesSuspended" size="small" type="danger">暂停熟食</el-tag>
                <el-tag v-else-if="row.restriction?.cookedLotteryBanned" size="small" type="warning">取消熟食抽签</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="入场" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.entryCheck" type="success" size="small">已核验</el-tag>
                <el-tag v-else type="info" size="small">未入场</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="按位" width="70">
              <template #default="{ row }">
                <span v-if="row.entryCheck">{{ row.entryCheck.atAssignedStall ? '✓' : '✗' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="patrolCount" label="巡查" width="60" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" @click="fillToken(row.result.qrToken)">核验</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { CATEGORIES, fmtTime } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'

const categories = CATEGORIES
const token = ref('')
const scanned = ref(null)
const events = ref([])
const eventId = ref(null)
const assignments = ref([])
const saving = ref(false)
const checkForm = ref({ actualCategory: '', atAssignedStall: true, photos: '', notes: '' })

const categoryChanged = computed(() =>
  scanned.value && checkForm.value.actualCategory !== scanned.value.result.application?.category)
const allPaid = computed(() => {
  if (!scanned.value?.payments?.length) return true
  return scanned.value.payments.every(p => p.status !== 'UNPAID')
})

function fillToken(t) { token.value = t; scan() }

async function scan() {
  if (!token.value) return
  try {
    scanned.value = await api.get(`/staff/entry/scan/${token.value}`)
    checkForm.value.actualCategory = scanned.value.result.application?.category
    checkForm.value.atAssignedStall = true
  } catch { scanned.value = null }
}

async function submitCheck() {
  saving.value = true
  try {
    await api.post('/staff/entry/check', { resultId: scanned.value.result.id, ...checkForm.value })
    ElMessage.success('入场核验完成')
    scanned.value = null
    token.value = ''
    if (eventId.value) await loadAssignments()
  } finally { saving.value = false }
}

async function loadAssignments() {
  assignments.value = await api.get(`/staff/events/${eventId.value}/assignments`)
}

onMounted(async () => {
  events.value = await api.get('/meta/events')
  const active = events.value.find(e => ['LOTTERY_DONE', 'IN_PROGRESS'].includes(e.status))
  if (active) { eventId.value = active.id; await loadAssignments() }
})
</script>
