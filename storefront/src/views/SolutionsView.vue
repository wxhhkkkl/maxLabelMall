<script setup lang="ts">
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'

/**
 * 行业方案页 —— 按设计稿 `www/solutions.html` 还原。
 *
 * ⚠️ 设计稿里的**客户案例与成效数字**（如「某头部电商仓储：日均 12,000 单」）
 * 属未经确认的业务声明，经 `PendingText` 渲染（FR-056）。
 */
</script>

<template>
  <section class="sol-hero">
    <h1>十大行业标签解决方案</h1>
    <p class="sw-hero-sub">为每一个行业打磨专属的标签打印与管理方案</p>
  </section>

  <section class="industry">
    <div class="sol-grid">
      <div class="sol-card">
        <div class="sol-icon c1">仓</div>
        <h3>仓储物流</h3>
        <p>标签协同 + 自动箱单 + 称重测体，出入库效率显著提升</p>
      </div>
      <div class="sol-card">
        <div class="sol-icon c2">产</div>
        <h3>生产制造</h3>
        <p>上下游生产链标签协同，批次追溯不出错、不遗漏</p>
      </div>
      <div class="sol-card">
        <div class="sol-icon c3">服</div>
        <h3>服装行业</h3>
        <p>吊牌水洗标快速替换，扫码即可批量改写标签内容</p>
      </div>
      <div class="sol-card">
        <div class="sol-icon c1">医</div>
        <h3>医药 UDI</h3>
        <p>满足 UDI 对标签内容、条码质量与数据留存的要求</p>
      </div>
      <div class="sol-card">
        <div class="sol-icon c2">食</div>
        <h3>食品饮料</h3>
        <p>配料表、效期与批次标签批量生成，合规又高效</p>
      </div>
      <div class="sol-card">
        <div class="sol-icon c3">商</div>
        <h3>零售门店</h3>
        <p>价签、促销标与货架标签快速更换，改价不再手写</p>
      </div>
    </div>
  </section>

  <!-- 客户案例：成效数字是未确认声明（FR-056） -->
  <section class="trust">
    <h2 class="section-title" style="text-align: center">客户案例</h2>
    <div class="quote">
      <p class="quote-text">
        <PendingText :value="RENDERED.inheritedClaims.testimonial.text" />
      </p>
      <p class="quote-author">—— <PendingText :value="RENDERED.inheritedClaims.testimonial.author" /></p>
    </div>
  </section>

  <section class="final-cta">
    <h2 class="cta-title">需要为您的行业定制标签方案？</h2>
    <p class="cta-sub">批量采购与行业定制，可走企业采购通道</p>
    <RouterLink class="cta-btn" to="/enterprise">联系我们</RouterLink>
  </section>
</template>
