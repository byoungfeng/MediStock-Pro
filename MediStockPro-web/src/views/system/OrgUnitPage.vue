<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="hint">集团 / 医院 / 院区 / 科室 四级组织树 (表格按编码排序, 上级列展示隶属)</span>
      <Button v-permission="PERMISSION.ORG_MANAGE_CREATE" type="primary" theme="light" :on-click="() => openEdit(null)">新增组织</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="false" />

    <Modal :visible="modalVisible" :title="editingId ? '编辑组织' : '新增组织'" :width="520"
           :on-ok="save" :on-cancel="closeModal" :confirm-loading="saving">
      <div class="form-grid">
        <label>组织编码 *</label>
        <Input :value="form.code" :disabled="!!editingId"
               :on-change="(v: string) => (form.code = v)" placeholder="唯一编码, 如 DEPT001" />
        <label>名称 *</label>
        <Input :value="form.name" :on-change="(v: string) => (form.name = v)" />
        <label>类型 *</label>
        <Select :value="form.type" :option-list="typeOptions" :on-change="(v: any) => (form.type = v)" />
        <label>上级组织</label>
        <Select :value="form.parentId" :option-list="parentOptions" :on-change="(v: any) => (form.parentId = v)" />
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import { listOrgUnits, createOrgUnit, updateOrgUnit, changeOrgUnitStatus } from '@/api/system'
import { PERMISSION } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const modalVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<any>({ code: '', name: '', type: 'DEPT', parentId: null })

const typeOptions = [
  { value: 'GROUP', label: '集团' },
  { value: 'HOSPITAL', label: '医院' },
  { value: 'CAMPUS', label: '院区' },
  { value: 'DEPT', label: '科室' },
]
const typeMap: Record<string, [string, any]> = {
  GROUP: ['集团', 'purple'],
  HOSPITAL: ['医院', 'blue'],
  CAMPUS: ['院区', 'cyan'],
  DEPT: ['科室', 'green'],
}

const parentOptions = computed(() => [
  { value: null, label: '(无上级)' },
  ...rows.value.filter((r) => r.id !== editingId.value).map((r) => ({ value: r.id, label: r.name })),
])

const columns = [
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  {
    title: '类型', dataIndex: 'type',
    render: (v: string) => {
      const [text, color] = typeMap[v] || [v, 'grey']
      return h(Tag, { color }, () => text)
    },
  },
  {
    title: '上级组织', dataIndex: 'parentId',
    render: (v: number) => rows.value.find((r) => r.id === v)?.name || '-',
  },
  {
    title: '状态', dataIndex: 'status',
    render: (v: number) => h(Tag, { color: (v === 1 ? 'green' : 'grey') as any }, () => (v === 1 ? '启用' : '停用')),
  },
  {
    title: '操作',
    render: (_: any, r: any) =>
      h('div', { style: 'display:flex;gap:8px' }, [
        userStore.hasPermission(PERMISSION.ORG_MANAGE_EDIT) && h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openEdit(r) }, () => '编辑'),
        userStore.hasPermission(PERMISSION.ORG_MANAGE_EDIT) && h(Button, {
          size: 'small', theme: 'light', type: r.status === 1 ? 'warning' : 'primary',
          onClick: () => toggle(r),
        }, () => (r.status === 1 ? '停用' : '启用')),
      ].filter(Boolean)),
  },
]

async function load() {
  loading.value = true
  try {
    rows.value = (await listOrgUnits()) as any[]
  } finally {
    loading.value = false
  }
}

function openEdit(row: any) {
  editingId.value = row?.id ?? null
  Object.assign(form, row ? { code: row.code, name: row.name, type: row.type, parentId: row.parentId } : { code: '', name: '', type: 'DEPT', parentId: null })
  modalVisible.value = true
}
function closeModal() {
  modalVisible.value = false
}

async function save() {
  if (!form.code || !form.name) {
    Toast.warning('编码和名称必填')
    return
  }
  saving.value = true
  try {
    if (editingId.value) await updateOrgUnit(editingId.value, form)
    else await createOrgUnit(form)
    Toast.success('已保存')
    closeModal()
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row: any) {
  await changeOrgUnitStatus(row.id, row.status === 1 ? 0 : 1)
  Toast.success(row.status === 1 ? '已停用' : '已启用')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; justify-content: space-between; }
.hint { font-size: 12px; color: #6b7280; }
.form-grid { display: grid; grid-template-columns: 90px 1fr; gap: 12px; align-items: center; padding: 8px 0; }
.form-grid label { font-size: 13px; color: #4b5563; text-align: right; }
</style>
