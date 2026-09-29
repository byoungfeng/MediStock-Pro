<template>
  <div class="page-card">
    <div class="toolbar">
      <Input class="w220" :value="query.keyword" :on-change="(v: string) => (query.keyword = v)"
             placeholder="编码 / 名称" @keyup.enter="load" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
      <Button v-permission="PERMISSION.SUPPLIER_EDIT" type="primary" theme="light" :on-click="() => { openEdit(null) }">新建供应商</Button>
      <Button v-permission="PERMISSION.DATA_IMPORT_EXPORT" theme="light" :on-click="() => downloadCsv('suppliers')">导出</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="{ currentPage: query.page, pageSize: query.size, total, onChange: (p: number) => { query.page = p; load() } }" />

    <Modal :visible="editVisible" :title="form.id ? '编辑供应商' : '新建供应商'" :width="560"
           :on-ok="save" :on-cancel="() => { editVisible  = false }" :confirm-loading="saving">
      <div class="form-grid">
        <label>编码 *</label>
        <Input :value="form.code" :on-change="(v: string) => (form.code = v)" :disabled="!!form.id" />
        <label>名称 *</label>
        <Input :value="form.name" :on-change="(v: string) => (form.name = v)" />
        <label>信用代码 *</label>
        <Input :value="form.creditCode" :on-change="(v: string) => (form.creditCode = v)" placeholder="统一社会信用代码" />
        <label>联系人</label>
        <Input :value="form.contact" :on-change="(v: string) => (form.contact = v)" />
        <label>电话</label>
        <Input :value="form.phone" :on-change="(v: string) => (form.phone = v)" />
        <label>风险等级</label>
        <Select :value="form.riskLevel" :option-list="riskOptions" :on-change="(v: any) => (form.riskLevel = v)" />
        <label>地址</label>
        <Input :value="form.address" :on-change="(v: string) => (form.address = v)" />
        <label>状态</label>
        <Select :value="form.status" :option-list="statusOptions" :on-change="(v: any) => (form.status = v)" />
      </div>
    </Modal>

    <!-- 资质管理 (P0: 无有效资质禁止新增采购订单) -->
    <Modal :visible="qualVisible" :title="`资质管理 - ${qualSupplier?.name || ''}`" :width="760" :footer="noFooter"
           :on-cancel="() => { qualVisible = false }">
      <Table :columns="qualColumns" :data-source="qualRows" row-key="id" size="small" :pagination="false" />
      <div class="qual-form">
        <Select :value="qualForm.type" :option-list="qualTypeOptions" style="width: 170px"
                :on-change="(v: any) => (qualForm.type = v)" />
        <Input :value="qualForm.certNo" placeholder="证书编号" style="width: 150px"
               :on-change="(v: string) => (qualForm.certNo = v)" />
        <Input :value="qualForm.validFrom" placeholder="生效 YYYY-MM-DD" style="width: 130px"
               :on-change="(v: string) => (qualForm.validFrom = v)" />
        <Input :value="qualForm.validTo" placeholder="截止 YYYY-MM-DD *" style="width: 130px"
               :on-change="(v: string) => (qualForm.validTo = v)" />
        <Button v-permission="PERMISSION.SUPPLIER_EDIT" theme="solid" type="primary" :loading="saving" :on-click="saveQual">登记资质</Button>
      </div>
      <p class="qual-tip">状态按有效期自动推导: 截止日早于今天 → 已过期; 供应商无有效资质时无法新建采购订单。</p>
    </Modal>

    <!-- 物资报价 P015 -->
    <Modal :visible="priceVisible" :title="`物资报价 - ${priceSupplier?.name || ''}`" :width="820" :footer="noFooter"
           :on-cancel="() => { priceVisible = false }">
      <Table :columns="priceColumns" :data-source="priceRows" row-key="id" size="small" :pagination="false" />
      <div class="qual-form">
        <Select :value="priceForm.materialId" :option-list="materialOptions" style="width: 220px"
                placeholder="选择物资" filterable
                :on-change="(v: any) => (priceForm.materialId = v)" />
        <Input :value="String(priceForm.price)" placeholder="报价" style="width: 100px"
               :on-change="(v: string) => (priceForm.price = Number(v) || 0)" />
        <Input :value="String(priceForm.taxRate)" placeholder="税率%" style="width: 80px"
               :on-change="(v: string) => (priceForm.taxRate = Number(v) || 0)" />
        <Input :value="String(priceForm.leadDays)" placeholder="交期(天)" style="width: 90px"
               :on-change="(v: string) => (priceForm.leadDays = Number(v) || 0)" />
        <Input :value="String(priceForm.moq)" placeholder="MOQ" style="width: 70px"
               :on-change="(v: string) => (priceForm.moq = Number(v) || 0)" />
        <Button v-permission="PERMISSION.SUPPLIER_MATERIAL_MANAGE_EDIT" theme="solid" type="primary" :loading="saving" :on-click="savePrice">新增报价</Button>
      </div>
      <p class="qual-tip">同一物资重复登记会被拦截, 调价请直接在列表行内修改后保存。</p>
    </Modal>
  </div>
