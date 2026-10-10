<script setup lang="ts">
import { useRoute } from 'vue-router'

/**
 * 个人中心左侧菜单 —— **共用组件**。
 *
 * 为什么抽出来：这些页面是一组（个人中心 / 我的订单 / 我的售后 / 领券中心 / 收货地址 /
 * 我的券 / 积分与等级），原先只有 `/account` 自带侧栏，点进其余任一页菜单就**消失**了，
 * 用户只能靠浏览器后退回去 —— 所有者 2026-09-27 反馈过这一点（"点选后不要没有菜单"）。
 *
 * **2026-10-09 改为按主题分三组**（FR-011e）：原先 7 项平铺，看不出「资料 / 交易 / 权益」
 * 的结构，这正是所有者说的"整理排布"。分组只按**用户心智**归，不按后端模块：
 *   · 地址属于「账户资料」（和姓名手机号同类）；
 *   · 售后属于「我的交易」（它是订单的延伸，`orderNo`/`orderItemId` 都来自订单）；
 *   · 券与积分属于「我的权益」。
 *
 * 版式沿用设计稿的 `.sidebar` / `.s-item`（design-new-pages §3.5），分组标题另加 `.s-group-title`。
 */
const GROUPS = [
  {
    title: '账户资料',
    items: [
      { to: '/account', label: '个人中心' },
      { to: '/account/address', label: '收货地址' },
    ],
  },
  {
    title: '我的交易',
    items: [
      { to: '/order', label: '我的订单' },
      { to: '/account/after-sale', label: '我的售后' },
    ],
  },
  {
    title: '我的权益',
    items: [
      { to: '/coupon/mine', label: '我的券' },
      { to: '/coupon', label: '领券中心' },
      { to: '/account/points', label: '积分与等级' },
    ],
  },
] as const

const route = useRoute()

/**
 * 有子路由的项必须**精确匹配**：
 *   · `/account` —— 否则 `/account/address`、`/account/points` 会把「个人中心」也点亮；
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
    <div v-for="g in GROUPS" :key="g.title" class="s-group">
      <div class="s-group-title">{{ g.title }}</div>
      <RouterLink
        v-for="item in g.items"
        :key="item.to"
        class="s-item"
        :class="{ active: isActive(item.to) }"
        :to="item.to"
      >
        {{ item.label }}
      </RouterLink>
    </div>
  </aside>
</template>
