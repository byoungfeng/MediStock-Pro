<template>
  <div class="overview">
    <aside class="side">
      <div class="side-block">
        <div class="side-title">库房选择</div>
        <Tree :tree-data="warehouseTree" :default-expand-all="true"
              :on-select="onWarehouseSelect" />
      </div>
      <div class="side-block">
        <div class="side-title">物资分类</div>
        <Tree :tree-data="categoryTree" :default-expand-all="true"
              :on-select="onCategorySelect" />
      </div>
    </aside>

    <main class="main">
      <!-- 指标条: 一个面板, 发丝分隔, 语义色只给风险 -->
      <section class="metrics">
        <div class="metric">
          <div class="metric-label">库存总数</div>
          <div class="metric-value">{{ stats.totalOnHand }}</div>
        </div>
        <div class="metric clickable" @click="go('/stock/query')">
          <div class="metric-label">库存预警</div>
          <div class="metric-value" :class="{ warn: stats.lowStockCount > 0 }">{{ stats.lowStockCount }}</div>
        </div>
        <div class="metric clickable" @click="go('/stock/expiry')">
          <div class="metric-label">近效期药品</div>
          <div class="metric-value" :class="{ danger: stats.nearExpiryCount > 0 }">{{ stats.nearExpiryCount }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">在途数量</div>
          <div class="metric-value">{{ stats.totalInTransit }}</div>
        </div>
      </section>

      <div class="table-card">
        <div class="table-toolbar">
          <span class="table-title">库存明细</span>
          <Input class="toolbar-search" placeholder="搜索物资编码/名称" :value="keyword"
                 :on-change="(v: string) => (keyword = v)" :on-enter="loadDetails" />
        </div>
        <Table :columns="columns" :data-source="rows" row-key="id" size="small" :loading="loading"
               :pagination="{ currentPage: page, pageSize: size, total, onChange: onPageChange }" />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Input, Table, Tag, Tree } from '@kousum/semi-ui-vue'
import { getDetails, getSummary } from '@/api/inventory'
import { getWarehouseTree } from '@/api/warehouse'
import { listMaterialCategoryOptions } from '@/api/material'
import { fmtNum } from '@/utils/format'

const keyword = ref('')
const warehouseId = ref<number | null>(null)
const categoryId = ref<number | null>(null)
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const summary = ref<any>({})

const warehouseTree = ref<any[]>([{ label: '全部库房', key: 'all', value: 'all', children: [] }])
const categoryTree = ref<any[]>([{ label: '全部物资', key: 'all', value: 'all', children: [] }])

/** 平铺分类列表组树 (parentId 为空为根) */
function buildCategoryTree(list: any[]): any[] {
  const nodes = new Map<number, any>()
  for (const c of list) nodes.set(c.id, { label: c.name, key: String(c.id), value: String(c.id), children: [] })
  const roots: any[] = []
  for (const c of list) {
    const node = nodes.get(c.id)
    const parent = c.parentId != null ? nodes.get(c.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  for (const n of nodes.values()) if (!n.children.length) delete n.children
  return roots
}

const stats = computed(() => ({
  totalOnHand: summary.value.totalOnHand ?? '-',
  lowStockCount: summary.value.lowStockCount ?? '-',
  nearExpiryCount: summary.value.nearExpiryCount ?? '-',
  totalInTransit: summary.value.totalInTransit ?? '-',
}))

const router = useRouter()
function go(path: string) {
  router.push(path)
}

function stockState(r: any): { text: string; color: string } {
  if (r.status === 'FROZEN') return { text: '已冻结', color: 'grey' }
  if (r.expiryDate && new Date(r.expiryDate) <= new Date(Date.now() + 90 * 864e5) && r.onHand > 0) {
    return { text: '近效期', color: 'red' }
  }
  if (r.safetyQty != null && r.availableQty < r.safetyQty) return { text: '库存不足', color: 'orange' }
  return { text: '正常', color: 'green' }
}

const columns = [
  { title: '物资编码', dataIndex: 'materialCode' },
  { title: '物资名称', dataIndex: 'materialName' },
  { title: '规格', dataIndex: 'spec' },
  { title: '生产厂家', dataIndex: 'manufacturer' },
  { title: '库房', dataIndex: 'warehouseName' },
  { title: '批号', dataIndex: 'batchNo' },
  { title: '有效期至', dataIndex: 'expiryDate' },
  { title: '可用/在库', dataIndex: 'availableQty', align: 'right' as const, render: (_: any, r: any) => `${fmtNum(r.availableQty)} / ${fmtNum(r.onHand)}` },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => {
      const s = stockState(r)
      return h(Tag, { color: s.color as any }, () => s.text)
    },
  },
]

async function loadSummary() {
  summary.value = await getSummary({ warehouseId: warehouseId.value ?? undefined })
}

async function loadDetails() {
  loading.value = true
  try {
    const data: any = await getDetails({
      page: page.value, size: size.value,
      warehouseId: warehouseId.value ?? undefined,
      categoryId: categoryId.value ?? undefined,
      keyword: keyword.value || undefined,
    })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onWarehouseSelect(key: string) {
  warehouseId.value = key === 'all' ? null : Number(key)
  page.value = 1
  loadSummary()
  loadDetails()
}

function onCategorySelect(key: string) {
  categoryId.value = key === 'all' ? null : Number(key)
  page.value = 1
  loadDetails()
}

function onPageChange(p: number, s: number) {
  page.value = p
  size.value = s
  loadDetails()
}

onMounted(async () => {
  loadSummary()
  loadDetails()
  try {
    const list: any[] = await getWarehouseTree()
    warehouseTree.value = [{
      label: '全部库房', key: 'all', value: 'all',
      children: list.map((w) => ({ label: w.name, key: String(w.id), value: String(w.id) })),
    }]
  } catch { /* 接口未就绪时保留默认树 */ }
  try {
    const cats: any[] = await listMaterialCategoryOptions()
    categoryTree.value = [{ label: '全部物资', key: 'all', value: 'all', children: buildCategoryTree(cats) }]
  } catch { /* 接口未就绪时保留默认树 */ }
})
</script>

<style scoped>
.overview { display: flex; gap: 16px; height: 100%; }
.side { width: 220px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px; }
.side-block { background: #fff; border: 1px solid rgba(17, 24, 39, 0.07); border-radius: 8px; padding: 12px; flex: 1; overflow: auto; }
.side-title { font-size: 13px; font-weight: 600; color: #111827; margin-bottom: 8px; }

.main { flex: 1; display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.metrics {
  display: grid; grid-template-columns: repeat(4, 1fr);
  background: #fff; border: 1px solid rgba(17, 24, 39, 0.07); border-radius: 8px; padding: 4px 0;
}
.metric { padding: 14px 20px; border-left: 1px solid rgba(17, 24, 39, 0.07); }
.metric:first-child { border-left: none; }
.metric.clickable { cursor: pointer; border-radius: 6px; transition: background 0.15s ease; }
.metric.clickable:hover { background: rgba(37, 99, 235, 0.04); }
.metric-label { font-size: 12px; color: #6b7280; margin-bottom: 8px; }
.metric-value { font-size: 24px; font-weight: 600; color: #111827; letter-spacing: -0.02em; font-variant-numeric: tabular-nums; }
.metric-value.warn { color: #d97706; }
.metric-value.danger { color: #dc2626; }

.table-card { background: #fff; border: 1px solid rgba(17, 24, 39, 0.07); border-radius: 8px; padding: 16px; flex: 1; }
.table-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.table-title { font-size: 14px; font-weight: 600; color: #111827; }
.toolbar-search { width: 240px; }
</style>
