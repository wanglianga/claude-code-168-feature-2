<template>
  <div class="page">
    <el-card>
      <div style="display:flex;justify-content:space-between;align-items:center" class="mb">
        <div class="card-title" style="margin:0">复盘看板（高投诉品类 / 拥堵区域 / 收费到账 / 食品安全）</div>
        <el-select v-model="eventId" placeholder="选择集市" style="width: 280px" @change="load">
          <el-option v-for="e in events" :key="e.id" :label="`${e.name}（${e.marketDate}）`" :value="e.id" />
        </el-select>
      </div>
      <template v-if="data">
        <el-row :gutter="16" class="mb">
          <el-col :span="4"><el-card shadow="never"><div class="stat">{{ data.overview.assigned }}</div><div class="lbl">中签摊位</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="stat">{{ data.overview.checkedIn }}</div><div class="lbl">已核验入场</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="stat">{{ data.overview.complaints }}</div><div class="lbl">投诉/申诉</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="stat">{{ data.overview.patrols }}</div><div class="lbl">巡查记录</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="stat">{{ data.overview.adjustments }}</div><div class="lbl">调整记录</div></el-card></el-col>
          <el-col :span="4"><el-card shadow="never"><div class="stat warn">{{ data.foodSafety.diarrheaComplaints }}</div><div class="lbl">腹泻投诉</div></el-card></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-card shadow="never"><div ref="catChart" style="height:300px" /></el-card></el-col>
          <el-col :span="12"><el-card shadow="never"><div ref="laneChart" style="height:300px" /></el-card></el-col>
        </el-row>
        <el-row :gutter="16" class="mt">
          <el-col :span="12"><el-card shadow="never"><div ref="payChart" style="height:300px" /></el-card></el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <div class="card-title">食品安全风险</div>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="应留样食品摊位">{{ data.foodSafety.sampleRequiredVendors }} 家</el-descriptions-item>
                <el-descriptions-item label="已提交留样">
                  <el-tag :type="data.foodSafety.samplesSubmitted >= data.foodSafety.sampleRequiredVendors ? 'success' : 'danger'">
                    {{ data.foodSafety.samplesSubmitted }} 批次
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="腹泻类投诉">{{ data.foodSafety.diarrheaComplaints }} 起</el-descriptions-item>
                <el-descriptions-item label="缺斤少两记录">{{ data.foodSafety.shortWeightRecords }} 次</el-descriptions-item>
                <el-descriptions-item label="平均卫生评分">{{ data.foodSafety.avgHygieneScore }} / 5</el-descriptions-item>
                <el-descriptions-item label="留样送检单">
                  {{ data.foodSafety.labTests }} 份
                  （检测中 {{ data.foodSafety.labTesting }}，不合格
                  <el-tag size="small" type="danger">{{ data.foodSafety.labUnqualified }}</el-tag>，合格
                  {{ data.foodSafety.labQualified }}）
                </el-descriptions-item>
                <el-descriptions-item label="暂停熟食售卖">
                  <el-tag size="small" :type="data.foodSafety.cookedSalesSuspended ? 'danger' : 'info'">
                    {{ data.foodSafety.cookedSalesSuspended }} 个摊主
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="冻结熟食抽签资格">
                  <el-tag size="small" :type="data.foodSafety.cookedLotteryBanned ? 'danger' : 'info'">
                    {{ data.foodSafety.cookedLotteryBanned }} 个摊主
                  </el-tag>
                </el-descriptions-item>
              </el-descriptions>
              <el-alert v-if="data.foodSafety.samplesSubmitted < data.foodSafety.sampleRequiredVendors"
                type="error" class="mt" :closable="false" title="存在食品摊位未按要求留样，请督促整改" />
            </el-card>
          </el-col>
        </el-row>
      </template>
      <el-empty v-else description="请选择集市查看复盘数据" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import api from '../../api'

const events = ref([])
const eventId = ref(null)
const data = ref(null)
const catChart = ref(null)
const laneChart = ref(null)
const payChart = ref(null)
let charts = []

function render() {
  charts.forEach(c => c.dispose())
  charts = []
  const cat = echarts.init(catChart.value)
  cat.setOption({
    title: { text: '高投诉品类' },
    tooltip: {},
    xAxis: { type: 'category', data: Object.keys(data.value.complaintByCategory) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', data: Object.values(data.value.complaintByCategory), itemStyle: { color: '#f56c6c' }, label: { show: true, position: 'top' } }]
  })
  const lane = echarts.init(laneChart.value)
  const zones = [...new Set([...Object.keys(data.value.laneOccupationByZone), ...Object.keys(data.value.relocateByZone)])]
  lane.setOption({
    title: { text: '拥堵/占道与调位区域' },
    tooltip: {},
    legend: { bottom: 0 },
    xAxis: { type: 'category', data: zones },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '占道记录', type: 'bar', data: zones.map(z => data.value.laneOccupationByZone[z] || 0), itemStyle: { color: '#e6a23c' } },
      { name: '调位次数', type: 'bar', data: zones.map(z => data.value.relocateByZone[z] || 0), itemStyle: { color: '#409eff' } }
    ]
  })
  const pay = echarts.init(payChart.value)
  pay.setOption({
    title: { text: '收费到账情况（元）' },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '65%'],
      data: Object.entries(data.value.paymentByStatus).map(([name, value]) => ({ name, value })),
      label: { formatter: '{b}: ¥{c}' }
    }]
  })
  charts = [cat, lane, pay]
}

async function load() {
  if (!eventId.value) return
  data.value = await api.get('/admin/dashboard', { params: { eventId: eventId.value } })
  await nextTick()
  render()
}

onMounted(async () => {
  events.value = await api.get('/admin/events')
  const active = events.value.find(e => ['LOTTERY_DONE', 'IN_PROGRESS', 'FINISHED'].includes(e.status))
  if (active) { eventId.value = active.id; await load() }
})
</script>

<style scoped>
.stat { font-size: 28px; font-weight: 700; color: #409eff; text-align: center; }
.stat.warn { color: #f56c6c; }
.lbl { text-align: center; color: #909399; font-size: 13px; margin-top: 4px; }
</style>
