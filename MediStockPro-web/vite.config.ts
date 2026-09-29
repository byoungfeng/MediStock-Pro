import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    // 相对基座: Electron file:// 加载时绝对路径 /assets 会解析到盘符根目录
    base: './',
    plugins: [vue()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
    },
    build: {
      // 开发包输出 dist-dev, 生产包/桌面端输出 dist, 避免互相覆盖
      outDir: mode === 'development' ? 'dist-dev' : 'dist',
      // 仅本地开发包保留 sourcemap 便于调试
      sourcemap: mode === 'development',
      chunkSizeWarningLimit: 1500
    },
    server: {
      port: 5173,
      proxy: {
        '/api': { target: env.VITE_API_TARGET || 'http://localhost:8081', changeOrigin: true }
      }
    }
  }
})
