<template>
  <div class="dashboard">
    <!-- 指标条: 一个面板, 发丝分隔, 语义色只给风险 -->
    <section class="panel metrics">
      <div class="metric clickable" @click="go('/report/inout')">
        <span class="metric-icon"><IconHistogram /></span>
        <div class="metric-label">库存总值</div>
        <div class="metric-value">¥{{ fmt(stock.totalValue) }}</div>
      </div>
      <div class="metric clickable" @click="go('/stock/overview')">
        <span class="metric-icon"><IconBox /></span>
        <div class="metric-label">在库总量</div>
        <div class="metric-value">{{ num(stock.totalOnHand) }}</div>
      </div>
      <div class="metric clickable" @click="go('/stock/query')">
        <span class="metric-icon"><IconBox /></span>
        <div class="metric-label">可用 / 锁定 / 在途</div>
        <div class="metric-value trio">
          {{ num(stock.totalAvailable) }}<span class="trio-sep">/</span>{{ num(stock.totalLocked) }}<span class="trio-sep">/</span>{{ num(stock.totalInTransit) }}
        </div>
      </div>
      <div class="metric clickable" @click="go('/stock/query')">
        <span class="metric-icon warn"><IconAlarm /></span>
        <div class="metric-label">低库存批次</div>
        <div class="metric-value" :class="{ warn: stock.lowStockCount > 0 }">{{ num(stock.lowStockCount) }}</div>
      </div>
      <div class="metric clickable" @click="go('/stock/expiry')">
        <span class="metric-icon danger"><IconAlarm /></span>
        <div class="metric-label">近效期批次</div>
        <div class="metric-value" :class="{ danger: stock.nearExpiryCount > 0 }">{{ num(stock.nearExpiryCount) }}</div>
      </div>
    </section>

    <div class="grid">
      <!-- 待办 -->
      <section class="panel">
        <div class="panel-head">
          <h3 class="panel-title">待办事项</h3>
          <span class="panel-meta">{{ openTodoCount }} 项待处理</span>
        </div>
        <div class="todo-list">
          <div v-for="t in todoItems" :key="t.text" class="todo-item" @click="go(t.path)">
            <span class="todo-text">{{ t.text }}</span>
            <span class="todo-right">
              <span class="todo-count" :class="{ zero: !t.count }">{{ t.count }}</span>
              <IconChevronRight class="todo-arrow" />
            </span>
          </div>
        </div>
      </section>

      <!-- 近7日趋势 -->
      <section class="panel">
        <div class="panel-head">
          <h3 class="panel-title">近 7 日出入库趋势</h3>
          <span class="legend"><span class="legend-key in"></span>入库<span class="legend-key out"></span>出库</span>
        </div>
        <div v-if="trend.length" class="trend-chart">
          <div v-for="d in trend" :key="d.day" class="trend-col">
            <div class="trend-bars">
              <div class="bar in" :style="{ height: barH(d.inQty) }" :title="`${d.day} 入库 ${d.inQty}`"></div>
              <div class="bar out" :style="{ height: barH(d.outQty) }" :title="`${d.day} 出库 ${d.outQty}`"></div>
            </div>
            <div class="trend-day">{{ d.day.slice(5) }}</div>
          </div>
        </div>
        <div v-else class="trend-empty">近 7 日无出入库流水</div>
      </section>
    </div>

    <!-- 快捷入口: Linear 式安静操作项, 图标芯片 + 文字 + hover 箭头 -->
    <section class="panel">
      <div class="panel-head"><h3 class="panel-title">快捷入口</h3></div>
      <div class="quick-row">
        <div v-for="q in quickLinks" :key="q.text" class="quick-item" @click="go(q.path)">
          <span class="quick-icon"><component :is="q.icon" /></span>
          <span class="quick-text">{{ q.text }}</span>
          <IconChevronRight class="quick-arrow" />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  IconAlarm, IconBox, IconCart, IconChevronRight, IconHistogram, IconSearchStroked, IconSendStroked,
} from '@kousum/semi-icons-vue'
import { getDashboard } from '@/api/dashboard'

