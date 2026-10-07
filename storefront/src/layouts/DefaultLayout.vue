<script setup lang="ts">
import MlToastHost from '@/components/base/MlToastHost.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import SiteHeader from '@/components/SiteHeader.vue'

/**
 * 全站唯一外壳：顶栏 + 内容 + 页脚 + 轻提示宿主。
 *
 * **所有页面 MUST 通过本组件挂载** —— 这是 FR-042（顶栏登录态与角标全站一致）
 * 的结构保证：顶栏与页脚只在这里渲染一次，页面自己不得再渲染它们。
 *
 * ⚠️ 顶部通告栏（`inheritedClaims.promises[0]`「新用户注册即享专业版 30 天免费
 * 试用」）**按所有者要求暂时下线** —— 那是软件相关的营销承诺，等软件真正上线后
 * 再开。它是"下线"不是"删除"：`design.css` 里的 `.topbar` 样式保留着，恢复时把
 * 模板里那段注释掉的块加回来即可。
 */
</script>

<template>
  <!-- 通告栏暂时下线。恢复时把下面这块加回来：
       <div class="topbar">
         <PendingText :value="RENDERED.inheritedClaims.promises[0] ?? ''" />
       </div>
       并同时把上面 `PendingText` 与 `RENDERED` 两个 import 加回来。 -->
  <SiteHeader />
  <main>
    <RouterView />
  </main>
  <SiteFooter />
  <MlToastHost />
</template>