</template>

<script setup lang="ts">
const noFooter: any = null // Semi Modal footer 类型不收 null, 运行时需要
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import { addQualification, createSupplier, getQualifications, pageSuppliers, updateSupplier } from '@/api/supplier'
import {
  listSupplierMaterials, createSupplierMaterial, updateSupplierMaterial, changeSupplierMaterialStatus,
} from '@/api/supplier'
import { pageMaterials } from '@/api/material'
import { moneyCol } from '@/utils/format'
import { downloadCsv } from '@/api/system'
import { PERMISSION } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const riskOptions = [
  { value: 'A', label: 'A (低风险)' },
  { value: 'B', label: 'B (中风险)' },
  { value: 'C', label: 'C (高风险)' },
]
const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
]

const columns = [
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '信用代码', dataIndex: 'creditCode' },
  { title: '联系人', dataIndex: 'contact' },
  { title: '电话', dataIndex: 'phone' },
  {
    title: '风险', dataIndex: 'riskLevel',
    render: (_: any, r: any) => h(Tag, { color: (({ A: 'green', B: 'orange', C: 'red' } as Record<string, string>)[r.riskLevel]) as any }, () => r.riskLevel),
  },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: r.status === 1 ? 'green' : 'grey' }, () => (r.status === 1 ? '启用' : '停用')),
  },
  {
    title: '操作',
    render: (_: any, r: any) => h('div', { style: 'display:flex;gap:6px' }, [
      userStore.hasPermission(PERMISSION.SUPPLIER_EDIT) && h(Button, { size: 'small', theme: 'light', onClick: () => openEdit(r) }, () => '编辑'),
      userStore.hasPermission(PERMISSION.SUPPLIER_EDIT) && h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openQuals(r) }, () => '资质'),
      userStore.hasPermission(PERMISSION.SUPPLIER_MATERIAL_MANAGE_EDIT) && h(Button, { size: 'small', theme: 'light', onClick: () => openPrices(r) }, () => '报价'),
    ].filter(Boolean)),
  },
]

