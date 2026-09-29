<template>
  <div class="page-card">
    <div class="toolbar">
      <Input class="toolbar-search" placeholder="物资编码/名称" :value="query.keyword"
             :on-change="(v: string) => (query.keyword = v)" :on-enter="load" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
      <Button v-permission="PERMISSION.MATERIAL_MANAGE_CREATE" type="primary" theme="light" :on-click="openCreate">新增物资</Button>
      <Button v-permission="PERMISSION.MATERIAL_CATEGORY_MANAGE_VIEW" theme="light" :on-click="openCategoryModal">分类管理</Button>
      <Button v-permission="PERMISSION.DATA_IMPORT_EXPORT" theme="light" :on-click="() => downloadCsv('materials')">导出</Button>
      <Button v-permission="PERMISSION.DATA_IMPORT_EXPORT" theme="light" :on-click="openImport">导入</Button>
      <Button theme="light" :on-click="openJobs">任务记录</Button>
      <Button v-permission="PERMISSION.MATERIAL_ATTRIBUTE_MANAGE_EDIT" theme="light" type="warning" :disabled="!selectedIds.length" :on-click="openBatchAttrs">
        批量修改{{ selectedIds.length ? `(${selectedIds.length})` : '' }}
      </Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="{
             currentPage: query.page,
             pageSize: query.size,
             total,
             showSizeChanger: true,
             pageSizeOpts: [20, 50, 100],
             onChange: onPageChange,
           }" />

    <Modal :visible="modalVisible" :title="editingId ? '编辑物资' : '新增物资'" :width="640"
           :on-ok="save" :on-cancel="closeModal" :confirm-loading="saving">
      <div class="form-grid">
        <label>物资编码 *</label>
        <Input :value="form.code" :on-change="(v: string) => (form.code = v)" placeholder="唯一编码" />
        <label>物资名称 *</label>
        <Input :value="form.name" :on-change="(v: string) => (form.name = v)" />
        <label>规格</label>
        <Input :value="form.spec" :on-change="(v: string) => (form.spec = v)" placeholder="如 0.25g*24粒" />
        <label>基本单位</label>
        <Select :value="form.uom" :option-list="uomOptions"
                :on-change="(v: any) => (form.uom = v)" />
        <label>生产厂家</label>
        <Input :value="form.manufacturer" :on-change="(v: string) => (form.manufacturer = v)" />
        <label>品牌</label>
        <Input :value="form.brand" :on-change="(v: string) => (form.brand = v)" />
        <label>批号管理</label>
        <Select :value="form.batchManaged" :option-list="yesNo"
                :on-change="(v: any) => (form.batchManaged = v)" />
        <label>效期管理</label>
        <Select :value="form.expiryManaged" :option-list="yesNo"
                :on-change="(v: any) => (form.expiryManaged = v)" />
        <label>安全库存</label>
        <Input :value="String(form.safetyQty ?? '')"
               :on-change="(v: string) => (form.safetyQty = Number(v) || 0)" />
        <label>库存上限</label>
        <Input :value="String(form.maxQty ?? '')"
               :on-change="(v: string) => (form.maxQty = Number(v) || 0)" />
      </div>
    </Modal>

    <!-- 分类管理 P009 -->
    <Modal :visible="categoryModal" title="物资分类管理" :width="640" :footer="noFooter"
           :on-cancel="() => { categoryModal = false }">
      <div class="mini-toolbar">
        <Input class="w140" :value="categoryForm.code" placeholder="编码"
               :on-change="(v: string) => (categoryForm.code = v)" />
        <Input class="w160" :value="categoryForm.name" placeholder="名称"
               :on-change="(v: string) => (categoryForm.name = v)" />
        <Input class="w100" :value="String(categoryForm.sort)"
               :on-change="(v: string) => (categoryForm.sort = Number(v) || 0)" placeholder="排序" />
        <Button size="small" theme="solid" type="primary" :on-click="saveCategory">新增分类</Button>
      </div>
      <Table :columns="categoryColumns" :data-source="categories" row-key="id" size="small"
             :pagination="false" />
    </Modal>

    <!-- 包装换算 P012 -->
    <Modal :visible="uomModal" :title="`包装换算 - ${uomMaterial?.name || ''}`" :width="680" :footer="noFooter"
           :on-cancel="() => { uomModal = false }">
      <div class="mini-toolbar">
        <Input class="w100" :value="uomForm.fromUom" placeholder="源单位 箱"
               :on-change="(v: string) => (uomForm.fromUom = v)" />
        <Input class="w100" :value="uomForm.toUom" placeholder="目标单位 盒"
               :on-change="(v: string) => (uomForm.toUom = v)" />
        <Input class="w120" :value="String(uomForm.rate)"
               :on-change="(v: string) => (uomForm.rate = Number(v) || 0)" placeholder="换算率" />
        <Button size="small" theme="solid" type="primary" :on-click="saveUom">新增换算</Button>
      </div>
      <Table :columns="uomColumns" :data-source="uomRows" row-key="id" size="small" :pagination="false" />
    </Modal>

    <!-- 导入 P060 -->
    <Modal :visible="importModal" title="导入物资 CSV" :width="520" :footer="noFooter"
           :on-cancel="() => { importModal = false }">
      <p class="io-tip">CSV 列: 编码,名称,规格,单位,品牌,生产厂家,安全库存,库存上限,状态(启用/停用)。按编码 upsert。</p>
      <input type="file" accept=".csv" @change="onImportFile" />
    </Modal>

    <!-- 导入导出任务 P060 -->
    <Modal :visible="jobsModal" title="导入导出任务" :width="760" :footer="noFooter"
           :on-cancel="() => { jobsModal = false }">
      <Table :columns="jobColumns" :data-source="jobRows" row-key="id" size="small"
             :pagination="{ currentPage: jobQuery.page, pageSize: jobQuery.size, total: jobTotal, onChange: (p: number) => { jobQuery.page = p; loadJobs() } }" />
    </Modal>

    <!-- 批量属性修改 P011 -->
    <Modal :visible="batchModal" :title="`批量修改属性 (${selectedIds.length} 项)`" :width="560"
           :on-ok="saveBatchAttrs" :on-cancel="() => { batchModal = false }" :confirm-loading="saving">
      <div class="form-grid">
        <label>高值耗材</label>
        <Select :value="batchForm.isHighValue" :option-list="triOptions" style="width: 100%"
                :on-change="(v: any) => (batchForm.isHighValue = v)" />
        <label>批号管理</label>
        <Select :value="batchForm.batchManaged" :option-list="triOptions" style="width: 100%"
                :on-change="(v: any) => (batchForm.batchManaged = v)" />
        <label>效期管理</label>
        <Select :value="batchForm.expiryManaged" :option-list="triOptions" style="width: 100%"
                :on-change="(v: any) => (batchForm.expiryManaged = v)" />
        <label>UDI 管理</label>
        <Select :value="batchForm.udiManaged" :option-list="triOptions" style="width: 100%"
                :on-change="(v: any) => (batchForm.udiManaged = v)" />
        <label>安全库存</label>
        <Input :value="batchForm.safetyQty" :on-change="(v: string) => (batchForm.safetyQty = v)" placeholder="留空不改" />
      </div>
      <p class="io-tip">仅修改选中项; 关闭批号/效期管理时若存在在库批次将被冲突检测跳过。</p>
      <template v-if="batchConflicts.length">
        <p class="conflict-title">冲突跳过 {{ batchConflicts.length }} 项:</p>
        <div v-for="c in batchConflicts" :key="c.materialId" class="conflict-item">
          {{ c.code }} {{ c.name }} — {{ c.reason }}
        </div>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import {
  batchUpdateMaterialAttrs,
  createMaterial, deleteMaterial, pageMaterials, updateMaterial,
  listMaterialCategories, createMaterialCategory, changeMaterialCategoryStatus,
  listMaterialUoms, createMaterialUom, changeMaterialUomStatus,
} from '@/api/material'
import { downloadCsv, importMaterials, pageIoJobs } from '@/api/system'
import { newIdempotencyKey } from '@/utils/request'
import { PERMISSION } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const uomOptions = ['盒', '瓶', '支', '片', '袋', '个'].map((u) => ({ value: u, label: u }))
const yesNo = [
  { value: 1, label: '是' },
  { value: 0, label: '否' },
]

