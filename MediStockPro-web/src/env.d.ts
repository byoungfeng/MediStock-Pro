/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 当前环境: development | production | electron */
  readonly VITE_APP_ENV: string
  /** 接口基础路径 */
  readonly VITE_API_BASE: string
  /** dev server 代理目标 (仅本地开发) */
  readonly VITE_API_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// semi-foundation 以 .ts 源码发布, 其 log.ts 引用 process; 此处补声明避免安装 @types/node
declare var process: { env: Record<string, string | undefined> }