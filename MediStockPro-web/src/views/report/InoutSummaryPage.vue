<template>
  <div class="page-card">
    <div class="toolbar">
      <Select class="w160" :value="query.warehouseId" :option-list="warehouseOptions" placeholder="仓库"
              :on-change="(v: any) => { query.warehouseId = v; load() }" />
      <Input class="w140" placeholder="开始日期" :value="query.from"
             :on-change="(v: string) => (query.from = v)" />
      <span class="sep">至</span>
      <Input class="w140" placeholder="结束日期" :value="query.to"
             :on-change="(v: string) => (query.to = v)" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="materialId" size="small" :loading="loading"
           :pagination="false" />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Select, Table, Tag } from '@kousum/semi-ui-vue'
import { getInoutSummary } from '@/api/report'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol, fmtNum } from '@/utils/format'

const loading = ref(false)
const rows = ref<any[]>([])

function fmt(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const today = new Date()
const query = reactive({
  warehouseId: undefined as number | undefined,
  from: fmt(new Date(today.getTime() - 30 * 864e5)),
  to: fmt(today),
})

const warehouseOptions = ref<any[]>([])

const columns = [
  { title: '物资编码', dataIndex: 'materialCode' },
  { title: '物资名称', dataIndex: 'materialName' },
  { title: '规格', dataIndex: 'spec' },
  numCol('期初库存', 'openingQty'),
  { title: '期间入库', dataIndex: 'inQty', align: 'right' as const, render: (v: number) => `+${fmtNum(v)}` },
  { title: '期间出库', dataIndex: 'outQty', align: 'right' as const, render: (v: number) => `-${fmtNum(v)}` },
  { title: '期末库存', dataIndex: 'closingQty', align: 'right' as const, render: (v: number) => h('b', {}, fmtNum(v)) },
]

async function load() {
  loading.value = true
  try {
    rows.value = (await getInoutSummary({
      warehouseId: query.warehouseId ?? undefined,
      from: query.from || undefined,
      to: query.to || undefined,
    })) as any[]
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  load()
  try {
    const wh: any = await getWarehouseTree()
    warehouseOptions.value = [{ value: null, label: '全部仓库' }, ...wh.map((w: any) => ({ value: w.id, label: w.name }))]
  } catch { /* 主数据未就绪 */ }
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.w160 { width: 160px; }
.w140 { width: 140px; }
.sep { color: #6b7280; }
</style>