// 批量选择 (P011)
const selectedIds = ref<number[]>([])
function toggleSelect(id: number) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

const columns = [
  {
    title: '', dataIndex: '_sel', width: 40,
    render: (_: any, r: any) => h('input', {
      type: 'checkbox', checked: selectedIds.value.includes(r.id), onChange: () => toggleSelect(r.id),
    }),
  },
  { title: '物资编码', dataIndex: 'code' },
  { title: '物资名称', dataIndex: 'name' },
  { title: '规格', dataIndex: 'spec' },
  { title: '单位', dataIndex: 'uom' },
  { title: '生产厂家', dataIndex: 'manufacturer' },
  {
    title: '批号/效期', dataIndex: 'batchManaged',
    render: (_: any, r: any) => `${r.batchManaged ? '批号' : '-'} / ${r.expiryManaged ? '效期' : '-'}`,
  },
  {
    title: '状态', dataIndex: 'status',
    render: (v: any) =>
      h(Tag, { color: v === 1 ? 'green' : 'grey' }, () => (v === 1 ? '启用' : '停用')),
  },
  {
    title: '操作', dataIndex: 'actions',
    render: (_: any, r: any) =>
      h('div', { style: 'display:flex;gap:8px' }, [
        userStore.hasPermission(PERMISSION.MATERIAL_MANAGE_EDIT) && h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openEdit(r) }, () => '编辑'),
        userStore.hasPermission(PERMISSION.MATERIAL_UOM_MANAGE_VIEW) && h(Button, { size: 'small', theme: 'light', onClick: () => openUomModal(r) }, () => '换算'),
        userStore.hasPermission(PERMISSION.MATERIAL_MANAGE_EDIT) && h(Button, { size: 'small', theme: 'light', type: 'danger', onClick: () => remove(r) }, () => '删除'),
      ].filter(Boolean)),
  },
]

