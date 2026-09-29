import request from '@/utils/request'

export function getInoutSummary(params: any) {
  return request.get('/reports/inout-summary', { params })
}

export function getPurchaseAnalysis(params?: any) {
  return request.get('/reports/purchase', { params })
}

export function getInventoryAnalysis(params?: any) {
  return request.get('/reports/inventory', { params })
}

export function getIssueAnalysis(params?: any) {
  return request.get('/reports/issue', { params })
}

export function getExpiryAnalysis(params?: any) {
  return request.get('/reports/expiry', { params })
}

export function getMovementTrend(params?: any) {
  return request.get('/reports/movement-trend', { params })
}
