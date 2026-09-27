<script setup lang="ts">
import { RENDERED } from '@/data/placeholders'

import PendingText from './base/PendingText.vue'

/**
 * 页脚 —— 按设计稿 `www/*.html` 的 `.footer` 结构还原，沿用其 class 名
 * （`.footer-main` / `.brand-col` / `.f-logo` / `.f-desc` / `.f-hotline` /
 * `.f-col` / `.f-divider` / `.f-bottom`），因此 `design.css` 直接生效。
 *
 * 两处**必须**接上真实目的地（FR-043 / FR-048）：
 *   · 软件栏的「模板中心」「更新日志」→ /templates、/changelog（原为 `href="#"`）
 *   · 关于我们栏的四项 → /about、/news、/contact、/jobs（原为 `href="#"`）
 *   · 商城栏的「企业批量采购」→ /enterprise
 *
 * 页脚的客服热线与备案号是**未确认的业务信息**，经 `PendingText` 渲染 ——
 * 业务方在 `placeholders.ts` 里替换，视图无需改动（FR-054）。
 */
</script>

<template>
  <footer class="footer">
    <div class="footer-main">
      <div class="brand-col">
        <div class="f-logo">
          <img class="f-logo-img" src="/assets/logo.png" alt="赋签 MaxLabel" />
          <span class="f-cn">赋签</span>
          <span class="f-en">MaxLabel</span>
        </div>
        <p class="f-desc">
          一站式标签打印解决方案：软件、打印机与耗材，让每一枚标签精准呈现。
        </p>
        <p class="f-hotline">
          <PendingText :value="RENDERED.siteMeta.hotline" /><br />
          <PendingText :value="RENDERED.siteMeta.hotlineHours" />
        </p>
      </div>

      <div class="f-col">
        <h4>商城</h4>
        <RouterLink to="/mall">标签耗材</RouterLink>
        <RouterLink to="/mall">智能打印机</RouterLink>
        <RouterLink to="/mall">软件订阅</RouterLink>
        <RouterLink to="/enterprise">企业批量采购</RouterLink>
      </div>

      <div class="f-col">
        <h4>软件</h4>
        <RouterLink to="/software">功能介绍</RouterLink>
        <RouterLink to="/templates">模板中心</RouterLink>
        <RouterLink to="/support">驱动下载</RouterLink>
        <RouterLink to="/changelog">更新日志</RouterLink>
      </div>

      <div class="f-col">
        <h4>解决方案</h4>
        <RouterLink to="/solutions">仓储物流</RouterLink>
        <RouterLink to="/solutions">生产制造</RouterLink>
        <RouterLink to="/solutions">医药 UDI</RouterLink>
        <RouterLink to="/solutions">跨境电商</RouterLink>
      </div>

      <div class="f-col">
        <h4>关于我们</h4>
        <RouterLink to="/about">公司介绍</RouterLink>
        <RouterLink to="/news">新闻动态</RouterLink>
        <RouterLink to="/contact">联系我们</RouterLink>
        <RouterLink to="/jobs">加入我们</RouterLink>
      </div>
    </div>

    <div class="f-divider"></div>
    <div class="f-bottom">
      <span><PendingText :value="RENDERED.siteMeta.copyright" /></span>
      <span><PendingText :value="RENDERED.siteMeta.icp" /> · 增值电信业务经营许可证</span>
    </div>
  </footer>
</template>
