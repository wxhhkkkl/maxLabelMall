<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { pageComments } from '@/api/comment'
import { getProductDetail } from '@/api/product'
import CommentList from '@/components/CommentList.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useToasts } from '@/components/base/useToasts'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { formatYuan } from '@/utils/money'
import { hasVisibleContent, sanitizeRichText } from '@/utils/sanitize'
import type { ProductComment, ProductSpu, ProductSku } from '@/types'
import '@/styles/product-detail.css'

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
const qtyInput = ref('1')
watch(qty, value => { qtyInput.value = String(value) })
const detailTab = ref<'description' | 'specs' | 'support'>('description')
const shownTab = computed(() => detailTab.value === 'description' && !safeDescription.value ? 'specs' : detailTab.value)
const selectedSpecText = computed(() => currentSku.value?.properties?.map(p => `${p.propertyName}：${p.valueName}`).join(' / ') || '标准规格')

const skus = computed<ProductSku[]>(() => spu.value?.skus ?? [])
const isMultiSpec = computed(() => !!spu.value?.specType && skus.value.length > 0)

/** 当前选中规格；单规格商品取唯一 SKU，多规格未选时为空 */
const currentSku = computed<ProductSku | null>(() => {
  if (!isMultiSpec.value) return skus.value[0] ?? null
  return skus.value.find((s) => s.id === currentSkuId.value) ?? null
})

/**
 * 当前规格的图。**只在多规格时生效**。
 *
 * 单规格不能走这条：线上 282 个单规格商品的 SKU 图都是全站占位图
 * （`/assets/logo.png`，首轮导入时统一填的），让 SKU 图优先会把商品封面顶掉。
 * 多规格的 SKU 图则来自旧商城的规格图（已传到 OSS），是真的。
 */
const specPic = computed(() => (isMultiSpec.value ? currentSku.value?.picUrl || '' : ''))

/**
 * 图集 = 封面 + **当前规格图** + 轮播图，**去重**。
 * 后端把封面放在 `picUrl`、轮播图放在 `sliderPicUrls`，两者**可能重复**
 * （实测的示例商品就是两张同一张图），不去重会出现两个一模一样的缩略图。
 * 规格图放进去是为了让用户点走之后还能点回来。
 */
const galleryPics = computed<string[]>(() => {
  const list = [spu.value?.picUrl, specPic.value, ...(spu.value?.sliderPicUrls ?? [])]
  return [...new Set(list.filter((p): p is string => !!p))]
})

/** 用户点过的图；为空表示还没点过，主图按 规格图 → 图集第一张 回落 */
const pickedPic = ref('')
const mainPic = computed(() => pickedPic.value || specPic.value || galleryPics.value[0] || '')

function pickPic(pic: string): void {
  pickedPic.value = pic
}

/** 展示价：多规格未选时用 SPU 的价格区间下界 */
const price = computed(() => currentSku.value?.price ?? spu.value?.price ?? 0)
const marketPrice = computed(() => currentSku.value?.marketPrice ?? spu.value?.marketPrice ?? 0)
const stock = computed(() => currentSku.value?.stock ?? spu.value?.stock ?? 0)

const priceText = computed(() => formatYuan(price.value))
const marketText = computed(() => (marketPrice.value > 0 ? formatYuan(marketPrice.value) : ''))

/**
 * 富文本：过滤后仍需判断是否为空 —— 空或只有空白时整块不渲染（FR-005b）。
 *
 * ⚠️ 判"有没有内容"必须用 {@link hasVisibleContent}，**不能剥掉标签看还剩不剩文字** ——
 * 那会把**纯图片的详情**（长图、参数图，电商里的常态）判成空、整块隐藏（FR-064）。
 */
const safeDescription = computed(() => {
  const raw = spu.value?.description ?? ''
  if (!raw.trim()) return ''
  const clean = sanitizeRichText(raw)
  return hasVisibleContent(clean) ? clean : ''
})

function pickSku(s: ProductSku) {
  if (s.stock <= 0) return
  currentSkuId.value = s.id
  qty.value = 1
  // 清掉"用户点过的那张图"，让新规格的图接管主图；否则翻了图再切规格会看不到变化
  pickedPic.value = ''
}

function commitQty() {
  const value = Number(qtyInput.value)
  qty.value = Math.max(1, Math.min(Number.isFinite(value) ? Math.floor(value) : 1, Math.max(1, stock.value)))
  qtyInput.value = String(qty.value)
}

function stepQty(d: number) {
  commitQty()
  const next = qty.value + d
  qty.value = Math.max(1, Math.min(next, Math.max(1, stock.value)))
}

