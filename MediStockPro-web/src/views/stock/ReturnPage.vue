<template>
  <div class="page-card">
    <div class="toolbar">
      <Select class="w160" :value="query.status" :option-list="statusOptions" placeholder="状态"
              :on-change="(v: any) => { query.status = v; load() }" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
      <Button v-permission="PERMISSION.STOCK_RETURN_CREATE" type="primary" theme="light" :on-click="openCreate">新建退库单</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="{ currentPage: query.page, pageSize: query.size, total, onChange: onPageChange }" />

    <!-- 新建退库单 -->
    <Modal :visible="createVisible" title="新建科室退库单" :width="820"
           :on-ok="save" :on-cancel="() => { createVisible  = false }" :confirm-loading="saving">
      <div class="form-row">
        <label>来源出库单 *</label>
        <Select :value="form.issueId" :option-list="issueOptions" style="width: 220px" filter
                :on-change="(v: any) => onIssueChange(v)" />
        <label>退回仓库 *</label>
        <Select :value="form.warehouseId" :option-list="warehouseOptions" style="width: 160px"
                :on-change="(v: any) => (form.warehouseId = v)" />
      </div>
      <div class="form-row">
        <label>退库原因</label>
        <Input :value="form.reason" :on-change="(v: string) => (form.reason = v)" style="width: 480px" />
      </div>
      <div class="line-header">
        <span>退库明细 (按批次)</span>
        <Button size="small" theme="light" :on-click="addLine">+ 添加行</Button>
      </div>
      <div class="line-grid line-grid-head">
        <span>物资 *</span><span>批次 *</span><span>数量 *</span><span></span>
      </div>
      <div v-for="(line, idx) in form.items" :key="idx" class="line-grid">
        <Select :value="line.materialId" :option-list="materialOptions" filter
                :on-change="(v: any) => (line.materialId = v)" />
        <Input :value="line.batchNo" :on-change="(v: string) => (line.batchNo = v)" placeholder="批号" />
        <Input :value="String(line.qty)" :on-change="(v: string) => (line.qty = Number(v) || 0)" />
        <Button size="small" theme="borderless" type="danger" :on-click="() => form.items.splice(idx, 1)">删</Button>
      </div>
    </Modal>

    <!-- 详情 -->
    <Modal :visible="detailVisible" title="退库单详情" :width="760" :footer="noFooter"
           :on-cancel="() => { detailVisible  = false }">
      <div v-if="detail" class="detail">
        <p>
          单号: {{ detail.master.returnNo }} ｜ 状态: {{ statusText(detail.master.status) }} ｜
          来源出库单: #{{ detail.master.issueId }} ｜ 原因: {{ detail.master.reason || '-' }}
        </p>
        <Table :columns="detailColumns" :data-source="detail.items" row-key="id" size="small" :pagination="false" />
        <div class="detail-actions" v-if="detail.master.status === 'DRAFT'">
          <Button v-permission="PERMISSION.STOCK_RETURN_CONFIRM" theme="solid" type="primary" :loading="saving" :on-click="doConfirm">确认退库(入库)</Button>
          <Button v-permission="PERMISSION.STOCK_RETURN_CREATE" type="danger" theme="light" :on-click="doCancel">取消</Button>
        </div>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
const noFooter: any = null // Semi Modal footer 类型不收 null, 运行时需要
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import { cancelReturn, confirmReturn, createReturn, getReturn, pageReturns } from '@/api/return'
import { pageMaterials } from '@/api/material'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol } from '@/utils/format'
import { pageIssues } from '@/api/issue'
import { PERMISSION } from '@/constants/permissions'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, status: '' })

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'CONFIRMED', label: '已退库' },
  { value: 'CANCELLED', label: '已取消' },
]
const warehouseOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const issueOptions = ref<any[]>([])

function statusText(s: string) {
  return statusOptions.find((o) => o.value === s)?.label || s
}
function statusColor(s: string) {
  return { DRAFT: 'grey', CONFIRMED: 'green', CANCELLED: 'red' }[s] || 'grey'
}
function warehouseName(id: number) {
  return warehouseOptions.value.find((w) => w.value === id)?.label || id
}
function materialName(id: number) {
  return materialOptions.value.find((m) => m.value === id)?.label || id
}

