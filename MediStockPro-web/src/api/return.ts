import request from '@/utils/request'

export function pageReturns(params: any) {
  return request.get('/return-orders', { params })
}

export function getReturn(id: number) {
  return request.get(`/return-orders/${id}`)
}

export function createReturn(data: any) {
  return request.post('/return-orders', data)
}

export function confirmReturn(id: number, version: number) {
  return request.post(`/return-orders/${id}/confirm`, { version })
}

export function cancelReturn(id: number, version: number) {
  return request.post(`/return-orders/${id}/cancel`, { version })
}
