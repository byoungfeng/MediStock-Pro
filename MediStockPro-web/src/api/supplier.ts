import request from '@/utils/request'

export function pageSuppliers(params: any) {
  return request.get('/suppliers', { params })
}

export function getAllSuppliers() {
  return request.get('/suppliers/all')
}

export function createSupplier(data: any) {
  return request.post('/suppliers', data)
}

export function updateSupplier(id: number, data: any) {
  return request.put(`/suppliers/${id}`, data)
}

export function getQualifications(supplierId: number) {
  return request.get(`/suppliers/${supplierId}/qualifications`)
}

export function addQualification(supplierId: number, data: any) {
  return request.post(`/suppliers/${supplierId}/qualifications`, data)
}

// ==================== 供应商物资报价 P015 ====================

export function listSupplierMaterials(supplierId: number) {
  return request.get('/supplier-materials', { params: { supplierId } })
}

export function createSupplierMaterial(data: any) {
  return request.post('/supplier-materials', data)
}

export function updateSupplierMaterial(id: number, data: any) {
  return request.put(`/supplier-materials/${id}`, data)
}

export function changeSupplierMaterialStatus(id: number, status: number) {
  return request.post(`/supplier-materials/${id}/status`, { status })
}