async function load() {
  loading.value = true
  try {
    const data: any = await pageMaterials({ ...query })
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

const modalVisible = ref(false)
const editingId = ref<number | null>(null)
const emptyForm = () => ({
  code: '', name: '', spec: '', uom: '盒', manufacturer: '', brand: '',
  batchManaged: 1, expiryManaged: 1, safetyQty: 0, maxQty: 0, status: 1,
})
const form = reactive<any>(emptyForm())

function openCreate() {
  editingId.value = null
  Object.assign(form, emptyForm())
  modalVisible.value = true
}

function openEdit(row: any) {
  editingId.value = row.id
  Object.assign(form, row)
  modalVisible.value = true
}

function closeModal() {
  modalVisible.value = false
}

async function save() {
  if (!form.code || !form.name) {
    Toast.warning('物资编码和名称必填')
    return
  }
  saving.value = true
  try {
    // P0: 写操作携带幂等键, 防重复提交
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    if (editingId.value) {
      await updateMaterial(editingId.value, form)
    } else {
      await createMaterial(form)
    }
    Toast.success('已保存')
    closeModal()
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row: any) {
  await deleteMaterial(row.id)
  Toast.success('已删除')
  load()
}

// ==================== 分类管理 P009 ====================
const noFooter: any = null
const categoryModal = ref(false)
const categories = ref<any[]>([])
const categoryForm = reactive({ code: '', name: '', sort: 0 })

const categoryColumns = [
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '排序', dataIndex: 'sort', width: 70 },
  {
    title: '状态', dataIndex: 'status', width: 80,
    render: (v: number) => h(Tag, { color: (v === 1 ? 'green' : 'grey') as any }, () => (v === 1 ? '启用' : '停用')),
  },
  {
    title: '操作', width: 90,
    render: (_: any, r: any) =>
      h(Button, {
        size: 'small', theme: 'light', type: r.status === 1 ? 'warning' : 'primary',
        onClick: () => toggleCategory(r),
      }, () => (r.status === 1 ? '停用' : '启用')),
  },
]

async function openCategoryModal() {
  categoryModal.value = true
  categories.value = (await listMaterialCategories()) as any[]
}

async function saveCategory() {
  if (!categoryForm.code || !categoryForm.name) {
    Toast.warning('分类编码和名称必填')
    return
  }
  await createMaterialCategory({ ...categoryForm })
  Toast.success('分类已创建')
  categoryForm.code = ''
  categoryForm.name = ''
  categoryForm.sort = 0
  categories.value = (await listMaterialCategories()) as any[]
}

async function toggleCategory(row: any) {
  await changeMaterialCategoryStatus(row.id, row.status === 1 ? 0 : 1)
  categories.value = (await listMaterialCategories()) as any[]
}

// ==================== 包装换算 P012 ====================
const uomModal = ref(false)
const uomMaterial = ref<any>(null)
const uomRows = ref<any[]>([])
const uomForm = reactive({ fromUom: '', toUom: '', rate: 1 })

const uomColumns = [
  { title: '源单位', dataIndex: 'fromUom' },
  { title: '目标单位', dataIndex: 'toUom' },
  { title: '换算率', dataIndex: 'rate', render: (v: any) => `1 = ${v}` },
  {
    title: '状态', dataIndex: 'status', width: 80,
    render: (v: number) => h(Tag, { color: (v === 1 ? 'green' : 'grey') as any }, () => (v === 1 ? '启用' : '停用')),
  },
  {
    title: '操作', width: 90,
    render: (_: any, r: any) =>
      h(Button, {
        size: 'small', theme: 'light', type: r.status === 1 ? 'warning' : 'primary',
        onClick: () => toggleUom(r),
      }, () => (r.status === 1 ? '停用' : '启用')),
  },
]

async function openUomModal(row: any) {
  uomMaterial.value = row
  uomModal.value = true
  uomRows.value = (await listMaterialUoms(row.id)) as any[]
}

async function saveUom() {
  if (!uomForm.fromUom || !uomForm.toUom || !uomForm.rate) {
    Toast.warning('源单位/目标单位/换算率必填')
    return
  }
  await createMaterialUom({ materialId: uomMaterial.value.id, ...uomForm })
  Toast.success('换算关系已创建')
  uomForm.fromUom = ''
  uomForm.toUom = ''
  uomForm.rate = 1
  uomRows.value = (await listMaterialUoms(uomMaterial.value.id)) as any[]
}

async function toggleUom(row: any) {
  await changeMaterialUomStatus(row.id, row.status === 1 ? 0 : 1)
  uomRows.value = (await listMaterialUoms(uomMaterial.value.id)) as any[]
}

// ==================== 导入导出 P060 ====================
const importModal = ref(false)
const jobsModal = ref(false)
const jobRows = ref<any[]>([])
const jobTotal = ref(0)
const jobQuery = reactive({ page: 1, size: 10 })

const jobColumns = [
  { title: '任务号', dataIndex: 'jobNo' },
  { title: '类型', dataIndex: 'type', render: (_: any, r: any) => (r.type === 'IMPORT' ? '导入' : '导出') },
  { title: '业务', dataIndex: 'bizType' },
  { title: '总数', dataIndex: 'totalCount' },
  { title: '成功', dataIndex: 'successCount' },
  { title: '失败', dataIndex: 'failCount' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, {
      color: (r.status === 'SUCCESS' ? 'green' : r.status === 'PARTIAL' ? 'orange' : 'red') as any,
    }, () => r.status),
  },
  { title: '时间', dataIndex: 'createdAt' },
  {
    title: '失败明细', dataIndex: 'failDetail',
    render: (_: any, r: any) => (r.failDetail ? h('span', { style: 'color:#dc2626;font-size:12px' }, r.failDetail.slice(0, 80)) : '-'),
  },
]

function openImport() {
  importModal.value = true
}

async function onImportFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    const job: any = await importMaterials(file)
    Toast.success(`导入完成: 共${job.totalCount}条, 成功${job.successCount}, 失败${job.failCount}`)
    importModal.value = false
    load()
  } finally {
    ;(e.target as HTMLInputElement).value = ''
  }
}