const router = useRouter()
const stock = ref<any>({})
const todo = ref<any>({})
const trend = ref<any[]>([])

const todoItems = computed(() => [
  { text: '待审批申领', count: todo.value.issueRequestPending ?? 0, path: '/approvals' },
  { text: '待审批采购申请', count: todo.value.purchaseRequestPending ?? 0, path: '/approvals' },
  { text: '待审批采购订单', count: todo.value.purchaseOrderPending ?? 0, path: '/approvals' },
  { text: '待确认入库单', count: todo.value.inboundPending ?? 0, path: '/stock/inbound' },
  { text: '待审批调拨单', count: todo.value.transferPending ?? 0, path: '/approvals' },
  { text: '待执行验收', count: todo.value.acceptancePending ?? 0, path: '/purchase/orders' },
])
const openTodoCount = computed(() => todoItems.value.reduce((s, t) => s + (t.count > 0 ? 1 : 0), 0))

const quickLinks = [
  { text: '新建申领', path: '/issue/requests', icon: h(IconSendStroked) },
  { text: '新建采购申请', path: '/purchase/orders', icon: h(IconCart) },
  { text: '库存查询', path: '/stock/query', icon: h(IconSearchStroked) },
  { text: '效期预警', path: '/stock/expiry', icon: h(IconAlarm) },
  { text: '收发存报表', path: '/report/inout', icon: h(IconHistogram) },
]

const maxQty = computed(() => Math.max(1, ...trend.value.flatMap((d) => [Number(d.inQty) || 0, Number(d.outQty) || 0])))
function barH(v: number) {
  return `${Math.max(2, Math.round(((Number(v) || 0) / maxQty.value) * 128))}px`
}
function num(v: any) {
  const n = Number(v)
  return isNaN(n) ? '0' : n.toLocaleString('zh-CN')
}
function fmt(v: any) {
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function go(path: string) {
  router.push(path)
}

onMounted(async () => {
  try {
    const r: any = await getDashboard()
    stock.value = r.stock || {}
    todo.value = r.todo || {}
    trend.value = r.trend || []
  } catch {
    // 无 DASHBOARD_VIEW 或接口异常时落地页保持空数据, 错误提示已由请求拦截器统一处理
  }
})
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 24px; }

