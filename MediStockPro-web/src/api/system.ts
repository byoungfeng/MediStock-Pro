import request from '@/utils/request'

// ==================== 用户 ====================

export function pageUsers(params: any) {
  return request.get('/system/users', { params })
}

export function createUser(data: any) {
  return request.post('/system/users', data)
}

export function assignUserRoles(id: number, roleIds: number[]) {
  return request.put(`/system/users/${id}/roles`, { ids: roleIds })
}

// ==================== 角色 ====================

export function listRoles() {
  return request.get('/system/roles')
}

export function createRole(data: any) {
  return request.post('/system/roles', data)
}

export function assignRolePerms(id: number, permIds: number[]) {
  return request.put(`/system/roles/${id}/perms`, { ids: permIds })
}

// ==================== 权限点 ====================

export function listPermissions() {
  return request.get('/system/permissions')
}

// ==================== 仓库 ====================

export function listWarehouses() {
  return request.get('/warehouses')
}

export function createWarehouse(data: any) {
  return request.post('/warehouses', data)
}

export function updateWarehouse(id: number, data: any) {
  return request.put(`/warehouses/${id}`, data)
}

export function deleteWarehouse(id: number) {
  return request.delete(`/warehouses/${id}`)
}

// ==================== 库位 ====================

export function listLocations(warehouseId?: number) {
  return request.get('/locations', { params: { warehouseId } })
}

export function createLocation(data: any) {
  return request.post('/locations', data)
}

export function updateLocation(id: number, data: any) {
  return request.put(`/locations/${id}`, data)
}

export function changeLocationStatus(id: number, status: number) {
  return request.post(`/locations/${id}/status`, { status })
}

// ==================== 操作日志 ====================

export function pageAuditLogs(params: any) {
  return request.get('/audit-logs', { params })
}

// ==================== 组织机构 P005 ====================

export function listOrgUnits() {
  return request.get('/org-units')
}

/** 启用组织选项 (业务单据选科室, 登录即可) */
export function listOrgUnitOptions() {
  return request.get('/org-units/options')
}

export function createOrgUnit(data: any) {
  return request.post('/org-units', data)
}

export function updateOrgUnit(id: number, data: any) {
  return request.put(`/org-units/${id}`, data)
}

export function changeOrgUnitStatus(id: number, status: number) {
  return request.post(`/org-units/${id}/status`, { status })
}

// ==================== 数据权限 P008 ====================

export function getRoleWarehouses(id: number) {
  return request.get(`/system/roles/${id}/warehouses`)
}

export function assignDataScope(id: number, data: { dataScope: string; warehouseIds: number[] }) {
  return request.put(`/system/roles/${id}/data-scope`, data)
}

// ==================== 导入导出 P060 ====================

export function importMaterials(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/io/import/materials', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function pageIoJobs(params: any) {
  return request.get('/io/jobs', { params })
}

/** 导出 CSV (axios blob 下载, 携带 token) */
export async function downloadCsv(kind: 'materials' | 'suppliers') {
  const resp = await (request as any).get(`/io/export/${kind}`, { responseType: 'blob' })
  const url = URL.createObjectURL(resp instanceof Blob ? resp : new Blob([resp]))
  const a = document.createElement('a')
  a.href = url
  a.download = `${kind}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

// ==================== 系统参数 P058 ====================

export function listSystemParams() {
  return request.get('/system-params')
}

export function listParamVersions(key: string) {
  return request.get(`/system-params/${encodeURIComponent(key)}/versions`)
}

export function publishSystemParam(data: { paramKey: string; paramValue: string; paramName?: string }) {
  return request.put('/system-params', data)
}

export function rollbackSystemParam(key: string, version: number) {
  return request.post(`/system-params/${encodeURIComponent(key)}/rollback`, { version })
}

// ==================== 接口监控 P059 ====================

export function getApiMonitoring() {
  return request.get('/api-monitoring')
}

export function resetApiMonitoring() {
  return request.post('/api-monitoring/reset')
}

// ==================== 数据字典 ====================

export function listDicts(dictType?: string) {
  return request.get('/dicts', { params: { dictType } })
}

export function createDict(data: { dictType: string; dictCode: string; dictLabel: string; sort?: number }) {
  return request.post('/dicts', data)
}

export function updateDict(id: number, data: { dictType: string; dictCode: string; dictLabel: string; sort?: number }) {
  return request.put(`/dicts/${id}`, data)
}

export function changeDictStatus(id: number, status: number) {
  return request.post(`/dicts/${id}/status`, null, { params: { status } })
}
