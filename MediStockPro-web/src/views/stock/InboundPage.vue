<template>
  <div class="page-card">
    <div class="toolbar">
      <Select class="w160" :value="query.status" :option-list="statusOptions" placeholder="状态"
              :on-change="(v: any) => { query.status = v; load() }" />
      <Input class="w200" placeholder="入库单号" :value="query.keyword"
             :on-change="(v: string) => (query.keyword = v)" :on-enter="load" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
      <Button v-permission="PERMISSION.STOCK_INBOUND_EXECUTE" type="primary" theme="light" :on-click="openCreate">新建入库单</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="{ currentPage: query.page, pageSize: query.size, total, onChange: onPageChange }" />

    <!-- 新建入库单 -->
    <Modal :visible="createVisible" title="新建入库单" :width="860"
           :on-ok="save" :on-cancel="() => { createVisible  = false }" :confirm-loading="saving">
      <div class="form-row">
        <label>入库仓库 *</label>
        <Select :value="form.warehouseId" :option-list="warehouseOptions" style="width: 240px"
                :on-change="(v: any) => (form.warehouseId = v)" />
        <label>业务类型</label>
        <Select :value="form.bizType" :option-list="bizTypes" style="width: 160px"
                :on-change="(v: any) => (form.bizType = v)" />
        <label>备注</label>
        <Input :value="form.remark" :on-change="(v: string) => (form.remark = v)" style="width: 200px" />
      </div>
      <div class="line-header">
        <span>入库明细</span>
        <Button size="small" theme="light" :on-click="addLine">+ 添加行</Button>
      </div>
      <div class="line-grid line-grid-head">
        <span>物资 *</span><span>批号 *</span><span>有效期至</span><span>数量 *</span><span>单价</span><span></span>
      </div>
      <div v-for="(line, idx) in form.items" :key="idx" class="line-grid">
        <Select :value="line.materialId" :option-list="materialOptions" filter
                :on-change="(v: any) => (line.materialId = v)" />
        <Input :value="line.batchNo" :on-change="(v: string) => (line.batchNo = v)" placeholder="批号" />
        <Input :value="line.expiryDate" :on-change="(v: string) => (line.expiryDate = v)" placeholder="YYYY-MM-DD" />
        <Input :value="String(line.qty)" :on-change="(v: string) => (line.qty = Number(v) || 0)" />
        <Input :value="String(line.unitCost ?? '')" :on-change="(v: string) => (line.unitCost = Number(v) || null)" />
        <Button size="small" theme="borderless" type="danger" :on-click="() => form.items.splice(idx, 1)">删</Button>
      </div>
    </Modal>

    <!-- 详情 -->
    <Modal :visible="detailVisible" title="入库单详情" :width="860" :footer="noFooter"
           :on-cancel="() => { detailVisible  = false }">
      <div v-if="detail" class="detail">
        <p>单号: {{ detail.master.inboundNo }} ｜ 状态: {{ statusText(detail.master.status) }} ｜ 总量: {{ detail.master.totalQty }}</p>
        <Table :columns="detailColumns" :data-source="detail.items" row-key="id" size="small" :pagination="false" />
        <div class="detail-actions" v-if="detail.master.status === 'PENDING'">
          <Button v-permission="PERMISSION.STOCK_INBOUND_EXECUTE" theme="solid" type="primary" :loading="saving" :on-click="confirm">确认入库</Button>
        </div>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
const noFooter: any = null // Semi Modal footer 类型不收 null, 运行时需要
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import { confirmInbound, createInbound, getInbound, pageInbounds } from '@/api/inbound'
import { pageMaterials } from '@/api/material'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol, moneyCol } from '@/utils/format'
import { PERMISSION } from '@/constants/permissions'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, status: '', keyword: '' })

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待入库' },
  { value: 'CONFIRMED', label: '已入库' },
  { value: 'REVERSED', label: '已红冲' },
]
const bizTypes = [
  { value: 'PURCHASE', label: '采购入库' },
  { value: 'TRANSFER_IN', label: '调拨入库' },
  { value: 'SURPLUS', label: '盘盈入库' },
  { value: 'RETURN', label: '退库入库' },
]
const warehouseOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])

