<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listCouponTemplates, takeCoupon } from '@/api/coupon'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useToasts } from '@/components/base/useToasts'
import { useUserStore } from '@/store/user'
import type { CouponTemplate } from '@/types'
import { couponAmountText, couponThresholdText, couponValidText } from '@/utils/coupon'

/**
 * 领券中心 —— 按 design-new-pages.md §3.7 实现（无设计稿）。
 *
 * 三条约束：
 *
 * 1. **能不能领由后端说**（`canTake`，`getUserCanCanTakeMap` 按当前用户算好）。
 *    前端不自己判断"这人还能领几张"—— 那需要知道已领张数与限领数，是服务端的知识。
 *
 * 2. **重复领取不得展示成两张**（FR-026c）。领取成功后本地立刻把该模板标成不可领，
 *    而不是重新拉一次列表（后端本来也只会给一张凭证）。
 *
 * 3. 未登录时**先引导登录**（§3.7 的状态定义），登录入口仍只有顶栏那一个弹层。
 */

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const toast = useToasts()

const templates = ref<CouponTemplate[]>([])
const loading = ref(true)
const error = ref(false)
/** 正在领取的模板 id（防重复点击） */
const takingId = ref<number | null>(null)

async function load() {
  loading.value = true
  error.value = false
  try {
    templates.value = await listCouponTemplates({ count: 20 })
  } catch {
    error.value = true
    templates.value = []
  } finally {
    loading.value = false
  }
}

async function onTake(t: CouponTemplate) {
  if (!t.canTake || takingId.value !== null) return

  // 未登录：先引导登录（沿用全站唯一的弹层入口），登录后用户再点一次
  if (!userStore.isLogin) {
    toast.warn('登录后即可领取')
    void router
      .push({
        path: route.path,
        query: { ...route.query, login: '1', redirect: route.fullPath },
      })
      .catch(() => undefined)
    return
  }

  takingId.value = t.id
  try {
    await takeCoupon(t.id)
    // 领到了就把这张标成不可领 —— 不再展示第二张凭证（FR-026c）
    t.canTake = false
    toast.success('领取成功，可在「我的券」查看')
  } catch (e) {
    toast.error((e as { message?: string })?.message || '领取失败，请稍后重试')
  } finally {
    takingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <span>领券中心</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ml-page-title">领券中心</h1>
    <p class="ml-page-sub">
      领取后可在「我的券」查看，结算时选用。
      <RouterLink class="cc-mine" to="/coupon/mine">我的券 ›</RouterLink>
    </p>
  </div>

  <div class="ml-wrap">
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
      v-else-if="!templates.length"
      title="暂无可领取的优惠券"
      desc="敬请期待，新券会在这里出现"
      action-text="去商城逛逛"
      @action="router.push('/mall')"
    />

    <div v-else class="grid-3 cc-grid">
      <div v-for="t in templates" :key="t.id" class="cc-card">
        <div class="cc-amount">
          <span class="cc-num">{{ couponAmountText(t) }}</span>
          <span class="cc-thresh">{{ couponThresholdText(t) }}</span>
        </div>
        <div class="cc-info">
          <div class="cc-name">{{ t.name }}</div>
          <div v-if="t.description" class="cc-desc">{{ t.description }}</div>
          <div v-if="couponValidText(t)" class="cc-valid">{{ couponValidText(t) }}</div>
        </div>
        <button
          v-if="t.canTake"
          class="btn-primary cc-take"
          type="button"
          :disabled="takingId === t.id"
          @click="onTake(t)"
        >
          {{ takingId === t.id ? '领取中…' : '立即领取' }}
        </button>
        <span v-else class="cc-taken">已领取</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cc-grid {
  align-items: start;
}
.cc-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #ffffff;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-card);
  padding: 18px;
}
/* 面额区：底与字色取自设计令牌，与全站同一套蓝 */
.cc-amount {
  background: #eaf1ff;
  border-radius: var(--ml-radius-field);
  padding: 14px 16px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.cc-num {
  font-size: 28px;
  font-weight: 700;
  color: #2e7cd6;
  font-variant-numeric: tabular-nums;
}
.cc-thresh {
  font-size: 13px;
  color: #2e7cd6;
}
.cc-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ml-text);
}
.cc-desc,
.cc-valid {
  font-size: 13px;
  color: var(--ml-text-sub);
  margin-top: 6px;
}
.cc-take {
  align-self: flex-start;
}
.cc-taken {
  align-self: flex-start;
  padding: 8px 18px;
  border-radius: 999px;
  background: #f0f4fb;
  color: var(--ml-text-ph);
  font-size: 14px;
}
.cc-mine {
  color: var(--ml-primary);
}
@media (max-width: 1100px) {
  .cc-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 768px) {
  .cc-grid {
    grid-template-columns: 1fr;
  }
}
</style>
