<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getMySubscription,
  listSubscriptionPlans,
  type MySubscription,
  type SubscriptionPlan,
} from '@/api/subscription'
import { useUserStore } from '@/store/user'

/**
 * 个人续费页 —— 标准版 / 专业版对比 + 当前订阅状态 + 周期直购。
 *
 * 三条约束：
 *
 * 1. **当前状态只认 `/member/subscription/get`**。桌面端「已登录即标准版」的
 *    判定在这里同样适用：登录但无有效订阅时展示标准版，不臆造到期时间。
 * 2. **周期按钮直接进结算页**（`/checkout?source=product&skuId=…`），不再经过
 *    商品详情页；会员授权商品本身也不在公开商城列表展示。
 * 3. 企业版由管理员续费，个人页不提供企业版周期按钮。
 */
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const plans = ref<SubscriptionPlan[]>([])
const subscription = ref<MySubscription | null>(null)
const loading = ref(true)
const error = ref('')
const subscriptionLoading = ref(false)
const subscriptionError = ref('')

const EDITION_LABELS: Record<string, string> = {
  standard: '标准版',
  professional: '专业版',
  enterprise: '企业版',
}

/** 标准版 / 专业版的差异（与桌面端功能矩阵一致，见 012 Q74）。 */
const COMPARISON = [
  { label: '标签编辑与打印', standard: true },
  { label: '数据库数据源（SQL Server / MySQL / SQLite）', standard: false },
  { label: '批量数据源（序列号 / 日期 / 键盘输入）', standard: false },
  { label: '打印记录与历史', standard: false },
]

/** 个人可购买的周期：只取专业版；旧数据没有 edition 时回退为全部周期。 */
const paidPlans = computed(() => {
  const professional = plans.value.filter((plan) => plan.edition === 'professional')
  return professional.length ? professional : plans.value
})

const currentEdition = computed(() => {
  if (!userStore.isLogin) return null
  return subscription.value?.edition ?? 'standard'
})
const currentEditionLabel = computed(() => {
  const edition = currentEdition.value
  if (!edition) return ''
  return EDITION_LABELS[edition] ?? edition
})
const isEnterprise = computed(() => currentEdition.value === 'enterprise')
const isProfessional = computed(() => currentEdition.value === 'professional')
const endDateLabel = computed(() => formatDate(subscription.value?.endDate ?? subscription.value?.endTime))
const hasExpiredSubscription = computed(
  () => userStore.isLogin && subscription.value?.active !== true && Boolean(endDateLabel.value),
)
const statusText = computed(() => {
  if (!userStore.isLogin) return ''
  if (isEnterprise.value) return '企业授权由管理员统一续费'
  if (subscription.value?.active === true) return '专业版已生效'
  if (hasExpiredSubscription.value) return `专业版已过期 · 到期日 ${endDateLabel.value}`
  return '当前为标准版，尚未开通专业版'
})
const renewableLabel = computed(() =>
  isProfessional.value || hasExpiredSubscription.value ? '立即续费' : '升级到专业版',
)

const cheapestPlan = computed(() => [...paidPlans.value].sort((a, b) => a.price - b.price)[0])
const proPriceLabel = computed(() => (cheapestPlan.value ? priceLabel(cheapestPlan.value.price) : '暂无可售周期'))
const proPriceUnit = computed(() =>
  cheapestPlan.value ? ` / ${periodLabel(cheapestPlan.value.periodMonths)} 起` : '',
)

function formatDate(value?: string): string {
  if (!value) return ''
  return value.slice(0, 10)
}

function periodLabel(months: number): string {
  if (months % 12 === 0) return `${months / 12} 年`
  return `${months} 个月`
}

function priceLabel(price: number): string {
  return `¥${(price / 100).toFixed(2)}`
}

