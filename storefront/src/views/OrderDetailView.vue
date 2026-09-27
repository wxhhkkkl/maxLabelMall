<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { cancelOrder, getOrderDetail } from '@/api/order'
import { submitPay } from '@/api/pay'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import MlAmountRow from '@/components/base/MlAmountRow.vue'
import MlModal from '@/components/base/MlModal.vue'
import MlPill from '@/components/base/MlPill.vue'
import type { OrderDetail } from '@/types'
import { OrderStatus } from '@/types'
import { formatYuan, reconcile } from '@/utils/money'
import { orderStatusText } from '@/utils/orderStatus'
import { formatDateTime } from '@/utils/time'

/**
 * 订单详情 —— 按 design-new-pages.md §3.4 的版式实现（无设计稿）。
 *
 * 五条约束：
 *
 * 1. **状态一律来自后端**（FR-039）。支付完成后后端把订单推进到 `10 待发货`，
 *    前端不得自行把它标成「已支付」，也不得按 `payTime` 是否为空反推状态。
 *
 * 2. **用户侧没有「确认收货」**（FR-041b）。发货由运营在后台操作，收货时限到期后
 *    后端自动完成订单 —— 这里不提供任何推进状态的控件。
 *
 * 3. **待支付才展示支付截止时间**（FR-041）：已经付款或已取消的订单，
 *    「支付截止」没有意义。
 *
 * 4. 五种状态**共用同一版式**，只有状态区与操作区不同（§3.4）。
 *
 * 5. **支付用模拟通道**（SC-010），提交后状态由后端异步回调推进 —— 见 `onPay`。
 */

const props = defineProps<{ id: number | string }>()

const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const error = ref(false)
const message = ref('')
const cancelOpen = ref(false)
const paying = ref(false)

const statusText = computed(() => (order.value ? orderStatusText(order.value.status) : ''))
const isUnpaid = computed(() => order.value?.status === OrderStatus.UNPAID)
/**
 * 全额抵扣（`payPrice === 0`）的订单后端**不创建支付单**，`payOrderId` 为 null ——
 * 此时没有任何 id 可以提交支付，所以不给支付入口（FR-037）。
 */
const needsNoPay = computed(() => isUnpaid.value && !order.value?.payOrderId)
// 详情的金额字段是**平铺**在返回对象上的，结构上即 OrderPrice
const amountConsistent = computed(() => (order.value ? reconcile(order.value) : true))
/** 券名后端未返回（`AppTradeOrderDetailRespVO` 只有 couponId/couponPrice） */
const usedCouponId = computed(() => order.value?.couponId ?? null)

/** 各状态下的一句话说明 —— 只说这一状态**确实意味着**的事，不做物流承诺 */
const STATUS_HINT: Record<number, string> = {
  [OrderStatus.UNPAID]: '请尽快完成支付，超时未支付订单将自动取消。',
  [OrderStatus.UNDELIVERED]: '已付款，商家正在准备发货。',
  [OrderStatus.DELIVERED]: '商品已发出，请留意物流信息。',
  [OrderStatus.COMPLETED]: '订单已完成，感谢购买。',
  [OrderStatus.CANCELED]: '订单已取消，商品库存与所用优惠券已释放。',
}

/**
 * `sync = true` 让后端**主动同步支付渠道**再返回 —— 支付后确认状态就靠它
 * （`AppTradeOrderController.getOrderDetail` 的 `sync` 分支会调
 * `syncOrderPayStatusQuietly`）。
 */
