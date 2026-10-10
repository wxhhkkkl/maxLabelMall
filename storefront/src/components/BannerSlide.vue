<script setup lang="ts">
import { computed } from 'vue'

import type { Banner } from '@/types'
import { parseBannerCopy } from '@/utils/banner'

/**
 * 单张横幅的排版：**产品图铺底 + 渐变遮罩 + 文案叠在左侧**。
 *
 * 为什么是组合式而不是"后台传一张拍平的图"：拍平之后窄屏要么把左边的字裁掉、
 * 要么整张缩得很小；拆成"图 + HTML 文字"后，怎么缩放都不丢内容，文字也始终清晰。
 * 设计稿本来就是这么交付的（那张 `one-stop-background.webp` 是**无文字**的产品图，
 * 生成提示词里明确要求"REMOVE ALL TEXT…the website will place crisp Chinese typography separately"）。
 *
 * 文案来源见 `utils/banner.ts`；**品牌行是前端固定的**（站点自己的名字，页头页脚本来就写着）。
 * 拆出来单独成件是为了让 `MallBanner` 的"单条 / 多条"两条分支**共用同一段标记**，
 * 而不是复制两遍 —— 复制两遍迟早会漂。
 */
const props = defineProps<{ banner: Banner }>()

const copy = computed(() => parseBannerCopy(props.banner))
</script>

<template>
  <div class="car-slide mbn-slide">
    <!-- 整块可点：配了 url 才是链接；没配就不可点（FR-083） -->
    <component
      :is="banner.url ? 'a' : 'div'"
      class="mbn-hit"
      :class="{ 'mbn-link': !!banner.url }"
      :href="banner.url || undefined"
      :target="banner.url ? '_blank' : undefined"
      :rel="banner.url ? 'noopener noreferrer' : undefined"
    >
      <img class="mbn-photo" :src="banner.picUrl" :alt="banner.title" >

      <div class="mbn-copy">
        <p class="mbn-brand">赋签 | MaxLabel</p>
        <h2 v-if="copy.titleLines.length" class="mbn-title">
          <span v-for="(line, i) in copy.titleLines" :key="i" class="mbn-title-line">{{ line }}</span>
        </h2>
        <p v-if="copy.subtitle" class="mbn-subtitle">{{ copy.subtitle }}</p>
        <span v-if="copy.badge" class="mbn-badge">{{ copy.badge }}</span>
      </div>
    </component>
  </div>
</template>

<style scoped>
/*
  高度固定 220px（所有者定的口径）。图与文字都绝对/浮层定位，所以 220px 必须写死 ——
  design.css 在窄屏把 .mall-banner 改成了 height:auto，那会让这个构图塌掉，
  MallBanner 里已用更高优先级改回 220px。
*/
.mbn-slide {
  position: relative;
  isolation: isolate;
  height: 220px;
  overflow: hidden;
}

.mbn-hit {
  display: block;
  height: 100%;
  text-decoration: none;
}

/*
  ⚠️ `contain` 而不是 `cover`：cover 是"放大到填满、溢出裁掉"，会把产品图切掉。
  这里是配景图，宁可留白也不能裁（2026-10-10 因 cover 出过一次线上问题）。
*/
.mbn-photo {
  position: absolute;
  top: 50%;
  right: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: right center;
  transform: translateY(-50%);
  z-index: -2;
}

/* 从左往右的渐变遮罩：保证压在浅色底上的文字始终读得清 */
.mbn-slide::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(
    90deg,
    var(--ml-bg-soft) 0%,
    var(--ml-bg-soft) 35%,
    transparent 55%
  );
}

.mbn-copy {
  height: 100%;
  width: 54%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  padding: 22px 46px;
  position: relative;
}

.mbn-brand {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--ml-primary);
}

.mbn-title {
  margin: 0;
  font-size: 30px;
  line-height: 1.35;
  font-weight: 700;
  color: var(--ml-text);
}

.mbn-title-line {
  display: block;
}

.mbn-subtitle {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--ml-text-sub);
}

.mbn-badge {
  margin-top: 10px;
  padding: 4px 10px;
  border-radius: var(--ml-radius-pill);
  background: var(--ml-bg-card-2);
  color: var(--ml-primary);
  font-size: 11px;
}

/* 手机：文字改成从上往下排，图沉到底部右下（与设计稿的移动端构图一致） */
@media (max-width: 768px) {
  .mbn-photo {
    top: auto;
    bottom: 0;
    height: 60%;
    transform: none;
    object-position: right bottom;
  }
  .mbn-slide::after {
    background: linear-gradient(90deg, var(--ml-bg-soft) 0%, var(--ml-bg-soft) 55%, transparent 85%);
  }
  .mbn-copy {
    width: 100%;
    justify-content: flex-start;
    padding: 20px;
  }
  .mbn-title {
    font-size: 22px;
  }
  .mbn-subtitle {
    font-size: 12px;
  }
}
</style>