async function loadPlans(): Promise<void> {
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

async function loadSubscription(): Promise<void> {
  subscriptionError.value = ''
  if (!userStore.isLogin) {
    subscription.value = null
    subscriptionLoading.value = false
    return
  }
  subscriptionLoading.value = true
  try {
    subscription.value = await getMySubscription()
  } catch (reason) {
    subscription.value = null
    subscriptionError.value = reason instanceof Error ? reason.message : '订阅状态加载失败'
  } finally {
    subscriptionLoading.value = false
  }
}

function openLogin(): void {
  void router.push({
    path: route.path,
    query: { ...route.query, login: '1', redirect: route.fullPath },
  })
}

function renew(plan: SubscriptionPlan): void {
  if (isEnterprise.value) return
  void router.push({
    path: '/checkout',
    query: { source: 'product', skuId: String(plan.skuId), count: '1' },
  })
}

onMounted(() => {
  void loadPlans()
  void loadSubscription()
})

watch(
  () => userStore.isLogin,
  () => {
    void loadSubscription()
  },
)
</script>

<template>
  <main class="membership-page">
    <section class="membership-hero">
      <div class="membership-container membership-head">
        <div class="membership-head-copy">
          <p class="membership-eyebrow">
            MaxLabel 个人会员
          </p>
          <h1>个人续费</h1>
          <p class="membership-lead">
            登录即享标准版；订阅专业版解锁数据库、批量数据与打印记录。选择周期后直接进入结算。
          </p>
        </div>

        <div class="membership-status" data-testid="membership-status">
          <template v-if="userStore.isLogin">
            <div class="membership-status-main">
              <span class="membership-status-label">当前订阅</span>
              <strong class="membership-status-edition">{{ subscriptionLoading ? '读取中…' : currentEditionLabel }}</strong>
              <span v-if="isEnterprise && subscription?.orgName" class="membership-status-org">
                企业授权 · {{ subscription.orgName }}
              </span>
            </div>
            <div class="membership-status-meta">
              <span v-if="subscriptionLoading">正在读取订阅状态…</span>
              <template v-else-if="subscription?.active === true && endDateLabel">
                <span>有效期至 {{ endDateLabel }}</span>
                <span v-if="typeof subscription.remainingDays === 'number'">剩余 {{ subscription.remainingDays }} 天</span>
              </template>
              <span v-else>{{ statusText }}</span>
            </div>
          </template>
          <template v-else>
            <div class="membership-status-main">
              <span class="membership-status-label">当前订阅</span>
              <strong class="membership-status-edition">未登录</strong>
            </div>
            <div class="membership-status-meta">
              <span>登录后查看当前订阅状态与到期时间</span>
              <button type="button" data-testid="membership-login" class="membership-link-button" @click="openLogin">
                登录查看
              </button>
            </div>
          </template>
        </div>
      </div>
    </section>

    <p v-if="subscriptionError" class="membership-container membership-error" role="alert">
      {{ subscriptionError }}
    </p>

    <section class="membership-container membership-compare" aria-live="polite">
      <p v-if="loading" class="membership-state">
        正在加载会员授权…
      </p>
      <p v-else-if="error" class="membership-state membership-error">
        {{ error }}
      </p>
      <div v-else class="membership-cards">
        <article class="membership-card membership-card--standard">
          <div class="membership-card-head">
            <span class="membership-badge">登录即享</span>
            <h2>标准版</h2>
            <p class="membership-price">
              <strong>免费</strong>
            </p>
            <p class="membership-desc">
              满足日常标签编辑与打印。
            </p>
          </div>
          <ul class="membership-features">
            <li v-for="row in COMPARISON" :key="row.label" :class="{ 'is-muted': !row.standard }">
              <span v-if="row.standard" class="membership-yes" aria-hidden="true">✓</span>
              <span v-else class="membership-no" aria-hidden="true">—</span>
              {{ row.label }}
            </li>
          </ul>
          <p v-if="!userStore.isLogin" class="membership-card-note">
            登录后自动获得标准版。
          </p>
          <p v-else-if="currentEdition === 'standard' && !hasExpiredSubscription" class="membership-card-note">
            当前账号正在使用标准版。
          </p>
        </article>

        <article class="membership-card membership-card--pro">
          <div class="membership-card-head">
            <span class="membership-badge membership-badge--pro">推荐</span>
            <h2>专业版</h2>
            <p class="membership-price">
              <strong>{{ proPriceLabel }}</strong><span class="membership-price-unit">{{ proPriceUnit }}</span>
            </p>
            <p class="membership-desc">
              在标准版基础上，解锁数据库、批量数据与打印记录。
            </p>
          </div>
          <ul class="membership-features">
            <li v-for="row in COMPARISON" :key="row.label">
              <span class="membership-yes" aria-hidden="true">✓</span>
              {{ row.label }}
            </li>
          </ul>
          <div class="membership-periods">
            <p v-if="paidPlans.length === 0" class="membership-card-note">
              暂无可购买的周期，请联系客服。
            </p>
            <button
              v-for="plan in paidPlans"
              :key="plan.skuId"
              type="button"
              class="membership-period-button"
              :data-testid="`membership-renew-${plan.skuId}`"
              :disabled="isEnterprise"
              @click="renew(plan)"
            >
              <span class="membership-period-label">{{ periodLabel(plan.periodMonths) }}</span>
              <strong class="membership-period-price">{{ priceLabel(plan.price) }}</strong>
              <span class="membership-period-action">{{ renewableLabel }}</span>
            </button>
            <p v-if="isEnterprise" class="membership-card-note membership-card-note--warn">
              请联系企业管理员续费企业版。
            </p>
          </div>
        </article>
      </div>

      <p class="membership-enterprise-note">
        云端与共享模板、脚本、电子秤、RFID 与全部条码类型属于企业版；
        <RouterLink to="/enterprise">
          了解企业版
        </RouterLink>
      </p>
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
  padding: 28px 0 22px;
  background: linear-gradient(135deg, #eaf1ff 0%, #f5f8ff 60%, #ffffff 100%);
}
.membership-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
}
.membership-head-copy {
  flex: 1;
  min-width: 0;
}
.membership-eyebrow {
  margin: 0;
  color: #2e7cd6;
  font-size: 13px;
  letter-spacing: 0.08em;
}
.membership-hero h1 {
  margin: 6px 0 8px;
  font-size: 34px;
  line-height: 1.2;
}
.membership-lead {
  max-width: 620px;
  margin: 0;
  color: #5b6c8f;
  font-size: 14px;
  line-height: 1.65;
}

