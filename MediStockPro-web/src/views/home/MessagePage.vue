<template>
  <div class="page-card">
    <div class="toolbar">
      <Select class="w140" :value="query.type" :option-list="typeOptions" placeholder="类型"
              :on-change="(v: any) => { query.type = v; load() }" />
      <Select class="w140" :value="query.unreadOnly" :option-list="readOptions" placeholder="已读状态"
              :on-change="(v: any) => { query.unreadOnly = v; load() }" />
      <Button theme="solid" type="primary" :on-click="load">查询</Button>
      <Button v-permission="PERMISSION.MESSAGE_MANAGE" theme="light" :on-click="readAll">全部已读</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
           :pagination="{ currentPage: query.page, pageSize: query.size, total, onChange: (p: number) => { query.page = p; load() } }" />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { Button, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import { pageMessages, markMessageRead, markAllMessagesRead } from '@/api/message'
import { PERMISSION } from '@/constants/permissions'

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, type: undefined as any, unreadOnly: undefined as any })

const typeOptions = [
  { value: null, label: '全部类型' },
  { value: 'APPROVAL', label: '审批通知' },
  { value: 'ALERT', label: '预警通知' },
  { value: 'SYSTEM', label: '系统通知' },
]
const readOptions = [
  { value: '', label: '全部' },
  { value: 'UNREAD', label: '仅未读' },
]

function typeTag(type: string) {
  const map: Record<string, [string, any]> = {
    APPROVAL: ['审批', 'blue'],
    ALERT: ['预警', 'orange'],
    SYSTEM: ['系统', 'grey'],
  }
  const [text, color] = map[type] || [type, 'grey']
  return h(Tag, { color }, () => text)
}

const columns = [
  { title: '时间', dataIndex: 'createdAt', width: 170 },
  { title: '类型', dataIndex: 'type', width: 90, render: (v: string) => typeTag(v) },
  {
    title: '标题', dataIndex: 'title',
    render: (v: string, row: any) => h('span', { style: { fontWeight: row.read === 0 ? '600' : '400' } }, v),
  },
  { title: '内容', dataIndex: 'content', ellipsis: true },
  {
    title: '操作', width: 90,
    render: (_: any, row: any) =>
      row.read === 0
        ? h(Button, { size: 'small', theme: 'light', onClick: () => readOne(row) }, () => '已读')
        : h('span', { style: { color: '#b0b8c0' } }, '已读'),
  },
]

async function load() {
  loading.value = true
  try {
    const r: any = await pageMessages({
      ...query,
      unreadOnly: query.unreadOnly === 'UNREAD' ? true : undefined,
    })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function readOne(row: any) {
  await markMessageRead(row.id)
  load()
}

async function readAll() {
  await markAllMessagesRead()
  Toast.success('已全部标记为已读')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.w140 { width: 140px; }
</style>