async function openJobs() {
  jobsModal.value = true
  await loadJobs()
}

// ==================== 批量属性修改 P011 ====================
const batchModal = ref(false)
const batchConflicts = ref<any[]>([])
const triOptions = [
  { value: '', label: '(不修改)' },
  { value: 1, label: '开启' },
  { value: 0, label: '关闭' },
]
const batchForm = reactive<any>({ isHighValue: '', batchManaged: '', expiryManaged: '', udiManaged: '', safetyQty: '' })

function openBatchAttrs() {
  Object.assign(batchForm, { isHighValue: '', batchManaged: '', expiryManaged: '', udiManaged: '', safetyQty: '' })
  batchConflicts.value = []
  batchModal.value = true
}

async function saveBatchAttrs() {
  const payload: any = { ids: [...selectedIds.value] }
  for (const k of ['isHighValue', 'batchManaged', 'expiryManaged', 'udiManaged']) {
    if (batchForm[k] !== '') payload[k] = Number(batchForm[k])
  }
  if (batchForm.safetyQty !== '') payload.safetyQty = Number(batchForm.safetyQty)
  if (Object.keys(payload).length === 1) {
    Toast.warning('请至少选择一项要修改的属性')
    return
  }
  saving.value = true
  try {
    const res: any = await batchUpdateMaterialAttrs(payload)
    batchConflicts.value = res.conflicts || []
    if (res.updated > 0) {
      Toast.success(`已更新 ${res.updated} 项${res.conflicts?.length ? `, 冲突跳过 ${res.conflicts.length} 项` : ''}`)
    }
    if (!res.conflicts?.length) {
      batchModal.value = false
      selectedIds.value = []
    }
    load()
  } finally {
    saving.value = false
  }
}

async function loadJobs() {
  const res: any = await pageIoJobs({ ...jobQuery })
  jobRows.value = res.records
  jobTotal.value = res.total
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.toolbar-search { width: 240px; }
.form-grid {
  display: grid;
  grid-template-columns: 90px 1fr 90px 1fr;
  gap: 12px;
  align-items: center;
  padding: 8px 0;
}
.form-grid label { font-size: 13px; color: #4b5563; text-align: right; }
.mini-toolbar { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }
.io-tip { font-size: 12px; color: #6b7280; margin: 0 0 12px; }
.conflict-title { font-size: 13px; color: #dc2626; margin: 12px 0 6px; }
.conflict-item { font-size: 12px; color: #dc2626; padding: 2px 0; }
.w100 { width: 100px; }
.w120 { width: 120px; }
.w140 { width: 140px; }
.w160 { width: 160px; }
</style>
