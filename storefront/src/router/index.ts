import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteLocationRaw,
} from 'vue-router'

import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { getAccessToken } from '@/utils/auth'

/**
 * 路由表 —— 共 28 条（含个人中心新增页、行业详情、支持文章及联系支持）。
 *   6 条来自设计稿（须忠实还原，FR-046）
 *   13 条功能页（无设计稿，版式见 design-new-pages.md）
 *    6 条信息页（无设计稿，承接全站 36 处营销/公司入口，FR-053）
 *
 * 登录**不占路由**：由 LoginDialog 弹层承载。需登录时在当前路由加
 * `?login=1&redirect=<path>` 打开弹层，登录成功后按 redirect 回原处（FR-010 / FR-015）。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_BASE_PATH || '/'),
  routes: [
    {
      path: '/',
      component: DefaultLayout,
      children: [
        // ── 设计稿页（6） ──────────────────────────────────────────
        { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
        { path: 'mall', name: 'mall', component: () => import('@/views/MallView.vue') },
        {
          path: 'product/:id',
          name: 'product',
          component: () => import('@/views/ProductView.vue'),
          // 必须显式声明：没有它，组件里的 `id` 永远是 undefined，
          // `Number(undefined)` 变成 NaN，请求就成了 `get-detail?id=NaN`。
          props: true,
        },
        { path: 'software', name: 'software', component: () => import('@/views/SoftwareView.vue') },
        { path: 'solutions', alias: '/solution', name: 'solutions', component: () => import('@/views/SolutionsView.vue') },
        { path: 'solutions/:industry', alias: '/solution/:industry', name: 'solution-detail', component: () => import('@/views/IndustrySolutionView.vue') },
        { path: 'support', name: 'support', component: () => import('@/views/SupportView.vue') },
        { path: 'support/contact', name: 'support-contact', component: () => import('@/views/SupportContactView.vue') },
        { path: 'support/:slug', name: 'support-article', component: () => import('@/views/SupportArticleView.vue') },

        // ── 功能页（11 条路由 / 10 个视图） ────────────────────────
        { path: 'cart', name: 'cart', meta: { requiresAuth: true }, component: () => import('@/views/CartView.vue') },
        {
          path: 'checkout',
          name: 'checkout',
          meta: { requiresAuth: true },
          component: () => import('@/views/CheckoutView.vue'),
          // 结算页有两条来源（商品页直购 / 购物车），入参走 query，这里显式映射成
          // props。**没有这个映射，`source` 会恒为默认的 `product`、`skuId` 恒为
          // undefined** —— 页面打得开，但永远在结算一个不存在的商品。
          props: (route) => ({
            source: route.query.source === 'cart' ? 'cart' : 'product',
            skuId: Number(route.query.skuId) || undefined,
            count: Number(route.query.count) || 1,
            cartIds: String(route.query.cartIds ?? '')
              .split(',')
              .filter(Boolean)
              .map(Number),
          }),
        },
        { path: 'order', name: 'order-list', meta: { requiresAuth: true }, component: () => import('@/views/OrderListView.vue') },
        {
          path: 'order/:id',
          name: 'order-detail',
          meta: { requiresAuth: true },
          component: () => import('@/views/OrderDetailView.vue'),
          // 同 product/:id —— 订单详情也靠 `id` 取数，漏了 props 就会请求 id=NaN
          props: true,
        },
        { path: 'account', name: 'account', meta: { requiresAuth: true }, component: () => import('@/views/AccountView.vue') },
        { path: 'account/address', name: 'address', meta: { requiresAuth: true }, component: () => import('@/views/AddressView.vue') },
        {
          path: 'account/after-sale',
          name: 'after-sale',
          meta: { requiresAuth: true },
          component: () => import('@/views/AfterSaleListView.vue'),
        },
        {
          path: 'account/points',
          name: 'points',
          meta: { requiresAuth: true },
          component: () => import('@/views/PointsRecordView.vue'),
        },
        { path: 'coupon', name: 'coupon-center', component: () => import('@/views/CouponCenterView.vue') },
        { path: 'coupon/mine', name: 'coupon-mine', meta: { requiresAuth: true }, component: () => import('@/views/MyCouponView.vue') },
        {
          path: 'agreement/user',
          name: 'agreement-user',
          component: () => import('@/views/AgreementView.vue'),
          props: { kind: 'user' },
        },
        {
          path: 'agreement/privacy',
          name: 'agreement-privacy',
          component: () => import('@/views/AgreementView.vue'),
          props: { kind: 'privacy' },
        },
        { path: 'enterprise', name: 'enterprise', component: () => import('@/views/EnterpriseView.vue') },

        // ── 信息页（6，承接营销/公司入口，FR-053） ─────────────────
        { path: 'templates', name: 'templates', component: () => import('@/views/TemplateCenterView.vue') },
        { path: 'changelog', name: 'changelog', component: () => import('@/views/ChangelogView.vue') },
        { path: 'about', name: 'about', component: () => import('@/views/AboutView.vue') },
        { path: 'news', name: 'news', component: () => import('@/views/NewsView.vue') },
        { path: 'contact', name: 'contact', component: () => import('@/views/ContactView.vue') },
        { path: 'jobs', name: 'jobs', component: () => import('@/views/JobsView.vue') },
      ],
    },
  ],
  scrollBehavior: (to) => {
    if (to.path.startsWith('/support') && to.hash) {
      const element = document.getElementById(to.hash.slice(1))
      if (element) return { el: element, top: 100, behavior: 'smooth' }
    }
    return { top: 0 }
  },
})

/**
 * 需登录页面的守卫（FR-015）。
 *
 * ⚠️ 本项目**没有 `/login` 路由** —— 登录由 `LoginDialog` 承载。所以守卫不是跳到
 * 登录页，而是在当前路由上挂 `?login=1&redirect=<去不了的页面>`：弹层打开，登录
 * 成功后按 `redirect` 回到原处，用户不必再找一遍自己要去的页面。
 */
export function authGuard(to: RouteLocationNormalized): true | RouteLocationRaw {
  if (!to.meta.requiresAuth) return true
  if (getAccessToken()) return true
  // ⚠️ 已经带着登录提示来过一次就**放行**，否则会无限重定向：
  // 重定向到同一路径 + ?login=1，而该路径仍需登录 → 又触发重定向 → 死循环。
  // （这条是本实现的第一版真实踩到的：整个应用会挂死。）
  if (to.query.login) return true
  return { path: to.path, query: { ...to.query, login: '1', redirect: to.fullPath } }
}

router.beforeEach(authGuard)

export default router
