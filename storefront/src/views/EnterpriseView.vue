<script setup lang="ts">
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'
import { telHref } from '@/utils/tel'

/**
 * 企业采购落地页 —— 按 design-new-pages.md §3.11 实现（无设计稿）。
 *
 * ⚠️ 本页**只负责获客与联系**，不产生任何线上交易能力（FR-049）：企业客户的
 * 实际交易与结算在线上商城之外完成。所以这里**没有**下单入口、**没有**授信或
 * 月结申请按钮、**没有**任何提交表单 —— 只有权益说明与联系途径。
 *
 * ⚠️ 权益文案、热线号码、企业微信二维码**都由业务方提供**（§4）。在拿到之前一律
 * 走 `placeholders.ts` 的占位文案（FR-054），**不得编造**看似真实的号码或承诺。
 */
const ent = RENDERED.enterprise
</script>

<template>
  <section class="sw-hero ent-hero">
    <div class="sw-hero-left">
      <h1><PendingText :value="ent.title" /></h1>
      <p class="sw-hero-sub"><PendingText :value="ent.subtitle" /></p>
      <div class="hero-cta">
        <RouterLink class="btn-cyan" to="/mall">浏览商品</RouterLink>
        <RouterLink class="btn-ghost-white" to="/contact">联系我们</RouterLink>
      </div>
    </div>
  </section>

  <section class="features">
    <div class="section-head ent-head">
      <h2 class="section-title">企业采购权益</h2>
    </div>
    <div class="grid-3 ent-benefits">
      <div v-for="b in ent.benefits" :key="b.name" class="f-card">
        <h3><PendingText :value="b.name" /></h3>
        <p><PendingText :value="b.desc" /></p>
      </div>
    </div>
  </section>

  <!-- 联系销售：只有联系途径，没有任何交易入口 -->
  <section class="ent-contact">
    <div class="section-head ent-head">
      <h2 class="section-title">联系销售</h2>
      <p class="section-desc">批量采购、账期与定制需求，请通过以下方式与我们联系</p>
    </div>

    <div class="grid-3 ent-contact-grid">
      <div class="contact-card">
        <span class="contact-label">企业采购热线</span>
        <!-- 业务方给出真实号码前不给 `tel:` 链接：占位拼出来的号码打不通，
             还会让人以为站点已经在提供该服务（FR-054） -->
        <a v-if="telHref(ent.hotline)" class="contact-val ent-hotline" :href="telHref(ent.hotline)">
          <PendingText :value="ent.hotline" />
        </a>
        <span v-else class="contact-val ent-hotline">
          <PendingText :value="ent.hotline" />
        </span>
        <span class="contact-note">工作日 9:00 - 21:00</span>
      </div>

      <div class="contact-card">
        <span class="contact-label">企业微信</span>
        <div class="ph ent-qr">
          <span class="ent-qr-text ent-wechat"><PendingText :value="ent.wechatQr" /></span>
        </div>
        <span class="contact-note">扫码添加专属客户经理</span>
      </div>

      <div class="contact-card">
        <span class="contact-label">其他方式</span>
        <RouterLink class="contact-val ent-link" to="/contact">查看全部联系方式</RouterLink>
        <span class="contact-note">客服、售后与媒体联络</span>
      </div>
    </div>
  </section>

  <div class="ent-footer">
    <RouterLink class="cta-btn" to="/mall">去商城看看</RouterLink>
  </div>
</template>

<style scoped>
.ent-hero {
  height: 420px;
}
.ent-head {
  flex-direction: column;
  gap: 10px;
  text-align: center;
}
.ent-benefits {
  width: 100%;
}
.ent-contact {
  padding: 80px 100px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 40px;
}
.ent-contact-grid {
  width: 100%;
  max-width: 1100px;
}
.ent-qr {
  width: 140px;
  height: 140px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  text-align: center;
}
.ent-qr-text {
  font-size: 12px;
  line-height: 1.5;
}
.ent-link {
  color: #ffffff;
  text-decoration: underline;
}
.ent-footer {
  display: flex;
  justify-content: center;
  padding: 0 24px 80px;
}
@media (max-width: 1100px) {
  .ent-hero {
    height: auto;
    padding: 60px 24px;
  }
  .ent-contact {
    padding: 56px 24px;
  }
  .ent-contact-grid {
    grid-template-columns: 1fr;
  }
}
</style>
