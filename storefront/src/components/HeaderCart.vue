<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listCart } from '@/api/cart'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { formatYuan } from '@/utils/money'
import type { CartItem } from '@/types'

const cart = useCartStore()
const user = useUserStore()
const route = useRoute()
const open = ref(false)
const loading = ref(false)
const failed = ref(false)
const items = ref<CartItem[]>([])
const invalidCount = ref(0)
const brokenImages = ref(new Set<number>())
const quantity = computed(() => items.value.reduce((sum, item) => sum + item.count, 0))
const subtotal = computed(() => formatYuan(items.value.reduce((sum, item) => sum + item.sku.price * item.count, 0)))
let request = 0
let closeTimer: ReturnType<typeof setTimeout> | undefined
function cancelClose() { clearTimeout(closeTimer) }
function close() { cancelClose(); open.value = false }
function scheduleClose() { cancelClose(); closeTimer = setTimeout(close, 300) }
async function load() {
  const current = ++request
  if (!user.isLogin) { items.value = []; invalidCount.value = 0; loading.value = false; return }
  loading.value = true
  failed.value = false
  try {
    const result = await listCart()
    if (current !== request) return
    items.value = result.validList
    invalidCount.value = result.invalidList.length
    brokenImages.value = new Set()
  } catch {
    if (current === request) { failed.value = true; items.value = []; invalidCount.value = 0 }
  } finally {
    if (current === request) loading.value = false
  }
}
function show() {
  cancelClose()
  if (open.value) return
  open.value = true
  void load()
}
function onFocusOut(event: FocusEvent) {
  if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) close()
}
watch(() => route.fullPath, close)
watch(() => user.isLogin, () => {
  ++request
  items.value = []
  invalidCount.value = 0
  if (open.value) void load()
})
watch(() => cart.count, () => { if (open.value && user.isLogin) void load() })
onBeforeUnmount(() => { ++request; cancelClose() })
</script>

