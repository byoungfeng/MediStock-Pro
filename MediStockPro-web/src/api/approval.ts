import request from '@/utils/request'

export function getApprovalTodo() {
  return request.get('/approvals/todo')
}

export function approvalAction(data: { bizType: string; bizId: number; pass: boolean; reason?: string }) {
  return request.post('/approvals/action', data)
}

export function getApprovalTrail(businessType: string, businessId: number) {
  return request.get('/approvals/trail', { params: { businessType, businessId } })
}
