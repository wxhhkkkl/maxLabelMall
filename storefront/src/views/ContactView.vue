<script setup lang="ts">
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'
import { telHref } from '@/utils/tel'

/**
 * 联系我们 —— 按 design-new-pages.md §3.16 实现（无设计稿）。
 *
 * ⚠️ 电话、邮箱、地址、服务时间**全部由业务方提供**（FR-054）。设计稿里那个
 * `400-800-XXXX` 是占位，**MUST NOT** 作为正式号码发布 —— 所以这里渲染的是
 * `placeholders.ts` 的可辨识占位文案。
 *
 * ⚠️ `tel:` 链接只在值**真的像一个号码**时才生成（FR-061 的「移动端可拨号」）。
 * 占位文案拼出来的 `tel:` 打不通，还会让人以为站点已经在提供该服务。
 */
const contact = RENDERED.contact
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <span>联系我们</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ml-page-title"><PendingText :value="contact.title" /></h1>
    <p class="ml-page-sub"><PendingText :value="contact.subtitle" /></p>
  </div>

  <div class="ml-wrap">
    <div class="ct-grid">
      <div v-for="c in contact.cards" :key="c.name" class="contact-card">
        <div class="ph ct-icon"></div>
        <span class="contact-label"><PendingText :value="c.name" /></span>
        <a v-if="telHref(c.value)" class="contact-val" :href="telHref(c.value)">
          <PendingText :value="c.value" />
        </a>
        <span v-else class="contact-val"><PendingText :value="c.value" /></span>
        <span class="contact-note"><PendingText :value="c.note" /></span>
      </div>
    </div>

    <div class="ct-addr">
      <div class="ct-addr-info">
        <h2 class="section-title">公司地址</h2>
        <p class="ml-addr"><PendingText :value="contact.address" /></p>
        <p class="ml-hint"><PendingText :value="contact.hours" /></p>
      </div>
      <!-- 地图占位：16:9。接入真实地图超出本期（无设计稿、也无需密钥） -->
      <div class="ph map-ph"></div>
    </div>
  </div>
</template>

<style scoped>
.ct-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}
.ct-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
}
.ct-addr {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 28px;
  align-items: center;
  margin-top: 40px;
}
.ct-addr-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ct-addr-info .section-title {
  font-size: 22px;
}
.ml-addr {
  font-size: 15px;
  color: var(--ml-text);
}
.map-ph {
  aspect-ratio: 16 / 9;
  border-radius: var(--ml-radius-card);
}
@media (max-width: 768px) {
  .ct-grid,
  .ct-addr {
    grid-template-columns: 1fr;
  }
}
</style>
