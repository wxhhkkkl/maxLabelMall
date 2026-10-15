<script setup lang="ts">
import { useToasts } from './useToasts'

/** C2 轻提示的渲染宿主，全站挂一个即可 */
const { items, dismiss } = useToasts()
</script>

<template>
  <div
    class="ml-toast-wrap"
    aria-live="polite"
  >
    <div
      v-for="t in items"
      :key="t.id"
      class="ml-toast"
      :class="[`is-${t.kind}`, { 'is-cart-success': t.kind === 'success' && t.text === '已加入购物车' }]"
      @click="dismiss(t.id)"
    >
      <template v-if="t.kind === 'success' && t.text === '已加入购物车'">
        <svg
          class="cart-toast-icon"
          viewBox="0 0 28 28"
          fill="none"
          aria-hidden="true"
        ><circle
          cx="14"
          cy="14"
          r="14"
          fill="#2e7cd6"
        /><path
          d="m8 14 4 4 8-8"
          stroke="white"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        /></svg>
        <div class="cart-toast-content">
          <strong>{{ t.text }}</strong><RouterLink
            to="/cart"
            @click.stop="dismiss(t.id)"
          >
            查看购物车 <span aria-hidden="true">→</span>
          </RouterLink>
        </div>
        <button
          class="cart-toast-close"
          type="button"
          aria-label="关闭加购提示"
          @click.stop="dismiss(t.id)"
        >
          <svg
            viewBox="0 0 20 20"
            fill="none"
            aria-hidden="true"
          ><path
            d="m5 5 10 10M15 5 5 15"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
          /></svg>
        </button>
      </template>
      <template v-else>
        {{ t.text }}
      </template>
    </div>
  </div>
</template>

<style scoped>
.ml-toast-wrap { top: calc(94px + env(safe-area-inset-top, 0px)); max-width: calc(100vw - 32px); }
.ml-toast.is-cart-success { display: flex; align-items: center; gap: 16px; width: 350px; max-width: calc(100vw - 32px); min-width: 0; padding: 20px; background: #f0f7ff; border: 1px solid #9cc3ef; border-left: 4px solid #2e7cd6; border-radius: 9px; box-shadow: 0 8px 30px #183c6933; pointer-events: auto; cursor: default; }
.cart-toast-icon { width: 28px; height: 28px; flex: none; }
.cart-toast-content { flex: 1; min-width: 0; }
.cart-toast-content strong { display: block; color: #193d66; font-size: 16px; font-weight: 600; line-height: 1.5; }
.cart-toast-content a { display: inline-flex; align-items: center; gap: 12px; margin-top: 6px; color: #2e7cd6; font-size: 13px; }
.cart-toast-content a:hover { text-decoration: underline; }
.cart-toast-close { align-self: flex-start; display: flex; align-items: center; justify-content: center; flex: none; border: 0; padding: 3px; background: transparent; color: #7893b2; cursor: pointer; border-radius: 3px; }
.cart-toast-close svg { width: 18px; height: 18px; }
.cart-toast-close:hover { color: #264f7c; background: #dbeafa; }
.is-cart-success a:focus-visible, .cart-toast-close:focus-visible { outline: 2px solid #2e7cd6; outline-offset: 3px; }
@media(max-width: 768px) { .ml-toast-wrap { top: calc(80px + env(safe-area-inset-top, 0px)); } }
</style>
