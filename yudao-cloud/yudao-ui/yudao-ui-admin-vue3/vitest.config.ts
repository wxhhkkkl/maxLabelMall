import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

/**
 * 管理端的**单元测试**运行器（本仓库自加，2026-10-11 · 特性 003 / 任务 T246）。
 *
 * 背景：这个前端此前**一个测试文件都没有**，而 `/speckit-analyze` 判定"新功能零测试"
 * 与项目宪法原则 I（适用范围 = 所有端）冲突。故本次接入 **最小** 测试栈 ——
 * 只加跑测试所需的配置与依赖，**不碰既有构建**（`vite.config.ts` 一行未动）。
 *
 * ⚠️ **刻意不用 `globals: true`**：那需要往 `tsconfig.json` 里加 `vitest/globals` 类型，
 * 而 tsconfig 是 `vue-tsc` 构建链的一环 —— 为省几个 import 去动它不划算。
 * 测试文件一律显式 `import { describe, it, expect } from 'vitest'`。
 *
 * ⚠️ **不复制 `vite.config.ts` 的 scss 预处理链与 UnoCSS**：那些是给生产构建用的，
 * 与本特性要测的逻辑无关。下面这两个插件是**必须**的 —— 管理端靠**自动导入**使用
 * `el-*` 组件与 `useMessage` / `useI18n` 这些 hook，不接上它们，任何组件测试都挂不起来
 * （会报 "Failed to resolve component"）。选项与 `build/vite/index.ts` 保持一致，
 * 但 `dts` 关掉 —— 跑测试不该顺手改写 `src/types/*.d.ts`。
 */
export default defineConfig({
  plugins: [
    vue({ template: { transformAssetUrls: false } }),
    AutoImport({
      include: [/\.[tj]sx?$/, /\.vue$/, /\.vue\?vue/],
      imports: [
        'vue',
        'vue-router',
        {
          '@/hooks/web/useI18n': ['useI18n'],
          '@/hooks/web/useMessage': ['useMessage'],
          '@/utils/dict': ['DICT_TYPE'],
        },
      ],
      dts: false,
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      dts: false,
      resolvers: [ElementPlusResolver()],
      globs: ['src/components/**/**.{vue,md}'],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    exclude: ['node_modules/**', 'dist/**'],
    // ⚠️ element-plus 必须**内联**处理：它内部 `import '.../base.css'`，
    // 一旦被当成外部依赖交给 Node 的 ESM 加载器，就会报 `Unknown file extension ".css"`。
    // 内联之后走 Vite 的转换管线，CSS 会被正常吞掉。
    server: {
      deps: {
        inline: ['element-plus'],
      },
    },
  },
})
