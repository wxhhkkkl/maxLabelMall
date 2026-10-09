<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import { listCategories } from '@/api/category'
import { pageProducts, SORT_FIELD } from '@/api/product'
import HomeHero from '@/components/home/HomeHero.vue'
import HomeIndustry from '@/components/home/HomeIndustry.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import ProductCard from '@/components/ProductCard.vue'
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'
import type { Category, ProductSpu } from '@/types'
import { deriveBadges } from '@/utils/badge'

/**
 * 首页 —— Banner 与行业区采用新版设计，其余区域沿用现有布局。
 *
 * 商品区取**后台真实商品**：按销量降序，取几个由视口宽度决定 —— 超宽屏 6、其余 4
 * （FR-008，2026-10-07 修订）。销量全为 0 时后端会退化为默认排序，商品区仍有
 * 内容，不会空白。
 *
 * ⚠️ 设计稿的信任背书区带**未确认的业务声明**（客户公司名、服务企业数、
 * 客户证言）与营销承诺 —— 全部经 `PendingText` 渲染（FR-056）。业务方在
 * `placeholders.ts` 里确认后替换，视图无需改动；未替换时页面上可见 `[[ ]]`，
 * 且门禁② 能扫到。
 */

const products = ref<ProductSpu[]>([])
const badges = ref<ReturnType<typeof deriveBadges>>({ hot: [], fresh: [] })
const categories = ref<Category[]>([])
const loading = ref(true)
const error = ref(false)

/**
 * 商品区取几个 —— **由视口宽度决定**（2026-10-07 所有者决策）：超宽屏 6 个、其余 4 个。
 *
 * ⚠️ `WIDE_VIEWPORT` 与 design.css 里控制 `.product-grid` 列数的
 * `@media (max-width: 1600px)` 是**互补的两半，必须同时改**：超宽屏一行 6 列、
 * 其余 4 列。只改一边就会错位 —— 取 6 个却排 4 列（第三张卡孤零零占一行），
 * 或取 4 个却排 6 列（右侧空出两格，正是首页最初那个空白的成因）。
 *
 * 门槛取 1601 而不是 1101：6 列在 1440px 上每张卡只剩约 193px，比设计稿的
 * 240px 下限窄，而且会出现「1100→1101 卡片反而变窄」的倒挂。
 * 见 design.css 里那条 `@media (max-width: 1600px)` 的注释。
 */
const WIDE_VIEWPORT = '(min-width: 1601px)'
const PRODUCT_COUNT_WIDE = 6
const PRODUCT_COUNT_NARROW = 4

const viewportQuery = window.matchMedia(WIDE_VIEWPORT)

function productCount(): number {
  return viewportQuery.matches ? PRODUCT_COUNT_WIDE : PRODUCT_COUNT_NARROW
}

/** 上一次实际请求的条数 —— 用于判断跨断点后要不要重新取 */
let requestedCount = 0

async function loadProducts() {
  requestedCount = productCount()
  const res = await pageProducts({
    pageNo: 1,
    pageSize: requestedCount,
    sortField: SORT_FIELD.sales,
    sortAsc: false,
  })
  products.value = res.list
  badges.value = deriveBadges(res.list)
}

function badgeOf(id: number): 'hot' | 'fresh' | null {
  if (badges.value.hot.includes(id)) return 'hot'
  if (badges.value.fresh.includes(id)) return 'fresh'
  return null
}

