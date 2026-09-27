<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'

import LoginDialog from './LoginDialog.vue'

/**
 * 顶栏 —— 按设计稿 `www/*.html` 的 `.header` 结构还原，沿用其 class 名
 * （`.logo` / `.nav` / `.header-right` / `.cart-chip` / `.login` / `.btn-primary`
 * / `.nav-toggle` / `.mobile-nav`），因此 `design.css` 的样式直接生效。
 *
 * 实时状态按故事分两步接入：
 *   · 登录态：已接入（随 US2）—— 本组件同时承载全站唯一的 `LoginDialog`
 *   · 购物车角标：T087（随 US3）
 *
 * ⚠️ 设计稿写死的「购物车 (2)」已被移除（FR-043）；未登录时不显示角标数字。
 *
 * 全站唯一性：`LoginDialog` 只在本组件里渲染一次，因此任何页面点「登录 / 注册」
 * 打开的都是同一个实例，登录态也天然全站同步（FR-042）。
 */

/**
 * 导航项 —— **固定为设计稿的这五项，不再往顶栏加入口**。
 *
 * 「领券中心」与「我的订单」一度放在这里（T125 / T113），但**已移到个人中心**
 * （2026-09-27，按所有者反馈：「放在个人中心里面比较合理」）。移到 `AccountView`
 * 的侧栏里，见那里的「领券中心」「我的订单」两项。
 *
 * 顺带解决了一个真实缺陷：顶栏加到 7 项后，1101–1200px 这段空间不够，
 * 而 header 里的文字**没有 `nowrap`**，于是每一个子项都逐字竖排折行
 * （「赋签」变成上下两个字）。回到 5 项 + 补 `nowrap` 之后不再出现。
 */
const NAV = [
  { to: '/', label: '首页', name: 'home' },
  { to: '/mall', label: '商城', name: 'mall' },
  { to: '/software', label: '标签软件', name: 'software' },
  { to: '/solutions', label: '行业方案', name: 'solutions' },
  { to: '/support', label: '服务支持', name: 'support' },
] as const

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const loginOpen = ref(false)

function toggleMobileNav(): void {
  document.body.classList.toggle('nav-open')
}

/**
 * `?login=1` 打开登录弹层。需登录的页面由路由守卫加上这个参数（T074），
 * 顶栏的「登录 / 注册」也走同一条路径 —— 只有一个入口。
 */
watch(
  () => route.query.login,
  (v) => {
    if (v) loginOpen.value = true
  },
  { immediate: true },
)

function openLogin(): void {
  loginOpen.value = true
}

function closeLogin(): void {
  loginOpen.value = false
  // 关掉弹层时把 ?login=1 清掉，避免刷新后又弹出来
  if (route.query.login) {
    const q = { ...route.query }
    delete q.login
    router.replace({ query: q })
  }
}

/** 从汉堡菜单打开登录弹层：先把菜单收起来，免得它挡在弹层后面 */
function openLoginFromMobile(): void {
  document.body.classList.remove('nav-open')
  openLogin()
}

function onLoginSuccess(): void {
  closeLogin()
}

// 角标数量以**后端**为准（FR-019）；未登录时不显示数字
onMounted(() => {
  void cartStore.refreshCount()
})

function isActive(name: string): boolean {
  return route.name === name
}
</script>

<template>
  <nav class="header">
    <RouterLink class="logo" to="/">
      <img class="logo-img" src="/assets/logo.png" alt="赋签 MaxLabel" />
      <span class="logo-cn">赋签</span>
      <span class="logo-en">MaxLabel</span>
    </RouterLink>

    <div class="nav">
      <RouterLink
        v-for="item in NAV"
        :key="item.name"
        :to="item.to"
        :class="{ active: isActive(item.name) }"
      >
        {{ item.label }}
      </RouterLink>
    </div>

    <div class="header-right">
      <!-- 角标数字由 T087 接入；未登录 / 为空时不显示数字（设计稿的「购物车 (2)」已移除） -->
      <RouterLink class="cart-chip" to="/cart">
        购物车<span v-if="cartStore.count > 0" class="cart-count">({{ cartStore.count }})</span>
      </RouterLink>
      <!-- 已登录：入口变为个人中心，并显示昵称或脱敏手机号（FR-016）
           未登录：点它打开登录弹层（全站唯一实例） -->
      <!-- ⚠️ 兜底文案不能省：`displayName` 在"有令牌但会员信息还没回来/拉不到"时是空的，
           没有兜底就会渲染成一个 **0×0 的空白链接** —— 用户看到的是"个人中心不见了"
           （实测过：`a` 标签、文字为空、宽高 0）。移动端那处一直有兜底，桌面端漏了。 -->
      <RouterLink v-if="userStore.isLogin" class="login" to="/account">
        {{ userStore.displayName || '个人中心' }}
      </RouterLink>
      <button v-else class="login login-btn" type="button" @click="openLogin">
        登录 / 注册
      </button>
      <RouterLink class="btn-primary" to="/software">免费试用软件</RouterLink>
      <button class="nav-toggle" aria-label="打开菜单" @click="toggleMobileNav">
        <span></span><span></span><span></span>
      </button>
    </div>

    <div class="mobile-nav">
      <!-- ⚠️ 汉堡菜单里的**登录 / 个人中心入口不能省**：
           设计稿有一条 `@media (max-width:768px) { .login { display:none } }`，
           而 `.login` 正是「登录入口」与「个人中心入口」的**同一个元素** ——
           少了它，手机上既登录不了、登录后也进不去个人中心（FR-016 在手机上失效）。 -->
      <RouterLink v-if="userStore.isLogin" class="m-login" to="/account">
        {{ userStore.displayName || '个人中心' }}
      </RouterLink>
      <button v-else class="m-login m-login-btn" type="button" @click="openLoginFromMobile">
        登录 / 注册
      </button>
      <RouterLink
        v-for="item in NAV"
        :key="item.name"
        :to="item.to"
        :class="{ active: isActive(item.name) }"
      >
        {{ item.label }}
      </RouterLink>
    </div>

  </nav>

  <!-- ⚠️ 弹层必须放在 `.header` **外面**（本组件因此是双根节点）。
       design.css 有一条 `@media (max-width:480px) { .header .btn-primary { display:none } }`，
       本意是藏顶栏那个「免费试用软件」按钮；弹层一旦嵌在 `<nav class="header">` 里，
       它的提交按钮（同样是 `.btn-primary`）会在 ≤480px 被**一起藏掉** ——
       手机上表现为"登录弹层能打开，但提交按钮点不动"。
       这条规则来自设计稿样式表（当时是逐字拷贝、不可改），所以只能把弹层移出去。
       ⚠️ 2026-09-27 起设计稿基线已放开、`design.css` 可改 —— 但**这里不要改回去**：
       把弹层塞回 `<nav class="header">` 里，等于让弹层的按钮继续受 `.header` 下的
       后代选择器摆布，是个结构性的坑，不是靠调 CSS 能根治的。 -->
  <LoginDialog :open="loginOpen" @close="closeLogin" @success="onLoginSuccess" />
</template>
