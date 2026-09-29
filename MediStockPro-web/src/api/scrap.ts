import request from '@/utils/request'

export function pageScraps(params: any) {
  return request.get('/scrap-orders', { params })
}

export function getScrap(id: number) {
  return request.get(`/scrap-orders/${id}`)
}

export function createScrap(data: any) {
  return request.post('/scrap-orders', data)
}

export function approveScrap(id: number, version: number) {
  return request.post(`/scrap-orders/${id}/approve`, { version })
}

export function cancelScrap(id: number, version: number) {
  return request.post(`/scrap-orders/${id}/cancel`, { version })
}
