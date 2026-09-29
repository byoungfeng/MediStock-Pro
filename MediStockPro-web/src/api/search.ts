import request from '@/utils/request'

export function globalSearch(keyword: string) {
  return request.get('/search', { params: { keyword } })
}

export function queryDocuments(params: any) {
  return request.get('/documents', { params })
}
