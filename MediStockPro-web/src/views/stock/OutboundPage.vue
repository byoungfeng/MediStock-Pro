<template>
  <div class="page-card">
    <div class="tab-bar">
      <div v-for="t in tabs" :key="t.key" class="tab" :class="{ active: tab === t.key }" @click="switchTab(t.key)">
        {{ t.text }}
      </div>
    </div>

    <!-- 拣货任务 -->
    <template v-if="tab === 'pick'">
      <Table :columns="pickColumns" :data-source="pickRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: pickQuery.page, pageSize: pickQuery.size, total: pickTotal, onChange: onPickPage }" />
    </template>

    <!-- 出库单 -->
    <template v-else>
      <Table :columns="issueColumns" :data-source="issueRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: issueQuery.page, pageSize: issueQuery.size, total: issueTotal, onChange: onIssuePage }" />
    </template>

    <!-- 拣货完成 -->
    <Modal :visible="pickVisible" title="拣货复核" :width="760" :footer="noFooter"
           :on-cancel="() => { pickVisible  = false }">
      <div v-if="pickDetail">
        <p class="meta">任务号: {{ pickDetail.master.taskNo }} ｜ 来源: {{ pickDetail.master.sourceNo }}</p>
        <div class="line-grid line-grid-head">
          <span>物资/批号</span><span>应拣</span><span>实拣 *</span><span>短拣原因</span>
        </div>
        <div v-for="item in pickItems" :key="item.id" class="line-grid">
          <span>{{ materialName(item.materialId) }} / {{ item.batchNo }}</span>
          <span>{{ item.suggestedQty }}</span>
          <Input :value="String(item.pickedQty)" :on-change="(v: string) => (item.pickedQty = Number(v) || 0)" />
          <Input :value="item.shortReason || ''" placeholder="短拣必填"
                 :on-change="(v: string) => (item.shortReason = v)" />
        </div>
        <div class="actions">
          <Button v-permission="PERMISSION.PICK_EXECUTE" theme="solid" type="primary" :loading="saving" :on-click="doComplete">完成拣货并生成出库单</Button>
          <Button v-permission="PERMISSION.PICK_EXECUTE" theme="light" type="danger" :loading="saving" :on-click="doCancelPick">取消任务(释放锁定)</Button>
        </div>
      </div>
    </Modal>

    <!-- 出库单详情 -->
    <Modal :visible="issueVisible" title="出库单详情" :width="760" :footer="noFooter"
           :on-cancel="() => { issueVisible  = false }">
      <div v-if="issueDetail">
        <p class="meta">
          单号: {{ issueDetail.master.issueNo }} ｜ 状态: {{ issueStatusText(issueDetail.master.status) }} ｜
          总量: {{ issueDetail.master.totalQty }}
        </p>
        <Table :columns="issueItemColumns" :data-source="issueDetail.items" row-key="id" size="small" :pagination="false" />
        <div class="actions">
          <Button v-if="issueDetail.master.status === 'PENDING'" v-permission="PERMISSION.ISSUE_CONFIRM" theme="solid" type="primary"
                  :loading="saving" :on-click="doConfirm">复核出库(扣减库存)</Button>
          <template v-if="issueDetail.master.status === 'CONFIRMED'">
            <Input :value="signBy" placeholder="签收人" style="width: 160px"
                   :on-change="(v: string) => (signBy = v)" />
            <Button v-permission="PERMISSION.ISSUE_CONFIRM" theme="solid" type="primary" :loading="saving" :on-click="doSign">科室签收</Button>
          </template>
          <template v-if="issueDetail.master.status === 'SIGNED'">
            <Input :value="reverseReason" placeholder="红冲原因" style="width: 200px"
                   :on-change="(v: string) => (reverseReason = v)" />
            <Button v-permission="PERMISSION.ISSUE_REVERSE" theme="solid" type="danger" :loading="saving" :on-click="doReverse">红冲(库存原批次退回)</Button>
          </template>
        </div>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
