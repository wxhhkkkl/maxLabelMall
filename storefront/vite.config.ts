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
    // 不配置 server.proxy：后端 CORS 已放开所有来源（research.md R4），
    // 浏览器直连 VITE_BASE_URL 即可。
    server: {
      port: Number(env.VITE_PORT) || 5173,
      open: true,
    },
    base: env.VITE_BASE_PATH || '/',
    build: {
      outDir: env.VITE_OUT_DIR || 'dist',
      sourcemap: false,
    },
  }
})