.panel {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 14px;
  padding: 22px 26px;
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.07);
}
.panel-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 18px; }
.panel-title { margin: 0; font-size: 15px; font-weight: 700; color: #0f172a; letter-spacing: -0.01em; }
.panel-meta { font-size: 12px; color: #64748b; }

/* ---------- 指标条 ---------- */
.metrics { display: grid; grid-template-columns: repeat(5, 1fr); padding: 6px 0; }
.metric {
  position: relative;
  padding: 18px 28px;
  border-left: 1px solid #eef2f7;
  transition: all 0.18s ease;
}
.metric:first-child { border-left: none; }
.metric.clickable { cursor: pointer; border-radius: 10px; }
.metric.clickable:hover { background: linear-gradient(180deg, rgba(37,99,235,0.04), rgba(37,99,235,0.01)); transform: translateY(-1px); }
.metric-icon {
  position: absolute;
  top: 18px;
  right: 24px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  transition: all 0.18s ease;
}
.metric-icon.warn { background: rgba(217, 119, 6, 0.1); color: #d97706; }
.metric-icon.danger { background: rgba(220, 38, 38, 0.1); color: #dc2626; }
.metric.clickable:hover .metric-icon { transform: scale(1.08); }
.metric-label { font-size: 12px; color: #64748b; margin-bottom: 12px; font-weight: 500; }
.metric-value { font-size: 28px; font-weight: 700; color: #0f172a; letter-spacing: -0.03em; font-variant-numeric: tabular-nums; line-height: 1.2; }
.metric-value.trio { font-size: 20px; font-weight: 600; }
.trio-sep { color: #d1d5db; margin: 0 6px; font-weight: 400; }
.metric-value.warn { color: #d97706; }
.metric-value.danger { color: #dc2626; }

/* ---------- 待办 + 趋势 ---------- */
.grid { display: grid; grid-template-columns: 5fr 7fr; gap: 24px; }
.todo-list { display: flex; flex-direction: column; }
.todo-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 11px 14px;
  margin: 0 -14px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.todo-item:hover { background: rgba(37, 99, 235, 0.05); }
.todo-item:hover .todo-text { color: #2563eb; }
.todo-text { font-size: 13px; color: #374151; font-weight: 500; }
.todo-count {
  font-size: 12px;
  font-weight: 600;
  color: #b45309;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 9999px;
  padding: 2px 10px;
  font-variant-numeric: tabular-nums;
  min-width: 26px;
  text-align: center;
}
.todo-count.zero { color: #6b7280; background: rgba(31, 41, 55, 0.05); border-color: transparent; }
.todo-right { display: inline-flex; align-items: center; gap: 6px; }
.todo-arrow { font-size: 13px; color: #9ca3af; opacity: 0; transform: translateX(-3px); transition: all 0.18s ease; }
.todo-item:hover .todo-arrow { opacity: 1; transform: none; color: #2563eb; }

/* 趋势图 */
.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 190px;
  padding: 10px 6px 0;
  background-image:
    linear-gradient(to top, rgba(17, 24, 39, 0.1), rgba(17, 24, 39, 0.1)),
    repeating-linear-gradient(to top, rgba(17, 24, 39, 0.04) 0, rgba(17, 24, 39, 0.04) 1px, transparent 1px, transparent 38px);
  background-size: 100% 1px, 100% 152px;
  background-position: 0 152px, 0 10px;
  background-repeat: no-repeat;
}
.trend-col { display: flex; flex-direction: column; align-items: center; gap: 10px; flex: 1; }
.trend-bars { display: flex; align-items: flex-end; gap: 6px; height: 152px; }
.bar { width: 18px; border-radius: 4px 4px 0 0; transition: opacity 0.18s ease; }
.bar.in { background: linear-gradient(180deg, #60a5fa 0%, #2563eb 100%); box-shadow: 0 2px 8px rgba(37, 99, 235, 0.22); }
.bar.out { background: linear-gradient(180deg, #e5e7eb 0%, #cbd5e1 100%); }
.trend-col:hover .bar { opacity: 0.82; }
.trend-day { font-size: 11px; color: #6b7280; font-variant-numeric: tabular-nums; }
.trend-empty { height: 190px; display: flex; align-items: center; justify-content: center; color: #9ca3af; font-size: 13px; }
.legend { font-size: 12px; color: #6b7280; display: inline-flex; align-items: center; gap: 6px; }
.legend-key { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }
.legend-key.in { background: #2563eb; }
.legend-key.out { background: #cbd5e1; margin-left: 12px; }

/* ---------- 快捷入口 ---------- */
.quick-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.quick-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid #e8edf5;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.18s ease;
  background: #f8fafc;
}
.quick-item:hover {
  border-color: rgba(37, 99, 235, 0.3);
  background: #fff;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.08);
  transform: translateY(-1px);
}
.quick-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  transition: all 0.18s ease;
}
.quick-item:hover .quick-icon { background: rgba(37, 99, 235, 0.14); }
.quick-text { font-size: 13px; font-weight: 500; color: #374151; flex: 1; white-space: nowrap; }
.quick-item:hover .quick-text { color: #111827; }
.quick-arrow { font-size: 14px; color: #9ca3af; opacity: 0; transform: translateX(-4px); transition: all 0.18s ease; }
.quick-item:hover .quick-arrow { opacity: 1; transform: none; color: #2563eb; }
</style>
