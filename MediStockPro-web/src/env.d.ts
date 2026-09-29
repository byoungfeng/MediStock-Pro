/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// semi-foundation 以 .ts 源码发布, 其 log.ts 引用 process; 此处补声明避免安装 @types/node
declare var process: { env: Record<string, string | undefined> }