<template>
  <div class="wrap">
    <el-card class="card">
      <h2>消费者投诉</h2>
      <p class="sub">无需注册，凭摊位号提交投诉，市场管理方将及时处理</p>
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
          <el-input v-model="form.reporterPhone" />
        </el-form-item>
        <el-form-item label="购买时间" :required="form.type === 'DIARRHEA'">
          <el-date-picker v-model="form.purchaseTime" type="datetime" placeholder="选择购买食品的时间"
            value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" style="width: 100%" />
          <div class="sub" style="margin:4px 0 0">用于按 48 小时留样规定锁定同批次留样与同批次消费者</div>
        </el-form-item>
        <el-form-item label="支付凭证">
          <ImageUpload v-model="form.paymentProof" />
          <div class="sub" style="margin:4px 0 0">微信/支付宝付款截图或收据照片（腹泻等食品安全投诉建议上传）</div>
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
        <el-form-item label="情况说明" required>
          <el-input v-model="form.description" type="textarea" :rows="4"
            placeholder="请描述事情经过，如食用某摊位凉拌菜后腹泻，尽量写明食用时间、就医情况" />
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
const form = ref({ eventId: null, stallNo: '', reporterName: '', reporterPhone: '', type: 'DIARRHEA', purchaseTime: null, paymentProof: '', description: '' })

async function submit() {
  if (!form.value.stallNo || !form.value.reporterName || !form.value.description) {
    return ElMessage.warning('请完整填写摊位号、称呼与情况说明')
  }
  if (form.value.type === 'DIARRHEA' && !form.value.purchaseTime) {
    return ElMessage.warning('腹泻投诉请填写购买时间，以便锁定同批次留样')
  }
  submitting.value = true
  try {
    const res = await api.post('/complaints/public', form.value)
    ElMessageBox.alert(`投诉编号 #${res.complaintId}，${res.message}`, '提交成功', { type: 'success' })
    form.value.stallNo = ''
    form.value.description = ''
    form.value.purchaseTime = null
    form.value.paymentProof = ''
  } finally { submitting.value = false }
}

onMounted(async () => {
  events.value = await api.get('/meta/events')
  const active = events.value.find(e => ['LOTTERY_DONE', 'IN_PROGRESS'].includes(e.status))
  if (active) form.value.eventId = active.id
})
</script>

<style scoped>
.wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1f3a5f, #2e5c3f); }
.card { width: 520px; }
h2 { text-align: center; }
.sub { text-align: center; color: #909399; font-size: 13px; margin: 8px 0 16px; }
</style>
