// flat config —— 与 yudao-ui-admin-vue3 的实际形态一致（eslint 10）
// 只取规则集，不引入 admin-vue3 的其它构建依赖。
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', 'playwright-report/**', 'coverage/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    // ⚠️ 少了 globals，`no-undef` 会把 window / document / setInterval / console /
    // MouseEvent 这类**环境自带的全局**全报成 error（本文件早先就是这样，26 个
    // error 里有 23 个是假的），真正的未定义引用反而被淹没。
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
    },
  },
  {
    files: ['**/*.vue'],
    languageOptions: { parserOptions: { parser: tseslint.parser } },
  },
  {
    rules: {
      // 设计稿保真的页面里允许单词组件名（如 HomeView 之外的工具组件）
      'vue/multi-word-component-names': 'off',
      '@typescript-eslint/no-explicit-any': 'warn',
    },
  },
)
