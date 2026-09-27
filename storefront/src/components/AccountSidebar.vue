<script setup lang="ts">
import { useRoute } from 'vue-router'

/**
 * 个人中心左侧菜单 —— **共用组件**。
 *
 * 为什么抽出来：这五个页面是一组（个人中心 / 我的订单 / 领券中心 / 收货地址 / 我的券），
 * 原先只有 `/account` 自带侧栏，点进其余任一页菜单就**消失**了，用户只能靠浏览器
 * 后退回去 —— 所有者 2026-09-27 反馈过这一点（"点选后不要没有菜单"）。
 *
 * 版式沿用设计稿的 `.sidebar` / `.s-item`（design-new-pages §3.5 给 `/account` 定的），
 * 两列布局的 `.account-wrap` / `.account-main` 放在 `store.css` 里供各页复用。
 */

const ITEMS = [
  { to: '/account', label: '个人中心' },
  { to: '/order', label: '我的订单' },
  { to: '/coupon', label: '领券中心' },
  { to: '/account/address', label: '收货地址' },
  { to: '/coupon/mine', label: '我的券' },
] as const

const route = useRoute()

/**
 * 有子路由的项必须**精确匹配**：
 *   · `/account` —— 否则 `/account/address` 会把「个人中心」也点亮；
 *   · `/coupon`  —— 否则 `/coupon/mine` 会**同时**点亮「领券中心」与「我的券」
 *     （实测过：两项都带 `active`）。
 * 其余项用 `startsWith`，这样订单详情（`/order/:id`）仍让「我的订单」保持高亮。
 */
const EXACT_MATCH = new Set(['/account', '/coupon'])

function isActive(to: string): boolean {
  return EXACT_MATCH.has(to) ? route.path === to : route.path.startsWith(to)
}
</script>

<template>
  <aside class="sidebar account-sidebar">
    <RouterLink
      v-for="item in ITEMS"
      :key="item.to"
      class="s-item"
      :class="{ active: isActive(item.to) }"
      :to="item.to"
    >
      {{ item.label }}
    </RouterLink>
  </aside>
</template>
