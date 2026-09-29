<template>
  <div class="page-card">
    <div class="seg-tabs">
      <div v-for="t in tabs" :key="t.key" class="seg-tab" :class="{ active: tab === t.key }"
           @click="switchTab(t.key)">{{ t.text }}</div>
    </div>

    <div class="toolbar" v-if="tab !== 'inventory' && tab !== 'expiry'">
      <span class="hint">统计区间: 近 30 天</span>
      <Button theme="solid" type="primary" :on-click="loadCurrent">刷新</Button>
    </div>
    <div class="toolbar" v-else>
      <Select class="w160" :value="warehouseId" :option-list="warehouseOptions" placeholder="仓库"
              :on-change="(v: any) => { warehouseId = v; loadCurrent() }" />
      <Button theme="solid" type="primary" :on-click="loadCurrent">刷新</Button>
    </div>

    <!-- 采购分析 P049 -->
    <template v-if="tab === 'purchase'">
      <div class="block-title">供应商采购金额 Top10</div>
      <Table :columns="purchaseSupplierColumns" :data-source="purchase.topSuppliers || []" row-key="supplierId"
             size="small" :loading="loading" :pagination="false" />
      <div class="block-title">采购金额趋势 (近30天)</div>
      <Table :columns="purchaseTrendColumns" :data-source="purchase.trend || []" row-key="day"
             size="small" :loading="loading" :pagination="false" />
    </template>

    <!-- 库存分析 P050 -->
    <template v-if="tab === 'inventory'">
      <div class="block-title">分类库存金额分布</div>
      <Table :columns="categoryColumns" :data-source="inventory.byCategory || []" row-key="categoryName"
             size="small" :loading="loading" :pagination="false" />
      <div class="block-title">库存金额物资 Top10</div>
      <Table :columns="materialValueColumns" :data-source="inventory.topMaterials || []" row-key="materialCode"
             size="small" :loading="loading" :pagination="false" />
    </template>

    <!-- 领用分析 P051 -->
    <template v-if="tab === 'issue'">
      <div class="block-title">科室领用排行</div>
      <Table :columns="deptColumns" :data-source="issue.byDepartment || []" row-key="departmentId"
             size="small" :loading="loading" :pagination="false" />
      <div class="block-title">领用物资 Top10</div>
      <Table :columns="issueMaterialColumns" :data-source="issue.topMaterials || []" row-key="materialCode"
             size="small" :loading="loading" :pagination="false" />
      <div class="block-title">领用趋势 (近30天)</div>
      <Table :columns="issueTrendColumns" :data-source="issue.trend || []" row-key="day"
             size="small" :loading="loading" :pagination="false" />
    </template>

    <!-- 近效期分析 P052 -->
    <template v-if="tab === 'expiry'">
      <Table :columns="expiryColumns" :data-source="expiryRows" row-key="bucket"
             size="small" :loading="loading" :pagination="false" />
    </template>

    <!-- 变动趋势 P054 -->
    <template v-if="tab === 'movement'">
      <Table :columns="movementColumns" :data-source="movementRows" row-key="key"
             size="small" :loading="loading" :pagination="false" />
    </template>

    <!-- 供应商绩效 P048 -->
    <template v-if="tab === 'supplier'">
      <Table :columns="supplierColumns" :data-source="supplierRows" row-key="supplierId"
             size="small" :loading="loading" :pagination="false" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { Button, Select, Table, Tag } from '@kousum/semi-ui-vue'
import { getPurchaseAnalysis, getInventoryAnalysis, getIssueAnalysis, getExpiryAnalysis, getMovementTrend } from '@/api/report'
import { getSupplierPerformance } from '@/api/alert'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol } from '@/utils/format'

const tabs = [
  { key: 'purchase', text: '采购分析' },
  { key: 'inventory', text: '库存分析' },
  { key: 'issue', text: '领用分析' },
  { key: 'expiry', text: '近效期分析' },
  { key: 'movement', text: '变动趋势' },
  { key: 'supplier', text: '供应商绩效' },
]
const tab = ref('purchase')
const loading = ref(false)
const warehouseId = ref<any>(null)
const warehouseOptions = ref<any[]>([])

const purchase = ref<any>({})
const inventory = ref<any>({})
const issue = ref<any>({})
const expiryRows = ref<any[]>([])
const movement = ref<any[]>([])

const money = (v: any) => `¥${Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`

