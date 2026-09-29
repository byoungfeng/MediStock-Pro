import request from '@/utils/request'

export function pageInbounds(params: any) {
  return request.get('/stock-inbounds', { params })
}

export function getInbound(id: number) {
  return request.get(`/stock-inbounds/${id}`)
}

export function createInbound(data: any) {
  return request.post('/stock-inbounds', data)
}

export function confirmInbound(id: number, version: number) {
  return request.post(`/stock-inbounds/${id}/confirm`, { version })
}
