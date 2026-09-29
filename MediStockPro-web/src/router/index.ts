import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ROUTE_PERMISSION } from '@/constants/permissions'

// hash 模式: Electron file:// 加载时 HTML5 history 刷新会 404, hash 双端通吃
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/login/LoginPage.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardPage.vue'), meta: { title: '首页仪表盘' } },
        { path: 'approvals', name: 'approvals', component: () => import('@/views/home/ApprovalCenterPage.vue'), meta: { title: '审批中心' } },
        { path: 'messages', name: 'messages', component: () => import('@/views/home/MessagePage.vue'), meta: { title: '消息中心' } },
        { path: 'documents', name: 'documents', component: () => import('@/views/home/DocumentCenterPage.vue'), meta: { title: '单据中心' } },
        { path: 'search', name: 'search', component: () => import('@/views/home/SearchPage.vue'), meta: { title: '全局搜索' } },
        { path: 'stock/overview', name: 'stock-overview', component: () => import('@/views/stock/OverviewPage.vue'), meta: { title: '库存总览' } },
        { path: 'stock/query', name: 'stock-query', component: () => import('@/views/stock/StockQueryPage.vue'), meta: { title: '库存查询' } },
        { path: 'stock/inbound', name: 'stock-inbound', component: () => import('@/views/stock/InboundPage.vue'), meta: { title: '入库管理' } },
        { path: 'stock/outbound', name: 'stock-outbound', component: () => import('@/views/stock/OutboundPage.vue'), meta: { title: '出库管理' } },
        { path: 'stock/transfer', name: 'stock-transfer', component: () => import('@/views/stock/TransferPage.vue'), meta: { title: '库存调拨' } },
        { path: 'stock/count', name: 'stock-count', component: () => import('@/views/stock/CountPage.vue'), meta: { title: '盘点管理' } },
        { path: 'stock/expiry', name: 'stock-expiry', component: () => import('@/views/stock/ExpiryPage.vue'), meta: { title: '效期预警' } },
        { path: 'stock/alerts', name: 'stock-alerts', component: () => import('@/views/stock/AlertCenterPage.vue'), meta: { title: '预警中心' } },
        { path: 'stock/scrap', name: 'stock-scrap', component: () => import('@/views/stock/ScrapPage.vue'), meta: { title: '报废管理' } },
        { path: 'stock/return', name: 'stock-return', component: () => import('@/views/stock/ReturnPage.vue'), meta: { title: '退库管理' } },
        { path: 'issue/requests', name: 'issue-requests', component: () => import('@/views/issue/IssueRequestPage.vue'), meta: { title: '科室领用' } },
        { path: 'purchase/orders', name: 'purchase-orders', component: () => import('@/views/purchase/PurchasePage.vue'), meta: { title: '采购管理' } },
        { path: 'report/inout', name: 'report-inout', component: () => import('@/views/report/InoutSummaryPage.vue'), meta: { title: '收发存汇总' } },
        { path: 'report/center', name: 'report-center', component: () => import('@/views/report/ReportCenterPage.vue'), meta: { title: '分析报表' } },
        { path: 'master/material', name: 'master-material', component: () => import('@/views/master/MaterialPage.vue'), meta: { title: '物资管理' } },
        { path: 'master/warehouse', name: 'master-warehouse', component: () => import('@/views/system/WarehousePage.vue'), meta: { title: '仓库管理' } },
        { path: 'master/supplier', name: 'master-supplier', component: () => import('@/views/system/SupplierPage.vue'), meta: { title: '供应商管理' } },
        { path: 'system/user', name: 'system-user', component: () => import('@/views/system/UserPage.vue'), meta: { title: '用户权限' } },
        { path: 'system/log', name: 'system-log', component: () => import('@/views/system/OperationLogPage.vue'), meta: { title: '操作日志' } },
        { path: 'system/org', name: 'system-org', component: () => import('@/views/system/OrgUnitPage.vue'), meta: { title: '组织机构' } },
        { path: 'system/param', name: 'system-param', component: () => import('@/views/system/SystemParamPage.vue'), meta: { title: '系统参数' } },
        { path: 'system/monitor', name: 'system-monitor', component: () => import('@/views/system/ApiMonitorPage.vue'), meta: { title: '接口监控' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

/**
 * 守卫:
 * 1) 未登录跳 /login
 * 2) 已登录访问 /login 跳首页
 * 3) 路由权限校验: ROUTE_PERMISSION 映射对应权限点
 *    权限不足时回首页并在 query 上带 forbidden=1, 由 MainLayout 弹 Toast 一次
 *    超管 (ADMIN / *:*:*) 直接放行
 */
router.beforeEach((to) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    if (userStore.token && to.name === 'login') return { path: '/' }
    return true
  }

  if (!userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const required = ROUTE_PERMISSION[to.path]
  if (required && !userStore.hasPermission(required)) {
    // 避免循环重定向: 已在首页就不重复加 forbidden
    if (to.path === '/dashboard' || to.path === '/') return true
    return { path: '/dashboard', query: { forbidden: '1', from: to.fullPath } }
  }
  return true
})

export default router