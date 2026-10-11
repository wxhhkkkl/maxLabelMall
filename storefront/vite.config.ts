import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: Number(env.VITE_PORT) || 5173,
      open: true,
      // 可在 .env.local 指定开发接口；仅开发服务器生效，生产仍使用站点自身接口。
      proxy: env.VITE_DEV_API_TARGET ? {
        '/app-api': { target: env.VITE_DEV_API_TARGET, changeOrigin: true },
      } : undefined,
    },
    base: env.VITE_BASE_PATH || '/',
    build: {
      outDir: env.VITE_OUT_DIR || 'dist',
      sourcemap: false,
    },
  }
})
