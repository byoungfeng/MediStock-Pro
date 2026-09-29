<template>
  <div class="page-card">
    <div class="seg-tabs">
      <div v-for="t in tabs" :key="t.key" class="seg-tab" :class="{ active: tab === t.key }"
           @click="switchTab(t.key)">{{ t.text }}</div>
    </div>

    <!-- ==================== 库存明细 P029 ==================== -->
    <template v-if="tab === 'details'">
      <div class="toolbar">
        <Select class="w160" :value="dQuery.warehouseId" :option-list="warehouseOptions" placeholder="仓库"
                :on-change="(v: any) => { dQuery.warehouseId = v; loadDetails() }" />
        <Select class="w140" :value="dQuery.stockState" :option-list="stockStateOptions" placeholder="库存状态"
                :on-change="(v: any) => { dQuery.stockState = v; loadDetails() }" />
        <Input class="w200" :value="dQuery.keyword" placeholder="物资编码/名称"
               :on-change="(v: string) => (dQuery.keyword = v)" @enter-press="loadDetails" />
        <Button theme="solid" type="primary" :on-click="loadDetails">查询</Button>
      </div>
      <Table :columns="dColumns" :data-source="dRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: dQuery.page, pageSize: dQuery.size, total: dTotal, onChange: (p: number) => { dQuery.page = p; loadDetails() } }" />
    </template>

    <!-- ==================== 批次库存 P030 ==================== -->
    <template v-if="tab === 'batches'">
      <div class="toolbar">
        <Select class="w160" :value="bQuery.warehouseId" :option-list="warehouseOptions" placeholder="仓库"
                :on-change="(v: any) => { bQuery.warehouseId = v; loadBatches() }" />
        <Select class="w140" :value="bQuery.status" :option-list="batchStatusOptions" placeholder="批次状态"
                :on-change="(v: any) => { bQuery.status = v; loadBatches() }" />
        <Input class="w200" :value="bQuery.keyword" placeholder="物资/批号"
               :on-change="(v: string) => (bQuery.keyword = v)" @enter-press="loadBatches" />
        <Button theme="solid" type="primary" :on-click="loadBatches">查询</Button>
      </div>
      <Table :columns="bColumns" :data-source="bRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: bQuery.page, pageSize: bQuery.size, total: bTotal, onChange: (p: number) => { bQuery.page = p; loadBatches() } }" />
    </template>

    <!-- ==================== 库存流水 P031 ==================== -->
    <template v-if="tab === 'ledger'">
      <div class="toolbar">
        <Select class="w160" :value="lQuery.warehouseId" :option-list="warehouseOptions" placeholder="仓库"
                :on-change="(v: any) => { lQuery.warehouseId = v; loadLedger() }" />
        <Select class="w160" :value="lQuery.sourceType" :option-list="sourceTypeOptions" placeholder="业务类型"
                :on-change="(v: any) => { lQuery.sourceType = v; loadLedger() }" />
        <Input class="w200" :value="lQuery.sourceNo" placeholder="来源单号"
               :on-change="(v: string) => (lQuery.sourceNo = v)" @enter-press="loadLedger" />
        <Button theme="solid" type="primary" :on-click="loadLedger">查询</Button>
      </div>
      <Table :columns="lColumns" :data-source="lRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: lQuery.page, pageSize: lQuery.size, total: lTotal, onChange: (p: number) => { lQuery.page = p; loadLedger() } }" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import { getDetails, getBatches, freezeBatch, getTransactions } from '@/api/inventory'
import { getWarehouseTree } from '@/api/warehouse'
import { buildBatchLabel, printLabel } from '@/utils/desktop'
import { numCol, moneyCol } from '@/utils/format'
import { PERMISSION } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const tabs = [
  { key: 'details', text: '库存明细' },
  { key: 'batches', text: '批次库存' },
  { key: 'ledger', text: '库存流水' },
]
const tab = ref('details')
const loading = ref(false)
const warehouseOptions = ref<any[]>([])

const stockStateOptions = [
  { value: null, label: '全部状态' },
  { value: 'LOW', label: '低于安全库存' },
  { value: 'ZERO', label: '零库存' },
]
const batchStatusOptions = [
  { value: null, label: '全部状态' },
  { value: 'NORMAL', label: '正常' },
  { value: 'FROZEN', label: '冻结' },
  { value: 'EXPIRED', label: '过期' },
]
const sourceTypeOptions = [
  { value: null, label: '全部类型' },
  { value: 'INBOUND', label: '入库' },
  { value: 'ISSUE', label: '出库' },
  { value: 'REVERSAL', label: '红冲' },
  { value: 'TRANSFER_SHIP', label: '调拨发运' },
  { value: 'TRANSFER_RECEIVE', label: '调拨接收' },
  { value: 'TRANSFER_LOSS', label: '调拨损耗' },
  { value: 'ADJUST', label: '盘点调整' },
  { value: 'SCRAP', label: '报废' },
  { value: 'RETURN', label: '退库' },
  { value: 'LOCK', label: '锁定' },
  { value: 'UNLOCK', label: '解锁' },
]