<template>
  <div
    class="header-cart"
    @mouseenter="show"
    @mouseleave="scheduleClose"
    @focusin="show"
    @focusout="onFocusOut"
    @keydown.esc.stop.prevent="close"
  >
    <RouterLink
      class="cart-chip"
      to="/cart"
      :aria-expanded="open"
      aria-controls="header-cart-preview"
    >
      购物车<span
        v-if="cart.count > 0"
        class="cart-count"
      >({{ cart.count }})</span>
    </RouterLink>
    <div
      v-if="open"
      id="header-cart-preview"
      class="header-cart-preview"
      role="region"
      aria-label="购物车预览"
      @mouseenter="cancelClose"
    >
      <div class="cart-preview-heading">
        <h2>我的购物车</h2><span v-if="user.isLogin && !loading && !failed">{{ quantity }} 件商品</span>
      </div>
      <div
        v-if="!user.isLogin"
        class="cart-preview-state"
      >
        <span
          class="cart-preview-symbol"
          aria-hidden="true"
        >▤</span><p>登录后查看你的购物车</p><small>已添加的商品会在这里显示</small>
      </div>
      <p
        v-else-if="loading"
        class="cart-preview-state"
        role="status"
      >
        正在加载购物车…
      </p>
      <div
        v-else-if="failed"
        class="cart-preview-state"
      >
        <p>购物车暂时加载失败</p><button
          type="button"
          @click="load"
        >
          重新加载
        </button>
      </div>
      <template v-else>
        <div
          v-if="items.length"
          class="cart-preview-items"
        >
          <RouterLink
            v-for="item in items.slice(0, 4)"
            :key="item.id"
            class="cart-preview-item"
            :to="`/product/${item.spu.id}`"
          >
            <div class="cart-preview-image">
              <img
                v-if="(item.sku.picUrl || item.spu.picUrl) && !brokenImages.has(item.id)"
                :src="item.sku.picUrl || item.spu.picUrl"
                :alt="item.spu.name"
                @error="brokenImages.add(item.id)"
              ><span v-else>暂无图片</span>
            </div>
            <div class="cart-preview-description">
              <strong>{{ item.spu.name }}</strong><small>{{ item.sku.properties.map(p => p.valueName).join(' / ') || '标准规格' }}</small><div><span>{{ formatYuan(item.sku.price) }}</span><small>× {{ item.count }}</small></div>
            </div>
          </RouterLink>
        </div>
        <div
          v-else
          class="cart-preview-state"
        >
          <span
            class="cart-preview-symbol"
            aria-hidden="true"
          >▤</span><p>{{ invalidCount ? '购物车中暂无可购买商品' : '购物车还是空的' }}</p><small>挑选合适的标签与设备，加入购物车</small>
        </div>
        <p
          v-if="items.length > 4"
          class="cart-preview-note"
        >
          更多商品请进入购物车查看
        </p>
        <p
          v-if="invalidCount"
          class="cart-preview-note"
        >
          另有 {{ invalidCount }} 项失效商品，请进入购物车处理
        </p>
        <div
          v-if="items.length"
          class="cart-preview-total"
        >
          <span>商品小计</span><strong>{{ subtotal }}</strong>
        </div>
      </template>
      <RouterLink
        class="cart-preview-action"
        to="/cart"
      >
        {{ user.isLogin ? '查看购物车' : '登录并查看购物车' }} <span aria-hidden="true">→</span>
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.header-cart { position: relative; display: flex; align-items: center; }
.header-cart-preview { position: absolute; right: 0; top: calc(100% + 16px); width: min(360px, calc(100vw - 32px)); box-sizing: border-box; padding: 22px; background: #fff; border: 1px solid #e5ebf4; border-radius: 14px; box-shadow: 0 16px 44px #152b501f; color: #162c50; }
.header-cart-preview::before { content: ''; position: absolute; left: 0; right: 0; top: -18px; height: 18px; }
.cart-preview-heading { display: flex; align-items: center; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid #edf1f7; }
.cart-preview-heading h2 { font-size: 16px; margin: 0; }
.cart-preview-heading > span, .cart-preview-note, .cart-preview-total > span { color: #8593a7; font-size: 12px; }
.cart-preview-items { max-height: min(380px, calc(100dvh - 290px)); overflow-y: auto; overscroll-behavior: contain; }
.header .cart-preview-item { display: flex; gap: 12px; padding: 14px 0; border-bottom: 1px solid #f0f3f8; white-space: normal; }
.cart-preview-image { width: 64px; height: 64px; flex: 0 0 64px; border-radius: 8px; background: #f6f8fc; display: grid; place-items: center; overflow: hidden; color: #96a3b7; font-size: 10px; }
.cart-preview-image img { width: 100%; height: 100%; object-fit: contain; }
.cart-preview-description { flex: 1; min-width: 0; }
.cart-preview-description strong { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 12px; line-height: 1.6; font-weight: 500; color: #213955; }
.cart-preview-description small { color: #8c99ad; font-size: 11px; }
.cart-preview-description > small { display: block; margin: 4px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cart-preview-description > div { display: flex; justify-content: space-between; color: #ef7545; font-size: 12px; }
.cart-preview-state { padding: 28px 0; text-align: center; font-size: 13px; color: #6e809a; }
.cart-preview-state p { margin: 10px 0 6px; }
.cart-preview-state small { color: #96a3b7; font-size: 12px; }
.cart-preview-symbol { display: inline-grid; place-items: center; width: 44px; height: 44px; background: #f0f6ff; color: #80a8d6; border-radius: 12px; font-size: 26px; }
.cart-preview-state button { margin-top: 8px; background: #eef5ff; border: 0; border-radius: 6px; color: #2e7cd6; padding: 8px 14px; cursor: pointer; }
.cart-preview-note { margin: 12px 0; line-height: 1.6; }
.cart-preview-total { display: flex; align-items: center; justify-content: space-between; padding: 18px 0; }
.cart-preview-total strong { color: #ef7545; font-size: 20px; }
.header .cart-preview-action { display: flex; justify-content: space-between; padding: 12px 16px; background: #2e7cd6; color: white; border-radius: 8px; font-size: 13px; }
.header .cart-preview-action:hover { background: #246bc1; }
@media (max-width: 1100px) { .header-cart-preview { display: none; } }
</style>
