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
            <el-tag :type="row.status === 'KEPT' ? 'success' : row.status === 'ABNORMAL' ? 'danger' : 'warning'" size="small">
              {{ { KEPT: '已留样', DISPOSED: '已处置', ABNORMAL: '异常', LAB_TESTING: '送检中', LAB_SENT: '已送机构', DESTROYED: '已销毁' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="同批次管控" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="SELLING_STATUS[row.sellingStatus]?.type">{{ SELLING_STATUS[row.sellingStatus]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="留样时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.retainedAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { fmtTime, SELLING_STATUS } from '../../utils/dict'
import ImageUpload from '../../components/ImageUpload.vue'

const results = ref([])
const samples = ref([])
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

onMounted(async () => {
  const [res, sams] = await Promise.all([api.get('/vendor/results'), api.get('/vendor/samples')])
  results.value = res
  samples.value = sams
  if (myEvents.value.length) {
    form.value.eventId = myEvents.value[0].id
    onEventChange(form.value.eventId)
  }
})
</script>
