<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { listAddress } from '@/api/address'
import { listCart } from '@/api/cart'
import { createOrder, settlement, type TradeOrderInput } from '@/api/order'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import MlAmountRow from '@/components/base/MlAmountRow.vue'
import MlModal from '@/components/base/MlModal.vue'
import MlSteps from '@/components/base/MlSteps.vue'
import AddressFormDialog from '@/components/AddressFormDialog.vue'
import { useToasts } from '@/components/base/useToasts'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import type { Address, CartItem, SettlementCoupon, SettlementResp } from '@/types'
import { reconcile } from '@/utils/money'

/**
 * 结算页 —— 版式见 design-new-pages.md §3.2（无设计稿）。
 *
 * 四条关键约束：
 *
 * 1. **券的可用性只由结算响应决定**（FR-026b）：后端在 `coupons[]` 里给出
 *    `match` 与 `mismatchReason`。前端 MUST NOT 自己按门槛/有效期算一遍 ——
 *    那会出现"前端说能用、提交时被拒"。
 *
 * 2. **促销优惠与优惠券抵扣是两行**（FR-026g），不合并成一行"优惠"；明细中
 *    **没有积分行**（本期固定不使用积分）。
 *
 * 3. **应付总额必须等于各明细之和**（SC-007）。这里用 `reconcile()` 核对，
 *    不成立时**显式告警**而不是照抄一个错的总数。
 *
 * 4. **防重复提交**（FR-032）：提交中禁用按钮 + 幂等保护，连点只产生一笔订单。
 */
const props = withDefaults(
  defineProps<{ source?: 'product' | 'cart'; skuId?: number; count?: number; cartIds?: number[] }>(),
  { source: 'product', count: 1 },
)

const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()
const toast = useToasts()

const loading = ref(true)
const error = ref(false)
const data = ref<SettlementResp | null>(null)

const addresses = ref<Address[]>([])
const addressId = ref<number | null>(null)
const couponId = ref<number | undefined>(undefined)
/** 购物车来源下、被勾选的那几条（提供 skuId 与数量给结算请求） */
const cartRows = ref<CartItem[]>([])

const couponPickerOpen = ref(false)
const addressPickerOpen = ref(false)
/** 「新增收货地址」弹层 —— **不跳转**，就地新增。跳走会让本页填的东西全丢 */
const addressFormOpen = ref(false)

const submitting = ref(false)
const submitError = ref('')
/** 价格变动待确认：置为 true 后按钮变为「确认并提交」 */
const priceChangePending = ref(false)

const price = computed(() => data.value?.price ?? null)
const amountConsistent = computed(() => (price.value ? reconcile(price.value) : true))

const availableCoupons = computed<SettlementCoupon[]>(() =>
  (data.value?.coupons ?? []).filter((c) => c.match),
)
const unavailableCoupons = computed<SettlementCoupon[]>(() =>
  (data.value?.coupons ?? []).filter((c) => !c.match),
)

const currentAddress = computed(() => addresses.value.find((a) => a.id === addressId.value) ?? null)
const currentCoupon = computed(
  () => (data.value?.coupons ?? []).find((c) => c.id === couponId.value) ?? null,
)
const itemCount = computed(() =>
  (data.value?.items ?? []).reduce((n, i) => n + i.count, 0),
)

/**
 * 组装给结算/下单接口的入参。`pointStatus` 与 `deliveryType` 由 API 层兜住。
 *
 * 购物车来源的条目**必须同时带 `skuId` 与 `cartId`**：后端 `Item.skuId` 标了
 * `@NotNull`，只给 `cartId` 会被参数校验拦掉。而 URL 上只有 cartId，所以
 * skuId/count 来自**先从后端拉一次购物车**（见 bootstrap）——不能从结算响应里
 * 反查，因为**第一次请求时响应还不存在**，那样只会发出 `skuId=0`。
 */
function buildInput(): TradeOrderInput {
  const items =
    props.source === 'cart'
      ? cartRows.value.map((i) => ({ cartId: i.id, skuId: i.sku.id, count: i.count }))
      : [{ skuId: props.skuId ?? 0, count: props.count }]

  return { items, addressId: addressId.value ?? undefined, couponId: couponId.value }
}

