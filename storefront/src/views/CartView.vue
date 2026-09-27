<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  deleteCartItems,
  listCart,
  updateCartCount,
  updateCartSelected,
} from '@/api/cart'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import MlCheck from '@/components/base/MlCheck.vue'
import { useCartStore } from '@/store/cart'
import type { CartItem } from '@/types'
import { formatYuan } from '@/utils/money'

/**
 * 购物车 —— 按 design-new-pages.md §3.1 的版式实现（无设计稿）。
 *
 * 两条"不要自作聪明"的约束：
 *
 * 1. **有效 / 失效由后端分好**（`validList` / `invalidList`）。前端 MUST NOT 重新
 *    判断"这个是不是下架了、是不是没库存"（FR-023）—— 那会与后端规则漂移，
 *    而且判断错了会让用户去结算一件买不了的商品。
 *
 * 2. **勾选与数量上限的判定方也是后端**：勾选走 `update-selected`（持久化），
 *    数量超限以后端拒绝为准，前端只负责提示与回退（FR-021）。
 */
const router = useRouter()
const cartStore = useCartStore()

const valid = ref<CartItem[]>([])
const invalid = ref<CartItem[]>([])
const loading = ref(true)
const error = ref(false)
const message = ref('')

const selectedItems = computed(() => valid.value.filter((i) => i.selected))

/** 合计只算**已勾选**的条目 */
const totalFen = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + i.sku.price * i.count, 0),
)
const totalText = computed(() => formatYuan(totalFen.value))
const canCheckout = computed(() => selectedItems.value.length > 0)

async function load() {
  loading.value = true
  error.value = false
  try {
    const res = await listCart()
    valid.value = res.validList
    invalid.value = res.invalidList
    await cartStore.refreshCount()
  } catch {
    error.value = true
    valid.value = []
    invalid.value = []
  } finally {
    loading.value = false
  }
}

async function onToggleSelected(item: CartItem, next: boolean) {
  const before = item.selected
  item.selected = next
  try {
    await updateCartSelected([item.id], next)
  } catch {
    item.selected = before // 后端拒绝则回退
    message.value = '操作失败，请重试'
  }
}

async function onStepQty(item: CartItem, delta: number) {
  const next = item.count + delta
  if (next < 1) return
  const before = item.count
  item.count = next
  message.value = ''
  try {
    await updateCartCount(item.id, next)
    await cartStore.refreshCount()
  } catch (e) {
    // 数量上限以后端为准：被拒就回退到原数量并说明原因（FR-021）
    item.count = before
    message.value = (e as { message?: string })?.message || '数量超出可售库存'
  }
}

async function onRemove(item: CartItem) {
  try {
    await deleteCartItems([item.id])
    valid.value = valid.value.filter((i) => i.id !== item.id)
    await cartStore.refreshCount()
  } catch {
    message.value = '删除失败，请重试'
  }
}

function onCheckout() {
  if (!canCheckout.value) return
  // 只把**被勾选**的条目带给结算页（FR-025）。结算页的 `cartIds` 走 query，
  // 由 router/index.ts 的 props 函数映射成组件 props。
  void router
    .push({
      path: '/checkout',
      query: { source: 'cart', cartIds: selectedItems.value.map((i) => i.id).join(',') },
    })
    .catch(() => undefined)
}