const noFooter: any = null // Semi Modal footer 类型不收 null, 运行时需要
import { h, onMounted, ref } from 'vue'
import { Button, Input, Modal, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import {
  cancelPickTask, completePickTask, confirmIssue, getIssue, getPickTask,
  pageIssues, pagePickTasks, reverseIssue, signIssue,
} from '@/api/issue'
import { pageMaterials } from '@/api/material'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol, moneyCol } from '@/utils/format'
import { PERMISSION } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const tabs = [
  { key: 'pick', text: '拣货任务' },
  { key: 'issue', text: '出库单' },
]
const tab = ref('pick')
const loading = ref(false)
const saving = ref(false)

const pickRows = ref<any[]>([])
const pickTotal = ref(0)
const pickQuery = ref({ page: 1, size: 20 })
const issueRows = ref<any[]>([])
const issueTotal = ref(0)
const issueQuery = ref({ page: 1, size: 20 })

const warehouseOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const materialName = (id: number) => materialOptions.value.find((m) => m.value === id)?.label || id
const warehouseName = (id: number) => warehouseOptions.value.find((w) => w.value === id)?.label || id

function pickStatusText(s: string) {
  return { PICKING: '拣货中', PICKED: '待复核', CANCELLED: '已取消' }[s] || s
}
function issueStatusText(s: string) {
  return { PENDING: '待复核', CONFIRMED: '已出库', SIGNED: '已签收', REVERSED: '已红冲' }[s] || s
}

const pickColumns = [
  { title: '任务号', dataIndex: 'taskNo' },
  { title: '来源单号', dataIndex: 'sourceNo' },
  { title: '仓库', dataIndex: 'warehouseId', render: (v: number) => warehouseName(v) },
  { title: '状态', dataIndex: 'status', render: (v: string) => h(Tag, { color: (({ PICKING: 'blue', PICKED: 'blue', CANCELLED: 'grey' } as Record<string, string>)[v] || 'grey') as any }, () => pickStatusText(v)) },
  { title: '创建时间', dataIndex: 'createdAt' },
  {
    title: '操作', dataIndex: 'actions',
    render: (_: any, r: any) =>
      r.status === 'PICKING' && userStore.hasPermission(PERMISSION.PICK_EXECUTE)
        ? h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openPick(r.id) }, () => '拣货')
        : h(Button, { size: 'small', theme: 'borderless', onClick: () => openPick(r.id) }, () => '查看'),
  },
]

const issueColumns = [
  { title: '出库单号', dataIndex: 'issueNo' },
  { title: '科室', dataIndex: 'departmentId' },
  { title: '仓库', dataIndex: 'warehouseId', render: (v: number) => warehouseName(v) },
  numCol('总数量', 'totalQty'),
  moneyCol('总金额', 'totalAmount'),
  { title: '状态', dataIndex: 'status', render: (v: string) => h(Tag, { color: (({ PENDING: 'blue', CONFIRMED: 'green', SIGNED: 'green', REVERSED: 'red' } as Record<string, string>)[v] || 'grey') as any }, () => issueStatusText(v)) },
  { title: '签收人', dataIndex: 'signBy' },
  {
    title: '操作', dataIndex: 'actions',
    render: (_: any, r: any) =>
      h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openIssue(r.id) }, () => '详情'),
  },
]

const issueItemColumns = [
  { title: '物资', dataIndex: 'materialId', render: (v: number) => materialName(v) },
  { title: '批号', dataIndex: 'batchNo' },
  numCol('数量', 'qty'),
  moneyCol('单价', 'unitCost'),
]

function switchTab(key: string) {
  tab.value = key
  key === 'pick' ? loadPick() : loadIssues()
}

async function loadPick() {
  loading.value = true
  try {
    const data: any = await pagePickTasks(pickQuery.value)
    pickRows.value = data.records
    pickTotal.value = data.total
  } finally {
    loading.value = false
  }
}
async function loadIssues() {
  loading.value = true
  try {
    const data: any = await pageIssues(issueQuery.value)
    issueRows.value = data.records
    issueTotal.value = data.total
  } finally {
    loading.value = false
  }
}
function onPickPage(page: number, size: number) {
  pickQuery.value = { page, size }
  loadPick()
}
function onIssuePage(page: number, size: number) {
  issueQuery.value = { page, size }
  loadIssues()
}