.membership-status {
  flex: 0 0 auto;
  width: min(480px, 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px 16px;
  padding: 14px 18px;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
  box-shadow: 0 6px 20px rgba(46, 124, 214, 0.06);
}
.membership-status-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.membership-status-label {
  color: #8a97b5;
  font-size: 13px;
}
.membership-status-edition {
  color: #0c2148;
  font-size: 20px;
  font-weight: 700;
}
.membership-status-org {
  color: #2e7cd6;
  font-size: 13px;
}
.membership-status-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #5b6c8f;
  font-size: 13px;
  flex-wrap: wrap;
}
.membership-link-button {
  padding: 0;
  border: 0;
  background: none;
  color: #2e7cd6;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.membership-error {
  margin-top: 12px;
  color: #ff5c22;
}

.membership-compare {
  padding: 16px 0 64px;
}
.membership-state {
  padding: 32px;
  text-align: center;
  color: #5b6c8f;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
}

.membership-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  align-items: stretch;
}
.membership-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 22px;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 16px;
}
.membership-card--standard {
  background: #f4f7fd;
}
.membership-card--pro {
  border-color: #2e7cd6;
  box-shadow: 0 16px 40px rgba(46, 124, 214, 0.14);
}
.membership-card-head h2 {
  margin: 6px 0 0;
  font-size: 22px;
}
.membership-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  background: #eef2fa;
  color: #5b6c8f;
  font-size: 12px;
}
.membership-badge--pro {
  background: #2e7cd6;
  color: #ffffff;
}
.membership-price {
  margin: 8px 0 0;
  color: #0c2148;
  font-size: 30px;
  font-weight: 700;
}
.membership-price-unit {
  color: #8a97b5;
  font-size: 14px;
  font-weight: 400;
}
.membership-desc {
  margin: 6px 0 0;
  color: #5b6c8f;
  font-size: 13.5px;
  line-height: 1.55;
}
.membership-features {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 12px 0 0;
  border-top: 1px solid #eef2fa;
  list-style: none;
  color: #16233f;
  font-size: 13.5px;
  line-height: 1.5;
}
.membership-features li {
  display: flex;
  gap: 8px;
}
.membership-features li.is-muted {
  color: #8a97b5;
}
.membership-yes {
  color: #2e7cd6;
  font-weight: 700;
}
.membership-no {
  color: #b4c0d8;
}
.membership-periods {
  display: grid;
  gap: 8px;
  margin-top: auto;
}
.membership-period-button {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 2px 12px;
  padding: 10px 14px;
  border: 1px solid #b4c0d8;
  border-radius: 10px;
  background: #f5f8ff;
  color: #16233f;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.membership-period-button:hover:not(:disabled) {
  border-color: #2e7cd6;
  background: #eef2fa;
}
.membership-period-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.membership-period-label {
  font-size: 15px;
  font-weight: 600;
}
.membership-period-price {
  font-size: 18px;
}
.membership-period-action {
  grid-column: 1 / -1;
  color: #2e7cd6;
  font-size: 13px;
  font-weight: 600;
}
.membership-card-note {
  margin: 0;
  color: #8a97b5;
  font-size: 13px;
}
.membership-card-note--warn {
  color: #ff5c22;
}
.membership-enterprise-note {
  margin: 20px 0 0;
  color: #8a97b5;
  font-size: 13px;
}
.membership-enterprise-note a {
  color: #2e7cd6;
}

@media (max-width: 768px) {
  .membership-cards {
    grid-template-columns: 1fr;
  }
  .membership-head {
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
  }
  .membership-status {
    width: 100%;
  }
  .membership-hero {
    padding: 22px 0 18px;
  }
  .membership-hero h1 {
    font-size: 30px;
  }
}
@media (max-width: 480px) {
  .membership-container {
    width: calc(100% - 32px);
  }
  .membership-hero {
    padding: 20px 0 16px;
  }
  .membership-card {
    padding: 18px;
  }
}
</style>