onMounted(load)
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink><span>/ 购物车</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ml-page-title">购物车</h1>
  </div>

  <div class="ml-wrap">
    <LoadingState v-if="loading" :count="2" />

    <EmptyState
      v-else-if="error"
      mode="error"
      title="购物车加载失败"
      desc="网络或服务暂时不可用，请稍后重试"
      action-text="重新加载"
      @action="load"
    />

    <template v-else-if="valid.length || invalid.length">
      <p v-if="message" class="cart-msg">{{ message }}</p>

      <!-- 有效条目 -->
      <div class="ml-card">
        <div class="ml-card-title">已选商品（{{ valid.length }}）</div>
        <div v-for="item in valid" :key="item.id" class="cart-row">
          <MlCheck
            class="cart-check"
            :model-value="item.selected"
            @update:model-value="(v: boolean) => onToggleSelected(item, v)"
          />
          <RouterLink class="cart-thumb" :to="`/product/${item.spu.id}`">
            <img v-if="item.spu.picUrl" :src="item.spu.picUrl" :alt="item.spu.name" />
            <span v-else class="ph">图</span>
          </RouterLink>
          <div class="cart-info">
            <RouterLink class="cart-name" :to="`/product/${item.spu.id}`">
              {{ item.spu.name }}
            </RouterLink>
            <p class="cart-spec">
              <template v-for="p in item.sku.properties" :key="p.valueId">
                {{ p.propertyName }}：{{ p.valueName }}
              </template>
            </p>
          </div>
          <div class="cart-price">{{ formatYuan(item.sku.price) }}</div>
          <div class="cart-qty">
            <span class="cart-qty-minus qty-btn" @click="onStepQty(item, -1)">−</span>
            <span class="cart-qty-num qty-num">{{ item.count }}</span>
            <span class="cart-qty-plus qty-btn plus" @click="onStepQty(item, 1)">+</span>
          </div>
          <div class="cart-subtotal">{{ formatYuan(item.sku.price * item.count) }}</div>
          <button class="cart-remove" type="button" aria-label="删除" @click="onRemove(item)">
            ×
          </button>
        </div>
      </div>

      <!--
        失效条目：**由后端 invalidList 决定**，前端不重新判断（FR-023）。
        这里不渲染勾选框 —— 它们不能参与结算。
      -->
      <div v-if="invalid.length" class="ml-card cart-invalid">
        <div class="ml-card-title">已失效（{{ invalid.length }}）</div>
        <p class="ml-hint">以下商品已下架或售罄，不参与结算</p>
        <div v-for="item in invalid" :key="item.id" class="cart-row is-invalid">
          <span class="cart-invalid-tag">失效</span>
          <div class="cart-thumb">
            <img v-if="item.spu.picUrl" :src="item.spu.picUrl" :alt="item.spu.name" />
            <span v-else class="ph">图</span>
          </div>
          <div class="cart-info">
            <span class="cart-name">{{ item.spu.name }}</span>
          </div>
          <div class="cart-price">{{ formatYuan(item.sku.price) }}</div>
          <button class="cart-remove" type="button" aria-label="删除" @click="onRemove(item)">
            ×
          </button>
        </div>
      </div>

      <!-- 结算条 -->
      <div class="cart-bar">
        <span class="cart-selected-count">已选 {{ selectedItems.length }} 件</span>
        <span class="cart-total-label">合计</span>
        <span class="cart-total">{{ totalText }}</span>
        <button
          id="toCheckout"
          class="btn-buy"
          type="button"
          :disabled="!canCheckout"
          @click="onCheckout"
        >
          去结算
        </button>
      </div>
    </template>

    <EmptyState
      v-else
      icon="🛒"
      title="购物车还是空的"
      desc="挑几件商品放进来吧"
      action-text="去商城逛逛"
      @action="router.push('/mall')"
    />
  </div>
</template>

<style scoped>
.cart-row {
  display: grid;
  grid-template-columns: 24px 72px 1fr auto auto auto 28px;
  gap: 12px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--ml-border);
}
.cart-row:last-child {
  border-bottom: 0;
}
.cart-thumb {
  width: 72px;
  height: 72px;
  border-radius: var(--ml-radius-field);
  overflow: hidden;
  background: var(--ml-bg-card);
  display: block;
}
.cart-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cart-name {
  font-size: 15px;
  color: var(--ml-text);
  display: block;
}
.cart-spec {
  font-size: 13px;
  color: var(--ml-text-ph);
  margin-top: 4px;
}
.cart-price,
.cart-subtotal {
  font-size: 14px;
  color: var(--ml-text);
  font-variant-numeric: tabular-nums;
  min-width: 72px;
  text-align: right;
}
.cart-subtotal {
  color: var(--ml-orange);
  font-weight: 600;
}
.cart-qty {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--ml-border-2);
  border-radius: var(--ml-radius-field);
  height: 32px;
  overflow: hidden;
}
.cart-qty .qty-btn {
  width: 30px;
  text-align: center;
  cursor: pointer;
  color: var(--ml-text-sub);
  user-select: none;
}
.cart-qty .qty-num {
  width: 34px;
  text-align: center;
}
.cart-remove {
  border: 0;
  background: none;
  color: var(--ml-text-ph);
  font-size: 16px;
  cursor: pointer;
  width: 28px;
}
.cart-remove:hover {
  color: var(--ml-orange);
}
.cart-row.is-invalid {
  opacity: 0.6;
}
.cart-invalid-tag {
  font-size: 11px;
  color: var(--ml-text-ph);
  border: 1px solid var(--ml-border-2);
  border-radius: 2px;
  padding: 1px 4px;
}
.cart-msg {
  color: var(--ml-orange);
  font-size: 13px;
  margin-bottom: 12px;
}
.cart-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: flex-end;
  padding: 16px 0 0;
}
.cart-selected-count,
.cart-total-label {
  font-size: 14px;
  color: var(--ml-text-sub);
}
.cart-total {
  font-size: 20px;
  font-weight: 700;
  color: var(--ml-orange);
  font-variant-numeric: tabular-nums;
}
#toCheckout {
  padding: 0 28px;
}
#toCheckout:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@media (max-width: 768px) {
  .cart-row {
    grid-template-columns: 24px 56px 1fr 28px;
    row-gap: 8px;
  }
  .cart-price,
  .cart-qty,
  .cart-subtotal {
    grid-column: 3 / 4;
    text-align: left;
  }
  .cart-bar {
    position: sticky;
    bottom: 0;
    background: var(--ml-white);
    border-top: 1px solid var(--ml-border);
    padding: 12px 0;
  }
}
</style>
