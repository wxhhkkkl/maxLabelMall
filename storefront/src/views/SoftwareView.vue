<script setup lang="ts">
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'

/**
 * 标签软件页 —— 按设计稿 `www/software.html` 还原，沿用其 class 名。
 *
 * ⚠️ 三个版本档的价格与能力声明（¥0 / ¥199/年 / ¥899/年、「200 次打印/月」、
 * 「无限次打印 · 全部 2,000+ 模板」、「私有化部署」等）**都是对外的报价与承诺**，
 * 全部经 `PendingText` 渲染（FR-056）。其中「2,000+ 模板」正是 FR-055 禁止
 * 未经确认就沿用的数字。业务方在 `placeholders.ts` 里确认后替换。
 */
const plans = RENDERED.softwarePlans
</script>

<template>
  <section class="sw-hero">
    <div class="sw-hero-left">
      <h1>MaxLabel 标签编辑软件</h1>
      <p class="sw-hero-sub">
        2,000+ 行业模板一键套用，Excel 批量打印，
        Windows / macOS / iOS / Android 全平台云同步
      </p>
      <div class="hero-cta">
        <RouterLink class="btn-cyan" to="/templates">浏览模板中心</RouterLink>
        <RouterLink class="btn-ghost-white" to="/support">查看使用文档</RouterLink>
      </div>
    </div>
  </section>

  <section class="features">
    <div>
      <h2 class="section-title" style="text-align: center">软件能做什么</h2>
      <p class="section-desc">从模板到成稿，全程不超过 5 分钟</p>
    </div>
    <div class="f-grid">
      <div class="f-card">
        <h3>海量云端模板库</h3>
        <p>覆盖零售、物流、服装、食品、医药等行业的模板，选中即改，改完即打。</p>
      </div>
      <div class="f-card">
        <h3>Excel 批量打印引擎</h3>
        <p>导入 Excel / TXT 数据源，自动生成流水号与可变内容，一次打印整批标签。</p>
      </div>
      <div class="f-card">
        <h3>多端云同步</h3>
        <p>四端实时同步，换设备、重装系统，标签数据永不丢失。</p>
      </div>
      <div class="f-card">
        <h3>主流机型兼容</h3>
        <p>支持 TSPL / CPCL / ESC-POS 标准指令集，兼容市面主流桌面与便携打印机。</p>
      </div>
    </div>
  </section>

  <!-- 版本选择。价格与能力声明均为占位（FR-056） -->
  <section class="features">
    <div>
      <h2 class="section-title" style="text-align: center">选择适合你的版本</h2>
      <p class="section-desc">免费开始，按需升级</p>
    </div>
    <div class="price-cards">
      <div v-for="plan in plans" :key="plan.name" class="plan-card">
        <div class="plan-name"><PendingText :value="plan.name" /></div>
        <div class="plan-price"><PendingText :value="plan.price" /></div>
        <ul class="plan-feat">
          <li v-for="f in plan.features" :key="f"><PendingText :value="f" /></li>
        </ul>
        <button class="plan-btn" :class="plan.ctaKind" type="button">
          <PendingText :value="plan.cta" />
        </button>
      </div>
    </div>
  </section>

  <section class="final-cta">
    <h2 class="cta-title">3 分钟上手标签设计</h2>
    <p class="cta-sub">免费注册 · 30 天专业版试用</p>
    <!-- 试用需先有账号；登录弹层由 ?login=1 打开（FR-010） -->
    <RouterLink class="cta-btn" to="/account?login=1">立即免费试用</RouterLink>
  </section>
</template>
