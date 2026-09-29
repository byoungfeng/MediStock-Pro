import request from '@/utils/request'

export function pageMessages(params: any) {
  return request.get('/messages', { params })
}

export function getUnreadCount() {
  return request.get('/messages/unread-count')
}

export function markMessageRead(id: number) {
  return request.post(`/messages/${id}/read`)
}

export function markAllMessagesRead() {
  return request.post('/messages/read-all')
}
