<template>
  <div class="page-card">
    <div class="toolbar">
      <Input class="w160" :value="query.username" placeholder="操作人"
             :on-change="(v: string) => (query.username = v)" @enter-press="load" />
      <Input class="w160" :value="query.module" placeholder="模块 (如 purchase-orders)"
             :on-change="(v: string) => (query.module = v)" @enter-press="load" />
      <Select class="w120" :value="query.status" :option-list="statusOptions" placeholder="结果"
              :on-change="(v: any) => { query.status = v; load() }" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="{ currentPage: query.page, pageSize: query.size, total, onChange: (p: number) => { query.page = p; load() } }" />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Select, Table, Tag } from '@kousum/semi-ui-vue'
import { pageAuditLogs } from '@/api/system'

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, username: '', module: '', status: undefined as any })

const statusOptions = [
  { value: null, label: '全部结果' },
  { value: 1, label: '成功' },
  { value: 0, label: '失败' },
]

const columns = [
  { title: '时间', dataIndex: 'createdAt' },
  { title: '操作人', dataIndex: 'username' },
  { title: '模块', dataIndex: 'module' },
  { title: '动作', dataIndex: 'action' },
  { title: '业务对象', dataIndex: 'businessId', render: (v: string) => v || '-' },
  { title: 'IP', dataIndex: 'ip' },
  {
    title: '结果', dataIndex: 'status',
    render: (v: number) => h(Tag, { color: (v === 1 ? 'green' : 'red') as any }, () => (v === 1 ? '成功' : '失败')),
  },
  { title: 'URI', dataIndex: 'uri', ellipsis: true },
]

async function load() {
  loading.value = true
  try {
    const r: any = await pageAuditLogs({ ...query })
    rows.value = r.records
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
.w160 { width: 160px; }
.w120 { width: 120px; }
</style>
