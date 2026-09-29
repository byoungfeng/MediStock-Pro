<template>
  <div class="shell">
    <!-- 左侧栏: 品牌 + 分组导航 -->
    <aside class="sider" :class="{ collapsed }">
      <div class="brand">
        <div class="brand-main" @click="router.push('/dashboard')">
          <span class="brand-mark">
            <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <rect x="9.5" y="4" width="5" height="16" rx="1.5" fill="currentColor" />
              <rect x="4" y="9.5" width="16" height="5" rx="1.5" fill="currentColor" />
            </svg>
          </span>
          <span v-if="!collapsed" class="brand-text">
            <span class="brand-name">MediStock Pro</span>
            <span class="brand-sub">医院进销存系统</span>
          </span>
        </div>
        <button type="button" class="sider-toggle" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
                @click="collapsed = !collapsed">
          <IconIndentLeft v-if="!collapsed" />
          <IconIndentRight v-else />
        </button>
      </div>
      <div class="sider-nav-scroll">
        <Nav mode="vertical" class="sider-nav" :items="navItems" :selected-keys="[route.path]"
             :is-collapsed="collapsed" :open-keys="openKeys" :on-open-change="onOpenChange" :on-select="onNavSelect" />
      </div>
      <div v-if="!collapsed" class="sider-foot">
        <div class="sider-foot-text">MediStock Pro v1.0</div>
      </div>
    </aside>

    <div class="main">
      <!-- 顶栏: 页题 / 搜索 / 消息 / 用户 -->
      <header class="topbar">
        <div class="page-title">{{ pageTitle }}</div>
        <div class="topbar-right">
          <Input class="topbar-search" :value="searchKeyword" placeholder="搜索单号 / 物资 / 批号 / 供应商"
                 :on-change="(v: string) => (searchKeyword = v)" :on-enter="goSearch" />
          <Badge :count="unread" :hidden="!unread" type="danger" :max-count="99">
            <IconButton :icon="bellIcon" theme="borderless" aria-label="消息中心"
                        :on-click="() => router.push('/messages')" />
          </Badge>
          <div class="user-chip">
            <span class="avatar">{{ userInitial }}</span>
            <span class="user-name">{{ userStore.user?.name || '用户' }}</span>
          </div>
          <IconButton :icon="exitIcon" theme="borderless" aria-label="退出登录" :on-click="doLogout" />
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Badge, IconButton, Input, Nav, Toast } from '@kousum/semi-ui-vue'
import {
  IconBellStroked, IconBox, IconCart, IconExit, IconHistogram,
  IconHomeStroked, IconIndentLeft, IconIndentRight, IconSendStroked, IconSettingStroked,
} from '@kousum/semi-icons-vue'
import { logout } from '@/api/auth'
import { getUnreadCount } from '@/api/message'
import { useUserStore } from '@/stores/user'
import { ROUTE_PERMISSION } from '@/constants/permissions'
import { onScan } from '@/utils/scanner'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const bellIcon = h(IconBellStroked)
const exitIcon = h(IconExit)
const collapsed = ref(false)

// 导航模型: 多子项模块为分组, 单子项模块直接为叶子
// permission 字段对齐 ROUTE_PERMISSION, 用于按权限过滤
const menu = [
  {
    key: 'grp-home', text: '工作台', icon: h(IconHomeStroked),
    children: [
      { path: '/dashboard', text: '首页仪表盘' },
      { path: '/approvals', text: '审批中心' },
      { path: '/messages', text: '消息中心' },
      { path: '/documents', text: '单据中心' },
    ],
  },
  {
    key: 'grp-stock', text: '库存管理', icon: h(IconBox),
    children: [
      { path: '/stock/overview', text: '库存总览' },
      { path: '/stock/query', text: '库存查询' },
      { path: '/stock/inbound', text: '入库管理' },
      { path: '/stock/outbound', text: '出库管理' },
      { path: '/stock/transfer', text: '库存调拨' },
      { path: '/stock/count', text: '盘点管理' },
      { path: '/stock/expiry', text: '效期预警' },
      { path: '/stock/alerts', text: '预警中心' },
      { path: '/stock/scrap', text: '报废管理' },
      { path: '/stock/return', text: '退库管理' },
    ],
  },
  { key: '/purchase/orders', text: '采购管理', icon: h(IconCart), children: [] },
  { key: '/issue/requests', text: '科室领用', icon: h(IconSendStroked), children: [] },
  {
    key: 'grp-report', text: '报表中心', icon: h(IconHistogram),
    children: [
      { path: '/report/inout', text: '收发存汇总' },
      { path: '/report/center', text: '分析报表' },
    ],
  },
  {
    key: 'grp-system', text: '系统管理', icon: h(IconSettingStroked),
    children: [
      { path: '/master/material', text: '物资管理' },
      { path: '/master/warehouse', text: '仓库管理' },
      { path: '/master/supplier', text: '供应商管理' },
      { path: '/system/org', text: '组织机构' },
      { path: '/system/user', text: '用户权限' },
      { path: '/system/log', text: '操作日志' },
      { path: '/system/param', text: '系统参数' },
      { path: '/system/monitor', text: '接口监控' },
    ],
  },
]

