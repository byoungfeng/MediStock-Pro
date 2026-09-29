<template>
  <div class="page-card">
    <div class="toolbar">
      <Select class="w150" :value="query.type" :option-list="typeOptions" placeholder="单据类型"
              :on-change="(v: any) => { query.type = v; load() }" />
      <Input class="w180" :value="query.no" placeholder="单据编号"
             :on-change="(v: string) => (query.no = v)" :on-enter="load" />
      <Input class="w130" :value="query.status" placeholder="状态 (如 PENDING)"
             :on-change="(v: string) => (query.status = v)" :on-enter="load" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="key" size="small" :loading="loading"
           :pagination="{ currentPage: query.page, pageSize: query.size, total, onChange: (p: number) => { query.page = p; load() } }" />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Select, Table, Tag } from '@kousum/semi-ui-vue'
import { queryDocuments } from '@/api/search'

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, type: undefined as any, no: '', status: '' })

const typeOptions = [
  { value: null, label: '全部类型' },
  { value: 'ISSUE_REQUEST', label: '领用申请' },
  { value: 'ISSUE_ORDER', label: '出库单' },
  { value: 'STOCK_INBOUND', label: '入库单' },
  { value: 'TRANSFER', label: '调拨单' },
  { value: 'COUNT_PLAN', label: '盘点计划' },
  { value: 'SCRAP', label: '报废单' },
  { value: 'RETURN', label: '退库单' },
  { value: 'PURCHASE_REQUEST', label: '采购申请' },
  { value: 'PURCHASE_ORDER', label: '采购订单' },
  { value: 'RECEIPT', label: '收货单' },
  { value: 'ACCEPTANCE', label: '验收单' },
  { value: 'PURCHASE_AGREEMENT', label: '采购协议' },
]

const statusColor: Record<string, any> = {
  DRAFT: 'grey', PENDING: 'blue', APPROVED: 'green', REJECTED: 'red',
  PICKING: 'blue', COMPLETED: 'green', CONFIRMED: 'green', SIGNED: 'green',
  REVERSED: 'red', SHIPPED: 'violet', RECEIVED: 'green', ORDERED: 'violet',
  RECEIVING: 'violet', EFFECTIVE: 'green', TERMINATED: 'grey', CANCELLED: 'grey',
  REGISTERED: 'blue',
}

const columns = [
  { title: '时间', dataIndex: 'createdAt', width: 170 },
  {
    title: '类型', dataIndex: 'typeName', width: 110,
    render: (v: string) => h(Tag, { color: 'blue' as any }, () => v),
  },
  { title: '单号', dataIndex: 'no' },
  {
    title: '状态', dataIndex: 'status', width: 110,
    render: (v: string) => h(Tag, { color: statusColor[v] || 'grey' }, () => v),
  },
]

async function load() {
  loading.value = true
  try {
    const r: any = await queryDocuments({ ...query })
    rows.value = r.records.map((d: any) => ({ ...d, key: `${d.type}-${d.id}` }))
    total.value = r.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.w150 { width: 150px; }
.w180 { width: 180px; }
.w130 { width: 130px; }
</style>
