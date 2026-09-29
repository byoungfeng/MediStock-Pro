import request from '@/utils/request'

export function getStockLevelAlerts(params?: { warehouseId?: number }) {
  return request.get('/alerts/stock-level', { params })
}

export function getArrivalAlerts() {
  return request.get('/alerts/arrival')
}

export function getSupplierPerformance() {
  return request.get('/reports/supplier-performance')
}

// ==================== 预警记录 (持久化) ====================

export function pageAlerts(params: any) {
  return request.get('/alerts', { params })
}

export function handleAlert(id: number, action: 'HANDLED' | 'IGNORED') {
  return request.post(`/alerts/${id}/handle`, { action })
}
