<template>
  <div class="page-card">
    <div class="toolbar">
      <Select class="w160" :value="query.warehouseId" :option-list="warehouseOptions" placeholder="仓库"
              :on-change="(v: any) => { query.warehouseId = v; load() }" />
      <Select class="w140" :value="query.days" :option-list="dayOptions"
              :on-change="(v: any) => { query.days = v; load() }" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
      <div class="bucket-summary">
        <Tag color="red">已过期 {{ buckets.EXPIRED || 0 }}</Tag>
        <Tag color="orange">30天内 {{ buckets.D30 || 0 }}</Tag>
        <Tag color="yellow">60天内 {{ buckets.D60 || 0 }}</Tag>
        <Tag color="blue">90天内 {{ buckets.D90 || 0 }}</Tag>
      </div>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="false" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { Button, Select, Table, Tag } from '@kousum/semi-ui-vue'
import { getExpiryAlerts } from '@/api/inventory'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol } from '@/utils/format'

const loading = ref(false)
const rows = ref<any[]>([])
const query = reactive({ warehouseId: undefined as number | undefined, days: 90 })

const dayOptions = [
  { value: 30, label: '30天内' },
  { value: 60, label: '60天内' },
  { value: 90, label: '90天内' },
  { value: 180, label: '180天内' },
  { value: 365, label: '一年内' },
]
const warehouseOptions = ref<any[]>([])

const buckets = computed(() => {
  const b: Record<string, number> = {}
  for (const r of rows.value) b[r.bucket] = (b[r.bucket] || 0) + 1
  return b
})

function bucketTag(bucket: string) {
  const map: Record<string, [string, any]> = {
    EXPIRED: ['已过期', 'red'],
    D30: ['30天内', 'orange'],
    D60: ['60天内', 'yellow'],
    D90: ['90天内', 'blue'],
  }
  const [text, color] = map[bucket] || [bucket, 'grey']
  return h(Tag, { color }, () => text)
}

const columns = [
  { title: '预警', dataIndex: 'bucket', render: (v: string) => bucketTag(v) },
  { title: '剩余天数', dataIndex: 'daysToExpiry', align: 'right' as const, render: (v: number) => (v < 0 ? `已过期${-v}天` : `${v}天`) },
  { title: '物资编码', dataIndex: 'materialCode' },
  { title: '物资名称', dataIndex: 'materialName' },
  { title: '规格', dataIndex: 'spec' },
  { title: '批号', dataIndex: 'batchNo' },
  { title: '有效期至', dataIndex: 'expiryDate' },
  numCol('在库数量', 'onHand'),
  { title: '仓库', dataIndex: 'warehouseName' },
]

async function load() {
  loading.value = true
  try {
    rows.value = (await getExpiryAlerts({
      warehouseId: query.warehouseId ?? undefined,
      days: query.days,
    })) as any[]
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  load()
  try {
    const wh: any = await getWarehouseTree()
    warehouseOptions.value = [{ value: null, label: '全部仓库' }, ...wh.map((w: any) => ({ value: w.id, label: w.name }))]
  } catch { /* 主数据未就绪 */ }
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.w160 { width: 160px; }
.w140 { width: 140px; }
.bucket-summary { display: flex; gap: 8px; margin-left: auto; }
</style>
