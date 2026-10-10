<script setup lang="ts">
import BaseCarousel from '@/components/BaseCarousel.vue'
import type { Banner } from '@/types'

/**
 * 商城页顶部横幅（FR-080~083）。
 *
 * **不发请求** —— 数据由 `MallView` 拉好传进来。这样"拉失败"由页面决定怎么降级
 * （商城页不该因为横幅挂了就白屏），组件只管渲染三态，也更好测。
 *
 * ## 三处刻意的取舍
 *
 * 1. **0 条整块不渲染**（而不是渲染一个空容器）—— 后者会把商品列表顶下去、留一片空白。
 * 2. **1 条时不走轮播组件**：`BaseCarousel` 无论如何都会渲染圆点与箭头，
 *    一张图配这些是无意义的交互噪音。这里宁可让两处标记重复几行，也不去改
 *    `BaseCarousel`（它有自己的契约与测试，改它超出本次范围）。
 * 3. **不套 `.mbn-*` 那套样式**：`design.css` 里那组是设计稿遗留的**文字型**横幅
 *    （渐变底 + 标题 + 描述 + 按钮），而后端 `promotion_banner` 只提供 `picUrl`
 *    —— 没有描述字段，硬套等于为不存在的字段编内容。
 *    只复用外层 `.mall-banner`（尺寸/圆角/阴影/响应式），图片自己用 `.mbn-img`。
 */
defineProps<{ banners: Banner[] }>()
</script>

<template>
  <div v-if="banners.length" class="mall-banner-wrap">
    <!-- 多条：版式是现成的，接上就行。slide **必须带 `car-slide`** —— BaseCarousel
         不会替插槽内容加这个类，而 `flex: 0 0 100%` 只写在 `.car-slide` 上 -->
    <BaseCarousel v-if="banners.length > 1" class="mall-banner" :count="banners.length">
      <div v-for="b in banners" :key="b.id" class="car-slide mbn-item">
        <a v-if="b.url" class="mbn-link" :href="b.url" target="_blank" rel="noopener noreferrer">
          <img class="mbn-img" :src="b.picUrl" :alt="b.title" >
        </a>
        <img v-else class="mbn-img" :src="b.picUrl" :alt="b.title" >
      </div>
    </BaseCarousel>

    <!-- 单条：同一个外层 class，但不挂轮播组件（否则会多出圆点与箭头）。
         `carousel` 那个类要一起带上 —— `position: relative; overflow: hidden` 在它身上 -->
    <div v-else class="carousel mall-banner">
      <div class="car-slide mbn-item">
        <a
          v-if="banners[0]!.url"
          class="mbn-link"
          :href="banners[0]!.url"
          target="_blank"
          rel="noopener noreferrer"
        >
          <img class="mbn-img" :src="banners[0]!.picUrl" :alt="banners[0]!.title" >
        </a>
        <img v-else class="mbn-img" :src="banners[0]!.picUrl" :alt="banners[0]!.title" >
      </div>
    </div>
  </div>
</template>

<style scoped>
/*
  横幅图片的尺寸：**高度固定 220px，用 `object-fit: contain` 保证既不被裁、也不被拉伸**。

  ⚠️ 不要写回 `object-fit: cover`。`cover` 是"等比放大到填满容器、溢出部分裁掉"，
  后果两条（2026-10-10 实际踩到过）：
    · 窄屏时左右被切 → **图片最左边的文字看不到**；
    · 源图比容器小时被放大到填满 → **模糊**。
  `contain` 则把整张图完整放进盒子里、保持自身比例 —— 不裁、不拉伸。

  **代价说清楚**：图片比例与盒子比例不一致时，四周会留白（通常是左右两条）。
  盒子宽度随屏幕变、高度固定 220px，所以**没有一个上传比例能在所有宽度下都填满** ——
  留白是这个口径下必然存在的，不是 bug。运营传接近横幅比例的大图（宽度 ≥ 1200px）
  可以把留白降到最小。

  高度不再由我覆盖：design.css 里 `.mall-banner` 本身就是 220px，
  之前那版额外写 `height: auto` 是为了配合"高度跟图片走"，现在口径回到固定高度，覆盖也一并撤掉。
*/

.mbn-item,
.mbn-link,
.mbn-img {
  display: block;
}

.mbn-img {
  width: 100%;
  height: 220px;
  /* 关键：contain 而不是 cover，见上方注释 */
  object-fit: contain;
}
</style>
