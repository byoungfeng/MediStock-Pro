<template>
  <div class="page-card">
    <div class="toolbar">
      <Select class="w160" :value="query.status" :option-list="statusOptions" placeholder="状态"
              :on-change="(v: any) => { query.status = v; load() }" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
      <Button v-permission="PERMISSION.STOCK_TRANSFER_CREATE" type="primary" theme="light" :on-click="openCreate">新建调拨单</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="{ currentPage: query.page, pageSize: query.size, total, onChange: onPageChange }" />

    <!-- 新建调拨单 -->
    <Modal :visible="createVisible" title="新建调拨单" :width="820"
           :on-ok="save" :on-cancel="() => { createVisible  = false }" :confirm-loading="saving">
      <div class="form-row">
        <label>调出仓 *</label>
        <Select :value="form.fromWarehouseId" :option-list="warehouseOptions" style="width: 180px"
                :on-change="(v: any) => (form.fromWarehouseId = v)" />
        <label>调入仓 *</label>
        <Select :value="form.toWarehouseId" :option-list="warehouseOptions" style="width: 180px"
                :on-change="(v: any) => (form.toWarehouseId = v)" />
        <label>原因</label>
        <Input :value="form.reason" :on-change="(v: string) => (form.reason = v)" style="width: 180px" />
      </div>
      <div class="line-header">
        <span>调拨明细 (按批次)</span>
        <Button size="small" theme="light" :on-click="addLine">+ 添加行</Button>
      </div>
      <div class="line-grid line-grid-head">
        <span>物资 *</span><span>批次 *</span><span>数量 *</span><span></span>
      </div>
      <div v-for="(line, idx) in form.items" :key="idx" class="line-grid">
        <Select :value="line.materialId" :option-list="materialOptions" filter
                :on-change="(v: any) => onLineMaterial(line, v)" />
        <Select :value="line.batchNo" :option-list="batchOptionsOf(line.materialId)"
                :on-change="(v: any) => (line.batchNo = v)" placeholder="选择批次" />
        <Input :value="String(line.qty)" :on-change="(v: string) => (line.qty = Number(v) || 0)" />
        <Button size="small" theme="borderless" type="danger" :on-click="() => form.items.splice(idx, 1)">删</Button>
      </div>
    </Modal>

    <!-- 详情 / 收货 -->
    <Modal :visible="detailVisible" title="调拨单详情" :width="860" :footer="noFooter"
           :on-cancel="() => { detailVisible  = false }">
      <div v-if="detail" class="detail">
        <p>
          单号: {{ detail.master.transferNo }} ｜ 状态: {{ statusText(detail.master.status) }} ｜
          {{ warehouseName(detail.master.fromWarehouseId) }} → {{ warehouseName(detail.master.toWarehouseId) }}
        </p>
        <Table :columns="detailColumns" :data-source="detail.items" row-key="id" size="small" :pagination="false" />

        <!-- 收货录入 (SHIPPED) -->
        <div v-if="detail.master.status === 'SHIPPED'" class="receive-box">
          <div class="line-header"><span>收货确认 (短收需填差异原因)</span></div>
          <div v-for="item in detail.items" :key="item.id" class="receive-row">
            <span>{{ materialName(item.materialId) }} / {{ item.batchNo }} (发运 {{ item.shippedQty }})</span>
            <Input style="width: 100px" :value="String(receiveForm[item.id]?.receivedQty ?? item.shippedQty)"
                   :on-change="(v: string) => setReceive(item.id, 'receivedQty', Number(v) || 0)" placeholder="实收" />
            <Input style="width: 200px" :value="receiveForm[item.id]?.diffReason ?? ''"
                   :on-change="(v: string) => setReceive(item.id, 'diffReason', v)" placeholder="差异原因(短收必填)" />
          </div>
        </div>

        <div class="detail-actions">
          <template v-if="detail.master.status === 'DRAFT'">
            <Button v-permission="PERMISSION.STOCK_TRANSFER_CREATE" theme="solid" type="primary" :loading="saving" :on-click="() => action('submit')">提交</Button>
            <Button v-permission="PERMISSION.STOCK_TRANSFER_CREATE" type="danger" theme="light" :on-click="() => action('cancel')">取消</Button>
          </template>
          <template v-if="detail.master.status === 'PENDING'">
            <Button v-permission="PERMISSION.STOCK_TRANSFER_APPROVE" theme="solid" type="primary" :loading="saving" :on-click="() => action('approve')">审批通过</Button>
            <Button v-permission="PERMISSION.STOCK_TRANSFER_APPROVE" type="danger" theme="light" :on-click="() => action('cancel')">取消</Button>
          </template>
          <Button v-if="detail.master.status === 'APPROVED'" v-permission="PERMISSION.STOCK_TRANSFER_EXECUTE" theme="solid" type="primary"
                  :loading="saving" :on-click="() => action('ship')">确认发运</Button>
          <Button v-if="detail.master.status === 'SHIPPED'" v-permission="PERMISSION.STOCK_TRANSFER_EXECUTE" theme="solid" type="primary"
                  :loading="saving" :on-click="doReceive">确认收货</Button>
        </div>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
const noFooter: any = null // Semi Modal footer 类型不收 null, 运行时需要
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import {
  approveTransfer, cancelTransfer, createTransfer, getTransfer,
  pageTransfers, receiveTransfer, shipTransfer, submitTransfer,
} from '@/api/transfer'
import { pageMaterials } from '@/api/material'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol } from '@/utils/format'
import { getBatches } from '@/api/inventory'
import { PERMISSION } from '@/constants/permissions'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, status: '' })

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '待发运' },
  { value: 'SHIPPED', label: '在途' },
  { value: 'RECEIVED', label: '已收货' },
  { value: 'CANCELLED', label: '已取消' },
]
const warehouseOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const batchRows = ref<any[]>([])

