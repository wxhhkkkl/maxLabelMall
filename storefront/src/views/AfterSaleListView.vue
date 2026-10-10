<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { cancelAfterSale, pageAfterSales } from '@/api/afterSale'
import AccountSidebar from '@/components/AccountSidebar.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import MlModal from '@/components/base/MlModal.vue'
import Pagination from '@/components/Pagination.vue'
import { AfterSaleStatus, type AfterSaleListItem } from '@/types'
import { afterSaleStatusText, afterSaleWayLabel, canCancelAfterSaleByStatus } from '@/utils/afterSale'
import { formatYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/time'

/**
 * 我的售后（FR-011c / SC-023）。
 *
 * 本页是上一轮做「申请退款」时留下的缺口的补丁：用户提交退款后，原来只能在订单详情
 * 某个商品行上看到一个状态，**没有地方能看自己所有的售后记录**。
 *
 * ⚠️ **撤销入口的判定用列表返回的精确状态**（`canCancelAfterSaleByStatus`），
 * 而不是订单详情页那个粗粒度的「售后中」—— 后者分不出"商家已收货待退款"这种
 * 已经不能撤的情形，只能靠后端拒绝后透文案。**这两个函数别互换。**
 *
 * ⚠️ 状态一律以后端为准：撤销成功后**重拉列表**，不做本地乐观更新。
 */
const PAGE_SIZE = 10

const list = ref<AfterSaleListItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const error = ref(false)
const message = ref('')

/** 待确认撤销的那一条 */
const cancelTarget = ref<AfterSaleListItem | null>(null)
const cancelling = ref(false)

/** 只对仍可撤销的售后单给入口（精确状态判断） */
function canCancel(item: AfterSaleListItem): boolean {
  return canCancelAfterSaleByStatus(item.status)
}

/**
 * 售后单状态 → 标签样式变体。
 *
 * ⚠️ **刻意不用 `MlPill`**：那个组件的契约是「传订单状态、变体与文案都由订单状态映射
 * 派生」（见 `components/base/MlPill.vue`），拿它渲染**售后单**状态会显示成订单状态文案
 * （售后单的 10 会渲染成「待发货」）。这里只借用它那套全局 `.ml-pill.is-*` 样式，
 * 变体按售后单语义另定：完成=成功态、已取消/被拒=终止态、其余=进行中。
 */
function statusVariant(status: number): string {
  if (status === AfterSaleStatus.COMPLETE) return 'is-done'
  if (
    status === AfterSaleStatus.BUYER_CANCEL ||
    status === AfterSaleStatus.SELLER_DISAGREE ||
    status === AfterSaleStatus.SELLER_REFUSE
  ) {
    return 'is-cancelled'
  }
  return 'is-pending'
}

async function load() {
  loading.value = true
  error.value = false
  try {
    const res = await pageAfterSales({ pageNo: pageNo.value, pageSize: PAGE_SIZE })
    list.value = res.list
    total.value = res.total
  } catch {
    error.value = true
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onPage(n: number) {
  pageNo.value = n
  void load()
}

function askCancel(item: AfterSaleListItem) {
  message.value = ''
  cancelTarget.value = item
}

/** 与订单详情侧同一套写法：**先关弹层再发请求**，状态回来以后端为准 */
async function confirmCancel() {
  const target = cancelTarget.value
  cancelTarget.value = null
  if (!target) return
  cancelling.value = true
  try {
    await cancelAfterSale(target.id)
    await load()
  } catch (e) {
    // 例如「售后单状态不允许取消」—— 原样透出
    message.value = (e as { message?: string })?.message || '撤销失败，请稍后重试'
  } finally {
    cancelling.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="account-section-page">
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <RouterLink to="/account">个人中心</RouterLink> /
    <span>我的售后</span>
  </div>

  <div class="account-wrap">
    <AccountSidebar />

    <div class="account-main account-section-main">
      <div class="account-page-heading">
        <div><h1>我的售后</h1><p>查看退款申请与处理进度。</p></div>
        <RouterLink to="/order" class="account-heading-link">查看我的订单 <span aria-hidden="true">→</span></RouterLink>
      </div>

      <div class="account-content-card as-records">
      <div class="account-section-heading"><h2>售后记录</h2><span v-if="!loading && !error">共 {{ total }} 条</span></div>

      <p v-if="message" class="as-msg">{{ message }}</p>

      <LoadingState v-if="loading" />

      <EmptyState
        v-else-if="error"
        mode="error"
        title="售后记录加载失败"
        desc="网络或服务暂时不可用"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!list.length"
        title="还没有售后记录"
        desc="如需退款，请进入对应订单详情，选择「申请退款」。"
        action-text="去看订单"
        @action="$router.push('/order')"
      />

      <template v-else>
        <div class="as-list">
        <div v-for="it in list" :key="it.id" class="as-card ml-card">
          <div class="as-head">
            <span class="as-no">售后单号 {{ it.no }}</span>
            <span class="ml-pill" :class="statusVariant(it.status)">
              {{ afterSaleStatusText(it.status) }}
            </span>
          </div>

          <div class="as-body">
            <div class="as-thumb">
              <img v-if="it.picUrl" :src="it.picUrl" :alt="it.spuName" />
              <span v-else class="ph">图</span>
            </div>
            <div class="as-info">
              <div class="as-name">{{ it.spuName }}</div>
              <div v-if="it.properties?.length" class="as-spec">
                <template v-for="p in it.properties" :key="p.valueName">
                  <span>{{ p.propertyName }}：{{ p.valueName }}</span>
                </template>
              </div>
              <div class="as-meta">
                {{ afterSaleWayLabel(it.way) }} · 申请于 {{ formatDateTime(it.createTime) }}
              </div>
              <div v-if="it.auditReason" class="as-audit">卖家说明：{{ it.auditReason }}</div>
            </div>
            <div class="as-amount">
              <span class="as-amount-label">退款金额</span>
              <b>{{ formatYuan(it.refundPrice) }}</b>
            </div>
          </div>

          <div class="as-foot">
            <RouterLink class="as-order" :to="`/order/${it.orderId}`">查看订单 {{ it.orderNo }}</RouterLink>
            <button
              v-if="canCancel(it)"
              class="as-cancel"
              type="button"
              :data-sale="it.id"
              @click="askCancel(it)"
            >
              撤销申请
            </button>
          </div>
        </div>
        </div>

        <Pagination :page-no="pageNo" :page-size="PAGE_SIZE" :total="total" @update:page-no="onPage" />
      </template>
      </div>
    </div>
  </div>

  <!-- 撤销前二次确认（与订单详情侧同一个动作、同一套措辞） -->
  <MlModal :open="!!cancelTarget" title="撤销退款申请" @close="cancelTarget = null">
    <p class="ml-hint">
      确认撤销「{{ cancelTarget?.spuName }}」的退款申请？撤销后该商品需重新申请，商家将不再受理这一条。
    </p>
    <template #foot>
      <button id="asDismissCancel" class="btn-cart" type="button" @click="cancelTarget = null">
        再想想
      </button>
      <button
        id="asConfirmCancel"
        class="btn-buy"
        type="button"
        :disabled="cancelling"
        @click="confirmCancel"
      >
        {{ cancelling ? '撤销中…' : '确认撤销' }}
      </button>
    </template>
  </MlModal>
  </div>
</template>

<style scoped>
.as-msg {
  margin-top: 16px;
  color: var(--ml-orange);
  font-size: 13px;
}
.as-list { display: grid; gap: 16px; margin-top: 24px; }
.as-list .as-card { margin: 0; padding: 0; border-radius: 12px; overflow: hidden; }
.as-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  gap: 12px;
  background: #F5F8FF;
  border-bottom: 1px solid var(--ml-border);
  font-size: 13px;
  color: var(--ml-text-sub);
}
.as-body {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  gap: 20px;
  align-items: start;
  padding: 24px 20px;
}
.as-thumb {
  width: 72px;
  height: 72px;
  flex: none;
  overflow: hidden;
  border-radius: var(--ml-radius-field);
  background: var(--ml-bg-soft);
}
.as-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.as-thumb .ph {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--ml-text-ph);
  font-size: 12px;
}
.as-info {
  flex: 1;
  min-width: 0;
}
.as-name {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.7;
  overflow-wrap: anywhere;
  color: var(--ml-text);
}
.as-spec,
.as-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--ml-text-sub);
  line-height: 1.8;
}
.as-spec { display: flex; flex-wrap: wrap; gap: 4px 14px; }
.as-no, .as-audit { overflow-wrap: anywhere; }
.as-head .ml-pill { flex-shrink: 0; }
.as-audit {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ml-orange);
}
.as-amount {
  flex: none;
  text-align: right;
  font-size: 14px;
}
.as-amount-label {
  display: block;
  font-size: 12px;
  color: var(--ml-text-ph);
  margin-bottom: 8px;
}
.as-amount b { font-size: 18px; font-weight: 600; font-variant-numeric: tabular-nums; }
.as-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  gap: 16px;
  border-top: 1px solid var(--ml-border);
}
.as-order {
  font-size: 13px;
  color: var(--ml-text-sub);
  overflow-wrap: anywhere;
}
.as-cancel {
  padding: 8px 16px;
  flex-shrink: 0;
  white-space: nowrap;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-pill);
  background: var(--ml-bg-card);
  color: var(--ml-text-sub);
  font-size: 13px;
  cursor: pointer;
}
.as-cancel:hover {
  border-color: var(--ml-primary);
  color: var(--ml-primary);
}
@media (max-width: 480px) {
  .as-head { padding: 12px 14px; align-items: flex-start; font-size: 11px; }
  .as-body { grid-template-columns: 52px minmax(0, 1fr); gap: 12px; padding: 20px 14px; }
  .as-thumb { width: 52px; height: 52px; }
  .as-amount { grid-column: 2; display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px; text-align: left; }
  .as-amount-label { margin: 0; }
  .as-name { font-size: 13px; }
  .as-foot { flex-wrap: wrap; padding: 14px; gap: 12px; }
  .as-order { flex: 1 1 100%; font-size: 12px; }
  .as-cancel { margin-left: auto; }
}
</style>
