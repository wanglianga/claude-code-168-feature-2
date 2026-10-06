<template>
  <div>
    <el-upload
      :show-file-list="false"
      :http-request="doUpload"
      accept="image/*"
    >
      <el-button :loading="uploading" size="small"><el-icon style="margin-right:4px"><Plus /></el-icon>{{ buttonText }}</el-button>
    </el-upload>
    <div class="preview" v-if="urls.length">
      <el-image
        v-for="(u, i) in urls" :key="u"
        :src="u" :preview-src-list="urls" :initial-index="i"
        class="img-thumb" fit="cover" preview-teleported
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '../api'

const props = defineProps({
  modelValue: { type: String, default: '' },
  publicUpload: { type: Boolean, default: false },
  buttonText: { type: String, default: '上传照片' }
})
const emit = defineEmits(['update:modelValue'])
const uploading = ref(false)
const urls = ref(props.modelValue ? props.modelValue.split(',').filter(Boolean) : [])

watch(() => props.modelValue, v => {
  urls.value = v ? v.split(',').filter(Boolean) : []
})

async function doUpload({ file }) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const url = props.publicUpload ? '/files/public-upload' : '/files/upload'
    const res = await api.post(url, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    urls.value.push(res.url)
    emit('update:modelValue', urls.value.join(','))
    ElMessage.success('上传成功')
  } finally { uploading.value = false }
}
</script>

<style scoped>
.preview { margin-top: 8px; display: flex; flex-wrap: wrap; }
</style>