// 权限过滤: 子项按 ROUTE_PERMISSION 对应权限点检查, 整组无可见子项则整组隐藏
const visibleMenu = computed(() => {
  const check = (path: string) => {
    const code = ROUTE_PERMISSION[path]
    if (!code) return true
    return userStore.hasPermission(code)
  }
  return menu
    .map((m) => {
      if (!m.children.length) {
        // 叶子节点 (单子项模块)
        return check(m.key) ? m : null
      }
      const kids = m.children.filter((c) => check(c.path))
      return kids.length ? { ...m, children: kids } : null
    })
    .filter(Boolean) as typeof menu
})

const navItems = computed(() =>
  visibleMenu.value.map((m) =>
    m.children.length
      ? { itemKey: m.key, text: m.text, icon: m.icon, items: m.children.map((c) => ({ itemKey: c.path, text: c.text })) }
      : { itemKey: m.key, text: m.text, icon: m.icon },
  ),
)

// 受控展开: 初始只展开当前路由所在分组; 手动开合由 onOpenChange 维护, 导航不重置用户选择
const groupOf = (path: string) => visibleMenu.value.find((m) => m.children.some((c) => c.path === path))?.key
const openKeys = ref<string[]>(groupOf(route.path) ? [groupOf(route.path)!] : [])
watch(
  () => route.path,
  (p) => {
    const g = groupOf(p)
    if (g && !openKeys.value.includes(g)) openKeys.value = [...openKeys.value, g]
  },
)
function onOpenChange(data: any) {
  if (Array.isArray(data?.openKeys)) openKeys.value = data.openKeys
}

const titleMap: Record<string, string> = { '/search': '全局搜索' }
for (const m of visibleMenu.value) for (const c of m.children) titleMap[c.path] = `${m.text} / ${c.text}`
for (const m of visibleMenu.value.filter((x) => !x.children.length)) titleMap[m.key] = m.text
const pageTitle = computed(() => titleMap[route.path] || '工作台')

function onNavSelect(data: any) {
  if (typeof data?.itemKey === 'string' && data.itemKey.startsWith('/')) router.push(data.itemKey)
}

const searchKeyword = ref('')
function goSearch() {
  if (searchKeyword.value.trim()) {
    router.push({ path: '/search', query: { keyword: searchKeyword.value.trim() } })
  }
}

// 消息未读数: 挂载加载 + 60s 轮询
const unread = ref(0)
let unreadTimer: ReturnType<typeof setInterval> | undefined
async function loadUnread() {
  try {
    const r: any = await getUnreadCount()
    unread.value = Number(r) || 0
  } catch { /* 静默, 不打扰主流程 */ }
}

const userInitial = computed(() => (userStore.user?.name || '用').slice(0, 1))

// 路由守卫命中 forbidden 时弹一次提示 (避免重定向循环, 仅在首次进入时触发)
let forbiddenToastShown = false
function maybeShowForbiddenToast() {
  if (forbiddenToastShown) return
  const q = route.query.forbidden
  if (q === '1') {
    forbiddenToastShown = true
    Toast.warning('您没有访问该页面的权限')
    // 清除 query 防止刷新重复触发
    router.replace({ path: route.path, query: {} })
  }
}

// 扫码枪 (键盘楔): 全局监听, 扫码命中直达全局搜索
let offScan: (() => void) | undefined
let offUpdate: (() => void) | undefined
onMounted(() => {
  loadUnread()
  unreadTimer = setInterval(loadUnread, 60_000)
  offScan = onScan((code) => {
    Toast.info(`扫码: ${code}`)
    router.push({ path: '/search', query: { keyword: code } })
  })
  // 桌面端自动更新事件
  offUpdate = (window as any).medistockDesktop?.onUpdateEvent?.((p: any) => {
    if (p.type === 'downloaded') Toast.success('新版本已下载，退出后自动安装')
    else if (p.type === 'available') Toast.info('发现新版本，正在后台下载')
  })
  maybeShowForbiddenToast()
})
// 路由变化后也检查一次 (跨页跳转 forbidden)
watch(() => route.fullPath, maybeShowForbiddenToast)
onBeforeUnmount(() => {
  offScan?.()
  offUpdate?.()
  if (unreadTimer) clearInterval(unreadTimer)
})

async function doLogout() {
  try {
    await logout()
  } finally {
    userStore.logout()
    router.push({ name: 'login' })
  }
}
</script>