function statusText(s: string) {
  return statusOptions.find((o) => o.value === s)?.label || s
}
function statusColor(s: string) {
  return { DRAFT: 'grey', PENDING: 'blue', APPROVED: 'green', SHIPPED: 'violet', RECEIVED: 'green', CANCELLED: 'grey' }[s] || 'grey'
}
function warehouseName(id: number) {
  return warehouseOptions.value.find((w) => w.value === id)?.label || id
}
function materialName(id: number) {
  return materialOptions.value.find((m) => m.value === id)?.label || id
}
function batchOptionsOf(materialId: number) {
  return batchRows.value
    .filter((b) => b.materialId === materialId && b.availableQty > 0)
    .map((b) => ({ value: b.batchNo, label: `${b.batchNo} (可用${b.availableQty})` }))
}

const columns = [
  { title: '调拨单号', dataIndex: 'transferNo' },
  { title: '调出仓', dataIndex: 'fromWarehouseId', render: (v: number) => warehouseName(v) },
  { title: '调入仓', dataIndex: 'toWarehouseId', render: (v: number) => warehouseName(v) },
  { title: '状态', dataIndex: 'status', render: (v: string) => h(Tag, { color: statusColor(v) as any }, () => statusText(v)) },
  { title: '发运时间', dataIndex: 'shipTime' },
  { title: '收货时间', dataIndex: 'receiveTime' },
  { title: '创建时间', dataIndex: 'createdAt' },
  {
    title: '操作', dataIndex: 'actions',
    render: (_: any, r: any) =>
      h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openDetail(r.id) }, () => '详情'),
  },
]

const detailColumns = [
  { title: '物资', dataIndex: 'materialId', render: (v: number) => materialName(v) },
  { title: '批次', dataIndex: 'batchNo' },
  numCol('申请量', 'qty'),
  { title: '发运量', dataIndex: 'shippedQty' },
  { title: '实收量', dataIndex: 'receivedQty' },
  { title: '差异原因', dataIndex: 'diffReason' },
]

async function load() {
  loading.value = true
  try {
    const data: any = await pageTransfers({ ...query, status: query.status || undefined })
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
const emptyLine = () => ({ materialId: null, batchNo: null, qty: 1 })
const form = reactive<any>({ fromWarehouseId: null, toWarehouseId: null, reason: '', items: [emptyLine()] })

function openCreate() {
  Object.assign(form, { fromWarehouseId: null, toWarehouseId: null, reason: '', items: [emptyLine()] })
  createVisible.value = true
}
function addLine() {
  form.items.push(emptyLine())
}
async function onLineMaterial(line: any, materialId: any) {
  line.materialId = materialId
  line.batchNo = null
  if (form.fromWarehouseId && materialId && !batchRows.value.some((b) => b.materialId === materialId)) {
    const data: any = await getBatches({ page: 1, size: 200, warehouseId: form.fromWarehouseId })
    batchRows.value = [...batchRows.value, ...data.records]
  }
}

async function save() {
  if (!form.fromWarehouseId || !form.toWarehouseId) return Toast.warning('请选择调出/调入仓')
  if (form.fromWarehouseId === form.toWarehouseId) return Toast.warning('调出仓与调入仓不能相同')
  if (!form.items.length || form.items.some((l: any) => !l.materialId || !l.batchNo || !(l.qty > 0))) {
    return Toast.warning('明细行需填写物资/批次/数量')
  }
  saving.value = true
  try {
    await createTransfer({ ...form })
    Toast.success('已创建')
    createVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---- 详情/操作 ----
const detailVisible = ref(false)
const detail = ref<any>(null)
const receiveForm = reactive<Record<number, any>>({})

async function openDetail(id: number) {
  detail.value = await getTransfer(id)
  Object.keys(receiveForm).forEach((k) => delete receiveForm[Number(k)])
  detailVisible.value = true
}

function setReceive(itemId: number, key: string, v: any) {
  receiveForm[itemId] = { ...receiveForm[itemId], [key]: v }
}

async function action(name: 'submit' | 'approve' | 'ship' | 'cancel') {
  saving.value = true
  try {
    const { id, version } = detail.value.master
    const fn = { submit: submitTransfer, approve: approveTransfer, ship: shipTransfer, cancel: cancelTransfer }[name]
    await fn(id, version)
    Toast.success('操作成功')
    detailVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function doReceive() {
  saving.value = true
  try {
    const { id, version } = detail.value.master
    const items = detail.value.items.map((i: any) => ({
      itemId: i.id,
      receivedQty: receiveForm[i.id]?.receivedQty ?? i.shippedQty,
      diffReason: receiveForm[i.id]?.diffReason || null,
    }))
    await receiveTransfer(id, { version, items })
    Toast.success('已收货')
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
.form-row { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.form-row label { font-size: 13px; color: #4b5563; }
.line-header { display: flex; justify-content: space-between; align-items: center; margin: 8px 0; font-weight: 600; font-size: 13px; }
.line-grid { display: grid; grid-template-columns: 2fr 1.6fr 0.8fr 40px; gap: 8px; margin-bottom: 8px; }
.line-grid-head { font-size: 12px; color: #6b7280; }
.detail p { font-size: 13px; color: #4b5563; }
.detail-actions { margin-top: 16px; text-align: right; display: flex; gap: 8px; justify-content: flex-end; }
.receive-box { margin-top: 12px; border-top: 1px dashed #e3e8ee; padding-top: 12px; }
.receive-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; font-size: 13px; }
.receive-row span { flex: 1; color: #374151; }
</style>