function statusText(s: string) {
  return { PENDING: '待入库', CONFIRMED: '已入库', REVERSED: '已红冲' }[s] || s
}
function statusColor(s: string) {
  return { PENDING: 'blue', CONFIRMED: 'green', REVERSED: 'red' }[s] || 'grey'
}

const columns = [
  { title: '入库单号', dataIndex: 'inboundNo' },
  { title: '业务类型', dataIndex: 'bizType', render: (v: string) => bizTypes.find((t) => t.value === v)?.label || v },
  { title: '仓库', dataIndex: 'warehouseId', render: (v: number) => warehouseOptions.value.find((w) => w.value === v)?.label || v },
  numCol('总数量', 'totalQty'),
  moneyCol('总金额', 'totalAmount'),
  { title: '状态', dataIndex: 'status', render: (v: string) => h(Tag, { color: statusColor(v) as any }, () => statusText(v)) },
  { title: '创建时间', dataIndex: 'createdAt' },
  {
    title: '操作', dataIndex: 'actions',
    render: (_: any, r: any) =>
      h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openDetail(r.id) }, () => '详情'),
  },
]

const detailColumns = [
  { title: '物资', dataIndex: 'materialId', render: (v: number) => materialOptions.value.find((m) => m.value === v)?.label || v },
  { title: '批号', dataIndex: 'batchNo' },
  { title: '有效期至', dataIndex: 'expiryDate' },
  numCol('数量', 'qty'),
  moneyCol('单价', 'unitCost'),
  moneyCol('金额', 'amount'),
]

async function load() {
  loading.value = true
  try {
    const data: any = await pageInbounds({ ...query, status: query.status || undefined })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onPageChange(page: number, size: number) {
  query.page = page
  query.size = size
  load()
}

// ---- 新建 ----
const createVisible = ref(false)
const emptyLine = () => ({ materialId: null, batchNo: '', expiryDate: '', qty: 1, unitCost: null })
const form = reactive<any>({ warehouseId: null, bizType: 'PURCHASE', remark: '', items: [emptyLine()] })

function openCreate() {
  Object.assign(form, { warehouseId: null, bizType: 'PURCHASE', remark: '', items: [emptyLine()] })
  createVisible.value = true
}
function addLine() {
  form.items.push(emptyLine())
}

async function save() {
  if (!form.warehouseId) return Toast.warning('请选择入库仓库')
  if (!form.items.length || form.items.some((l: any) => !l.materialId || !l.batchNo || !(l.qty > 0))) {
    return Toast.warning('明细行需填写物资/批号/数量')
  }
  saving.value = true
  try {
    await createInbound({ ...form })
    Toast.success('已创建')
    createVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---- 详情/确认 ----
const detailVisible = ref(false)
const detail = ref<any>(null)

async function openDetail(id: number) {
  detail.value = await getInbound(id)
  detailVisible.value = true
}

async function confirm() {
  saving.value = true
  try {
    await confirmInbound(detail.value.master.id, detail.value.master.version)
    Toast.success('已确认入库')
    detailVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  try {
    const [wh, mt]: any[] = await Promise.all([getWarehouseTree(), pageMaterials({ page: 1, size: 1000 })])
    warehouseOptions.value = wh.map((w: any) => ({ value: w.id, label: w.name }))
    materialOptions.value = mt.records.map((m: any) => ({ value: m.id, label: `${m.code} ${m.name} ${m.spec || ''}` }))
  } catch { /* 主数据未就绪 */ }
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.w160 { width: 160px; }
.w200 { width: 200px; }
.form-row { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.form-row label { font-size: 13px; color: #4b5563; }
.line-header { display: flex; justify-content: space-between; align-items: center; margin: 8px 0; font-weight: 600; font-size: 13px; }
.line-grid { display: grid; grid-template-columns: 2fr 1.2fr 1.2fr 0.8fr 0.8fr 40px; gap: 8px; margin-bottom: 8px; }
.line-grid-head { font-size: 12px; color: #6b7280; }
.detail p { font-size: 13px; color: #4b5563; }
.detail-actions { margin-top: 16px; text-align: right; }
</style>
