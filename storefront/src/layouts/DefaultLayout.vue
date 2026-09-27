<script setup lang="ts">
import { RENDERED } from '@/data/placeholders'

import MlToastHost from '@/components/base/MlToastHost.vue'
import PendingText from '@/components/base/PendingText.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import SiteHeader from '@/components/SiteHeader.vue'

/**
 * 全站唯一外壳：通告栏 + 顶栏 + 内容 + 页脚 + 轻提示宿主。
 *
 * **所有页面 MUST 通过本组件挂载** —— 这是 FR-042（顶栏登录态与角标全站一致）
 * 的结构保证：顶栏与页脚只在这里渲染一次，页面自己不得再渲染它们。
 *
 * 通告栏的文案属于「设计稿自带的未确认声明」（`inheritedClaims.promises`），
 * 经 PendingText 渲染 —— 业务方确认后改 placeholders.ts 即可，视图无需改动（FR-056）。
 */
</script>

<template>
  <div class="topbar">
    <PendingText :value="RENDERED.inheritedClaims.promises[0] ?? ''" />
  </div>
  <SiteHeader />
  <main>
    <RouterView />
  </main>
  <SiteFooter />
  <MlToastHost />
</template>
