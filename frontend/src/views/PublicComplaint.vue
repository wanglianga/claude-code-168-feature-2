<template>
  <div class="wrap">
    <el-card class="card">
      <h2>消费者投诉</h2>
      <p class="sub">无需注册，凭摊位号提交投诉；腹泻等食品安全问题请尽量填写购买时间与支付凭证，便于平台关联留样送检</p>
      <el-form :model="form" label-width="90px">
        <el-form-item label="集市">
          <el-select v-model="form.eventId" style="width: 100%">
            <el-option v-for="e in events" :key="e.id" :label="`${e.name}（${e.marketDate}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="摊位号" required>
          <el-input v-model="form.stallNo" placeholder="如：B-01" />
        </el-form-item>
        <el-form-item label="您的称呼" required>
          <el-input v-model="form.reporterName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.reporterPhone" placeholder="便于同批次食品协查时联系您" />
        </el-form-item>
        <el-form-item label="投诉类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="腹泻/食品安全" value="DIARRHEA" />
            <el-option label="价格问题" value="PRICE" />
            <el-option label="缺斤少两" value="SHORT_WEIGHT" />
            <el-option label="卫生问题" value="HYGIENE" />
            <el-option label="质量问题" value="QUALITY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <template v-if="form.type === 'DIARRHEA' || form.type === 'QUALITY'">
          <el-form-item label="食品名称">
            <el-input v-model="form.foodName" placeholder="如：凉拌黄瓜、卤牛肉" />
          </el-form-item>
          <el-form-item label="购买时间">
            <el-date-picker v-model="purchaseTime" type="datetime" placeholder="选择购买/食用时间"
              style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-form-item>
          <el-form-item label="包装批次">
            <el-input v-model="form.batchNo" placeholder="包装/小票上的批次号（如有）" />
          </el-form-item>
          <el-form-item label="支付凭证">
            <ImageUpload v-model="form.paymentProof" public-upload button-text="上传支付截图/小票" />
          </el-form-item>
        </template>
        <el-form-item label="情况说明" required>
          <el-input v-model="form.description" type="textarea" :rows="4"
            placeholder="请描述事情经过，如食用某摊位凉拌菜后腹泻，含就餐人数、发病时间等" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">提交投诉</el-button>
          <el-button @click="$router.push('/login')">返回登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import ImageUpload from '../components/ImageUpload.vue'

const events = ref([])
const submitting = ref(false)
const purchaseTime = ref(null)
const form = ref({
  eventId: null, stallNo: '', reporterName: '', reporterPhone: '',
  type: 'DIARRHEA', description: '', foodName: '', batchNo: '', paymentProof: ''
})

async function submit() {
  if (!form.value.stallNo || !form.value.reporterName || !form.value.description) {
    return ElMessage.warning('请完整填写摊位号、称呼与情况说明')
  }
  submitting.value = true
  try {
    const payload = { ...form.value, purchaseTime: purchaseTime.value || null }
    const res = await api.post('/complaints/public', payload)
    ElMessageBox.alert(
      `投诉编号 #${res.complaintId}，${res.message}。如涉及食品安全，平台将关联摊主留样发起送检，可向管理方查询编号 ${res.complaintId}`,
      '提交成功', { type: 'success' })
    form.value.stallNo = ''
    form.value.description = ''
    form.value.paymentProof = ''
    purchaseTime.value = null
  } finally { submitting.value = false }
}

onMounted(async () => {
  events.value = await api.get('/meta/events')
  const active = events.value.find(e => ['LOTTERY_DONE', 'IN_PROGRESS'].includes(e.status))
  if (active) form.value.eventId = active.id
})
</script>

<style scoped>
.wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1f3a5f, #2e5c3f); padding: 24px; }
.card { width: 560px; }
h2 { text-align: center; }
.sub { text-align: center; color: #909399; font-size: 13px; margin: 8px 0 16px; }
</style>
