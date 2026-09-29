<template>
  <div class="page-card">
    <div class="toolbar">
      <Button theme="solid" type="primary" :on-click="load">刷新</Button>
      <Button v-permission="PERMISSION.ORG_MANAGE_CREATE" type="primary" theme="light" :on-click="() => { openEdit(null) }">新建仓库</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading" :pagination="false" />

    <Modal :visible="editVisible" :title="form.id ? '编辑仓库' : '新建仓库'" :width="520"
           :on-ok="save" :on-cancel="() => { editVisible  = false }" :confirm-loading="saving">
      <div class="form-grid">
        <label>编码 *</label>
        <Input :value="form.code" :on-change="(v: string) => (form.code = v)" :disabled="!!form.id" />
        <label>名称 *</label>
        <Input :value="form.name" :on-change="(v: string) => (form.name = v)" />
        <label>类型 *</label>
        <Select :value="form.type" :option-list="typeOptions" :on-change="(v: any) => (form.type = v)" />
        <label>状态</label>
        <Select :value="form.status" :option-list="statusOptions" :on-change="(v: any) => (form.status = v)" />
      </div>
    </Modal>

    <!-- 库位管理 -->
    <Modal :visible="locVisible" :title="`库位管理 - ${locWarehouse?.name || ''}`" :width="720" :footer="noFooter"
           :on-cancel="() => { locVisible = false }">
      <Table :columns="locColumns" :data-source="locRows" row-key="id" size="small" :pagination="false" />
      <div class="loc-form">
        <Input :value="locForm.zone" placeholder="区 (如 A区)" style="width: 110px"
               :on-change="(v: string) => (locForm.zone = v)" />
        <Input :value="locForm.shelf" placeholder="架 (如 01架)" style="width: 110px"
               :on-change="(v: string) => (locForm.shelf = v)" />
        <Input :value="locForm.code" placeholder="库位编码 *" style="width: 130px"
               :on-change="(v: string) => (locForm.code = v)" />
        <Input :value="String(locForm.capacity)" placeholder="容量" style="width: 90px"
               :on-change="(v: string) => (locForm.capacity = Number(v) || 0)" />
        <Button v-permission="PERMISSION.ORG_MANAGE_CREATE" theme="solid" type="primary" :loading="saving" :on-click="saveLocation">添加库位</Button>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
const noFooter: any = null // Semi Modal footer 类型不收 null, 运行时需要
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import {
  changeLocationStatus, createLocation, createWarehouse, deleteWarehouse,
  listLocations, listWarehouses, updateWarehouse,
} from '@/api/system'
import { PERMISSION } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])

const typeOptions = [
  { value: 'DRUG_DEPOT', label: '药库' },
  { value: 'CENTER_PHARMACY', label: '中心药房' },
  { value: 'OUTPATIENT', label: '门诊药房' },
  { value: 'INPATIENT', label: '住院药房' },
  { value: 'DEPARTMENT', label: '科室二级库' },
]
const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
]
function typeText(t: string) {
  return typeOptions.find((o) => o.value === t)?.label || t
}

const columns = [
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '类型', dataIndex: 'type', render: (_: any, r: any) => typeText(r.type) },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: r.status === 1 ? 'green' : 'grey' }, () => (r.status === 1 ? '启用' : '停用')),
  },
  { title: '创建时间', dataIndex: 'createdAt' },
  {
    title: '操作',
    render: (_: any, r: any) => h('div', { style: 'display:flex;gap:4px' }, [
      userStore.hasPermission(PERMISSION.ORG_MANAGE_EDIT) && h(Button, { size: 'small', theme: 'light', onClick: () => openEdit(r) }, () => '编辑'),
      h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openLocations(r) }, () => '库位'),
      h(Button, { size: 'small', theme: 'borderless', type: 'danger', onClick: () => remove(r) }, () => '删除'),
    ].filter(Boolean)),
  },
]

async function load() {
  loading.value = true
  try {
    rows.value = (await listWarehouses()) as any
  } finally {
    loading.value = false
  }
}

const editVisible = ref(false)
const form = reactive<any>({})

function openEdit(row: any) {
  Object.assign(form, row ? { ...row } : { id: null, code: '', name: '', type: 'DRUG_DEPOT', status: 1 })
  editVisible.value = true
}

async function save() {
  if (!form.code || !form.name || !form.type) {
    Toast.warning('编码/名称/类型必填')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateWarehouse(form.id, form)
    } else {
      await createWarehouse(form)
    }
    Toast.success('已保存')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row: any) {
  await deleteWarehouse(row.id)
  Toast.success('已删除')
  load()
}

// ==================== 库位 ====================
const locVisible = ref(false)
const locWarehouse = ref<any>(null)
const locRows = ref<any[]>([])
const locForm = reactive<any>({ zone: '', shelf: '', code: '', capacity: 0 })
const locColumns = [
  { title: '编码', dataIndex: 'code' },
  { title: '区', dataIndex: 'zone' },
  { title: '架', dataIndex: 'shelf' },
  { title: '容量', dataIndex: 'capacity' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: r.status === 1 ? 'green' : 'grey' }, () => (r.status === 1 ? '启用' : '停用')),
  },
  {
    title: '操作',
    render: (_: any, r: any) => userStore.hasPermission(PERMISSION.ORG_MANAGE_EDIT) && h(Button, {
      size: 'small', theme: 'light', type: r.status === 1 ? 'danger' : 'primary',
      onClick: () => toggleLocation(r),
    }, () => (r.status === 1 ? '停用' : '启用')),
  },
]

async function openLocations(row: any) {
  locWarehouse.value = row
  locRows.value = (await listLocations(row.id)) as any
  Object.assign(locForm, { zone: '', shelf: '', code: '', capacity: 0 })
  locVisible.value = true
}

async function saveLocation() {
  if (!locForm.code) {
    Toast.warning('库位编码必填')
    return
  }
  saving.value = true
  try {
    await createLocation({ warehouseId: locWarehouse.value.id, ...locForm, capacity: locForm.capacity || null })
    Toast.success('库位已添加')
    locRows.value = (await listLocations(locWarehouse.value.id)) as any
    locForm.code = ''
  } finally {
    saving.value = false
  }
}

async function toggleLocation(row: any) {
  await changeLocationStatus(row.id, row.status === 1 ? 0 : 1)
  locRows.value = (await listLocations(locWarehouse.value.id)) as any
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.form-grid { display: grid; grid-template-columns: 80px 1fr; gap: 10px 12px; align-items: center; }
.form-grid label { font-size: 13px; color: #4b5563; text-align: right; }
.loc-form { display: flex; gap: 8px; margin-top: 14px; align-items: center; }
</style>