const columns = [
  { title: '单号', dataIndex: 'returnNo' },
  { title: '来源出库单', dataIndex: 'issueId', render: (_: any, r: any) => `#${r.issueId}` },
  { title: '仓库', dataIndex: 'warehouseId', render: (_: any, r: any) => warehouseName(r.warehouseId) },
  { title: '原因', dataIndex: 'reason' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: statusColor(r.status) as any }, () => statusText(r.status)),
  },
  { title: '创建时间', dataIndex: 'createdAt' },
  {
    title: '操作',
    render: (_: any, r: any) => h(Button, { size: 'small', theme: 'light', onClick: () => openDetail(r.id) }, () => '详情'),
  },
]

const detailColumns = [
  { title: '物资', dataIndex: 'materialId', render: (_: any, r: any) => materialName(r.materialId) },
  { title: '批次', dataIndex: 'batchNo' },
  numCol('退库数量', 'qty'),
]

async function load() {
  loading.value = true
  try {
    const res: any = await pageReturns({ ...query })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function onPageChange(p: number) {
  query.page = p
  load()
}

const createVisible = ref(false)
const form = reactive({ issueId: undefined as any, warehouseId: undefined as any, reason: '', items: [] as any[] })

function openCreate() {
  form.issueId = undefined
  form.warehouseId = undefined
  form.reason = ''
  form.items = [{ materialId: undefined, batchNo: '', qty: 1 }]
  createVisible.value = true
}
function addLine() {
  form.items.push({ materialId: undefined, batchNo: '', qty: 1 })
}
function onIssueChange(issueId: number) {
  const issue = issueOptions.value.find((o) => o.value === issueId)?.raw
  if (issue) form.warehouseId = issue.warehouseId
}

async function save() {
  if (!form.issueId || !form.warehouseId) {
    Toast.warning('来源出库单与退回仓库必填')
    return
  }
  if (!form.items.length || form.items.some((l) => !l.materialId || !l.batchNo || l.qty <= 0)) {
    Toast.warning('明细行需完整: 物资/批次/数量>0')
    return
  }
  saving.value = true
  try {
    await createReturn(form)
    Toast.success('退库单已创建, 待确认')
    createVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const detailVisible = ref(false)
const detail = ref<any>(null)

async function openDetail(id: number) {
  detail.value = await getReturn(id)
  detailVisible.value = true
}
async function doConfirm() {
  saving.value = true
  try {
    await confirmReturn(detail.value.master.id, detail.value.master.version)
    Toast.success('已确认, 库存已退回')
    detailVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function doCancel() {
  saving.value = true
  try {
    await cancelReturn(detail.value.master.id, detail.value.master.version)
    Toast.success('已取消')
    detailVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const [wh, mat, issues]: any[] = await Promise.all([
    getWarehouseTree(),
    pageMaterials({ page: 1, size: 500 }),
    pageIssues({ page: 1, size: 100, status: 'SIGNED' }),
  ])
  warehouseOptions.value = wh.map((w: any) => ({ value: w.id, label: w.name }))
  materialOptions.value = (mat.records || mat).map((m: any) => ({ value: m.id, label: `${m.code} ${m.name}` }))
  issueOptions.value = (issues.records || []).map((o: any) => ({
    value: o.id, label: `${o.issueNo} (已签收)`, raw: o,
  }))
  load()
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.w160 { width: 160px; }
.form-row { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.form-row label { font-size: 13px; color: #4b5563; }
.line-header { display: flex; justify-content: space-between; align-items: center; margin: 8px 0; font-size: 13px; color: #4b5563; }
.line-grid { display: grid; grid-template-columns: 2fr 2fr 1fr 40px; gap: 8px; margin-bottom: 8px; align-items: center; }
.line-grid-head { font-size: 12px; color: #6b7280; }
.detail p { margin: 0 0 12px; font-size: 13px; color: #374151; }
.detail-actions { display: flex; gap: 8px; margin-top: 16px; justify-content: flex-end; }
</style>
