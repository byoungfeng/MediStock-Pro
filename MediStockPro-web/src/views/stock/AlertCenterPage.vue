<template>
  <div class="page-card">
    <div class="seg-tabs">
      <div v-for="t in tabs" :key="t.key" class="seg-tab" :class="{ active: tab === t.key }"
           @click="switchTab(t.key)">{{ t.text }}</div>
    </div>

    <!-- 库存预警 P047 -->
    <template v-if="tab === 'stock'">
      <div class="toolbar">
        <Select class="w160" :value="warehouseId" :option-list="warehouseOptions" placeholder="仓库"
                :on-change="(v: any) => { warehouseId = v; loadStock() }" />
        <Button theme="solid" type="primary" :on-click="loadStock">查询</Button>
      </div>
      <Table :columns="stockColumns" :data-source="stockRows" row-key="key" size="small" :loading="loading"
             :pagination="false" />
    </template>

    <!-- 到货预警 P048 -->
    <template v-if="tab === 'arrival'">
      <div class="toolbar">
        <span class="hint">已生效未完结且超过承诺交期的采购订单</span>
        <Button theme="solid" type="primary" :on-click="loadArrival">刷新</Button>
      </div>
      <Table :columns="arrivalColumns" :data-source="arrivalRows" row-key="id" size="small" :loading="loading"
             :pagination="false" />
    </template>

    <!-- 预警记录 (持久化, 处置/忽略) -->
    <template v-if="tab === 'records'">
      <div class="toolbar">
        <Select class="w160" :value="recordQuery.status" :option-list="recordStatusOptions" placeholder="状态"
                :on-change="(v: any) => { recordQuery.status = v; loadRecords() }" />
        <Button theme="solid" type="primary" :on-click="loadRecords">查询</Button>
      </div>
      <Table :columns="recordColumns" :data-source="recordRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: recordQuery.page, pageSize: recordQuery.size, total: recordTotal, onChange: (p: number) => { recordQuery.page = p; loadRecords() } }" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import { getStockLevelAlerts, getArrivalAlerts, handleAlert, pageAlerts } from '@/api/alert'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol } from '@/utils/format'

const tabs = [
  { key: 'stock', text: '库存预警' },
  { key: 'arrival', text: '到货预警' },
  { key: 'records', text: '预警记录' },
]
const tab = ref('stock')
const loading = ref(false)
const warehouseId = ref<any>(null)
const warehouseOptions = ref<any[]>([])
const stockRows = ref<any[]>([])
const arrivalRows = ref<any[]>([])

const stockColumns = [
  {
    title: '预警', dataIndex: 'alertType', width: 90,
    render: (v: string) => h(Tag, { color: (v === 'STOCKOUT' ? 'red' : 'orange') as any },
      () => (v === 'STOCKOUT' ? '断货' : '低库存')),
  },
  { title: '物资编码', dataIndex: 'materialCode' },
  { title: '物资名称', dataIndex: 'materialName' },
  { title: '规格', dataIndex: 'spec' },
  { title: '仓库', dataIndex: 'warehouseName' },
  numCol('可用量', 'availableQty'),
  numCol('安全库存', 'safetyQty'),
  { title: '缺口', dataIndex: 'gapQty', render: (v: any) => h('span', { style: { color: '#dc2626', fontWeight: 600 } }, String(v)) },
]
const arrivalColumns = [
  { title: '订单号', dataIndex: 'orderNo' },
  { title: '供应商', dataIndex: 'supplierName' },
  { title: '承诺交期', dataIndex: 'expectDate' },
  {
    title: '逾期天数', dataIndex: 'overdueDays',
    render: (v: any) => h(Tag, { color: 'red' as any }, () => `${v} 天`),
  },
  numCol('订单数量', 'totalQty'),
  { title: '状态', dataIndex: 'status', render: (v: string) => (v === 'APPROVED' ? '待收货' : '收货中') },
]

