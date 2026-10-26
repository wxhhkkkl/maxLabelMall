<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEnterprisePlan, type EnterprisePlan } from '@/api/enterprisePlan'
import { getMyOrg } from '@/api/org'
import { hasVisibleContent, sanitizeRichText } from '@/utils/sanitize'

const props = defineProps<{ skuId: string }>()
const route = useRoute()
const router = useRouter()
const plan = ref<EnterprisePlan | null>(null)
const count = ref(1)
const currentSeats = ref(0)
const loading = ref(true)
const error = ref('')
const purchaseError = ref('')

const skuId = computed(() => Number(props.skuId))
const totalPrice = computed(() => (plan.value ? plan.value.price * count.value : 0))
const maxCount = computed(() => (plan.value?.stock && plan.value.stock > 0 ? plan.value.stock : 9999))
const minCount = computed(() => Math.max(1, currentSeats.value))
const isRenew = computed(() => route.query.renew === '1')
const safeDescription = computed(() => {
  const raw = plan.value?.description ?? ''
  if (!raw.trim()) return ''
  const clean = sanitizeRichText(raw)
  return hasVisibleContent(clean) ? clean : ''
})

function periodLabel(months: number): string {
  if (months % 12 === 0) return `${months / 12} 年`
  return `${months} 个月`
}

function priceLabel(price: number): string {
  return `¥${(price / 100).toFixed(2)}`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    plan.value = await getEnterprisePlan(skuId.value)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '企业商品加载失败'
  } finally {
    loading.value = false
  }
}

async function loadCurrentSeats(): Promise<void> {
  try {
    const org = await getMyOrg()
    currentSeats.value = org.seatTotal ?? 0
    count.value = Math.max(1, currentSeats.value)
  } catch {
    currentSeats.value = 0
  }
}

function buy(): void {
  if (!plan.value) return
  if (count.value < minCount.value) {
    purchaseError.value = `购买席位数不能少于当前 ${minCount.value} 个`
    return
  }
  purchaseError.value = ''
  void router.push({
    path: '/checkout',
    query: {
      source: 'product',
      skuId: String(plan.value.skuId),
      count: String(count.value),
      enterprise: '1',
    },
  })
}

onMounted(async () => {
  await load()
  await loadCurrentSeats()
})
</script>

<template>
  <main class="ent-plan">
    <section class="ent-plan-hero">
      <div class="ent-plan-container">
        <p class="ent-plan-eyebrow">
          企业授权
        </p>
        <h1>按席位购买，分配给旗下成员</h1>
        <p class="ent-plan-lead">
          一个购买单位 = 一个席位 × 一个周期。付款后自动生成企业授权批次，无需激活码。
        </p>
      </div>
    </section>

    <section class="ent-plan-container ent-plan-content" aria-live="polite">
      <p v-if="loading" class="ent-plan-state">
        正在加载企业商品…
      </p>
      <p v-else-if="error" class="ent-plan-state ent-plan-error">
        {{ error }}
      </p>
      <div v-else-if="plan" class="ent-plan-card">
        <img v-if="plan.picUrl" :src="plan.picUrl" :alt="plan.name" class="ent-plan-pic" />
        <div class="ent-plan-info">
          <h2>{{ plan.name }}</h2>
          <p class="ent-plan-period">
            {{ periodLabel(plan.periodMonths) }}
          </p>
          <p class="ent-plan-price">
            {{ priceLabel(plan.price) }} <small>/ 席位</small>
          </p>
          <div v-if="safeDescription" class="ent-plan-desc" v-html="safeDescription" />
          <label class="ent-plan-count">
            席位数量
            <input
              v-model.number="count"
              type="number"
              :min="minCount"
              :max="maxCount"
              @input="purchaseError = ''"
            />
          </label>
          <p v-if="purchaseError" class="ent-plan-purchase-error">
            {{ purchaseError }}
          </p>
          <p class="ent-plan-total">
            合计：{{ priceLabel(totalPrice) }}
          </p>
          <p class="ent-plan-hint">
            <span v-if="currentSeats > 0">
              购买席位数不能少于当前 {{ currentSeats }} 个席位；再次购买时，当前未用尽的时间会按
              新的席位数折算（例如当前 1 席还剩 10 个月，改买 2 席，有效期会按 2 席摊平）。
            </span>
            企业授权订单若开票，只能使用企业类型抬头，税号由买家填写；也可以不开发票。
          </p>
          <button type="button" class="ent-plan-buy" @click="buy">
            {{ isRenew ? '续费' : '立即购买' }}
          </button>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.ent-plan {
  color: #16233f;
  background: #f5f8ff;
  min-height: 60vh;
}
.ent-plan-container {
  width: min(1100px, calc(100% - 48px));
  margin: 0 auto;
}
.ent-plan-hero {
  padding: 64px 0 40px;
  background: #eaf1ff;
}
.ent-plan-eyebrow {
  color: #2e7cd6;
  font-size: 14px;
  letter-spacing: 0.08em;
}
.ent-plan-hero h1 {
  margin: 12px 0;
  font-size: 40px;
  line-height: 1.2;
}
.ent-plan-lead {
  max-width: 620px;
  color: #5b6c8f;
  font-size: 16px;
  line-height: 1.8;
}
.ent-plan-content {
  padding: 40px 0 72px;
}
.ent-plan-state {
  padding: 32px;
  text-align: center;
  color: #5b6c8f;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
}
.ent-plan-error {
  color: #ff5c22;
}
.ent-plan-card {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 32px;
  padding: 28px;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
}
.ent-plan-pic {
  width: 100%;
  border-radius: 12px;
  object-fit: cover;
}
.ent-plan-info h2 {
  margin: 0 0 12px;
  font-size: 26px;
}
.ent-plan-period {
  color: #2e7cd6;
  font-weight: 600;
}
.ent-plan-price {
  margin: 8px 0;
  color: #0c2148;
  font-size: 30px;
  font-weight: 700;
}
.ent-plan-price small {
  color: #8a97b5;
  font-size: 14px;
  font-weight: 400;
}
.ent-plan-desc {
  color: #5b6c8f;
  line-height: 1.8;
}
.ent-plan-count {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
  color: #5b6c8f;
}
.ent-plan-count input {
  width: 120px;
  padding: 8px 10px;
  border: 1px solid #e3e9f4;
  border-radius: 8px;
}
.ent-plan-total {
  color: #0c2148;
  font-size: 20px;
  font-weight: 600;
}
.ent-plan-hint {
  color: #8a97b5;
  font-size: 13px;
}
.ent-plan-purchase-error {
  margin: 0 0 12px;
  color: #ff5c22;
  font-size: 13px;
}
.ent-plan-buy {
  padding: 12px 24px;
  border: 0;
  border-radius: 8px;
  background: #2e7cd6;
  color: #ffffff;
  font-size: 16px;
  cursor: pointer;
}
@media (max-width: 1100px) {
  .ent-plan-card { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .ent-plan-hero h1 { font-size: 30px; }
}
@media (max-width: 480px) {
  .ent-plan-container { width: calc(100% - 32px); }
  .ent-plan-hero { padding: 44px 0 28px; }
}
</style>
