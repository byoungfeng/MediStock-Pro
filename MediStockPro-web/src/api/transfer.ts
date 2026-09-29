import request from '@/utils/request'

export function pageTransfers(params: any) {
  return request.get('/transfers', { params })
}

export function getTransfer(id: number) {
  return request.get(`/transfers/${id}`)
}

export function createTransfer(data: any) {
  return request.post('/transfers', data)
}

export function submitTransfer(id: number, version: number) {
  return request.post(`/transfers/${id}/submit`, { version })
}

export function approveTransfer(id: number, version: number) {
  return request.post(`/transfers/${id}/approve`, { version })
}

export function shipTransfer(id: number, version: number) {
  return request.post(`/transfers/${id}/ship`, { version })
}

export function receiveTransfer(id: number, data: any) {
  return request.post(`/transfers/${id}/receive`, data)
}

export function cancelTransfer(id: number, version: number) {
  return request.post(`/transfers/${id}/cancel`, { version })
}
