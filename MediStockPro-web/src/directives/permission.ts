import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-permission: 按权限点隐藏元素
 * 用法:
 *   v-permission="'MATERIAL_MANAGE_CREATE'"      // 单个权限点, 无权限则移除
 *   v-permission="['A','B']"                     // 任意一个匹配即显示
 *   v-permission:disable="'STOCK_SCRAP_APPROVE'" // 修饰符 disable: 仅置灰不禁用 (适合按钮保留布局)
 */
function check(value: string | string[]): boolean {
  const userStore = useUserStore()
  if (typeof value === 'string') return userStore.hasPermission(value)
  if (Array.isArray(value)) return userStore.hasAnyPermission(value)
  return true
}

export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    if (check(binding.value)) return
    if (binding.modifiers.disable) {
      el.setAttribute('disabled', 'disabled')
      el.classList.add('is-forbidden')
      el.style.cursor = 'not-allowed'
      el.style.opacity = '0.5'
      // 屏蔽点击: 在 capture 阶段拦截
      el.addEventListener('click', (e) => e.stopImmediatePropagation(), true)
      return
    }
    el.parentNode?.removeChild(el)
  },
}