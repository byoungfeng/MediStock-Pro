import request from '@/utils/request'

// ==================== 采购协议 ====================

export function pageAgreements(params: any) {
  return request.get('/purchase-agreements', { params })
}

export function getAgreement(id: number) {
  return request.get(`/purchase-agreements/${id}`)
}

export function createAgreement(data: any) {
  return request.post('/purchase-agreements', data)
}

export function activateAgreement(id: number) {
  return request.post(`/purchase-agreements/${id}/activate`)
}

export function terminateAgreement(id: number) {
  return request.post(`/purchase-agreements/${id}/terminate`)
}

// ==================== 采购申请 ====================

export function pagePurchaseRequests(params: any) {
  return request.get('/purchase-requests', { params })
}

export function getPurchaseRequest(id: number) {
  return request.get(`/purchase-requests/${id}`)
}

export function createPurchaseRequest(data: any) {
  return request.post('/purchase-requests', data)
}

export function submitPurchaseRequest(id: number, version: number) {
  return request.post(`/purchase-requests/${id}/submit`, { version })
}

export function approvePurchaseRequest(id: number, data: any) {
  return request.post(`/purchase-requests/${id}/approve`, data)
}

export function cancelPurchaseRequest(id: number, version: number) {
  return request.post(`/purchase-requests/${id}/cancel`, { version })
}

export function requestToOrder(id: number, data: any) {
  return request.post(`/purchase-requests/${id}/to-order`, data)
}

// ==================== 采购订单 ====================

export function pagePurchaseOrders(params: any) {
  return request.get('/purchase-orders', { params })
}

export function getPurchaseOrder(id: number) {
  return request.get(`/purchase-orders/${id}`)
}

export function createPurchaseOrder(data: any) {
  return request.post('/purchase-orders', data)
}

export function submitPurchaseOrder(id: number, version: number) {
  return request.post(`/purchase-orders/${id}/submit`, { version })
}

export function approvePurchaseOrder(id: number, version: number) {
  return request.post(`/purchase-orders/${id}/approve`, { version })
}

export function cancelPurchaseOrder(id: number, version: number) {
  return request.post(`/purchase-orders/${id}/cancel`, { version })
}

// ==================== 收货单 ====================

export function pageReceipts(params: any) {
  return request.get('/receipts', { params })
}

export function getReceipt(id: number) {
  return request.get(`/receipts/${id}`)
}

export function registerReceipt(data: any) {
  return request.post('/receipts', data)
}

// ==================== 验收单 ====================

export function pageAcceptances(params: any) {
  return request.get('/acceptances', { params })
}

export function getAcceptance(id: number) {
  return request.get(`/acceptances/${id}`)
}

export function createAcceptanceFromReceipt(receiptId: number) {
  return request.post(`/acceptances/from-receipt/${receiptId}`)
}

export function actAcceptance(id: number, data: any) {
  return request.post(`/acceptances/${id}/act`, data)
}

// ==================== 验收异常 P025 ====================

export function pageAcceptanceExceptions(params: any) {
  return request.get('/acceptance-exceptions', { params })
}

export function createAcceptanceException(data: any) {
  return request.post('/acceptance-exceptions', data)
}

export function handleAcceptanceException(id: number, data: any) {
  return request.post(`/acceptance-exceptions/${id}/handle`, data)
}
