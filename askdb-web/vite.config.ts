import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 开发时前端跑在 5173，接口请求转发给后端，避免跨域
    proxy: { '/api': 'http://localhost:8088' },
  },
  build: {
    // 打包结果直接放进后端的静态资源目录，后端启动后访问 http://localhost:8088/ 即可
    outDir: fileURLToPath(new URL('../src/main/resources/static', import.meta.url)),
    emptyOutDir: true,
    // 图表库单独一个文件（约 170 KB gzip），只在第一次画图时加载，不影响首屏
    chunkSizeWarningLimit: 600,
  },
  test: {
    environment: 'node',
  },
})
