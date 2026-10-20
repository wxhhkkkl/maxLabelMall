<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listSubscriptionPlans, type SubscriptionPlan } from '@/api/subscription'

const router = useRouter()
const plans = ref<SubscriptionPlan[]>([])
const loading = ref(true)
const error = ref('')

function periodLabel(months: number): string {
  if (months % 12 === 0) return `${months / 12} 年`
  return `${months} 个月`
}

function priceLabel(price: number): string {
  return `¥${(price / 100).toFixed(2)}`
}

function openPlan(plan: SubscriptionPlan): void {
  void router.push(`/product/${plan.spuId}`)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    plans.value = await listSubscriptionPlans()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '会员授权加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="membership-page">
    <section class="membership-hero">
      <div class="membership-container">
        <p class="membership-eyebrow">
          MaxLabel 会员授权
        </p>
        <h1>一次开通，持续使用</h1>
        <p class="membership-lead">
          会员授权用于云端模板、共享模板与商城会员身份。选择周期后进入商城结算。
        </p>
      </div>
    </section>

    <section class="membership-container membership-content" aria-live="polite">
      <p v-if="loading" class="membership-state">
        正在加载会员授权…
      </p>
      <p v-else-if="error" class="membership-state membership-error">
        {{ error }}
      </p>
      <p v-else-if="plans.length === 0" class="membership-state">
        暂无可购买的会员授权，请联系客服。
      </p>
      <div v-else class="membership-grid">
        <article v-for="plan in plans" :key="plan.skuId" class="membership-card">
          <p class="membership-period">
            {{ periodLabel(plan.periodMonths) }}
          </p>
          <h2>{{ plan.name }}</h2>
          <p class="membership-price">
            {{ priceLabel(plan.price) }}
          </p>
          <p class="membership-stock">
            库存 {{ plan.stock ?? '—' }}
          </p>
          <button type="button" @click="openPlan(plan)">
            选择该周期
          </button>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped>
.membership-page {
  color: #16233f;
  background: #f5f8ff;
  min-height: 60vh;
}
.membership-container {
  width: min(1100px, calc(100% - 48px));
  margin: 0 auto;
}
.membership-hero {
  padding: 64px 0 40px;
  background: #eaf1ff;
}
.membership-eyebrow {
  color: #2e7cd6;
  font-size: 14px;
  letter-spacing: 0.08em;
}
.membership-hero h1 {
  margin: 12px 0;
  font-size: 40px;
  line-height: 1.2;
}
.membership-lead {
  max-width: 620px;
  color: #5b6c8f;
  font-size: 16px;
  line-height: 1.8;
}
.membership-content {
  padding: 40px 0 72px;
}
.membership-state {
  padding: 32px;
  text-align: center;
  color: #5b6c8f;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
}
.membership-error {
  color: #ff5c22;
}
.membership-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.membership-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 28px;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
}
.membership-period {
  color: #2e7cd6;
  font-weight: 600;
}
.membership-card h2 {
  margin: 0;
  font-size: 20px;
}
.membership-price {
  margin: 8px 0 0;
  color: #0c2148;
  font-size: 30px;
  font-weight: 700;
}
.membership-stock {
  margin: 0;
  color: #8a97b5;
  font-size: 13px;
}
.membership-card button {
  margin-top: auto;
  padding: 10px 16px;
  border: 0;
  border-radius: 8px;
  background: #2e7cd6;
  color: #ffffff;
  font-size: 15px;
  cursor: pointer;
}
@media (max-width: 1100px) {
  .membership-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .membership-hero h1 { font-size: 30px; }
  .membership-grid { grid-template-columns: 1fr; }
}
@media (max-width: 480px) {
  .membership-container { width: calc(100% - 32px); }
  .membership-hero { padding: 44px 0 28px; }
}
</style>
