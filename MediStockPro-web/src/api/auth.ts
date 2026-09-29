import request from '@/utils/request'

export function login(data: { username: string; password: string; orgCode?: string }) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function getUserInfo() {
  return request.get('/auth/userinfo')
}