async function load(sync = false) {
  loading.value = true
  error.value = false
  try {
    order.value = await getOrderDetail(Number(props.id), sync)
  } catch {
    error.value = true
    order.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 立即支付（模拟通道，SC-010）。
 *
 * ⚠️ **提交成功后不把订单标成已支付**：模拟通道提交即成功，但交易订单要等后端
 * 回调 `/app-api/trade/order/update-paid` 才推进。所以这里重新拉一次详情
 * （`sync=true` 顺便让后端同步一次渠道状态），**页面显示的状态始终是后端给的**
 * —— 后端说还是待支付，就还是待支付（FR-039）。
 */
async function onPay() {
  const payOrderId = order.value?.payOrderId
  // 契约要求：payOrderId 为空时不得提交（全额抵扣的订单没有支付单）
  if (!payOrderId) return
  message.value = ''
  paying.value = true
  try {
    await submitPay(payOrderId)
    await load(true)
  } catch (e) {
    // 支付放弃/中断/失败：订单仍是「待支付」，入口留着让用户再发起（FR-038）
    message.value = (e as { message?: string })?.message || '支付失败，请稍后重试'
  } finally {
    paying.value = false
  }
}

async function confirmCancel() {
  cancelOpen.value = false
  const target = order.value
  if (!target) return
  message.value = ''
  try {
    await cancelOrder(target.id)
    // 状态由后端改，重新拉一次详情 —— 不在这里把本地对象标成「已取消」
    await load()
  } catch (e) {
    message.value = (e as { message?: string })?.message || '取消订单失败，请稍后重试'
  }
}

onMounted(async () => {
  await load()
  // 全额抵扣（payPrice=0）的订单没有支付单，也就没有「提交支付」这一步可做。
  // 按契约走另一条路：不给支付入口、提示「本单无需支付」，并同步一次状态，
  // 确认后端到底把它算成什么（不臆断它会自动完成）。
  if (needsNoPay.value) await load(true)
})
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <RouterLink to="/order">我的订单</RouterLink> /
    <span>订单详情</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ml-page-title">订单详情</h1>
  </div>

  <div class="ml-wrap">
    <LoadingState v-if="loading" :count="3" />

    <EmptyState
      v-else-if="error"
      mode="error"
      title="订单加载失败"
      desc="网络或服务暂时不可用，请稍后重试"
      action-text="重新加载"
      @action="load"
    />

    <template v-else-if="order">
      <p v-if="message" class="od-msg">{{ message }}</p>

      <!-- 状态区：大号状态 + 一句话说明；待支付额外给支付截止时间 -->
      <div class="ml-card od-status">
        <div class="od-status-row">
          <span class="od-status-text">{{ statusText }}</span>
          <MlPill :status="order.status" />
        </div>
        <p class="ml-hint">{{ STATUS_HINT[order.status] ?? '' }}</p>
        <p v-if="isUnpaid" class="od-deadline">
          支付截止 {{ formatDateTime(order.payExpireTime) }}，逾期订单将自动取消
        </p>
      </div>

      <!-- 收货信息 -->
      <div class="ml-card">
        <div class="ml-card-title">收货信息</div>
        <div class="addr-line">
          <b>{{ order.receiverName }}</b>
          <span>{{ order.receiverMobile }}</span>
        </div>
        <p class="od-text">
          {{ order.receiverAreaName }} {{ order.receiverDetailAddress }}
        </p>
      </div>

      <!-- 商品明细：名称 / 规格 / 单价 / 数量都是下单时的快照 -->
      <div class="ml-card">
        <div class="ml-card-title">商品明细（{{ order.items.length }} 项）</div>
        <div v-for="it in order.items" :key="it.id" class="od-item">
          <div class="od-thumb">
            <img v-if="it.picUrl" :src="it.picUrl" :alt="it.spuName" />
            <span v-else class="ph">图</span>
          </div>
          <div class="od-info">
            <div class="od-name">{{ it.spuName }}</div>
            <div class="od-spec">
              <template v-for="p in it.properties ?? []" :key="p.valueName">
                {{ p.propertyName }}：{{ p.valueName }}
              </template>
            </div>
          </div>
          <div class="od-price">{{ formatYuan(it.price) }}</div>
          <div class="od-count">×{{ it.count }}</div>
        </div>
      </div>

      <!-- 金额构成：促销优惠与优惠券抵扣分列两行，无积分行（FR-026g） -->
      <div class="ml-card">
        <div class="ml-card-title">金额构成</div>
        <div class="ml-amount-card">
          <MlAmountRow label="商品小计" :fen="order.totalPrice" />
          <MlAmountRow label="促销优惠" :fen="-order.discountPrice" cut />
          <MlAmountRow label="运费" :fen="order.deliveryPrice" />
          <MlAmountRow label="优惠券抵扣" :fen="-order.couponPrice" cut />
          <MlAmountRow label="应付总额" :fen="order.payPrice" total />
        </div>
        <p v-if="usedCouponId" class="ml-hint od-coupon">
          本单使用了优惠券（券编号 {{ usedCouponId }}），抵扣
          {{ formatYuan(order.couponPrice) }}
        </p>
        <p v-if="!amountConsistent" class="od-msg">
          金额明细与应付总额不一致，请联系客服核对
        </p>
      </div>

      <!-- 订单信息 -->
      <div class="ml-card">
        <div class="ml-card-title">订单信息</div>
        <div class="dl-table">
          <div class="dl-row">
            <span class="dl-name">订单号</span><span class="dl-val">{{ order.no }}</span>
          </div>
          <div class="dl-row">
            <span class="dl-name">下单时间</span>
            <span class="dl-val">{{ formatDateTime(order.createTime) }}</span>
          </div>
          <div class="dl-row">
            <span class="dl-name">支付时间</span>
            <span class="dl-val">
              {{ order.payTime ? formatDateTime(order.payTime) : '尚未支付' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 操作区：只有「待支付」有用户侧动作（FR-041b 不提供确认收货） -->
      <div v-if="isUnpaid" class="od-actions">
        <!-- 全额抵扣的订单后端没有支付单，没有可提交的 id（FR-037） -->
        <span v-if="needsNoPay" class="od-nopay">本单无需支付</span>
        <button class="btn-cart cancel-order" type="button" @click="cancelOpen = true">
          取消订单
        </button>
        <button
          v-if="!needsNoPay"
          id="payOrder"
          class="btn-buy"
          type="button"
          :disabled="paying"
          @click="onPay"
        >
          {{ paying ? '支付中…' : '立即支付' }}
        </button>
      </div>
    </template>
  </div>

  <MlModal :open="cancelOpen" title="取消订单" @close="cancelOpen = false">
    <p class="ml-hint">
      确认取消订单 {{ order?.no }}？取消后商品库存与所用优惠券会释放，订单不可恢复。
    </p>
    <template #foot>
      <button id="dismissCancel" class="btn-cart" type="button" @click="cancelOpen = false">
        再想想
      </button>
      <button id="confirmCancel" class="btn-buy" type="button" @click="confirmCancel">
        确认取消
      </button>
    </template>
  </MlModal>
</template>

<style scoped>
.od-msg {
  color: var(--ml-orange);
  font-size: 13px;
  margin: 12px 0;
}
.od-status-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.od-status-text {
  font-size: 22px;
  font-weight: 700;
  color: var(--ml-text);
}
.od-deadline {
  margin-top: 8px;
  font-size: 13px;
  color: var(--ml-orange);
}
.addr-line {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 4px;
}
.od-text {
  font-size: 14px;
  color: var(--ml-text-sub);
}
.od-item {
  display: grid;
  grid-template-columns: 56px 1fr auto auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--ml-border);
}
.od-item:last-child {
  border-bottom: 0;
}
.od-thumb {
  width: 56px;
  height: 56px;
  border-radius: var(--ml-radius-field);
  overflow: hidden;
  background: var(--ml-bg-card);
}
.od-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.od-name {
  font-size: 14px;
  color: var(--ml-text);
}
.od-spec {
  font-size: 12px;
  color: var(--ml-text-ph);
  margin-top: 4px;
}
.od-price,
.od-count {
  font-size: 14px;
  color: var(--ml-text-sub);
  font-variant-numeric: tabular-nums;
}
.od-coupon {
  margin-top: 10px;
}
.dl-table .dl-row {
  grid-template-columns: 1fr 2fr;
}
.od-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
.od-nopay {
  font-size: 14px;
  color: var(--ml-text-sub);
}
@media (max-width: 768px) {
  .od-item {
    grid-template-columns: 56px 1fr;
  }
  .od-price,
  .od-count {
    grid-column: 2;
  }
}
</style>
