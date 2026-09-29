import request from '@/utils/request'

export function pageCountPlans(params: any) {
  return request.get('/count-plans', { params })
}

export function getCountPlan(id: number) {
  return request.get(`/count-plans/${id}`)
}

export function createCountPlan(data: any) {
  return request.post('/count-plans', data)
}

export function startCountPlan(id: number, version: number) {
  return request.post(`/count-plans/${id}/start`, { version })
}

export function entryCountPlan(id: number, data: any) {
  return request.post(`/count-plans/${id}/entry`, data)
}

export function confirmCountPlan(id: number, version: number) {
  return request.post(`/count-plans/${id}/confirm`, { version })
}

export function cancelCountPlan(id: number, version: number) {
  return request.post(`/count-plans/${id}/cancel`, { version })
}
