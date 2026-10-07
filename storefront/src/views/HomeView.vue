<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import { listCategories } from '@/api/category'
import { pageProducts, SORT_FIELD } from '@/api/product'
import BaseCarousel from '@/components/BaseCarousel.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import ProductCard from '@/components/ProductCard.vue'
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'
import type { Category, ProductSpu } from '@/types'
import { deriveBadges } from '@/utils/badge'

/**
 * 首页 —— 按设计稿 `www/index.html` 还原，沿用其 class 名。
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

const HERO_COUNT = 3

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
  <!-- Hero 首屏轮播（3 张，与设计稿一致）。
       顺序按所有者要求调整：「标签耗材」那张（原第 2 张）提到第一张 —— 首屏第一眼
       先看到商城导流。第 1、3 张的相对次序不变。 -->
  <BaseCarousel class="hero" :count="HERO_COUNT">
    <div class="car-slide hero-slide">
      <div class="hero-left">
        <div class="hero-badge">商城大促 · 耗材囤货季</div>
        <h1 class="hero-title">标签耗材设备
一站购齐更省心</h1>
        <p class="hero-sub">热敏纸 / 铜版纸 / PET / 碳带 / 打印机 现货速发</p>
        <div class="hero-cta">
          <RouterLink class="btn-cyan" to="/mall">立即选购</RouterLink>
          <RouterLink class="btn-ghost-white" to="/product/1">看看明星单品</RouterLink>
        </div>
      </div>
      <div class="hero-right">
        <div class="ph hero-visual">商城精选商品图</div>
      </div>
    </div>

    <div class="car-slide hero-slide">
      <div class="hero-left">
        <div class="hero-badge">全新 MaxLabel 3.0 · 云标签时代</div>
        <h1 class="hero-title">让每一枚标签
都精准呈现</h1>
        <p class="hero-sub">标签软件 · 打印机 · 标签耗材一站式商城，
编辑、打印、管理从未如此简单</p>
        <div class="hero-cta">
          <RouterLink class="btn-cyan" to="/mall">进入商城选购</RouterLink>
          <RouterLink class="btn-ghost-white" to="/software">免费使用标签软件</RouterLink>
        </div>
        <p class="trust-line">
          已服务 <PendingText :value="trustStats[0]?.value ?? ''" /> 企业用户 ·
          兼容 <PendingText :value="trustStats[2]?.value ?? ''" /> 打印机型号
        </p>
      </div>
      <div class="hero-right">
        <div class="ph hero-visual">标签打印机产品图</div>
        <div class="hero-chips">
          <div class="glass-chip"><b>赋签 M3 Pro</b><span>双模高速 · 300dpi</span></div>
          <div class="glass-chip"><b>MaxLabel 云标签</b><span>多端同步 · 批量打印</span></div>
        </div>
      </div>
    </div>

    <div class="car-slide hero-slide">
      <div class="hero-left">
        <div class="hero-badge">MaxLabel 3.0 · 免费下载</div>
        <h1 class="hero-title">3 分钟上手
标签设计如此简单</h1>
        <p class="hero-sub">行业模板一键套用，Excel 批量打印，
Windows / macOS / iOS / Android 全平台云同步</p>
        <div class="hero-cta">
          <RouterLink class="btn-cyan" to="/software">免费下载软件</RouterLink>
          <RouterLink class="btn-ghost-white" to="/templates">浏览模板中心</RouterLink>
        </div>
      </div>
      <div class="hero-right">
        <div class="ph hero-visual">软件编辑器界面图</div>
      </div>
    </div>
  </BaseCarousel>

  <!-- 商城商品区：真实商品 -->
  <section class="mall">
    <div class="section-head">
      <div>
        <h2 class="section-title">赋签商城 · 一站式购齐</h2>
        <p class="section-desc">标签耗材 · 智能打印机 · 软件服务，一个商城全部搞定</p>
      </div>
      <div class="tabs">
        <RouterLink class="tab active" to="/mall">全部</RouterLink>
        <RouterLink v-for="c in categories.slice(0, 3)" :key="c.id" class="tab" :to="`/mall?categoryId=${c.id}`">
          {{ c.name }}
        </RouterLink>
      </div>
    </div>

    <LoadingState v-if="loading" variant="grid" :count="4" />

    <EmptyState
      v-else-if="error"
      mode="error"
      title="商品加载失败"
      desc="网络或服务暂时不可用，请稍后重试"
      action-text="重新加载"
      @action="loadAll"
    />

    <div v-else-if="products.length" class="product-grid">
      <ProductCard v-for="p in products" :key="p.id" :spu="p" :badge="badgeOf(p.id)" />
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
      <h2 class="section-title" style="text-align: center">MaxLabel 标签编辑软件</h2>
      <p class="section-desc">编辑变得如此简单 —— 软件免费下载，专业版按需订阅</p>
    </div>
    <div class="f-grid">
      <div class="f-card">
        <h3>海量模板 · 一键套用</h3>
        <p>覆盖零售、物流、服装、烘焙等行业的精品模板，选好即改，改完即打。</p>
      </div>
      <div class="f-card">
        <h3>Excel 批量打印</h3>
        <p>导入 Excel / TXT 数据源，自动生成流水号与可变内容，批量打印效率大幅提升。</p>
      </div>
      <div class="f-card">
        <h3>多端云同步</h3>
        <p>Windows / iOS / Android / Web 四端实时同步，换设备、重装系统标签数据永不丢失。</p>
      </div>
      <div class="f-card">
        <h3>主流打印机兼容</h3>
        <p>兼容市面主流桌面与便携打印机，支持 TSPL / CPCL / ESC-POS 标准指令集。</p>
      </div>
    </div>
  </section>

  <!-- 三步使用流程（静态） -->
  <section class="steps">
    <div>
      <h2 class="section-title" style="text-align: center">三步完成标签打印</h2>
      <p class="section-desc">从模板到成稿，全程不超过 5 分钟</p>
    </div>
    <div class="steps-row">
      <div class="step-card">
        <div class="step-num">1</div>
        <h3>选择模板</h3>
        <p>从模板库中挑选，或从空白画布开始创作</p>
      </div>
      <span class="step-arrow">→</span>
      <div class="step-card">
        <div class="step-num">2</div>
        <h3>拖拽编辑</h3>
        <p>文字、条码、二维码、表格自由排版，所见即所得</p>
      </div>
      <span class="step-arrow">→</span>
      <div class="step-card">
        <div class="step-num">3</div>
        <h3>连接打印</h3>
        <p>一键连接打印机，即点即打</p>
      </div>
    </div>
  </section>

  <!-- 行业解决方案（静态） -->
  <section class="industry">
    <div>
      <h2 class="section-title" style="text-align: center">行业标签方案</h2>
      <p class="section-desc">为每一个行业打磨专属的标签打印与管理方案</p>
    </div>
    <div class="i-grid">
      <div class="i-card">
        <div class="i-icon c1">仓</div>
        <h3>仓储物流</h3>
        <p>标签协同 + 自动箱单 + 称重测体，出入库效率显著提升</p>
        <RouterLink class="i-link" to="/solutions">查看方案 →</RouterLink>
      </div>
      <div class="i-card">
        <div class="i-icon c2">产</div>
        <h3>生产制造</h3>
        <p>上下游生产链标签协同，批次追溯不出错、不遗漏</p>
        <RouterLink class="i-link" to="/solutions">查看方案 →</RouterLink>
      </div>
      <div class="i-card">
        <div class="i-icon c3">服</div>
        <h3>服装行业</h3>
        <p>吊牌水洗标快速替换，扫码即可批量改写标签内容</p>
        <RouterLink class="i-link" to="/solutions">查看方案 →</RouterLink>
      </div>
    </div>
    <RouterLink class="more-link" to="/solutions">查看全部行业方案 →</RouterLink>
  </section>

  <!--
    信任背书 —— 这里的数字、公司名与客户证言**全部是未确认的业务声明**，
    经 PendingText 渲染（FR-056）。其中 6 个第三方公司名若未获授权，公开宣传
    可能构成虚假宣传与商标侵权，故 MUST 由业务方确认后方可作为事实展示。
  -->
  <section class="trust">
    <div class="stats-row">
      <div v-for="s in trustStats" :key="s.label" class="stat">
        <div class="stat-num"><PendingText :value="s.value" /></div>
        <div class="stat-label"><PendingText :value="s.label" /></div>
      </div>
    </div>

    <div class="quote">
      <p class="quote-text"><PendingText :value="testimonial.text" /></p>
      <p class="quote-author">—— <PendingText :value="testimonial.author" /></p>
    </div>

    <p class="partner-label">他们都在使用赋签</p>
    <div class="logo-row">
      <PendingText v-for="n in partnerNames" :key="n" tag="span" :value="n" />
    </div>
  </section>

  <!-- 服务支持（静态） -->
  <section class="support-home">
    <div>
      <h2 class="section-title" style="text-align: center">全程护航的售后服务</h2>
      <p class="section-desc">购买只是开始，赋签为每一次打印保驾护航</p>
    </div>
    <div class="s-grid">
      <div class="s-card">
        <h3>7×12 在线客服</h3>
        <p>工作日 9:00-21:00 随时响应打印问题</p>
      </div>
      <div class="s-card">
        <h3>驱动与手册下载</h3>
        <p>全型号驱动 · 快速指南，持续更新一键获取</p>
      </div>
      <div class="s-card">
        <h3>视频教程学院</h3>
        <p>从入门到进阶，手把手教你玩转标签</p>
      </div>
      <div class="s-card">
        <h3>整机一年质保</h3>
        <p>打印机整机质保一年，耗材支持无理由退换</p>
      </div>
    </div>
  </section>

  <!-- 底部 CTA -->
  <section class="final-cta">
    <h2 class="cta-title">开启高效标签打印之旅</h2>
    <p class="cta-sub">免费注册 · 商城下单更便捷</p>
    <RouterLink class="cta-btn" to="/software">立即免费试用</RouterLink>
  </section>
</template>