async function load() {
  loading.value = true
  error.value = false
  try {
    data.value = await settlement(buildInput())
    // 首次进入时用默认地址重算一次运费（FR-028）
    if (addressId.value === null) {
      const def = addresses.value.find((a) => a.defaultStatus) ?? addresses.value[0]
      if (def) {
        addressId.value = def.id
        data.value = await settlement(buildInput())
      }
    }
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

function pickCoupon(id?: number) {
  couponId.value = id
  couponPickerOpen.value = false
  priceChangePending.value = false
  void load()
}

function pickAddress(id: number) {
  addressId.value = id
  addressPickerOpen.value = false
  // 换地址要**重新结算**，否则运费还是按旧地址算的（FR-028）
  void load()
}

/** 新增地址保存成功：重拉列表、把新地址选上，并重新结算（FR-028） */
async function onAddressSaved(id: number) {
  addressFormOpen.value = false
  try {
    addresses.value = await listAddress()
  } catch {
    // 拉不到就保持原列表 —— 别把用户已有选择清掉
  }
  addressId.value = id
  await load()
}

async function onSubmit() {
  if (submitting.value) return

  if (!addressId.value) {
    submitError.value = '请先选择收货地址'
    return
  }

  submitting.value = true
  submitError.value = ''
  try {
    const res = await createOrder(buildInput())

    // 下单成功后同步角标（FR-031 / FR-019）。已结算条目的**删除由后端完成**，
    // 前端不再删一次 —— 见 store/cart.ts 的 onOrderPlaced。
    await cartStore.onOrderPlaced()

    priceChangePending.value = false
    router.push(`/order/${res.id}`)
  } catch (e) {
    const msg = (e as { message?: string })?.message ?? ''
    submitError.value = msg || '提交订单失败，请稍后重试'
    // 价格变动：要求用户确认后再提交，**不静默按旧价或新价成交**（FR-030）
    if (/价格|变动/.test(msg)) priceChangePending.value = true
    await load() // 重新拉一次最新金额，供用户确认
  } finally {
    submitting.value = false
  }
}

async function onConfirmPriceChange() {
  priceChangePending.value = false
  await load()
  toast.warn('已按最新价格重新计算，请核对后再次提交')
}

/**
 * 首次进入 / 登录后重新进入时的加载。
 *
 * 登录前地址取不到（401），此时把地址当作空列表，由 `load()` 的失败态兜底；
 * 登录成功后 `isLogin` 变真，下面这个 watcher 会再跑一次 —— 这就是 FR-015
 * 的「登录成功后自动继续原操作」：用户点了「立即购买」被守卫挂上 `?login=1`，
 * 登录完不该停在一个 401 的失败页上，要求他再点一次。
 */
async function bootstrap() {
  // 购物车来源：URL 上只有 cartId，先拉一次购物车才拿得到每个条目的 skuId 与数量
  if (props.source === 'cart') {
    try {
      const cart = await listCart()
      const wanted = props.cartIds ?? []
      cartRows.value = [...cart.validList, ...cart.invalidList].filter((i) =>
        wanted.includes(i.id),
      )
    } catch {
      cartRows.value = []
    }
  }

  try {
    addresses.value = await listAddress()
  } catch {
    addresses.value = []
  }
  await load()
}

onMounted(bootstrap)

watch(
  () => userStore.isLogin,
  (loggedIn) => {
    if (loggedIn) void bootstrap()
  },
)
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink><span>/ 结算</span>
  </div>

  <div class="ml-wrap">
    <MlSteps :steps="['购物车', '填写信息', '支付']" :current="1" />

    <LoadingState v-if="loading" :count="2" />

    <EmptyState
      v-else-if="error"
      mode="error"
      title="结算信息加载失败"
      desc="网络或服务暂时不可用"
      action-text="重新加载"
      @action="load"
    />

    <div v-else-if="data" class="ml-2col">
      <div>
        <!-- 收货地址 -->
        <div class="ml-card">
          <div class="ml-card-title">收货地址</div>
          <div v-if="currentAddress" class="addr-current">
            <div class="addr-line">
              <b>{{ currentAddress.name }}</b>
              <span>{{ currentAddress.mobile }}</span>
            </div>
            <p class="addr-text">{{ currentAddress.areaName }} {{ currentAddress.detailAddress }}</p>
            <button id="addressPicker" class="btn-cart" type="button" @click="addressPickerOpen = true">
              更换地址
            </button>
          </div>
          <div v-else>
            <p class="ml-hint">还没有收货地址，先添加一个</p>
            <!-- 就地弹层新增，**不跳转** —— 跳走会丢掉本页已选的券/地址/备注 -->
            <button id="coAddAddress" class="btn-cyan" type="button" @click="addressFormOpen = true">
              新增收货地址
            </button>
          </div>
        </div>

        <!-- 商品清单（只读，不可改量） -->
        <div class="ml-card">
          <div class="ml-card-title">商品清单（{{ itemCount }} 件）</div>
          <div v-for="item in data.items" :key="item.skuId" class="co-item">
            <div class="co-thumb">
              <img v-if="item.picUrl" :src="item.picUrl" :alt="item.spuName" />
              <span v-else class="ph">图</span>
            </div>
            <div class="co-info">
              <div class="co-name">{{ item.spuName }}</div>
              <div class="co-spec">
                <template v-for="p in item.properties" :key="p.valueId">
                  {{ p.propertyName }}：{{ p.valueName }}
                </template>
              </div>
            </div>
            <div class="co-price">¥{{ (item.price / 100).toFixed(2) }}</div>
            <div class="co-count">×{{ item.count }}</div>
          </div>
        </div>

        <!-- 券选择入口 -->
        <div class="ml-card">
          <div class="ml-card-title">优惠券</div>
          <button id="openCouponPicker" class="coupon-trigger" type="button" @click="couponPickerOpen = true">
            <span v-if="currentCoupon">{{ currentCoupon.name }}（-¥{{ ((currentCoupon.discountPrice ?? 0) / 100).toFixed(2) }}）</span>
            <span v-else-if="availableCoupons.length">有 {{ availableCoupons.length }} 张可用</span>
            <span v-else>暂无可用优惠券</span>
            <span class="arrow">›</span>
          </button>
        </div>
      </div>

      <!-- 金额明细：逐行可核对 -->
      <div>
        <div class="ml-amount-card">
          <MlAmountRow label="商品小计" :fen="data.price.totalPrice" />
          <MlAmountRow label="促销优惠" :fen="-data.price.discountPrice" cut />
          <MlAmountRow label="运费" :fen="data.price.deliveryPrice" />
          <MlAmountRow label="优惠券抵扣" :fen="-data.price.couponPrice" cut />
          <MlAmountRow label="应付总额" :fen="data.price.payPrice" total />
        </div>

        <!-- 明细之和与后端总额不一致时显式告警，而不是照抄一个错的总数 -->
        <p v-if="!amountConsistent" class="amount-warning">
          金额明细与应付总额不一致，请勿提交并联系客服核对
        </p>

        <p v-if="submitError" class="submit-error">{{ submitError }}</p>

        <button
          v-if="priceChangePending"
          id="confirmPriceChange"
          class="btn-cart confirm-price"
          type="button"
          @click="onConfirmPriceChange"
        >
          价格已变动，点此按最新价格重算
        </button>

        <button
          id="submitOrder"
          class="btn-buy submit-order"
          type="button"
          :disabled="submitting || !amountConsistent"
          @click="onSubmit"
        >
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
      </div>
    </div>

    <!-- 券选择弹层：可用与不可用分开，不可用带原因（FR-026b） -->
    <MlModal :open="couponPickerOpen" title="选择优惠券" @close="couponPickerOpen = false">
      <div v-if="availableCoupons.length">
        <p class="ml-hint">可用</p>
        <div
          v-for="c in availableCoupons"
          :key="c.id"
          class="coupon-item"
          :class="{ 'is-active': c.id === couponId }"
          @click="pickCoupon(c.id)"
        >
          <span class="coupon-name">{{ c.name }}</span>
          <span class="coupon-value">-¥{{ (c.discountPrice / 100).toFixed(2) }}</span>
        </div>
      </div>

      <div v-if="unavailableCoupons.length" class="coupon-unavailable">
        <p class="ml-hint">不可用</p>
        <div
          v-for="c in unavailableCoupons"
          :key="c.id"
          class="coupon-item is-disabled"
        >
          <span class="coupon-name">{{ c.name }}</span>
          <span class="coupon-reason">{{ c.mismatchReason || '不满足使用条件' }}</span>
        </div>
      </div>

      <button id="couponNone" class="coupon-none" type="button" @click="pickCoupon(undefined)">
        不使用优惠券
      </button>
    </MlModal>

    <!-- 地址选择弹层 -->
    <MlModal :open="addressPickerOpen" title="选择收货地址" @close="addressPickerOpen = false">
      <div
        v-for="a in addresses"
        :key="a.id"
        class="addr-option"
        :class="{ 'is-active': a.id === addressId }"
        @click="pickAddress(a.id)"
      >
        <div class="addr-line"><b>{{ a.name }}</b><span>{{ a.mobile }}</span></div>
        <p class="addr-text">{{ a.areaName }} {{ a.detailAddress }}</p>
      </div>
      <button class="btn-cyan" type="button" @click="addressFormOpen = true">
        新增收货地址
      </button>
    </MlModal>

    <!-- 新增地址弹层（与地址管理页共用同一个组件） -->
    <AddressFormDialog
      :open="addressFormOpen"
      @close="addressFormOpen = false"
      @saved="onAddressSaved"
    />
  </div>
</template>

<style scoped>
.co-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--ml-border);
}
.co-item:last-child {
  border-bottom: 0;
}
.co-thumb {
  width: 56px;
  height: 56px;
  border-radius: var(--ml-radius-field);
  overflow: hidden;
  background: var(--ml-bg-card);
  flex: 0 0 56px;
}
.co-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.co-info {
  flex: 1;
}
.co-name {
  font-size: 14px;
  color: var(--ml-text);
}
.co-spec {
  font-size: 12px;
  color: var(--ml-text-ph);
  margin-top: 4px;
}
.co-price,
.co-count {
  font-size: 14px;
  color: var(--ml-text-sub);
  font-variant-numeric: tabular-nums;
}
.addr-current .addr-line,
.addr-option .addr-line {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 4px;
}
.addr-text {
  font-size: 14px;
  color: var(--ml-text-sub);
  margin-bottom: 10px;
}
.coupon-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  background: var(--ml-bg-card-3);
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-field);
  padding: 12px 14px;
  font-size: 14px;
  color: var(--ml-text);
  cursor: pointer;
}
.coupon-trigger .arrow {
  color: var(--ml-text-ph);
}
.coupon-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-field);
  margin-bottom: 8px;
  cursor: pointer;
}
.coupon-item.is-active {
  border-color: var(--ml-primary);
  background: var(--ml-bg-soft);
}
.coupon-item.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
  background: var(--ml-bg-card);
}
.coupon-reason {
  font-size: 12px;
  color: var(--ml-text-ph);
}
.coupon-none {
  width: 100%;
  border: 0;
  background: none;
  color: var(--ml-primary);
  font-size: 14px;
  padding: 10px 0;
  cursor: pointer;
}
.addr-option {
  padding: 12px 14px;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-field);
  margin-bottom: 8px;
  cursor: pointer;
}
.addr-option.is-active {
  border-color: var(--ml-primary);
  background: var(--ml-bg-soft);
}
.amount-warning {
  margin-top: 12px;
  font-size: 13px;
  color: var(--ml-orange);
}
.submit-error {
  margin-top: 12px;
  font-size: 13px;
  color: var(--ml-orange);
}
.confirm-price {
  width: 100%;
  margin-top: 12px;
}
.submit-order {
  width: 100%;
  margin-top: 12px;
}
.submit-order:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
