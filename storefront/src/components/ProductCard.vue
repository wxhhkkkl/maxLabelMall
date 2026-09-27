<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getProductDetail } from '@/api/product'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import type { ProductSpu } from '@/types'
import { formatYuan } from '@/utils/money'

import { useToasts } from './base/useToasts'

/**
 * 商品卡片 —— 复用设计稿的 class 名（`.p-card` / `.p-img` / `.p-tags` / `.p-tag`
 * / `.p-title` / `.p-sell` / `.p-price` / `.p-btn`），因此 `design.css` 直接生效。
 *
 * 三条约束：
 *   · 角标由 `badge` prop 传入（值由 `deriveBadges` 从真实数据派生，见 utils/badge.ts）；
 *     无角标时**不渲染空的角标容器**。**永不出现「旗舰」「订阅」**。
 *   · **不渲染促销活动标签**（FR-026h）—— 后端不提供面向用户的促销查询能力。
 *   · 价格以**分**传入，展示经 `utils/money` 统一换算。
 */
const props = defineProps<{
  spu: ProductSpu
  /** 由 deriveBadges 派生；null 表示无角标 */
  badge?: 'hot' | 'fresh' | null
}>()

const emit = defineEmits<{ add: [ProductSpu] }>()

const router = useRouter()
const route = useRoute()
const cartStore = useCartStore()
const userStore = useUserStore()
const toast = useToasts()

const priceText = computed(() => formatYuan(props.spu.price))
/** marketPrice 为 0 视为没有划线价 */
const marketText = computed(() =>
  props.spu.marketPrice > 0 ? formatYuan(props.spu.marketPrice) : '',
)

const BADGE_TEXT: Record<'hot' | 'fresh', string> = { hot: '热销', fresh: '新品' }
const BADGE_CLASS: Record<'hot' | 'fresh', string> = { hot: 'hot', fresh: 'new' }

/**
 * 加购。
 *
 * 未登录时不发请求，而是**暂存加购意图并引导登录**（FR-015）—— 登录成功后
 * `LoginDialog` 会调 `flushPendingIntent()` 自动补上这次加购，用户不必重新点击。
 *
 * 单规格商品直接取唯一 SKU；多规格商品必须去详情页选规格（FR-018），
 * 所以卡片上的加购按钮对多规格商品只做引导，不做静默加购。
 */
async function onAdd(e: MouseEvent) {
  // 卡片整体是链接，加购按钮不应触发跳转
  e.preventDefault()
  e.stopPropagation()

  // ⚠️ **商城的列表接口不返回 `skus`**（`AppProductSpuRespVO` 里没有该字段），
  // 卡片手上只有 SPU。所以不能直接把 `spu.skus[0].id` 当 skuId —— 那会发出去
  // `{"skuId":0}` 被后端拒绝，用户看到的是「点了加购没反应」。
  //
  // 判断依据只能是「有没有 skus」，不能是 `specType`：单规格商品在列表里同样是
  // `specType:false` **且没有 skus**，照样得知道它那个唯一 SKU 的 id。所以 skus 为空
  // 就按需拉一次详情，并以**详情返回的** specType / skus 为准。
  // （组件测试的夹具自带 skus，所以只有 e2e 能发现这个问题。）
  let skus = props.spu.skus ?? []
  let specType = props.spu.specType
  if (skus.length === 0) {
    try {
      const detail = await getProductDetail(props.spu.id)
      skus = detail.skus ?? []
      specType = detail.specType
    } catch {
      toast.error('加入购物车失败，请重试')
      return
    }
  }

  if (specType && skus.length > 1) {
    toast.warn('请先选择规格')
    router.push(`/product/${props.spu.id}`)
    return
  }

  const skuId = skus[0]?.id
  if (!skuId) {
    toast.warn('请先选择规格')
    router.push(`/product/${props.spu.id}`)
    return
  }

  if (!userStore.isLogin) {
    // 暂存意图 + 打开登录弹层（沿用全站唯一的入口）。
    // cartStore.add 在未登录时**不发请求**，只把意图记下来；登录成功后由
    // LoginDialog 调 flushPendingIntent() 自动补上（FR-015）。
    await cartStore.add(skuId, 1)
    toast.warn('登录后将自动加入购物车')
    // 显式带 path：只给 query 时 router 会去匹配"当前位置"，在未匹配的页面上会抛错。
    // 另外导航失败（重复导航、被守卫中止）不该变成未处理的拒绝。
    void router
      .push({ path: route.path, query: { ...route.query, login: '1', redirect: route.fullPath } })
      .catch(() => undefined)
    return
  }

  // 已登录：真正加购，并刷新顶栏角标（角标数量来自后端 get-count，不是本地累加）
  try {
    await cartStore.add(skuId, 1)
    toast.success('已加入购物车')
  } catch (e) {
    toast.error((e as { message?: string })?.message || '加入购物车失败，请重试')
    return
  }
  emit('add', props.spu)
}
</script>

<template>
  <RouterLink class="p-card" :to="`/product/${spu.id}`">
    <div v-if="spu.picUrl" class="p-img-wrap">
      <img class="p-img-real" :src="spu.picUrl" :alt="spu.name" loading="lazy" />
    </div>
    <!-- 无图时沿用设计稿的占位块，而不是渲染一个会坏掉的 img -->
    <div v-else class="ph p-img">暂无图片</div>

    <div v-if="badge" class="p-tags">
      <span class="p-tag" :class="BADGE_CLASS[badge]">{{ BADGE_TEXT[badge] }}</span>
    </div>

    <div class="p-title">{{ spu.name }}</div>
    <div v-if="spu.introduction" class="p-sell">{{ spu.introduction }}</div>

    <!-- 售价与划线价是**兄弟元素**（与详情页的 .price-row 一致），
         不是把划线价塞进 .p-price 里 -->
    <div class="p-price-row">
      <span class="p-price">{{ priceText }}</span>
      <span v-if="marketText" class="p-orig">{{ marketText }}</span>
    </div>

    <button class="p-btn" type="button" @click="onAdd">加入购物车</button>
  </RouterLink>
</template>