async function load() {
  loading.value = true
  try {
    const res: any = await pageSuppliers({ ...query })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const editVisible = ref(false)
const form = reactive<any>({})

function openEdit(row: any) {
  Object.assign(form, row
    ? { ...row }
    : { id: null, code: '', name: '', creditCode: '', contact: '', phone: '', address: '', riskLevel: 'C', status: 1 })
  editVisible.value = true
}

async function save() {
  if (!form.code || !form.name || !form.creditCode) {
    Toast.warning('编码/名称/信用代码必填')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateSupplier(form.id, form)
    } else {
      await createSupplier(form)
    }
    Toast.success('已保存')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ==================== 资质 ====================
const qualVisible = ref(false)
const qualSupplier = ref<any>(null)
const qualRows = ref<any[]>([])
const qualForm = reactive<any>({ type: 'OPERATION_LICENSE', certNo: '', validFrom: '', validTo: '' })
const qualTypeOptions = [
  { value: 'BUSINESS_LICENSE', label: '营业执照' },
  { value: 'OPERATION_LICENSE', label: '经营许可证' },
  { value: 'PRODUCTION_LICENSE', label: '生产许可证' },
  { value: 'GSP', label: 'GSP 认证' },
]
function qualTypeText(t: string) {
  return qualTypeOptions.find((o) => o.value === t)?.label || t
}
const qualColumns = [
  { title: '类型', dataIndex: 'type', render: (_: any, r: any) => qualTypeText(r.type) },
  { title: '证书编号', dataIndex: 'certNo' },
  { title: '生效', dataIndex: 'validFrom' },
  { title: '截止', dataIndex: 'validTo' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, {
      color: (({ EFFECTIVE: 'green', EXPIRED: 'red', INVALID: 'grey' } as Record<string, string>)[r.status]) as any,
    }, () => ({ EFFECTIVE: '有效', EXPIRED: '已过期', INVALID: '已作废' }[r.status] || r.status)),
  },
]

async function openQuals(row: any) {
  qualSupplier.value = row
  qualRows.value = await getQualifications(row.id) as any
  Object.assign(qualForm, { type: 'OPERATION_LICENSE', certNo: '', validFrom: '', validTo: '' })
  qualVisible.value = true
}

async function saveQual() {
  if (!qualForm.validTo) {
    Toast.warning('有效期止必填')
    return
  }
  saving.value = true
  try {
    await addQualification(qualSupplier.value.id, { ...qualForm, validFrom: qualForm.validFrom || null })
    Toast.success('资质已登记')
    qualRows.value = await getQualifications(qualSupplier.value.id) as any
  } finally {
    saving.value = false
  }
}

// ==================== 物资报价 P015 ====================
const priceVisible = ref(false)
const priceSupplier = ref<any>(null)
const priceRows = ref<any[]>([])
const materialOptions = ref<any[]>([])
const priceForm = reactive<any>({ editingId: null, materialId: null, price: 0, taxRate: 13, leadDays: 7, moq: 1 })

const priceColumns = [
  { title: '物资编码', dataIndex: 'materialCode' },
  { title: '物资名称', dataIndex: 'materialName' },
  { title: '规格', dataIndex: 'spec' },
  moneyCol('报价', 'price'),
  { title: '税率%', dataIndex: 'taxRate' },
  { title: '交期(天)', dataIndex: 'leadDays' },
  { title: 'MOQ', dataIndex: 'moq' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: r.status === 1 ? 'green' : 'grey' }, () => (r.status === 1 ? '启用' : '停用')),
  },
  {
    title: '操作',
    render: (_: any, r: any) => h('div', { style: 'display:flex;gap:6px' }, [
      userStore.hasPermission(PERMISSION.SUPPLIER_MATERIAL_MANAGE_EDIT) && h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => editPrice(r) }, () => '调价'),
      userStore.hasPermission(PERMISSION.SUPPLIER_MATERIAL_MANAGE_EDIT) && h(Button, {
        size: 'small', theme: 'light', type: r.status === 1 ? 'warning' : 'primary',
        onClick: () => togglePrice(r),
      }, () => (r.status === 1 ? '停用' : '启用')),
    ].filter(Boolean)),
  },
]

async function openPrices(row: any) {
  priceSupplier.value = row
  priceVisible.value = true
  Object.assign(priceForm, { editingId: null, materialId: null, price: 0, taxRate: 13, leadDays: 7, moq: 1 })
  priceRows.value = await listSupplierMaterials(row.id) as any
  if (!materialOptions.value.length) {
    const mats: any = await pageMaterials({ page: 1, size: 1000, keyword: '' })
    materialOptions.value = mats.records.map((m: any) => ({ value: m.id, label: `${m.code} ${m.name}` }))
  }
}

function editPrice(row: any) {
  Object.assign(priceForm, {
    editingId: row.id, materialId: row.materialId,
    price: row.price ?? 0, taxRate: row.taxRate ?? 13, leadDays: row.leadDays ?? 7, moq: row.moq ?? 1,
  })
}

async function savePrice() {
  saving.value = true
  try {
    if (priceForm.editingId) {
      await updateSupplierMaterial(priceForm.editingId, {
        price: priceForm.price, taxRate: priceForm.taxRate, leadDays: priceForm.leadDays, moq: priceForm.moq,
      })
      Toast.success('报价已更新')
    } else {
      if (!priceForm.materialId) {
        Toast.warning('请选择物资')
        return
      }
      await createSupplierMaterial({
        supplierId: priceSupplier.value.id, materialId: priceForm.materialId,
        price: priceForm.price, taxRate: priceForm.taxRate, leadDays: priceForm.leadDays, moq: priceForm.moq,
      })
      Toast.success('报价已登记')
    }
    Object.assign(priceForm, { editingId: null, materialId: null, price: 0, taxRate: 13, leadDays: 7, moq: 1 })
    priceRows.value = await listSupplierMaterials(priceSupplier.value.id) as any
  } finally {
    saving.value = false
  }
}

async function togglePrice(row: any) {
  await changeSupplierMaterialStatus(row.id, row.status === 1 ? 0 : 1)
  priceRows.value = await listSupplierMaterials(priceSupplier.value.id) as any
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.w220 { width: 220px; }
.form-grid { display: grid; grid-template-columns: 90px 1fr; gap: 10px 12px; align-items: center; }
.form-grid label { font-size: 13px; color: #4b5563; text-align: right; }
.qual-form { display: flex; gap: 8px; margin-top: 14px; align-items: center; }
.qual-tip { margin: 10px 0 0; font-size: 12px; color: #6b7280; }
</style>
