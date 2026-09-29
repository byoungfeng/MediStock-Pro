import request from '@/utils/request'

export function getSummary(params?: { warehouseId?: number }) {
  return request.get('/inventory/summary', { params })
}

export function getDetails(params: any) {
  return request.get('/inventory/details', { params })
}

export function getBatches(params: any) {
  return request.get('/inventory/batches', { params })
}

export function freezeBatch(id: number, freeze: boolean) {
  return request.post(`/inventory/batches/${id}/freeze`, { freeze })
}

export function getTransactions(params: any) {
  return request.get('/inventory/transactions', { params })
}

export function getExpiryAlerts(params?: { warehouseId?: number; days?: number }) {
  return request.get('/inventory/expiry-alerts', { params })
}