async function loadStock() {
  loading.value = true
  try {
    const rows = (await getStockLevelAlerts({ warehouseId: warehouseId.value ?? undefined })) as any[]
    stockRows.value = rows.map((r) => ({ ...r, key: `${r.materialId}-${r.warehouseId}` }))
  } finally {
    loading.value = false
  }
}
async function loadArrival() {
  loading.value = true
  try {
    arrivalRows.value = (await getArrivalAlerts()) as any[]
  } finally {
    loading.value = false
  }
}
// ==================== 预警记录 (持久化) ====================
const recordRows = ref<any[]>([])
const recordTotal = ref(0)
const recordQuery = reactive({ page: 1, size: 20, status: 'OPEN' })
const recordStatusOptions = [
  { value: 'OPEN', label: '待处置' },
  { value: 'HANDLED', label: '已处置' },
  { value: 'IGNORED', label: '已忽略' },
  { value: 'CLOSED', label: '已关闭(条件消除)' },
  { value: '', label: '全部' },
]
const alertTypeText: Record<string, string> = {
  EXPIRY: '效期', LOW_STOCK: '库存下限', OVERSTOCK: '超储', ARRIVAL: '到货逾期',
}
const recordStatusText: Record<string, string> = {
  OPEN: '待处置', HANDLED: '已处置', IGNORED: '已忽略', CLOSED: '已关闭',
}
const recordColumns = [
  { title: '类型', dataIndex: 'type', width: 100, render: (v: string) => alertTypeText[v] || v },
  {
    title: '级别', dataIndex: 'level', width: 80,
    render: (v: string) => h(Tag, { color: (v === 'URGENT' ? 'red' : v === 'WARN' ? 'orange' : 'blue') as any }, () => v),
  },
  { title: '标题', dataIndex: 'title' },
  { title: '内容', dataIndex: 'content', ellipsis: true },
  {
    title: '状态', dataIndex: 'status', width: 110,
    render: (v: string) => h(Tag, {
      color: (({ OPEN: 'red', HANDLED: 'green', IGNORED: 'grey', CLOSED: 'grey' } as Record<string, string>)[v]) as any,
    }, () => recordStatusText[v] || v),
  },
  { title: '产生时间', dataIndex: 'createdAt', width: 170 },
  {
    title: '操作', width: 150,
    render: (_: any, r: any) =>
      r.status === 'OPEN'
        ? h('div', { style: 'display:flex;gap:6px' }, [
            h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => doHandle(r, 'HANDLED') }, () => '处置'),
            h(Button, { size: 'small', theme: 'light', onClick: () => doHandle(r, 'IGNORED') }, () => '忽略'),
          ])
        : h('span', { style: { color: '#b0b8c0' } }, '-'),
  },
]

async function loadRecords() {
  loading.value = true
  try {
    const res: any = await pageAlerts({ ...recordQuery, status: recordQuery.status || undefined })
    recordRows.value = res.records
    recordTotal.value = res.total
  } finally {
    loading.value = false
  }
}

async function doHandle(row: any, action: 'HANDLED' | 'IGNORED') {
  await handleAlert(row.id, action)
  Toast.success(action === 'HANDLED' ? '已处置' : '已忽略')
  loadRecords()
}

function switchTab(key: string) {
  tab.value = key
  if (key === 'stock') loadStock()
  else if (key === 'arrival') loadArrival()
  else loadRecords()
}

onMounted(async () => {
  loadStock()
  try {
    const wh: any = await getWarehouseTree()
    warehouseOptions.value = [{ value: null, label: '全部仓库' }, ...wh.map((w: any) => ({ value: w.id, label: w.name }))]
  } catch { /* 主数据未就绪 */ }
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.seg-tabs { display: flex; gap: 4px; margin-bottom: 16px; border-bottom: 1px solid rgba(17, 24, 39, 0.08); }
.seg-tab { padding: 8px 16px; font-size: 13px; color: #4b5563; cursor: pointer; border-bottom: 2px solid transparent; }
.seg-tab:hover { color: #2563eb; }
.seg-tab.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 500; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.hint { font-size: 12px; color: #6b7280; }
.w160 { width: 160px; }
</style>
