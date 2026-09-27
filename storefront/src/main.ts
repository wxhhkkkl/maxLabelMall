import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import pinia from './store'

// 视觉基线：design.css 即 www/css/style.css 原样搬入，不得改动
import './styles/design.css'
// 新增页面样式 + C1–C8 组件契约（design-new-pages.md §2）
import './styles/store.css'

createApp(App).use(pinia).use(router).mount('#app')