function statusTag(status: string) {
  const map: Record<string, [string, any]> = {
    NORMAL: ['正常', 'green'],
    FROZEN: ['冻结', 'grey'],
    EXPIRED: ['已过期', 'red'],
  }
  const [text, color] = map[status] || [status, 'grey']
  return h(Tag, { color }, () => text)
}

// ---------- 明细 ----------
const dQuery = reactive({ page: 1, size: 20, warehouseId: undefined as any, keyword: '', stockState: undefined as any })
const dRows = ref<any[]>([])
const dTotal = ref(0)
const dColumns = [
  { title: '物资编码', dataIndex: 'materialCode' },
  { title: '物资名称', dataIndex: 'materialName' },
  { title: '规格', dataIndex: 'spec' },
  { title: '批号', dataIndex: 'batchNo' },
  { title: '效期', dataIndex: 'expiryDate' },
  { title: '仓库', dataIndex: 'warehouseName' },
  numCol('在库', 'onHand'),
  numCol('锁定', 'lockedQty'),
  numCol('可用', 'availableQty'),
  numCol('在途', 'inTransitQty'),
  numCol('安全库存', 'safetyQty'),
  { title: '状态', dataIndex: 'status', render: (v: string) => statusTag(v) },
]
async function loadDetails() {
  loading.value = true
  try {
    const r: any = await getDetails({ ...dQuery })
    dRows.value = r.records
    dTotal.value = r.total
  } finally {
    loading.value = false
  }
}

// ---------- 批次 ----------
const bQuery = reactive({ page: 1, size: 20, warehouseId: undefined as any, keyword: '', status: undefined as any })
const bRows = ref<any[]>([])
const bTotal = ref(0)
const bColumns = [
  { title: '物资编码', dataIndex: 'materialCode' },
  { title: '物资名称', dataIndex: 'materialName' },
  { title: '批号', dataIndex: 'batchNo' },
  { title: '生产日期', dataIndex: 'productionDate' },
  { title: '效期', dataIndex: 'expiryDate' },
  { title: '仓库', dataIndex: 'warehouseName' },
  numCol('在库', 'onHand'),
  moneyCol('成本价', 'unitCost'),
  { title: '状态', dataIndex: 'status', render: (v: string) => statusTag(v) },
  {
    title: '操作', dataIndex: 'op',
    render: (_: any, row: any) => [
      userStore.hasPermission(PERMISSION.BATCH_MANAGE_EDIT) && h(Button, {
        size: 'small', theme: 'light', type: row.status === 'FROZEN' ? 'primary' : 'warning',
        onClick: () => toggleFreeze(row),
      }, () => (row.status === 'FROZEN' ? '解冻' : '冻结')),
      h(Button, {
        size: 'small', theme: 'light', style: 'margin-left: 4px',
        onClick: () => printBatchLabel(row),
      }, () => '标签'),
    ].filter(Boolean),
  },
]
async function loadBatches() {
  loading.value = true
  try {
    const r: any = await getBatches({ ...bQuery })
    bRows.value = r.records
    bTotal.value = r.total
  } finally {
    loading.value = false
  }
}
async function toggleFreeze(row: any) {
  const freeze = row.status !== 'FROZEN'
  await freezeBatch(row.id, freeze)
  Toast.success(freeze ? '批次已冻结' : '批次已解冻')
  loadBatches()
}
async function printBatchLabel(row: any) {
  try {
    await printLabel(buildBatchLabel(row))
    Toast.success('标签已发送打印')
  } catch (e: any) {
    Toast.error(e?.message || '打印失败')
  }
}

// ---------- 流水 ----------
const lQuery = reactive({ page: 1, size: 20, warehouseId: undefined as any, sourceType: undefined as any, sourceNo: '' })
const lRows = ref<any[]>([])
const lTotal = ref(0)
const lColumns = [
  { title: '时间', dataIndex: 'createdAt' },
  { title: '业务类型', dataIndex: 'sourceType', render: (v: string) => sourceTypeOptions.find((o) => o.value === v)?.label || v },
  { title: '来源单号', dataIndex: 'sourceNo' },
  { title: '物资', dataIndex: 'materialName' },
  { title: '批号', dataIndex: 'batchNo' },
  { title: '仓库', dataIndex: 'warehouseName' },
  {
    title: '方向', dataIndex: 'direction',
    render: (v: string) => h(Tag, { color: (v === 'IN' ? 'blue' : 'grey') as any }, () => (v === 'IN' ? '入' : '出')),
  },
  numCol('数量', 'qty'),
  numCol('变动前', 'beforeQty'),
  numCol('变动后', 'afterQty'),
  { title: '操作人', dataIndex: 'operatorName' },
]
async function loadLedger() {
  loading.value = true
  try {
    const r: any = await getTransactions({ ...lQuery })
    lRows.value = r.records
    lTotal.value = r.total
  } finally {
    loading.value = false
  }
}

function switchTab(key: string) {
  tab.value = key
  if (key === 'details') loadDetails()
  else if (key === 'batches') loadBatches()
  else loadLedger()
}

onMounted(async () => {
  loadDetails()
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
.w160 { width: 160px; }
.w140 { width: 140px; }
.w200 { width: 200px; }
</style>
