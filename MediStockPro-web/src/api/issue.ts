import request from '@/utils/request'

// ---- 领用申请 ----
export function pageIssueRequests(params: any) {
  return request.get('/issue-requests', { params })
}

export function getIssueRequest(id: number) {
  return request.get(`/issue-requests/${id}`)
}

export function createIssueRequest(data: any) {
  return request.post('/issue-requests', data)
}

export function submitIssueRequest(id: number, version: number) {
  return request.post(`/issue-requests/${id}/submit`, { version })
}

export function approveIssueRequest(id: number, data: { version: number; pass: boolean; items?: any[] }) {
  return request.post(`/issue-requests/${id}/approve`, data)
}

// ---- 拣货任务 ----
export function pagePickTasks(params: any) {
  return request.get('/pick-tasks', { params })
}

export function getPickTask(id: number) {
  return request.get(`/pick-tasks/${id}`)
}

export function completePickTask(id: number, data: { version: number; items: any[] }) {
  return request.post(`/pick-tasks/${id}/complete`, data)
}

export function cancelPickTask(id: number, version: number) {
  return request.post(`/pick-tasks/${id}/cancel`, { version })
}

// ---- 出库单 ----
export function pageIssues(params: any) {
  return request.get('/issues', { params })
}

export function getIssue(id: number) {
  return request.get(`/issues/${id}`)
}

export function confirmIssue(id: number, version: number) {
  return request.post(`/issues/${id}/confirm`, { version })
}

export function signIssue(id: number, version: number, signBy: string) {
  return request.post(`/issues/${id}/sign`, { version, signBy })
}

export function reverseIssue(id: number, version: number, reason: string) {
  return request.post(`/issues/${id}/reverse`, { version, reason })
}
