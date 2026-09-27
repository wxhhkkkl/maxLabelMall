import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // 测试环境关闭 Vue 的静态资源 URL 转换：`public/` 下的 /assets/logo.png 在
  // vitest 里会被当成模块导入解析而失败（file:// 路径）。生产构建不受影响 ——
  // public 目录的资源本来就是按绝对 URL 提供的。
  plugins: [vue({ template: { transformAssetUrls: false } })],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.spec.ts'],
    // e2e 由 Playwright 单独运行（需要后端在线），不进单元测试套件
    exclude: ['node_modules/**', 'dist/**', 'e2e/**'],
  },
})
