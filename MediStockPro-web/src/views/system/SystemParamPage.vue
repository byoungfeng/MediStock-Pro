<template>
  <div class="page-card">
    <div class="seg-tabs">
      <div v-for="t in tabs" :key="t.key" class="seg-tab" :class="{ active: tab === t.key }"
           @click="switchTab(t.key)">{{ t.text }}</div>
    </div>

    <!-- ==================== 系统参数 P058 ==================== -->
    <template v-if="tab === 'params'">
      <div class="toolbar">
        <Button theme="solid" type="primary" :on-click="loadParams">刷新</Button>
        <Button v-permission="PERMISSION.SYSTEM_PARAM_MANAGE" type="primary" theme="light" :on-click="openPublish">发布参数</Button>
      </div>
      <Table :columns="paramColumns" :data-source="paramRows" row-key="paramKey" size="small"
             :loading="loading" :pagination="false" />
    </template>

    <!-- ==================== 数据字典 ==================== -->
    <template v-if="tab === 'dicts'">
      <div class="toolbar">
        <Input class="w200" :value="dictTypeFilter" :on-change="(v: string) => (dictTypeFilter = v)"
               placeholder="字典类型过滤, 如 EXCEPTION_TYPE" @keyup.enter="loadDicts" />
        <Button theme="solid" type="primary" :on-click="loadDicts">查询</Button>
        <Button v-permission="PERMISSION.SYSTEM_PARAM_MANAGE" type="primary" theme="light" :on-click="openDictCreate">新增字典项</Button>
      </div>
      <Table :columns="dictColumns" :data-source="dictRows" row-key="id" size="small"
             :loading="loading" :pagination="false" />
    </template>

    <!-- 发布/编辑参数 -->
    <Modal :visible="publishVisible" :title="publishKey ? `编辑参数: ${publishKey}` : '发布参数'" :width="480"
           :on-ok="savePublish" :on-cancel="() => { publishVisible = false }" :confirm-loading="saving">
      <div class="form-grid">
        <label>参数键 *</label>
        <Input :value="publishForm.paramKey" :on-change="(v: string) => (publishForm.paramKey = v)"
               :disabled="!!publishKey" placeholder="如 expiry.alert.days" />
        <label>参数值 *</label>
        <Input :value="publishForm.paramValue" :on-change="(v: string) => (publishForm.paramValue = v)" />
        <label>名称</label>
        <Input :value="publishForm.paramName" :on-change="(v: string) => (publishForm.paramName = v)" />
      </div>
      <p class="tip">发布即生成新版本并生效; 旧版本保留可回滚。</p>
    </Modal>

    <!-- 版本历史/回滚 -->
    <Modal :visible="versionsVisible" :title="`版本历史: ${versionsKey}`" :width="640" :footer="noFooter"
           :on-cancel="() => { versionsVisible = false }">
      <Table :columns="versionColumns" :data-source="versionRows" row-key="id" size="small" :pagination="false" />
    </Modal>

    <!-- 新增字典项 -->
    <Modal :visible="dictVisible" title="新增字典项" :width="480"
           :on-ok="saveDict" :on-cancel="() => { dictVisible = false }" :confirm-loading="saving">
      <div class="form-grid">
        <label>类型 *</label>
        <Input :value="dictForm.dictType" :on-change="(v: string) => (dictForm.dictType = v)"
               placeholder="如 EXCEPTION_TYPE" />
        <label>编码 *</label>
        <Input :value="dictForm.dictCode" :on-change="(v: string) => (dictForm.dictCode = v)" />
        <label>显示名 *</label>
        <Input :value="dictForm.dictLabel" :on-change="(v: string) => (dictForm.dictLabel = v)" />
        <label>排序</label>
        <Input :value="String(dictForm.sort)" :on-change="(v: string) => (dictForm.sort = Number(v) || 0)" />
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
const noFooter: any = null
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import {
  changeDictStatus, createDict, listDicts, listParamVersions, listSystemParams,
  publishSystemParam, rollbackSystemParam,
} from '@/api/system'
import { PERMISSION } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const tab = ref('params')
const tabs = [
  { key: 'params', text: '系统参数' },
  { key: 'dicts', text: '数据字典' },
]

// ==================== 系统参数 ====================
const paramRows = ref<any[]>([])

const paramColumns = [
  { title: '参数键', dataIndex: 'paramKey' },
  { title: '名称', dataIndex: 'paramName' },
  { title: '当前值', dataIndex: 'paramValue', render: (v: string) => h('b', v) },
  { title: '版本', dataIndex: 'version', render: (v: number) => `v${v}` },
  { title: '更新时间', dataIndex: 'updatedAt' },
  {
    title: '操作',
    render: (_: any, r: any) => h('div', { style: 'display:flex;gap:6px' }, [
      userStore.hasPermission(PERMISSION.SYSTEM_PARAM_MANAGE) && h(Button, { size: 'small', theme: 'light', onClick: () => openEditParam(r) }, () => '编辑'),
      h(Button, { size: 'small', theme: 'light', onClick: () => openVersions(r) }, () => '历史/回滚'),
    ].filter(Boolean)),
  },
]

