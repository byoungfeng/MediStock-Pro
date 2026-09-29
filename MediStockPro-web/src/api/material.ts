import request from '@/utils/request'

// ==================== 物资分类 P009 ====================

export function listMaterialCategories() {
  return request.get('/material-categories')
}

/** 启用分类选项 (库存筛选等业务场景, 登录即可) */
export function listMaterialCategoryOptions() {
  return request.get('/material-categories/options')
}

export function createMaterialCategory(data: any) {
  return request.post('/material-categories', data)
}

export function updateMaterialCategory(id: number, data: any) {
  return request.put(`/material-categories/${id}`, data)
}

export function changeMaterialCategoryStatus(id: number, status: number) {
  return request.post(`/material-categories/${id}/status`, { status })
}

// ==================== 包装换算 P012 ====================

export function listMaterialUoms(materialId?: number) {
  return request.get('/material-uoms', { params: { materialId } })
}

export function createMaterialUom(data: any) {
  return request.post('/material-uoms', data)
}

export function updateMaterialUom(id: number, data: any) {
  return request.put(`/material-uoms/${id}`, data)
}

export function changeMaterialUomStatus(id: number, status: number) {
  return request.post(`/material-uoms/${id}/status`, { status })
}

export interface MaterialQuery {
  page?: number
  size?: number
  keyword?: string
  categoryId?: number
  status?: number
}

export function pageMaterials(params: MaterialQuery) {
  return request.get('/materials', { params })
}

export function createMaterial(data: any) {
  return request.post('/materials', data)
}

export function updateMaterial(id: number, data: any) {
  return request.put(`/materials/${id}`, data)
}

/** 批量属性修改 (P011, 返回 conflicts 冲突明细) */
export function batchUpdateMaterialAttrs(data: {
  ids: number[]; isHighValue?: number; batchManaged?: number; expiryManaged?: number
  udiManaged?: number; safetyQty?: number; maxQty?: number; categoryId?: number
}) {
  return request.post('/materials/batch-attrs', data)
}

export function deleteMaterial(id: number) {
  return request.delete(`/materials/${id}`)
}