// ---- 拣货 ----
const pickVisible = ref(false)
const pickDetail = ref<any>(null)
const pickItems = ref<any[]>([])

async function openPick(id: number) {
  pickDetail.value = await getPickTask(id)
  pickItems.value = pickDetail.value.items.map((i: any) => ({ ...i, pickedQty: i.pickedQty ?? i.suggestedQty }))
  pickVisible.value = true
}

async function doComplete() {
  const short = pickItems.value.find((i: any) => i.pickedQty < i.suggestedQty && !i.shortReason)
  if (short) return Toast.warning(`物资 ${materialName(short.materialId)} 短拣需填写原因`)
  saving.value = true
  try {
    await completePickTask(pickDetail.value.master.id, {
      version: pickDetail.value.master.version,
      items: pickItems.value.map((i: any) => ({
        itemId: i.id, pickedQty: i.pickedQty, shortReason: i.shortReason, overrideReason: i.overrideReason,
      })),
    })
    Toast.success('拣货完成, 已生成出库单')
    pickVisible.value = false
    loadPick()
  } finally {
    saving.value = false
  }
}

async function doCancelPick() {
  saving.value = true
  try {
    await cancelPickTask(pickDetail.value.master.id, pickDetail.value.master.version)
    Toast.success('已取消并释放锁定')
    pickVisible.value = false
    loadPick()
  } finally {
    saving.value = false
  }
}

// ---- 出库单 ----
const issueVisible = ref(false)
const issueDetail = ref<any>(null)
const signBy = ref('')
const reverseReason = ref('')

async function openIssue(id: number) {
  issueDetail.value = await getIssue(id)
  signBy.value = ''
  reverseReason.value = ''
  issueVisible.value = true
}

async function doReverse() {
  saving.value = true
  try {
    await reverseIssue(issueDetail.value.master.id, issueDetail.value.master.version, reverseReason.value || '红冲')
    Toast.success('已红冲, 库存按原批次退回')
    issueVisible.value = false
    loadIssues()
  } finally {
    saving.value = false
  }
}

async function doConfirm() {
  saving.value = true
  try {
    await confirmIssue(issueDetail.value.master.id, issueDetail.value.master.version)
    Toast.success('已出库')
    issueVisible.value = false
    loadIssues()
  } finally {
    saving.value = false
  }
}

async function doSign() {
  if (!signBy.value) return Toast.warning('请填写签收人')
  saving.value = true
  try {
    await signIssue(issueDetail.value.master.id, issueDetail.value.master.version, signBy.value)
    Toast.success('已签收, 流程闭环')
    issueVisible.value = false
    loadIssues()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  loadPick()
  try {
    const [wh, mt]: any[] = await Promise.all([getWarehouseTree(), pageMaterials({ page: 1, size: 1000 })])
    warehouseOptions.value = wh.map((w: any) => ({ value: w.id, label: w.name }))
    materialOptions.value = mt.records.map((m: any) => ({ value: m.id, label: `${m.code} ${m.name}` }))
  } catch { /* 主数据未就绪 */ }
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.tab-bar { display: flex; gap: 4px; border-bottom: 1px solid rgba(17, 24, 39, 0.08); margin-bottom: 16px; }
.tab { padding: 8px 16px; font-size: 13px; color: #4b5563; cursor: pointer; border-bottom: 2px solid transparent; }
.tab.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 500; }
.meta { font-size: 13px; color: #4b5563; }
.line-grid { display: grid; grid-template-columns: 2fr 0.6fr 0.8fr 1.4fr; gap: 8px; margin-bottom: 8px; align-items: center; }
.line-grid-head { font-size: 12px; color: #6b7280; }
.actions { margin-top: 16px; display: flex; gap: 12px; justify-content: flex-end; }
</style>
