<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProductDetail } from '@/api/product'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useToasts } from '@/components/base/useToasts'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { formatYuan } from '@/utils/money'
import { sanitizeRichText } from '@/utils/sanitize'
import type { ProductSpu, ProductSku } from '@/types'

/**
 * 商品详情页 —— 按设计稿 `www/product.html` 还原，沿用其 class 名。
 *
 * 两处刻意的实现选择：
 *
 * 1. **没有独立的「规格参数」表**（FR-005a）。后端 SPU 没有结构化参数键值对字段，
 *    设计稿那张「打印分辨率 / 打印速度 / 连接方式…」的表无处取值。改为由
 *    **SKU 规格项**承载该位置；产品参数类信息由运营写在详情富文本里。
 *
 * 2. **富文本必经 `sanitizeRichText`**（FR-005c / SC-020）。详情 HTML 由运营在
 *    后台撰写，直接注入等于把 XSS 面交给运营账号，故一律白名单过滤后再渲染。
 */
const props = defineProps<{ id: number | string }>()

const router = useRouter()
const route = useRoute()
const cartStore = useCartStore()
const userStore = useUserStore()
const toast = useToasts()

const spu = ref<ProductSpu | null>(null)
const loading = ref(true)
const notFound = ref(false)
const currentSkuId = ref<number | null>(null)
const qty = ref(1)

const skus = computed<ProductSku[]>(() => spu.value?.skus ?? [])
const isMultiSpec = computed(() => !!spu.value?.specType && skus.value.length > 0)

/** 当前选中规格；单规格商品取唯一 SKU，多规格未选时为空 */
const currentSku = computed<ProductSku | null>(() => {
  if (!isMultiSpec.value) return skus.value[0] ?? null
  return skus.value.find((s) => s.id === currentSkuId.value) ?? null
})

/** 展示价：多规格未选时用 SPU 的价格区间下界 */
const price = computed(() => currentSku.value?.price ?? spu.value?.price ?? 0)
const marketPrice = computed(() => currentSku.value?.marketPrice ?? spu.value?.marketPrice ?? 0)
const stock = computed(() => currentSku.value?.stock ?? spu.value?.stock ?? 0)

const priceText = computed(() => formatYuan(price.value))
const marketText = computed(() => (marketPrice.value > 0 ? formatYuan(marketPrice.value) : ''))

/** 富文本：过滤后仍需判断是否为空 —— 空或只有空白时整块不渲染（FR-005b） */
const safeDescription = computed(() => {
  const raw = spu.value?.description ?? ''
  if (!raw.trim()) return ''
  const clean = sanitizeRichText(raw)
  // 过滤后可能只剩空标签，用文本判断是否真有内容
  const textOnly = clean.replace(/<[^>]*>/g, '').trim()
  return textOnly ? clean : ''
})

function pickSku(s: ProductSku) {
  if (s.stock <= 0) return
  currentSkuId.value = s.id
  qty.value = 1
}

function stepQty(d: number) {
  const next = qty.value + d
  qty.value = next < 1 ? 1 : next
}

/**
 * 取当前可下单的 SKU。多规格商品在**没有任何有货规格**（也就无从预选）时为空 ——
 * 此时必须明确提示而不是拿一个空 skuId 去加购或结算（FR-017 / FR-018）。
 */
function requireSku(): ProductSku | null {
  if (currentSku.value) return currentSku.value
  toast.warn('请先选择规格')
  return null
}

/**
 * 加购。未登录时**不发请求**，把意图暂存下来并引导登录，登录成功后由
 * `LoginDialog` 调 `flushPendingIntent()` 自动补上（FR-015）—— 与商品卡片同一套。
 */
async function onAddToCart(): Promise<void> {
  const s = requireSku()
  if (!s) return

  if (!userStore.isLogin) {
    await cartStore.add(s.id, qty.value)
    toast.warn('登录后将自动加入购物车')
    // 显式带 path：只给 query 时 router 会去匹配"当前位置"，在未匹配的页面上会抛错。
    // 导航失败（重复导航、被守卫中止）不该变成未处理的拒绝。
    void router
      .push({ path: route.path, query: { ...route.query, login: '1', redirect: route.fullPath } })
      .catch(() => undefined)
    return
  }

  try {
    await cartStore.add(s.id, qty.value)
    toast.success('已加入购物车')
  } catch (e) {
    toast.error((e as { message?: string })?.message || '加入购物车失败，请重试')
  }
}

