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
  横幅图片的尺寸：**高度由图片自己的比例决定**（height: auto）。

  ⚠️ 不要给横幅图片钉死高度。第一版是「height: 220px + object-fit: cover」，
  而 `cover` 的含义是"等比放大到填满容器、溢出的裁掉"，后果两条：
    · 窄屏时左右被切 → **图片最左边的文字看不到**；
    · 源图比容器小时被放大 → **模糊**（运营传了张小图就特别明显）。
  `width: 100%; height: auto` 则既不裁、也不为了填满而拉伸。

  代价说清楚：多张横幅且**比例不一致**时，容器高度取最高的那张，矮的那张下方会留白。
  这是刻意的取舍 —— 留白只是不好看，裁掉/糊掉是**内容丢失**。
  想让它整齐，运营按统一比例传图即可（建议宽度 ≥ 1200px、约 5:1）。
*/

/* 容器高度跟着图片走。design.css 给 .mall-banner 定死了 220px ——
   那是为**文字型**横幅写的（里面没有图片可裁），图片型必须放开 */
.mall-banner {
  height: auto;
}

.mbn-item,
.mbn-link,
.mbn-img {
  display: block;
}

.mbn-img {
  width: 100%;
  /* 关键：不要写死像素值，见上方注释 */
  height: auto;
}
</style>