<style scoped>
.shell { height: 100%; min-height: 100vh; display: flex; background: #eef2f7; overflow: hidden; }

/* ---------- 侧栏: 白色轨道, 与登录页白卡同一浅色体系 ---------- */
.sider {
  width: 240px;
  flex-shrink: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid #e8edf5;
  box-shadow: none;
  transition: width 0.15s cubic-bezier(0.4, 0, 0.2, 1);
}
.sider.collapsed { width: 64px; }
.sider.collapsed .brand {
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 10px 4px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 60px;
  padding: 0 12px 0 16px;
  border-bottom: 1px solid #eef2f7;
}
.brand-main {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.sider.collapsed .brand-main { flex: none; justify-content: center; }
.sider-toggle {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: background 0.15s cubic-bezier(0.4, 0, 0.2, 1), color 0.15s cubic-bezier(0.4, 0, 0.2, 1);
}
.sider-toggle:hover {
  background: #eff4ff;
  color: #2563eb;
}
.sider-toggle :deep(svg) { width: 18px; height: 18px; }
.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
}
.brand-text { display: flex; flex-direction: column; line-height: 1.3; }
.brand-name { font-size: 15px; font-weight: 700; color: #16295e; letter-spacing: -0.01em; }
.brand-sub { font-size: 12px; color: #64748b; }
/* Semi Nav 自带 overflow:hidden + 固定宽, 滚动放在外层容器 */
.sider-nav-scroll {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 12px 8px;
}
.sider-nav { width: 100%; }
.sider-nav :deep(.semi-navigation) {
  width: 100% !important;
  height: auto !important;
  overflow: visible !important;
  display: flex;
  flex-direction: column;
  border-right: none;
  padding-left: 4px;
  padding-right: 4px;
  background: transparent;
}
.sider-nav :deep(.semi-navigation-inner) { height: auto; }
.sider-nav :deep(.semi-navigation-list-wrapper) { overflow: visible; }
.sider-foot {
  flex-shrink: 0;
  padding: 10px 16px;
  border-top: 1px solid #eef2f7;
}
.sider-foot-text { font-size: 11px; color: #94a3b8; }

/* 导航项: 8px 圆角, 白色轨道上选中为浅蓝底 + 医疗蓝蓝字 */
.sider-nav :deep(.semi-navigation-item) {
  border-radius: 8px;
  margin-bottom: 2px;
  height: 38px;
  line-height: 38px;
  font-size: 13px;
  padding-left: 12px;
  color: #475569;
}
.sider-nav :deep(.semi-navigation-sub-title) {
  border-radius: 8px;
  height: 38px;
  line-height: 38px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  padding-left: 12px;
}
.sider-nav :deep(.semi-navigation-item-icon-toggle-right),
.sider-nav :deep(.semi-navigation-item-icon-toggle-left) {
  color: #94a3b8;
}
.sider-nav :deep(.semi-navigation-item-selected) {
  background: #eff4ff !important;
  color: #2563eb !important;
  font-weight: 600;
  box-shadow: none;
}
.sider-nav :deep(.semi-navigation-item:hover),
.sider-nav :deep(.semi-navigation-sub-title:hover) {
  background: #f6f8fc;
  color: #1d4ed8;
}
/* 子项选中时组标题不带底色, 只保留文字层次 */
.sider-nav :deep(.semi-navigation-sub-title-selected) {
  background: transparent !important;
  color: #0f172a;
}
/* 浅色轨道内的滚动条 */
.sider-nav-scroll::-webkit-scrollbar-thumb { background: rgba(15, 23, 42, 0.12); }
.sider-nav-scroll::-webkit-scrollbar-thumb:hover { background: rgba(15, 23, 42, 0.22); }

/* ---------- 顶栏 ---------- */
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.topbar {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e8edf5;
  box-shadow: none;
}
.page-title { font-size: 16px; font-weight: 700; color: #0f172a; letter-spacing: -0.01em; }
.topbar-right { display: flex; align-items: center; gap: 12px; }
.topbar-search { width: 260px; }
.topbar-search :deep(.semi-input-wrapper) {
  height: 38px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}
.topbar-search :deep(.semi-input-wrapper:hover) { background: #fff; border-color: #cbd5e1; }
.topbar-search :deep(.semi-input-wrapper-focus) {
  background: #fff;
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12) !important;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  margin-left: 4px;
  border-radius: 9999px;
  background: #f6f8fc;
  border: 1px solid #e8edf5;
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
}
.user-name { font-size: 13px; color: #334155; font-weight: 500; }

/* 主区: 登录页同款浅蓝灰地面 + 顶部极淡品牌蓝晕, 让白面板"浮"起来 */
.content {
  flex: 1;
  overflow: auto;
  padding: 24px;
  background:
    radial-gradient(1200px 320px at 50% -100px, rgba(37, 99, 235, 0.06), transparent 70%),
    #eef2f7;
}
</style>