<script setup lang="ts">
import BannerSlide from '@/components/BannerSlide.vue'
import BaseCarousel from '@/components/BaseCarousel.vue'
import type { Banner } from '@/types'

/**
 * 商城页顶部横幅容器（FR-080~083）—— 只管**三态**，单张的排版在 `BannerSlide`。
 *
 * **不发请求**：数据由 `MallView` 拉好传进来，这样"拉失败"由页面决定怎么降级
 * （商城页不该因为横幅挂了就白屏），组件只管渲染，也更好测。
 *
 * 三态：
 *   · **0 条 → 整块不渲染**（渲染一个空容器会把商品列表顶下去、留一片空白）；
 *   · 1 条 → 直接渲染，**不套轮播组件**（它无论如何都会渲染圆点与箭头，
 *     一张图配这些是无意义的交互噪音）；
 *   · ≥2 条 → 走 `BaseCarousel`。
 *
 * ⚠️ 两条分支的 slide 标记**共用 `BannerSlide`**，不复制两遍 —— 复制迟早会漂。
 */
defineProps<{ banners: Banner[] }>()
</script>

<template>
  <div v-if="banners.length" class="mall-banner-wrap">
    <BaseCarousel v-if="banners.length > 1" class="mall-banner" :count="banners.length">
      <BannerSlide v-for="b in banners" :key="b.id" :banner="b" />
    </BaseCarousel>

    <!-- 单条：同一个外层 class，但不挂轮播组件。`carousel` 那个类要一起带上 ——
         `position: relative; overflow: hidden` 在它身上 -->
    <div v-else class="carousel mall-banner">
      <BannerSlide :banner="banners[0]!" />
    </div>
  </div>
</template>

<style scoped>
/*
  高度**固定 220px**（所有者定的口径）。

  ⚠️ 这一行必须有，不能省：design.css 在窄屏（≤480px）把 `.mall-banner` 改成了
  `height: auto` —— 那是给**文字型**横幅写的。而我们的构图是"图与文字都浮层定位"，
  容器一旦 auto 就塌成内容高度，220px 的构图散架。
  这里用更高优先级（scoped 会给选择器加属性选择器）改回 220px，全宽度一致。
*/
.mall-banner {
  height: 220px;
}
</style>