/**
 * 取当前可下单的 SKU。多规格商品在**没有任何有货规格**（也就无从预选）时为空 ——
 * 此时必须明确提示而不是拿一个空 skuId 去加购或结算（FR-017 / FR-018）。
 */
function requireSku(): ProductSku | null {
  commitQty()
  if (currentSku.value) {
    if (currentSku.value.stock <= 0) { toast.warn('当前规格暂时缺货'); return null }
    if (qty.value > currentSku.value.stock) { toast.warn('购买数量超过可用库存'); return null }
    return currentSku.value
  }
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

/**
 * 评价列表。**`null` 与 `[]` 是两回事**：
 * - `null` = 还没拿到、或没拿到（接口挂了）→ **不渲染评价区**；
 * - `[]`   = 拿到了但一条评价都没有 → 按 FR-069 **明说"暂无评价"**，不留空白。
 */
const comments = ref<ProductComment[] | null>(null)
const commentTotal = ref(0)

/**
 * 拉评价。
 *
 * ⚠️ **不 await、失败也不上抛**：评价是辅助区块，详情页在支付链路上，
 * 为一个副区块白屏的代价远大于收益（plan 风险 R1）。
 */
async function loadComments() {
  try {
    const res = await pageComments({ spuId: Number(props.id), pageSize: 5 })
    comments.value = res.list ?? []
    commentTotal.value = res.total ?? 0
  } catch {
    comments.value = null
    commentTotal.value = 0
  }
}

function goAllComments() {
  void router.push(`/product/${props.id}/comments`)
}

async function load() {
  loading.value = true
  notFound.value = false
  // 换商品时先清掉，免得短暂显示上一个商品的评价
  comments.value = null
  try {
    const detail = await getProductDetail(Number(props.id))
    spu.value = detail
    const list = detail.skus ?? []
    // 多规格默认选第一个**有货**的规格；全都无货则不预选
    currentSkuId.value = list.find((s) => s.stock > 0)?.id ?? null
    // 换了商品要把"用户点过的那张图"清掉，否则会显示上一个商品选中的图
    pickedPic.value = ''
    // 不 await —— 评价区不阻塞详情渲染
    void loadComments()
    qty.value = 1
    detailTab.value = 'description'
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
  <div class="product-page">
    <LoadingState
      v-if="loading"
      :count="3"
    />

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
        <RouterLink to="/">
          首页
        </RouterLink> / <RouterLink to="/mall">
          商城
        </RouterLink> /
        <span class="current">{{ spu.name }}</span>
      </div>

      <div class="pd-main">
        <div class="gallery">
          <div class="g-main">
            <img
              v-if="mainPic"
              :src="mainPic"
              :alt="spu.name"
            >
            <div
              v-else
              class="ph g-main-ph"
            >
              暂无图片
            </div>
          </div>
          <div class="g-thumbs">
            <button
              v-for="(pic, i) in galleryPics"
              :key="pic"
              class="g-thumb"
              type="button"
              :aria-label="`查看商品图片 ${i + 1}`"
              :aria-pressed="pic === mainPic"
              :class="{ active: pic === mainPic }"
              @click="pickPic(pic)"
            >
              <img
                :src="pic"
                :alt="`${spu.name} 图 ${i + 1}`"
              >
            </button>
          </div>
        </div>

        <div class="pd-info">
          <div class="product-eyebrow">
            赋签商城 <span>·</span> {{ isMultiSpec ? '多规格可选' : '标准规格' }}
          </div>
          <h1 class="pd-name">
            {{ spu.name }}
          </h1>
          <p
            v-if="spu.introduction"
            class="pd-sub"
          >
            {{ spu.introduction }}
          </p>

          <div class="price-box">
            <div class="price-row">
              <span class="pd-price">{{ priceText }}</span>
              <span
                v-if="marketText"
                class="pd-orig"
              >{{ marketText }}</span>
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
            <p class="spec-label">
              选择规格
            </p>
            <div class="spec-row">
              <button
                v-for="s in skus"
                :key="s.id"
                class="spec"
                type="button"
                :disabled="s.stock <= 0"
                :aria-pressed="s.id === currentSkuId"
                :class="{ active: s.id === currentSkuId, disabled: s.stock <= 0 }"
                @click="pickSku(s)"
              >
                <template
                  v-for="p in s.properties"
                  :key="p.valueId"
                >
                  {{ p.propertyName }}：{{ p.valueName }}
                </template>
                <em
                  v-if="s.stock <= 0"
                  class="spec-out"
                >无货</em>
              </button>
            </div>
          </template>

          <div class="qty-row">
            <span class="spec-label">购买数量</span>
            <div class="qty-box">
              <button
                class="qty-btn"
                type="button"
                aria-label="减少数量"
                :disabled="qty <= 1"
                @click="stepQty(-1)"
              >
                −
              </button>
              <input
                v-model="qtyInput"
                class="qty-num"
                type="number"
                inputmode="numeric"
                aria-label="购买数量"
                min="1"
                :max="Math.max(1, stock)"
                step="1"
                :disabled="stock <= 0"
                @change="commitQty"
                @blur="commitQty"
                @keydown.enter.prevent="commitQty"
              >
              <button
                class="qty-btn plus"
                type="button"
                aria-label="增加数量"
                :disabled="qty >= stock"
                @click="stepQty(1)"
              >
                +
              </button>
            </div>
          </div>

          <p class="product-selection">
            <span>已选</span>{{ selectedSpecText }} <span>／ {{ qty }} 件</span>
          </p>
          <div class="btn-row">
            <button
              class="btn-buy"
              type="button"
              @click="onBuyNow"
            >
              立即购买
            </button>
            <button
              class="btn-cart"
              type="button"
              @click="onAddToCart"
            >
              加入购物车
            </button>
          </div>

          <div class="product-consult">
            <div><strong>型号或耗材不确定？</strong><p>提供设备型号与使用场景，帮你确认搭配。</p></div><RouterLink to="/support/contact">
              咨询选型 →
            </RouterLink>
          </div>
        </div>
      </div>

      <!--
      商品详情富文本。内容经 sanitizeRichText 白名单过滤（FR-005c / SC-020）；
      为空或过滤后无实际内容时整块不渲染（FR-005b）。
    -->
      <section class="product-information">
        <div
          class="product-tabs"
          role="tablist"
          aria-label="商品信息"
        >
          <button
            v-if="safeDescription"
            id="product-tab-description"
            type="button"
            role="tab"
            :aria-selected="shownTab === 'description'"
            aria-controls="product-panel"
            @click="detailTab = 'description'"
          >
            商品详情
          </button><button
            id="product-tab-specs"
            type="button"
            role="tab"
            :aria-selected="shownTab === 'specs'"
            aria-controls="product-panel"
            @click="detailTab = 'specs'"
          >
            规格与库存
          </button><button
            id="product-tab-support"
            type="button"
            role="tab"
            :aria-selected="shownTab === 'support'"
            aria-controls="product-panel"
            @click="detailTab = 'support'"
          >
            选购与服务
          </button>
        </div>
        <div
          id="product-panel"
          role="tabpanel"
          :aria-labelledby="`product-tab-${shownTab}`"
        >
          <div
            v-if="safeDescription && shownTab === 'description'"
            class="params"
          >
            <h2>商品详情</h2>
            <div
              class="pd-desc"
              v-html="safeDescription"
            />
          </div>
          <div
            v-else-if="shownTab === 'specs'"
            class="product-specifications"
          >
            <h2>规格与库存</h2><p>选择适合的规格，价格和库存以当前商品信息为准。</p><div class="product-spec-table">
              <div class="product-spec-table-head">
                <span>商品规格</span><span>售价</span><span>库存</span>
              </div><div
                v-for="s in skus"
                :key="s.id"
                class="product-spec-table-row"
              >
                <span>{{ s.properties?.map(p => `${p.propertyName}：${p.valueName}`).join(' / ') || '标准规格' }}</span><span>{{ formatYuan(s.price) }}</span><span>{{ s.stock > 0 ? `${s.stock} 件` : '暂时缺货' }}</span>
              </div><p v-if="!skus.length">
                暂无可售规格，请联系工作人员确认。
              </p>
            </div>
          </div>
          <div
            v-else
            class="product-service"
          >
            <h2>购买前，确认这三件事</h2><div><section><span>01</span><h3>确认规格</h3><p>核对尺寸、材质和设备适配情况，避免型号或耗材搭配不合适。</p></section><section><span>02</span><h3>确认数量</h3><p>批量采购或特殊应用，可先联系张经理确认需求与交付安排。</p></section><section><span>03</span><h3>获取使用帮助</h3><p>安装、装纸与打印排查，可查看服务支持或联系工作人员。</p></section></div><RouterLink to="/support/contact">
              联系张经理 →
            </RouterLink><RouterLink to="/support">
              查看服务支持 →
            </RouterLink>
          </div>
        </div>
      </section>
      <section
        v-if="comments"
        class="params pd-comments product-information"
      >
        <h2>用户评价</h2>
        <CommentList
          :comments="comments"
          :show-view-all="commentTotal > comments.length"
          @view-all="goAllComments"
        />
      </section>
    </template>
  </div>
</template>
