import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import pinia from './store'
import { migrateLegacyTokens } from './utils/auth'

// 应用启动前先清理/迁移撞过车的旧 localStorage key（管理端与本前台同源，
// 曾共用 ACCESS_TOKEN / REFRESH_TOKEN）。必须在任何请求发出之前跑。
migrateLegacyTokens()

// 视觉基线：design.css 原为设计稿 www/css/style.css 的原样搬入。
// 2026-09-27 起设计稿基线已放开（www/ 已删除），本文件**可以演进** ——
// 它现在是本项目自己的样式基线。
import './styles/design.css'
// 新增页面样式 + C1–C8 组件契约（design-new-pages.md §2）
import './styles/store.css'

createApp(App).use(pinia).use(router).mount('#app')
