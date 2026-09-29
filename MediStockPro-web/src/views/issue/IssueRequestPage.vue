<template>
  <div class="page-card">
    <div class="toolbar">
      <Select class="w160" :value="query.status" :option-list="statusOptions"
              :on-change="(v: any) => { query.status = v; load() }" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
      <Button v-permission="PERMISSION.ISSUE_REQUEST_EDIT" type="primary" theme="light" :on-click="openCreate">新建申领</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="{ currentPage: query.page, pageSize: query.size, total, onChange: onPageChange }" />

    <!-- 新建申领 -->
    <Modal :visible="createVisible" title="新建科室申领" :width="760"
           :on-ok="save" :on-cancel="() => { createVisible  = false }" :confirm-loading="saving">
      <div class="form-row">
        <label>发放仓库 *</label>
        <Select :value="form.warehouseId" :option-list="warehouseOptions" style="width: 200px"
                :on-change="(v: any) => (form.warehouseId = v)" />
        <label>科室 *</label>
        <TreeSelect :value="form.departmentId" :tree-data="deptTree" style="width: 220px"
                    placeholder="选择科室" :on-change="(v: any) => (form.departmentId = v)" />
        <label>优先级</label>
        <Select :value="form.priority" style="width: 110px"
                :option-list="[{ value: 'NORMAL', label: '普通' }, { value: 'URGENT', label: '紧急' }]"
                :on-change="(v: any) => (form.priority = v)" />
      </div>
      <div class="form-row">
        <label>用途</label>
        <Input :value="form.purpose" style="width: 100%"
               :on-change="(v: string) => (form.purpose = v)" />
      </div>
      <div class="line-header">
        <span>申领明细</span>
        <Button size="small" theme="light" :on-click="addLine">+ 添加行</Button>
      </div>
      <div class="line-grid line-grid-head"><span>物资 *</span><span>数量 *</span><span></span></div>
      <div v-for="(line, idx) in form.items" :key="idx" class="line-grid">
        <Select :value="line.materialId" :option-list="materialOptions" filter
                :on-change="(v: any) => (line.materialId = v)" />
        <Input :value="String(line.qty)" :on-change="(v: string) => (line.qty = Number(v) || 0)" />
        <Button size="small" theme="borderless" type="danger" :on-click="() => form.items.splice(idx, 1)">删</Button>
      </div>
    </Modal>

    <!-- 详情/审批 -->
    <Modal :visible="detailVisible" title="申领详情" :width="760" :footer="noFooter"
           :on-cancel="() => { detailVisible  = false }">
      <div v-if="detail">
        <p class="detail-meta">
          单号: {{ detail.master.requestNo }} ｜ 状态: {{ statusText(detail.master.status) }} ｜
          优先级: {{ detail.master.priority === 'URGENT' ? '紧急' : '普通' }}
        </p>
        <Table :columns="approveColumns" :data-source="approveItems" row-key="id" size="small" :pagination="false" />
        <div class="detail-actions">
          <Button v-if="detail.master.status === 'DRAFT'" v-permission="PERMISSION.ISSUE_REQUEST_EDIT"
                  theme="solid" type="primary" :loading="saving" :on-click="submit">提交审批</Button>
          <template v-if="detail.master.status === 'PENDING'">
            <Button v-permission="PERMISSION.ISSUE_REQUEST_APPROVE" theme="solid" type="primary"
                    :loading="saving" :on-click="() => doApprove(true)">
              通过并锁定库存
            </Button>
            <Button v-permission="PERMISSION.ISSUE_REQUEST_APPROVE" theme="light" type="danger"
                    :loading="saving" :on-click="() => doApprove(false)">驳回</Button>
          </template>
        </div>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
const noFooter: any = null // Semi Modal footer 类型不收 null, 运行时需要
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast, TreeSelect } from '@kousum/semi-ui-vue'
import {
  approveIssueRequest, createIssueRequest, getIssueRequest, pageIssueRequests, submitIssueRequest,
} from '@/api/issue'
import { pageMaterials } from '@/api/material'
import { getWarehouseTree } from '@/api/warehouse'
import { listOrgUnitOptions } from '@/api/system'
import { numCol } from '@/utils/format'
import { PERMISSION } from '@/constants/permissions'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, status: '' })

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '审批中' },
  { value: 'PICKING', label: '拣货中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '已驳回' },
]
const warehouseOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const deptTree = ref<any[]>([])
const deptNameMap = ref<Record<string, string>>({})

