<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { pageMyCoupons, type CouponStatusFilter } from '@/api/coupon'
import AccountSidebar from '@/components/AccountSidebar.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import type { Coupon } from '@/types'
import { couponAmountText, couponThresholdText, couponValidText } from '@/utils/coupon'

/**
 * 我的券 —— 按 design-new-pages.md §3.8 实现（无设计稿）。
 *
 * 券卡结构与领券中心（§3.7）一致，差别只在操作区：这里给「去使用」而不是「领取」。
 *
 * ⚠️ **本页不判断券能不能用** —— 券的可用性只在结算时由后端裁决
 * （`coupons[].match` / `mismatchReason`，FR-026b）。这里只按 `status` 分组展示。
 */

const TABS = [
  { status: 1, label: '未使用' },
  { status: 2, label: '已使用' },
  { status: 3, label: '已过期' },
] as const

const STATUS_TEXT: Record<number, string> = { 1: '未使用', 2: '已使用', 3: '已过期' }

const active = ref<CouponStatusFilter>(1)
const coupons = ref<Coupon[]>([])
const loading = ref(true)
const error = ref(false)

async function load() {
  loading.value = true
  error.value = false
  try {
    const res = await pageMyCoupons({ pageNo: 1, pageSize: 50, status: active.value })
    coupons.value = res.list
  } catch {
    error.value = true
    coupons.value = []
  } finally {
    loading.value = false
  }
}

function onPick(status: CouponStatusFilter) {
  active.value = status
  load()
}

/**
 * 「去使用」的目的地。
 *
 * 券限定到**单个商品**（`productScope=2`）时可以直接落到那件商品的详情页；
 * 其余情况落到商城。
 *
 * ⚠️ 限定**品类**（`productScope=3`）暂时只能落到商城，不能落到该品类下 ——
 * 商城的筛选状态是组件内部的 ref，不从 URL 查询参数读，带不过去。
 * 这一处是**已知缺口**，记在 tasks.md 里，不静默当成已实现。
 */
function useLink(c: Coupon): string {
  if (c.productScope === 2 && c.productScopeValues.length === 1) {
    return `/product/${c.productScopeValues[0]}`
  }
  return '/mall'
}

onMounted(load)
</script>

<template>
  <div class="account-section-page">
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <RouterLink to="/account">个人中心</RouterLink> / <span>我的券</span>
  </div>

  <div class="account-wrap">
    <AccountSidebar />
    <!-- 与个人中心同一套两列布局：侧栏常驻，点进子页不丢菜单 -->
    <div class="account-main account-section-main">
      <div class="account-page-heading">
        <div><h1>我的券</h1><p>查看优惠券与使用记录，结算时按需选用。</p></div>
        <RouterLink class="account-heading-link" to="/coupon">去领券中心 <span aria-hidden="true">→</span></RouterLink>
      </div>
      <div class="account-content-card account-coupon-content">
      <div class="cat-pills account-tabs" aria-label="优惠券状态">
        <button
          v-for="t in TABS"
          :key="t.status"
          class="tab"
          :class="{ active: active === t.status }"
          :aria-pressed="active === t.status"
          type="button"
          @click="onPick(t.status)"
        >
          {{ t.label }}
        </button>
      </div>

      <LoadingState v-if="loading" :count="3" />

      <EmptyState
        v-else-if="error"
        mode="error"
        title="优惠券加载失败"
        desc="网络或服务暂时不可用，请稍后重试"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!coupons.length"
        :title="`暂无${STATUS_TEXT[active]}的优惠券`"
        desc="去领券中心看看有什么可以领"
        action-text="去领券中心"
        @action="$router.push('/coupon')"
      />

      <div v-else class="grid-3 mc-grid account-coupon-grid">
        <div v-for="c in coupons" :key="c.id" class="mc-card" :class="{ 'is-inactive': c.status !== 1 }">
          <div class="mc-amount">
            <span class="mc-num">{{ couponAmountText(c) }}</span>
            <span class="mc-thresh">{{ couponThresholdText(c) }}</span>
          </div>
          <div class="mc-info">
            <div class="mc-name">{{ c.name }}</div>
            <div v-if="couponValidText(c)" class="mc-valid">{{ couponValidText(c) }}</div>
          </div>
          <!-- 只有「未使用」才有可去的地方；已使用/已过期是灰态，不给操作 -->
          <RouterLink v-if="c.status === 1" class="btn-cyan mc-use" :to="useLink(c)">
            去使用
          </RouterLink>
          <span v-else class="mc-status">{{ STATUS_TEXT[c.status] }}</span>
        </div>
      </div>
      </div>
    </div>
  </div>
  </div>
</template>

<!-- Coupon cards use the shared account styles in store.css. -->