/**
 * 立即购买（FR-024）：只结算这一件商品与所选规格数量，**不经过购物车**。
 *
 * 未登录时不做任何特殊处理 —— `/checkout` 的 `requiresAuth` 守卫会挂上
 * `?login=1&redirect=…`，登录后回到结算页继续（FR-015）。
 */
function onBuyNow(): void {
  const s = requireSku()
  if (!s) return
  void router
    .push({
      path: '/checkout',
      query: { source: 'product', skuId: String(s.id), count: String(qty.value) },
    })
    .catch(() => undefined)
}

async function load() {
  loading.value = true
  notFound.value = false
  try {
    const detail = await getProductDetail(Number(props.id))
    spu.value = detail
    const list = detail.skus ?? []
    // 多规格默认选第一个**有货**的规格；全都无货则不预选
    currentSkuId.value = list.find((s) => s.stock > 0)?.id ?? null
  } catch {
    // 已下架 / 不存在：后端返回业务异常，这里给出明确提示而非空白页（FR-007）
    notFound.value = true
    spu.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.id, load)
</script>

<template>
  <LoadingState v-if="loading" :count="3" />

  <EmptyState
    v-else-if="notFound"
    mode="error"
    title="商品已下架"
    desc="该商品可能已下架或不存在，看看别的商品吧"
    action-text="返回商城"
    @action="$router.push('/mall')"
  />

  <template v-else-if="spu">
    <div class="crumbs">
      <RouterLink to="/">首页</RouterLink> / <RouterLink to="/mall">商城</RouterLink> /
      <span class="current">{{ spu.name }}</span>
    </div>

    <div class="pd-main">
      <div class="gallery">
        <div class="g-main">
          <img v-if="spu.picUrl" :src="spu.picUrl" :alt="spu.name" />
          <div v-else class="ph g-main-ph">暂无图片</div>
        </div>
        <div class="g-thumbs">
          <div v-for="(pic, i) in spu.sliderPicUrls" :key="pic" class="g-thumb" :class="{ active: i === 0 }">
            <img :src="pic" :alt="`${spu.name} 图 ${i + 1}`" />
          </div>
        </div>
      </div>

      <div class="pd-info">
        <h1 class="pd-name">{{ spu.name }}</h1>
        <p v-if="spu.introduction" class="pd-sub">{{ spu.introduction }}</p>

        <div class="price-box">
          <div class="price-row">
            <span class="pd-price">{{ priceText }}</span>
            <span v-if="marketText" class="pd-orig">{{ marketText }}</span>
          </div>
          <p class="ship-tip">
            库存 {{ stock }} 件 · 已售 {{ spu.salesCount }} 件
          </p>
        </div>

        <!--
          规格选择。这里**同时承担设计稿「规格参数」表的位置**（FR-005a）：
          显示规格名与规格值，不再另做一张产品参数表。
        -->
        <template v-if="isMultiSpec">
          <p class="spec-label">选择规格</p>
          <div class="spec-row">
            <span
              v-for="s in skus"
              :key="s.id"
              class="spec"
              :class="{ active: s.id === currentSkuId, disabled: s.stock <= 0 }"
              @click="pickSku(s)"
            >
              <template v-for="p in s.properties" :key="p.valueId">{{ p.propertyName }}：{{ p.valueName }}</template>
              <em v-if="s.stock <= 0" class="spec-out">无货</em>
            </span>
          </div>
        </template>

        <div class="qty-row">
          <span class="spec-label">购买数量</span>
          <div class="qty-box">
            <span class="qty-btn" @click="stepQty(-1)">−</span>
            <span class="qty-num">{{ qty }}</span>
            <span class="qty-btn plus" @click="stepQty(1)">+</span>
          </div>
        </div>

        <div class="btn-row">
          <button class="btn-buy" type="button" @click="onBuyNow">立即购买</button>
          <button class="btn-cart" type="button" @click="onAddToCart">加入购物车</button>
        </div>

        <p class="svc-line">✓ 正品保障 &nbsp;&nbsp; ✓ 极速发货 &nbsp;&nbsp; ✓ 一年质保 &nbsp;&nbsp; ✓ 7天无理由退换</p>
      </div>
    </div>

    <!--
      商品详情富文本。内容经 sanitizeRichText 白名单过滤（FR-005c / SC-020）；
      为空或过滤后无实际内容时整块不渲染（FR-005b）。
    -->
    <div v-if="safeDescription" class="params">
      <h2>商品详情</h2>
      <div class="pd-desc" v-html="safeDescription"></div>
    </div>
  </template>
</template>