/** 平铺组织列表组树 (parentId 为空/0 为根) */
function buildTree(list: any[]): any[] {
  const nodes = new Map<number, any>()
  for (const u of list) nodes.set(u.id, { label: u.name, value: u.id, key: String(u.id), children: [] })
  const roots: any[] = []
  for (const u of list) {
    const node = nodes.get(u.id)
    const parent = u.parentId != null ? nodes.get(u.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  // Semi Tree 不渲染空 children 之外的差异, 清理空数组保持简洁
  for (const n of nodes.values()) if (!n.children.length) delete n.children
  return roots
}

function statusText(s: string) {
  return { DRAFT: '草稿', PENDING: '审批中', APPROVED: '已通过', PICKING: '拣货中', COMPLETED: '已完成', REJECTED: '已驳回' }[s] || s
}
function statusColor(s: string) {
  return { DRAFT: 'grey', PENDING: 'blue', APPROVED: 'green', PICKING: 'blue', COMPLETED: 'green', REJECTED: 'red' }[s] || 'grey'
}

const columns = [
  { title: '申领单号', dataIndex: 'requestNo' },
  { title: '科室', dataIndex: 'departmentId', render: (v: number) => deptNameMap.value[String(v)] || v },
  { title: '发放仓库', dataIndex: 'warehouseId', render: (v: number) => warehouseOptions.value.find((w) => w.value === v)?.label || v },
  { title: '优先级', dataIndex: 'priority', render: (v: string) => (v === 'URGENT' ? '紧急' : '普通') },
  { title: '状态', dataIndex: 'status', render: (v: string) => h(Tag, { color: statusColor(v) as any }, () => statusText(v)) },
  { title: '创建时间', dataIndex: 'createdAt' },
  {
    title: '操作', dataIndex: 'actions',
    render: (_: any, r: any) =>
      h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openDetail(r.id) }, () => '详情/审批'),
  },
]

async function load() {
  loading.value = true
  try {
    const data: any = await pageIssueRequests({ ...query, status: query.status || undefined })
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
const form = reactive<any>({ warehouseId: null, departmentId: null, priority: 'NORMAL', purpose: '', items: [{ materialId: null, qty: 1 }] })

function openCreate() {
  Object.assign(form, { warehouseId: null, departmentId: null, priority: 'NORMAL', purpose: '', items: [{ materialId: null, qty: 1 }] })
  createVisible.value = true
}
function addLine() {
  form.items.push({ materialId: null, qty: 1 })
}

async function save() {
  if (!form.warehouseId || !form.departmentId) return Toast.warning('请选择仓库和科室')
  if (form.items.some((l: any) => !l.materialId || !(l.qty > 0))) return Toast.warning('明细行需填写物资和数量')
  saving.value = true
  try {
    await createIssueRequest({ ...form })
    Toast.success('已创建(草稿)')
    createVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---- 详情/审批 ----
const detailVisible = ref(false)
const detail = ref<any>(null)
const approveItems = ref<any[]>([])

const approveColumns = [
  { title: '物资', dataIndex: 'materialId', render: (v: number) => materialOptions.value.find((m) => m.value === v)?.label || v },
  numCol('申请数量', 'qty'),
  {
    title: '审批数量', dataIndex: 'approvedQty',
    render: (_: any, r: any) =>
      detail.value?.master.status === 'PENDING'
        ? h(Input, { value: String(r.approvedQty), onChange: (v: string) => (r.approvedQty = Number(v) || 0), style: { width: "90px" } })
        : r.approvedQty ?? '-',
  },
  { title: '已发数量', dataIndex: 'issuedQty' },
]

async function openDetail(id: number) {
  detail.value = await getIssueRequest(id)
  approveItems.value = detail.value.items.map((i: any) => ({ ...i, approvedQty: i.approvedQty ?? i.qty }))
  detailVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    await submitIssueRequest(detail.value.master.id, detail.value.master.version)
    Toast.success('已提交')
    detailVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function doApprove(pass: boolean) {
  saving.value = true
  try {
    await approveIssueRequest(detail.value.master.id, {
      version: detail.value.master.version,
      pass,
      items: pass ? approveItems.value.map((i: any) => ({ itemId: i.id, approvedQty: i.approvedQty })) : undefined,
    })
    Toast.success(pass ? '已通过并锁定库存(FEFO)' : '已驳回')
    detailVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  try {
    const [wh, mt, orgs]: any[] = await Promise.all([getWarehouseTree(), pageMaterials({ page: 1, size: 1000 }), listOrgUnitOptions()])
    warehouseOptions.value = wh.map((w: any) => ({ value: w.id, label: w.name }))
    materialOptions.value = mt.records.map((m: any) => ({ value: m.id, label: `${m.code} ${m.name} ${m.spec || ''}` }))
    deptTree.value = buildTree(orgs)
    for (const o of orgs) deptNameMap.value[String(o.id)] = o.name
  } catch { /* 主数据未就绪 */ }
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.w160 { width: 160px; }
.form-row { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.form-row label { font-size: 13px; color: #4b5563; }
.line-header { display: flex; justify-content: space-between; align-items: center; margin: 8px 0; font-weight: 600; font-size: 13px; }
.line-grid { display: grid; grid-template-columns: 2fr 1fr 40px; gap: 8px; margin-bottom: 8px; }
.line-grid-head { font-size: 12px; color: #6b7280; }
.detail-meta { font-size: 13px; color: #4b5563; }
.detail-actions { margin-top: 16px; display: flex; gap: 12px; justify-content: flex-end; }
</style>