async function loadParams() {
  loading.value = true
  try {
    paramRows.value = (await listSystemParams()) as any[]
  } finally {
    loading.value = false
  }
}

const publishVisible = ref(false)
const publishKey = ref('')
const publishForm = reactive({ paramKey: '', paramValue: '', paramName: '' })

function openPublish() {
  publishKey.value = ''
  Object.assign(publishForm, { paramKey: '', paramValue: '', paramName: '' })
  publishVisible.value = true
}

function openEditParam(row: any) {
  publishKey.value = row.paramKey
  Object.assign(publishForm, { paramKey: row.paramKey, paramValue: row.paramValue, paramName: row.paramName })
  publishVisible.value = true
}

async function savePublish() {
  if (!publishForm.paramKey || publishForm.paramValue === '') {
    Toast.warning('参数键/值必填')
    return
  }
  saving.value = true
  try {
    await publishSystemParam({ ...publishForm })
    Toast.success('已发布新版本')
    publishVisible.value = false
    loadParams()
  } finally {
    saving.value = false
  }
}

const versionsVisible = ref(false)
const versionsKey = ref('')
const versionRows = ref<any[]>([])

const versionColumns = [
  { title: '版本', dataIndex: 'version', render: (v: number) => `v${v}` },
  { title: '值', dataIndex: 'paramValue' },
  { title: '状态', dataIndex: 'status', render: (v: number) => (v === 1 ? '已发布' : '草稿') },
  { title: '时间', dataIndex: 'updatedAt' },
  {
    title: '操作',
    render: (_: any, r: any) =>
      userStore.hasPermission(PERMISSION.SYSTEM_PARAM_MANAGE) && h(Button, { size: 'small', theme: 'light', onClick: () => doRollback(r) }, () => '回滚到此版本'),
  },
]

async function openVersions(row: any) {
  versionsKey.value = row.paramKey
  versionRows.value = (await listParamVersions(row.paramKey)) as any[]
  versionsVisible.value = true
}

async function doRollback(row: any) {
  await rollbackSystemParam(versionsKey.value, row.version)
  Toast.success(`已回滚到 v${row.version} (生成新版本)`)
  versionsVisible.value = false
  loadParams()
}

// ==================== 数据字典 ====================
const dictRows = ref<any[]>([])
const dictTypeFilter = ref('')

const dictColumns = [
  { title: '类型', dataIndex: 'dictType' },
  { title: '编码', dataIndex: 'dictCode' },
  { title: '显示名', dataIndex: 'dictLabel' },
  { title: '排序', dataIndex: 'sort', width: 70 },
  {
    title: '状态', dataIndex: 'status', width: 90,
    render: (_: any, r: any) => h(Tag, { color: r.status === 1 ? 'green' : 'grey' }, () => (r.status === 1 ? '启用' : '停用')),
  },
  {
    title: '操作', width: 100,
    render: (_: any, r: any) =>
      userStore.hasPermission(PERMISSION.SYSTEM_PARAM_MANAGE) && h(Button, { size: 'small', theme: 'light', onClick: () => toggleDict(r) },
        () => (r.status === 1 ? '停用' : '启用')),
  },
]

async function loadDicts() {
  loading.value = true
  try {
    dictRows.value = (await listDicts(dictTypeFilter.value || undefined)) as any[]
  } finally {
    loading.value = false
  }
}

const dictVisible = ref(false)
const dictForm = reactive({ dictType: '', dictCode: '', dictLabel: '', sort: 0 })

function openDictCreate() {
  Object.assign(dictForm, { dictType: dictTypeFilter.value, dictCode: '', dictLabel: '', sort: 0 })
  dictVisible.value = true
}

async function saveDict() {
  if (!dictForm.dictType || !dictForm.dictCode || !dictForm.dictLabel) {
    Toast.warning('类型/编码/显示名必填')
    return
  }
  saving.value = true
  try {
    await createDict({ ...dictForm })
    Toast.success('字典项已创建')
    dictVisible.value = false
    loadDicts()
  } finally {
    saving.value = false
  }
}

async function toggleDict(row: any) {
  await changeDictStatus(row.id, row.status === 1 ? 0 : 1)
  loadDicts()
}

function switchTab(key: string) {
  tab.value = key
  if (key === 'params') loadParams()
  if (key === 'dicts') loadDicts()
}

onMounted(loadParams)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.seg-tabs { display: flex; gap: 4px; margin-bottom: 14px; border-bottom: 1px solid rgba(17, 24, 39, 0.08); }
.seg-tab { padding: 8px 16px; font-size: 13px; color: #4b5563; cursor: pointer; border-bottom: 2px solid transparent; }
.seg-tab.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 500; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.w200 { width: 200px; }
.form-grid { display: grid; grid-template-columns: 80px 1fr; gap: 10px 12px; align-items: center; }
.form-grid label { font-size: 13px; color: #4b5563; text-align: right; }
.tip { font-size: 12px; color: #6b7280; margin: 10px 0 0; }
</style>