const purchaseSupplierColumns = [
  { title: '供应商', dataIndex: 'supplierName' },
  { title: '采购金额', dataIndex: 'amount', align: 'right' as const, render: (v: any) => money(v) },
  numCol('采购数量', 'qty'),
]
const purchaseTrendColumns = [
  { title: '日期', dataIndex: 'day' },
  { title: '金额', dataIndex: 'amount', align: 'right' as const, render: (v: any) => money(v) },
  numCol('数量', 'qty'),
]
const categoryColumns = [
  { title: '分类', dataIndex: 'categoryName' },
  numCol('在库数量', 'qty'),
  { title: '库存金额', dataIndex: 'value', align: 'right' as const, render: (v: any) => money(v) },
]
const materialValueColumns = [
  { title: '编码', dataIndex: 'materialCode' },
  { title: '名称', dataIndex: 'materialName' },
  { title: '规格', dataIndex: 'spec' },
  numCol('在库数量', 'qty'),
  { title: '库存金额', dataIndex: 'value', align: 'right' as const, render: (v: any) => money(v) },
]
const deptColumns = [
  { title: '科室', dataIndex: 'departmentName' },
  numCol('领用数量', 'qty'),
]
const issueMaterialColumns = [
  { title: '编码', dataIndex: 'materialCode' },
  { title: '名称', dataIndex: 'materialName' },
  numCol('领用数量', 'qty'),
]
const issueTrendColumns = [
  { title: '日期', dataIndex: 'day' },
  numCol('领用数量', 'qty'),
]
const bucketMap: Record<string, [string, any]> = {
  EXPIRED: ['已过期', 'red'],
  D30: ['30天内', 'orange'],
  D60: ['60天内', 'yellow'],
  D90: ['90天内', 'blue'],
  OVER90: ['90天以上', 'green'],
}
const expiryColumns = [
  {
    title: '效期分档', dataIndex: 'bucket',
    render: (v: string) => {
      const [text, color] = bucketMap[v] || [v, 'grey']
      return h(Tag, { color }, () => text)
    },
  },
  numCol('批次数', 'batchCount'),
  numCol('数量', 'qty'),
  { title: '金额', dataIndex: 'value', align: 'right' as const, render: (v: any) => money(v) },
]
const movementTypeMap: Record<string, string> = {
  INBOUND: '入库', ISSUE: '出库', REVERSAL: '红冲', RETURN: '退库', SCRAP: '报废',
  TRANSFER_SHIP: '调拨发运', TRANSFER_RECEIVE: '调拨接收', COUNT: '盘点调整', ADJUST: '库存调整',
}
const movementRows = computed(() =>
  movement.value.map((r: any) => ({ ...r, key: `${r.day}-${r.sourceType}`, typeName: movementTypeMap[r.sourceType] || r.sourceType }))
)
const movementColumns = [
  { title: '日期', dataIndex: 'day' },
  { title: '业务类型', dataIndex: 'typeName' },
  numCol('净变动', 'netQty'),
]
const supplierRows = ref<any[]>([])
const pctText = (v: any) => (v === null || v === undefined ? '-' : `${v}%`)
const supplierColumns = [
  { title: '供应商', dataIndex: 'supplierName' },
  numCol('订单数', 'orderCount'),
  { title: '采购金额', dataIndex: 'totalAmount', align: 'right' as const, render: (v: any) => money(v) },
  { title: '到货准时率', dataIndex: 'onTimeRate', align: 'right' as const, render: pctText },
  { title: '验收合格率', dataIndex: 'qualifiedRate', align: 'right' as const, render: pctText },
]

async function loadCurrent() {
  loading.value = true
  try {
    const params = { warehouseId: warehouseId.value ?? undefined }
    if (tab.value === 'purchase') purchase.value = await getPurchaseAnalysis()
    else if (tab.value === 'inventory') inventory.value = await getInventoryAnalysis(params)
    else if (tab.value === 'issue') issue.value = await getIssueAnalysis(params)
    else if (tab.value === 'expiry') expiryRows.value = (await getExpiryAnalysis(params)) as any[]
    else if (tab.value === 'supplier') supplierRows.value = (await getSupplierPerformance()) as any[]
    else movement.value = (await getMovementTrend(params)) as any[]
  } finally {
    loading.value = false
  }
}

function switchTab(key: string) {
  tab.value = key
  loadCurrent()
}

onMounted(async () => {
  loadCurrent()
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
.block-title { font-size: 13px; font-weight: 600; color: #111827; margin: 16px 0 8px; }
.block-title:first-of-type { margin-top: 0; }
.w160 { width: 160px; }
</style>
