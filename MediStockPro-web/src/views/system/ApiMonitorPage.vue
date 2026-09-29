<template>
  <div class="page-card">
    <div class="toolbar">
      <Button theme="solid" type="primary" :on-click="load">刷新</Button>
      <Button theme="light" type="warning" :on-click="doReset">清零重计</Button>
      <span v-if="data" class="meta">统计起点: {{ data.since }}</span>
    </div>

    <div v-if="data" class="stat-cards">
      <div class="stat-card">
        <div class="stat-value">{{ data.totalCount }}</div>
        <div class="stat-label">总请求数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ data.totalError }}</div>
        <div class="stat-label">错误数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ data.successRate }}%</div>
        <div class="stat-label">成功率</div>
      </div>
    </div>

    <Table :columns="columns" :data-source="data?.endpoints || []" row-key="endpoint" size="small"
           :loading="loading" :pagination="false" />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { Button, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import { getApiMonitoring, resetApiMonitoring } from '@/api/system'

const loading = ref(false)
const data = ref<any>(null)

const columns = [
  { title: '接口', dataIndex: 'endpoint' },
  { title: '请求数', dataIndex: 'count', width: 90 },
  {
    title: '成功率', dataIndex: 'successRate', width: 110,
    render: (v: number) => h(Tag, { color: (v >= 99 ? 'green' : v >= 95 ? 'orange' : 'red') as any }, () => `${v}%`),
  },
  { title: '平均耗时', dataIndex: 'avgMs', width: 100, render: (v: number) => `${v} ms` },
  { title: '最大耗时', dataIndex: 'maxMs', width: 100, render: (v: number) => `${v} ms` },
  {
    title: '状态码分布', dataIndex: 'statusDist',
    render: (v: Record<string, number>) =>
      h('span', Object.entries(v).map(([code, n]) => `${code}×${n}`).join('  ')),
  },
]

async function load() {
  loading.value = true
  try {
    data.value = await getApiMonitoring()
  } finally {
    loading.value = false
  }
}

async function doReset() {
  await resetApiMonitoring()
  Toast.success('已清零, 重新统计')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 14px; align-items: center; }
.meta { font-size: 12px; color: #6b7280; }
.stat-cards { display: flex; gap: 12px; margin-bottom: 14px; }
.stat-card { flex: 0 0 160px; background: #f7f9fa; border-radius: 8px; padding: 14px 16px; }
.stat-value { font-size: 22px; font-weight: 600; color: #2563eb; }
.stat-label { font-size: 12px; color: #6b7280; margin-top: 4px; }
</style>
