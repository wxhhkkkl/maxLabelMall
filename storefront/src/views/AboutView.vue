<script setup lang="ts">
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'

/**
 * 公司介绍 —— 按 design-new-pages.md §3.14 实现（无设计稿）。
 *
 * ⚠️ **公司简介、数据、发展历程全部由业务方提供**（FR-054）。在拿到之前一律渲染
 * `placeholders.ts` 的占位文案，**不得编造** —— 在正式站点上发布虚构的成立年份、
 * 客户数或历程是不可接受的。所以种子里写的是 `20XX` 而不是某个具体年份。
 */
const about = RENDERED.about
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <span>关于我们</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ab-title ml-page-title"><PendingText :value="about.title" /></h1>
    <p class="ml-page-sub"><PendingText :value="about.tagline" /></p>
  </div>

  <div class="ml-wrap">
    <!-- 图文段：左右交替（左文右图 / 左图右文） -->
    <section class="ab-rows">
      <div
        v-for="(text, i) in about.intro"
        :key="i"
        class="ab-row"
        :class="{ 'is-reverse': i % 2 === 1 }"
      >
        <p class="ab-text"><PendingText :value="text" /></p>
        <div class="ph ab-img"></div>
      </div>
    </section>

    <!-- 数据条 -->
    <section class="stats-row ab-stats">
      <div v-for="s in about.stats" :key="s.label" class="stat ab-stat">
        <div class="stat-num"><PendingText :value="s.value" /></div>
        <div class="stat-label"><PendingText :value="s.label" /></div>
      </div>
    </section>

    <!-- 发展历程：与更新日志同一套时间线版式 -->
    <section class="ab-history">
      <h2 class="section-title">发展历程</h2>
      <ol class="ab-milestones">
        <li v-for="m in about.milestones" :key="m.text" class="ab-milestone">
          <span class="ab-year"><PendingText :value="m.year" /></span>
          <span class="ab-mtext"><PendingText :value="m.text" /></span>
        </li>
      </ol>
    </section>

    <div class="ab-cta">
      <RouterLink class="cta-btn" to="/contact">联系我们</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.ab-rows {
  display: flex;
  flex-direction: column;
  gap: 40px;
  margin-bottom: 56px;
}
.ab-row {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 32px;
  align-items: center;
}
/* 偶数行把图换到左边 —— 图文交替（§3.14） */
.ab-row.is-reverse {
  grid-template-columns: 380px 1fr;
}
.ab-row.is-reverse .ab-text {
  order: 2;
}
.ab-text {
  font-size: 15px;
  line-height: 1.9;
  color: var(--ml-text-sub);
}
.ab-img {
  height: 220px;
  border-radius: var(--ml-radius-card);
}
.ab-stats {
  padding: 28px 0;
  border-top: 1px solid var(--ml-border);
  border-bottom: 1px solid var(--ml-border);
  margin-bottom: 56px;
}
.ab-stat {
  text-align: center;
}
.ab-history {
  margin-bottom: 56px;
}
.ab-history .section-title {
  margin-bottom: 24px;
}
.ab-milestones {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 18px;
  border-left: 2px solid #e3e9f4;
  padding-left: 20px;
}
.ab-milestone {
  display: flex;
  gap: 16px;
  align-items: baseline;
}
.ab-year {
  flex: 0 0 72px;
  font-size: 15px;
  font-weight: 700;
  color: var(--ml-text);
}
.ab-mtext {
  font-size: 14px;
  color: var(--ml-text-sub);
  line-height: 1.7;
}
.ab-cta {
  display: flex;
  justify-content: center;
}
@media (max-width: 768px) {
  .ab-row,
  .ab-row.is-reverse {
    grid-template-columns: 1fr;
  }
  .ab-row.is-reverse .ab-text {
    order: 0;
  }
}
</style>
