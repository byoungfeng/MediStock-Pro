<template>
  <div class="page-card">
    <div class="toolbar">
      <Select class="w160" :value="query.warehouseId" :option-list="warehouseOptions" placeholder="仓库"
              :on-change="(v: any) => { query.warehouseId = v; load() }" />
      <Select class="w160" :value="query.status" :option-list="statusOptions" placeholder="状态"
              :on-change="(v: any) => { query.status = v; load() }" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
      <Button v-permission="PERMISSION.STOCK_COUNT_CREATE" type="primary" theme="light" :on-click="openCreate">新建盘点计划</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="{ currentPage: query.page, pageSize: query.size, total, onChange: onPageChange }" />

    <!-- 新建盘点计划 -->
    <Modal :visible="createVisible" title="新建盘点计划" :width="480"
           :on-ok="save" :on-cancel="() => { createVisible  = false }" :confirm-loading="saving">
      <div class="form-col">
        <label>盘点仓库 *</label>
        <Select :value="form.warehouseId" :option-list="warehouseOptions"
                :on-change="(v: any) => (form.warehouseId = v)" />
        <label>范围说明</label>
        <Input :value="form.scope" :on-change="(v: string) => (form.scope = v)" placeholder="如: 全仓 / 冷藏区" />
        <label>盘点期间冻结库存 (冻结后禁止出库/调拨)</label>
        <Select :value="form.freeze" :option-list="freezeOptions"
                :on-change="(v: any) => (form.freeze = v)" />
      </div>
    </Modal>

    <!-- 详情 / 录入 -->
    <Modal :visible="detailVisible" title="盘点单详情" :width="900" :footer="noFooter"
           :on-cancel="() => { detailVisible  = false }">
      <div v-if="detail" class="detail">
        <p>
          单号: {{ detail.master.planNo }} ｜ 状态: {{ statusText(detail.master.status) }} ｜
          快照时间: {{ detail.master.snapshotAt || '-' }} ｜ 冻结: {{ detail.master.freeze ? '是' : '否' }}
        </p>

        <div v-if="detail.master.status === 'COUNTING'" class="entry-tip">
          逐行录入实盘数量, 有差异需填原因; 全部录完后点「保存录入」再「确认盘点」
        </div>
        <Table :columns="detailColumns" :data-source="detail.items" row-key="id" size="small" :pagination="false" />

        <div class="detail-actions">
          <Button v-if="detail.master.status === 'DRAFT'" v-permission="PERMISSION.STOCK_COUNT_EXECUTE" theme="solid" type="primary"
                  :loading="saving" :on-click="() => action('start')">开始盘点(生成快照)</Button>
          <template v-if="detail.master.status === 'COUNTING'">
            <Button v-permission="PERMISSION.STOCK_COUNT_EXECUTE" theme="solid" type="primary" :loading="saving" :on-click="saveEntry">保存录入</Button>
            <Button v-permission="PERMISSION.STOCK_COUNT_CONFIRM" theme="solid" type="warning" :loading="saving" :on-click="() => action('confirm')">确认盘点(差异调账)</Button>
          </template>
          <Button v-if="['DRAFT', 'COUNTING'].includes(detail.master.status)" v-permission="PERMISSION.STOCK_COUNT_CREATE" type="danger" theme="light"
                  :on-click="() => action('cancel')">取消盘点</Button>
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
  cancelCountPlan, confirmCountPlan, createCountPlan, entryCountPlan,
  getCountPlan, pageCountPlans, startCountPlan,
} from '@/api/count'
import { pageMaterials } from '@/api/material'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol } from '@/utils/format'
import { PERMISSION } from '@/constants/permissions'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, warehouseId: undefined as number | undefined, status: '' })
const freezeOptions: any[] = [{ value: false, label: '不冻结' }, { value: true, label: '冻结' }]

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'COUNTING', label: '盘点中' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'CANCELLED', label: '已取消' },
]
const warehouseOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])

function statusText(s: string) {
  return statusOptions.find((o) => o.value === s)?.label || s
}
function statusColor(s: string) {
  return { DRAFT: 'grey', COUNTING: 'blue', CONFIRMED: 'green', CANCELLED: 'grey' }[s] || 'grey'
}
function materialName(id: number) {
  return materialOptions.value.find((m) => m.value === id)?.label || id
}

