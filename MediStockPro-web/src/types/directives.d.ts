import type { Directive } from 'vue'

// 全局自定义指令模板类型增强 (文件含 import, declare module 即模块增强而非替换)
declare module 'vue' {
  interface GlobalDirectives {
    vPermission: Directive<HTMLElement, string | string[]>
  }
}