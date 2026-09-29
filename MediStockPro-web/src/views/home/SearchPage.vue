<template>
  <div class="page-card">
    <div class="toolbar">
      <Input class="search-input" :value="keyword" placeholder="单号 / 物资编码 / 名称 / 批号 / 供应商"
             :on-change="(v: string) => (keyword = v)" :on-enter="load" />
      <Button theme="solid" type="primary" :on-click="load">搜索</Button>
    </div>

    <template v-if="searched">
      <div class="group" v-if="result.documents?.length">
        <div class="group-title">单据 ({{ result.documents.length }})</div>
        <div v-for="d in result.documents" :key="`${d.type}-${d.id}`" class="hit" @click="goDoc(d)">
          <Tag color="blue" size="small">{{ d.typeName }}</Tag>
          <span class="hit-no">{{ d.no }}</span>
          <span class="hit-status">{{ d.status }}</span>
        </div>
      </div>
      <div class="group" v-if="result.materials?.length">
        <div class="group-title">物资 ({{ result.materials.length }})</div>
        <div v-for="m in result.materials" :key="m.id" class="hit" @click="go('/master/material')">
          <Tag color="green" size="small">物资</Tag>
          <span class="hit-no">{{ m.code }}</span>
          <span>{{ m.name }}</span>
          <span class="hit-status">{{ m.spec }}</span>
        </div>
      </div>
      <div class="group" v-if="result.suppliers?.length">
        <div class="group-title">供应商 ({{ result.suppliers.length }})</div>
        <div v-for="s in result.suppliers" :key="s.id" class="hit" @click="go('/master/supplier')">
          <Tag color="purple" size="small">供应商</Tag>
          <span class="hit-no">{{ s.code }}</span>
          <span>{{ s.name }}</span>
        </div>
      </div>
      <div class="group" v-if="result.batches?.length">
        <div class="group-title">批次 ({{ result.batches.length }})</div>
        <div v-for="b in result.batches" :key="b.id" class="hit" @click="go('/stock/query')">
          <Tag color="orange" size="small">批次</Tag>
          <span class="hit-no">{{ b.batchNo }}</span>
          <span class="hit-status">在库 {{ b.onHand }}</span>
        </div>
      </div>
      <div v-if="isEmpty" class="empty">未找到匹配「{{ lastKeyword }}」的内容</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button, Input, Tag } from '@kousum/semi-ui-vue'
import { globalSearch } from '@/api/search'

const route = useRoute()
const router = useRouter()
const keyword = ref((route.query.keyword as string) || '')
const lastKeyword = ref('')
const searched = ref(false)
const result = ref<any>({})

const isEmpty = computed(() =>
  !result.value.documents?.length && !result.value.materials?.length
  && !result.value.suppliers?.length && !result.value.batches?.length)

const docRoutes: Record<string, string> = {
  ISSUE_REQUEST: '/issue/requests',
  ISSUE_ORDER: '/stock/outbound',
  STOCK_INBOUND: '/stock/inbound',
  TRANSFER: '/stock/transfer',
  COUNT_PLAN: '/stock/count',
  SCRAP: '/stock/scrap',
  RETURN: '/stock/return',
  PURCHASE_REQUEST: '/purchase/orders',
  PURCHASE_ORDER: '/purchase/orders',
  RECEIPT: '/purchase/orders',
  ACCEPTANCE: '/purchase/orders',
  PURCHASE_AGREEMENT: '/purchase/orders',
}

async function load() {
  if (!keyword.value.trim()) return
  lastKeyword.value = keyword.value
  result.value = await globalSearch(keyword.value.trim())
  searched.value = true
}

function go(path: string) {
  router.push(path)
}
function goDoc(d: any) {
  router.push(docRoutes[d.type] || '/documents')
}

onMounted(() => {
  if (keyword.value) load()
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.search-input { width: 380px; }
.group { margin-bottom: 18px; }
.group-title { font-size: 13px; font-weight: 600; color: #111827; margin-bottom: 8px; }
.hit { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 13px; }
.hit:hover { background: #f4f9fa; }
.hit-no { font-weight: 600; color: #111827; }
.hit-status { color: #6b7280; font-size: 12px; }
.empty { color: #6b7280; font-size: 13px; padding: 24px; text-align: center; }
</style>
