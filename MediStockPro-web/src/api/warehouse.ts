import request from '@/utils/request'

export function getWarehouseTree() {
  return request.get('/warehouses/tree')
}