async function loadAll() {
  loading.value = true
  error.value = false
  try {
    const [cats] = await Promise.all([listCategories(), loadProducts()])
    categories.value = cats.slice(0, 4)
  } catch {
    // 请求失败要给出可重试提示，不能白屏或永久加载（FR-045）
    error.value = true
    products.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 跨断点时重新取数。`matchMedia` 的 `change` **只在跨过断点时触发** ——
 * 拖动窗口时逐像素变化不会反复发请求，只有 1101px 这条线被越过才重取。
 */
function onViewportChange(): void {
  if (productCount() !== requestedCount) void loadProducts()
}

onMounted(() => {
  viewportQuery.addEventListener('change', onViewportChange)
  void loadAll()
})

onBeforeUnmount(() => {
  viewportQuery.removeEventListener('change', onViewportChange)
})

/** 信任背书区的数字（全部来自占位符，业务方确认前不当作事实） */
const trustStats = RENDERED.inheritedClaims.stats
const partnerNames = RENDERED.inheritedClaims.partnerNames
const testimonial = RENDERED.inheritedClaims.testimonial
</script>

<template>
  <HomeHero />

  <!-- 商城商品区：真实商品 -->
  <section class="mall">
    <div class="section-head">
      <div>
        <h2 class="section-title">
          赋签商城 · 一站式购齐
        </h2>
        <p class="section-desc">
          标签耗材 · 智能打印机 · 软件服务，一个商城全部搞定
        </p>
      </div>
      <div class="tabs">
        <RouterLink
          class="tab active"
          to="/mall"
        >
          全部
        </RouterLink>
        <RouterLink
          v-for="c in categories.slice(0, 3)"
          :key="c.id"
          class="tab"
          :to="`/mall?categoryId=${c.id}`"
        >
          {{ c.name }}
        </RouterLink>
      </div>
    </div>

    <LoadingState
      v-if="loading"
      variant="grid"
      :count="4"
    />

    <EmptyState
      v-else-if="error"
      mode="error"
      title="商品加载失败"
      desc="网络或服务暂时不可用，请稍后重试"
      action-text="重新加载"
      @action="loadAll"
    />

    <div
      v-else-if="products.length"
      class="product-grid"
    >
      <ProductCard
        v-for="p in products"
        :key="p.id"
        :spu="p"
        :badge="badgeOf(p.id)"
      />
    </div>

    <EmptyState
      v-else
      title="暂无商品"
      desc="后台还没有上架商品，稍后再来看看"
      action-text="刷新看看"
      @action="loadAll"
    />
  </section>

  <!-- 软件功能特性（静态内容，还原设计稿） -->
  <section class="features">
    <div>
      <h2
        class="section-title"
        style="text-align: center"
      >
        MaxLabel 标签编辑软件
      </h2>
      <p class="section-desc">
        编辑变得如此简单 —— 软件免费下载，专业版按需订阅
      </p>
    </div>
    <div class="f-grid">
      <div class="f-card">
        <svg
          width="48"
          height="48"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        ><rect
          x="3"
          y="3"
          width="7"
          height="7"
          rx="1"
          stroke="#6FC8FF"
          stroke-width="2"
        /><rect
          x="14"
          y="3"
          width="7"
          height="7"
          rx="1"
          stroke="#6FC8FF"
          stroke-width="2"
        /><rect
          x="3"
          y="14"
          width="7"
          height="7"
          rx="1"
          stroke="#6FC8FF"
          stroke-width="2"
        /><rect
          x="14"
          y="14"
          width="7"
          height="7"
          rx="1"
          stroke="#6FC8FF"
          stroke-width="2"
        /></svg>
        <h3>海量模板 · 一键套用</h3>
        <p>覆盖零售、物流、服装、烘焙等行业的精品模板，选好即改，改完即打。</p>
      </div>
      <div class="f-card">
        <svg
          width="48"
          height="48"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        ><path
          d="M6 9V3h12v6"
          stroke="#6FC8FF"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        /><rect
          x="3"
          y="9"
          width="18"
          height="8"
          rx="2"
          stroke="#6FC8FF"
          stroke-width="2"
        /><path
          d="M7 14h10v7H7z"
          stroke="#6FC8FF"
          stroke-width="2"
          stroke-linejoin="round"
        /></svg>
        <h3>Excel 批量打印</h3>
        <p>导入 Excel / TXT 数据源，自动生成流水号与可变内容，批量打印效率大幅提升。</p>
      </div>
      <div class="f-card">
        <svg
          width="48"
          height="48"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        ><path
          d="M17.5 19a4.5 4.5 0 0 0 0-9 6 6 0 0 0-11.4 1.7A4 4 0 0 0 7 19h10.5z"
          stroke="#6FC8FF"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        /></svg>
        <h3>多端云同步</h3>
        <p>Windows / iOS / Android / Web 四端实时同步，换设备、重装系统标签数据永不丢失。</p>
      </div>
      <div class="f-card">
        <svg
          width="48"
          height="48"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        ><path
          d="M9 2v6M15 2v6M6 8h12v4a6 6 0 0 1-12 0V8zM12 18v4"
          stroke="#6FC8FF"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        /></svg>
        <h3>主流打印机兼容</h3>
        <p>兼容市面主流桌面与便携打印机，支持 TSPL / CPCL / ESC-POS 标准指令集。</p>
      </div>
    </div>
  </section>

  <!-- 三步使用流程（静态） -->
  <section class="steps">
    <div>
      <h2
        class="section-title"
        style="text-align: center"
      >
        三步完成标签打印
      </h2>
      <p class="section-desc">
        从模板到成稿，全程不超过 5 分钟
      </p>
    </div>
    <div class="steps-row">
      <div class="step-card">
        <div class="step-num">
          1
        </div>
        <h3>选择模板</h3>
        <p>从模板库中挑选，或从空白画布开始创作</p>
      </div>
      <span class="step-arrow">→</span>
      <div class="step-card">
        <div class="step-num">
          2
        </div>
        <h3>拖拽编辑</h3>
        <p>文字、条码、二维码、表格自由排版，所见即所得</p>
      </div>
      <span class="step-arrow">→</span>
      <div class="step-card">
        <div class="step-num">
          3
        </div>
        <h3>连接打印</h3>
        <p>一键连接打印机，即点即打</p>
      </div>
    </div>
  </section>

  <HomeIndustry />

  <!--
    信任背书 —— 这里的数字、公司名与客户证言**全部是未确认的业务声明**，
    经 PendingText 渲染（FR-056）。其中 6 个第三方公司名若未获授权，公开宣传
    可能构成虚假宣传与商标侵权，故 MUST 由业务方确认后方可作为事实展示。
  -->
  <section class="trust">
    <div class="stats-row">
      <div
        v-for="s in trustStats"
        :key="s.label"
        class="stat"
      >
        <div class="stat-num">
          <PendingText :value="s.value" />
        </div>
        <div class="stat-label">
          <PendingText :value="s.label" />
        </div>
      </div>
    </div>

    <div class="quote">
      <p class="quote-text">
        <PendingText :value="testimonial.text" />
      </p>
      <p class="quote-author">
        —— <PendingText :value="testimonial.author" />
      </p>
    </div>

    <p class="partner-label">
      他们都在使用赋签
    </p>
    <div class="logo-row">
      <PendingText
        v-for="n in partnerNames"
        :key="n"
        tag="span"
        :value="n"
      />
    </div>
  </section>

  <!-- 服务支持（静态） -->
  <section class="support-home">
    <div>
      <h2
        class="section-title"
        style="text-align: center"
      >
        全程护航的售后服务
      </h2>
      <p class="section-desc">
        购买只是开始，赋签为每一次打印保驾护航
      </p>
    </div>
    <div class="s-grid">
      <div class="s-card">
        <svg
          width="44"
          height="44"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        ><path
          d="M4 13a8 8 0 0 1 16 0v5a3 3 0 0 1-3 3h-2"
          stroke="#1B66FF"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        /><path
          d="M4 13v3a3 3 0 0 0 3 3h1v-6H7a3 3 0 0 0-3 3z"
          fill="#1B66FF"
        /></svg>
        <h3>7×12 在线客服</h3>
        <p>工作日 9:00-21:00 随时响应打印问题</p>
      </div>
      <div class="s-card">
        <svg
          width="44"
          height="44"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        ><path
          d="M12 3v12m0 0l-4-4m4 4l4-4"
          stroke="#1B66FF"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        /><path
          d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"
          stroke="#1B66FF"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        /></svg>
        <h3>驱动与手册下载</h3>
        <p>全型号驱动 · 快速指南，持续更新一键获取</p>
      </div>
      <div class="s-card">
        <svg
          width="44"
          height="44"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        ><circle
          cx="12"
          cy="12"
          r="9"
          stroke="#1B66FF"
          stroke-width="2"
        /><path
          d="M10 8.5l6 3.5-6 3.5v-7z"
          fill="#1B66FF"
        /></svg>
        <h3>视频教程学院</h3>
        <p>从入门到进阶，手把手教你玩转标签</p>
      </div>
      <div class="s-card">
        <svg
          width="44"
          height="44"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        ><path
          d="M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6l7-3z"
          stroke="#1B66FF"
          stroke-width="2"
          stroke-linejoin="round"
        /><path
          d="M9 12l2 2 4-4"
          stroke="#1B66FF"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        /></svg>
        <h3>整机一年质保</h3>
        <p>打印机整机质保一年，耗材支持无理由退换</p>
      </div>
    </div>
  </section>

  <!-- 底部 CTA -->
  <section class="final-cta">
    <h2 class="cta-title">
      开启高效标签打印之旅
    </h2>
    <p class="cta-sub">
      免费注册 · 商城下单更便捷
    </p>
    <RouterLink
      class="cta-btn"
      to="/software"
    >
      立即免费试用
    </RouterLink>
  </section>
</template>
