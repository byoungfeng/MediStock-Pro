import request from '@/utils/request'

export function getDashboard(params?: { warehouseId?: number }) {
  return request.get('/dashboard', { params })
}