const columns = [
  { title: '盘点单号', dataIndex: 'planNo' },
  { title: '仓库', dataIndex: 'warehouseId', render: (v: number) => warehouseOptions.value.find((w) => w.value === v)?.label || v },
  { title: '范围', dataIndex: 'scope' },
  { title: '冻结', dataIndex: 'freeze', render: (v: number) => (v ? '是' : '否') },
  { title: '状态', dataIndex: 'status', render: (v: string) => h(Tag, { color: statusColor(v) as any }, () => statusText(v)) },
  { title: '快照时间', dataIndex: 'snapshotAt' },
  { title: '创建时间', dataIndex: 'createdAt' },
  {
    title: '操作', dataIndex: 'actions',
    render: (_: any, r: any) =>
      h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openDetail(r.id) }, () => '详情'),
  },
]

// 录入态: itemId -> { countQty, diffReason }
const entryForm = reactive<Record<number, any>>({})

const detailColumns = [
  { title: '物资', dataIndex: 'materialId', render: (v: number) => materialName(v) },
  { title: '批次', dataIndex: 'batchNo' },
  numCol('账面数', 'bookQty'),
  {
    title: '实盘数', dataIndex: 'countQty',
    render: (v: any, r: any) =>
      detail.value?.master.status === 'COUNTING'
        ? h(Input, {
            style: { width: "90px" },
            value: String(entryForm[r.id]?.countQty ?? v ?? ''),
            'on-change': (val: string) => setEntry(r.id, 'countQty', val === '' ? null : Number(val)),
          })
        : v,
  },
  { title: '差异', dataIndex: 'diffQty', align: 'right' as const, render: (v: any) => (v == null ? '-' : v === 0 ? '0' : h(Tag, { color: 'red' }, () => (v > 0 ? `+${v}` : String(v)))) },
  {
    title: '差异原因', dataIndex: 'diffReason',
    render: (v: any, r: any) =>
      detail.value?.master.status === 'COUNTING'
        ? h(Input, {
            style: { width: "160px" },
            value: entryForm[r.id]?.diffReason ?? v ?? '',
            'on-change': (val: string) => setEntry(r.id, 'diffReason', val),
          })
        : v || '-',
  },
]

async function load() {
  loading.value = true
  try {
    const data: any = await pageCountPlans({
      ...query,
      warehouseId: query.warehouseId || undefined,
      status: query.status || undefined,
    })
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
const form = reactive<any>({ warehouseId: null, scope: '', freeze: false })

function openCreate() {
  Object.assign(form, { warehouseId: null, scope: '', freeze: false })
  createVisible.value = true
}

async function save() {
  if (!form.warehouseId) return Toast.warning('请选择盘点仓库')
  saving.value = true
  try {
    await createCountPlan({ ...form })
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

async function openDetail(id: number) {
  detail.value = await getCountPlan(id)
  Object.keys(entryForm).forEach((k) => delete entryForm[Number(k)])
  detailVisible.value = true
}

function setEntry(itemId: number, key: string, v: any) {
  entryForm[itemId] = { ...entryForm[itemId], [key]: v }
}

async function action(name: 'start' | 'confirm' | 'cancel') {
  saving.value = true
  try {
    const { id, version } = detail.value.master
    const fn = { start: startCountPlan, confirm: confirmCountPlan, cancel: cancelCountPlan }[name]
    await fn(id, version)
    Toast.success('操作成功')
    detailVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function saveEntry() {
  saving.value = true
  try {
    const { id, version } = detail.value.master
    const items = Object.entries(entryForm)
      .filter(([, v]) => v.countQty !== undefined && v.countQty !== null)
      .map(([itemId, v]) => ({ itemId: Number(itemId), countQty: v.countQty, diffReason: v.diffReason || null }))
    if (!items.length) {
      Toast.warning('请先录入实盘数')
      return
    }
    await entryCountPlan(id, { version, items })
    Toast.success('已保存录入')
    detail.value = await getCountPlan(id)
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
.form-col { display: flex; flex-direction: column; gap: 10px; }
.form-col label { font-size: 13px; color: #4b5563; }
.detail p { font-size: 13px; color: #4b5563; }
.detail-actions { margin-top: 16px; display: flex; gap: 8px; justify-content: flex-end; }
.entry-tip { font-size: 12px; color: #6b7280; margin-bottom: 8px; }
</style>
