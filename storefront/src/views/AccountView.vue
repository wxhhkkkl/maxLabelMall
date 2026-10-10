<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { isCaptchaEnabled } from '@/api/captcha'
import { sendSmsCode, SMS_SCENE_UPDATE_PASSWORD, updatePassword } from '@/api/member'
import AccountSidebar from '@/components/AccountSidebar.vue'
import ProfileEditDialog from '@/components/ProfileEditDialog.vue'
import CaptchaSlider from '@/components/CaptchaSlider.vue'
import MlField from '@/components/base/MlField.vue'
import MlModal from '@/components/base/MlModal.vue'
import { useUserStore } from '@/store/user'

/**
 * 个人中心。
 *
 * 关键点：**设置密码是登录之后才做的事**。后端没有独立注册接口，注册由
 * 「手机号 + 验证码」隐式完成，所以新用户一开始是没有密码的 —— 本页的
 * 设置密码入口就是补上这一步，让用户之后也能用密码登录（FR-011）。
 *
 * ⚠️ **改密必须带一张 scene 3 的短信验证码**。后端
 * `AppMemberUserUpdatePasswordReqVO.code` 是必填，且服务端会 `useSmsCode(scene=3)`
 * 实际核销。早先这里只传了 `password`，后端一律回「手机验证码不能为空」，
 * 于是这条路径**从未真正可用** —— 只有断言请求体的测试才抓得住这类漏写。
 */
const router = useRouter()
const userStore = useUserStore()

const password = ref('')
const password2 = ref('')
const code = ref('')
const error = ref('')
const codeError = ref('')
const ok = ref('')
const submitting = ref(false)
const countdown = ref(0)
/** 滑块弹层开关（发短信前的那道闸门） */
const captchaOpen = ref(false)
/** 编辑资料弹层开关与保存成功提示 */
const profileOpen = ref(false)
const profileOk = ref('')
const passwordOpen = ref(false)

async function openPassword() {
  passwordOpen.value = true
  await nextTick()
  document.getElementById('newPassword')?.focus()
}

async function closePassword() {
  if (submitting.value || captchaOpen.value) return
  passwordOpen.value = false
  password.value = ''
  password2.value = ''
  code.value = ''
  error.value = ''
  codeError.value = ''
  ok.value = ''
  // Keep the SMS countdown when closing so reopening cannot bypass the interval.
  await nextTick()
  document.getElementById('changePassword')?.focus()
}

/**
 * 资料保存成功。
 *
 * ⚠️ **必须重新拉取会员信息**：顶栏的昵称/头像读的是 store 里的 `member`，
 * 只改本地副本不会让顶栏变 —— 而 SC-022 要求「不刷新页面，顶栏与个人中心都显示新值」。
 */
async function onProfileSaved() {
  profileOk.value = '资料已保存'
  await userStore.loadMember()
}

const MIN_LEN = 6

/** 发码要有手机号 —— 会员信息是异步来的，没回来之前不知道该发给谁 */
const mobile = computed(() => userStore.member?.mobile ?? '')

/**
 * 会员等级名。**没有等级时显示「暂无等级」**（租户 162 现在就是零配置）——
 * 如实呈现，不编造一个等级名（FR-011d）。
 */
const levelName = computed(() => userStore.member?.level?.name || '暂无等级')
const canGetCode = computed(() => !!mobile.value && countdown.value === 0)

let timer: ReturnType<typeof setInterval> | null = null
function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
      countdown.value = 0
    }
  }, 1000)
}

// 离开页面 / 组件卸载时别留着定时器
watch(
  () => userStore.isLogin,
  (v) => {
    if (!v && timer) {
      clearInterval(timer)
      timer = null
      countdown.value = 0
    }
  },
)

/**
 * 点「获取验证码」。
 *
 * 与登录弹层同一道闸门：发短信前先过图形验证码（开关由**服务端**给，
 * 关着时直接发，不弹滑块）。
 */
async function onGetCode() {
  if (!canGetCode.value) return
  codeError.value = ''
  if (await isCaptchaEnabled()) {
    captchaOpen.value = true
    return
  }
  await doSendCode()
}

/** 真正发短信。没凭据时**不传第三个参数**（与 `@/api/member` 的 body 处理一致） */
async function doSendCode(captchaVerification?: string) {
  try {
    if (captchaVerification) {
      await sendSmsCode(mobile.value, SMS_SCENE_UPDATE_PASSWORD, captchaVerification)
    } else {
      await sendSmsCode(mobile.value, SMS_SCENE_UPDATE_PASSWORD)
    }
    startCountdown()
  } catch (e) {
    // 频率限制要告知还需等待多久，而不是笼统报错（FR-010b 的同一条口径）
    const raw = (e as { message?: string })?.message || '验证码发送失败，请稍后重试'
    codeError.value = /频繁|频率|过于/.test(raw) ? raw : `验证码发送失败：${raw}`
  }
}

/** 滑块通过 → 关上弹层，带上凭据继续发码 */
async function onCaptchaPassed(verification: string) {
  captchaOpen.value = false
  await doSendCode(verification)
}

async function onSetPassword() {
  error.value = ''
  codeError.value = ''
  ok.value = ''
  if (password.value.length < MIN_LEN) {
    error.value = `密码至少 ${MIN_LEN} 位`
    return
  }
  if (password.value !== password2.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  if (!code.value) {
    codeError.value = '请先获取并填写手机验证码'
    return
  }
  submitting.value = true
  try {
    await updatePassword(password.value, code.value)
    ok.value = '密码已设置，之后可用手机号 + 密码登录'
    password.value = ''
    password2.value = ''
    code.value = ''
  } catch (e) {
    error.value = (e as { message?: string })?.message || '设置失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function onLogout() {
  await userStore.logout()
  router.push('/')
}
</script>

<template>
  <div class="account-page">
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink><span>/ 个人中心</span>
  </div>

  <div class="account-wrap">
    <AccountSidebar />

    <div class="account-main account-dashboard">
      <div class="acct-heading">
        <div><h1>个人中心</h1><p>管理账户信息，轻松处理每一笔采购。</p></div>
        <RouterLink to="/mall" class="acct-shop-link">前往商城 <span aria-hidden="true">↗</span></RouterLink>
      </div>
      <div class="ml-card acct-profile-card">
        <div class="ml-card-title">账号信息</div>
        <div class="acct-profile">
          <img
            v-if="userStore.member?.avatar"
            class="acct-avatar"
            :src="userStore.member.avatar"
            :alt="userStore.displayName || '头像'"
          />
          <span v-else class="acct-avatar-ph" aria-label="尚未设置头像">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/></svg>
          </span>
          <div class="acct-profile-info">
            <p class="acct-name">
              {{ userStore.displayName || '未登录' }}
            </p>
            <p class="acct-mobile">{{ userStore.member?.mobile }}</p>
            <p v-if="!userStore.member?.nickname" class="ml-hint">
              尚未设置昵称，当前展示脱敏手机号
            </p>
          </div>
          <div class="acct-profile-actions">
          <button id="editProfile" class="btn-cart" type="button" @click="profileOpen = true">
            编辑资料
          </button>
          <button id="changePassword" class="acct-password-link" type="button" @click="openPassword">
            修改密码
          </button>
          </div>
        </div>
        <p v-if="profileOk" class="acct-ok">{{ profileOk }}</p>
      </div>

      <!-- 快捷入口三卡（design-new-pages.md §3.5）—— 订单 / 地址 / 券 -->
      <div class="acct-entries">
        <RouterLink class="entry-card" to="/order">
          <span class="acct-entry-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V2h6v2M9 10h6M9 15h6"/></svg></span>
          <div><h3>我的订单</h3><p>查看订单状态与物流</p></div><span class="acct-entry-arrow" aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink class="entry-card" to="/account/address">
          <span class="acct-entry-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2.5"/></svg></span>
          <div><h3>收货地址</h3><p>管理常用收货信息</p></div><span class="acct-entry-arrow" aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink class="entry-card" to="/coupon/mine">
          <span class="acct-entry-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M3 5h18v5a2 2 0 0 0 0 4v5H3v-5a2 2 0 0 0 0-4Z"/><path d="M15 5v3m0 3v2m0 3v3"/></svg></span>
          <div><h3>我的券</h3><p>查看可用优惠与使用记录</p></div><span class="acct-entry-arrow" aria-hidden="true">→</span>
        </RouterLink>
      </div>

      <div class="ml-card acct-benefits">
        <div class="acct-section-head"><div class="ml-card-title">积分与等级</div>
          <RouterLink class="acct-detail-link" to="/account/points">查看积分明细 <span aria-hidden="true">→</span></RouterLink>
        </div>
        <!-- 数据直接来自 /member/user/get，不需要额外请求 -->
        <div class="acct-stats">
          <div class="acct-stat">
            <span class="k">积分</span>
            <b>{{ userStore.member?.point ?? 0 }}</b>
          </div>
          <div class="acct-stat">
            <span class="k">经验值</span>
            <b>{{ userStore.member?.experience ?? 0 }}</b>
          </div>
          <div class="acct-stat">
            <span class="k">会员等级</span>
            <!-- ⚠️ 后台没配等级时如实显示「暂无等级」，不编造 -->
            <b>{{ levelName }}</b>
          </div>
        </div>
      </div>

      <MlModal :open="passwordOpen" title="修改登录密码" @close="closePassword">
        <p class="acct-password-hint">
          通过绑定手机号验证后，即可设置新的登录密码。
        </p>
        <div class="acct-password-form">
        <MlField label="新密码" required :error="error" class="acct-password-field">
          <input
            id="newPassword"
            aria-label="新密码"
            v-model="password"
            class="ml-input"
            type="password"
            placeholder="至少 6 位"
            autocomplete="new-password"
          />
        </MlField>
        <MlField label="确认新密码" required class="acct-password-field">
          <input
            id="newPassword2"
            aria-label="确认新密码"
            v-model="password2"
            class="ml-input"
            type="password"
            placeholder="再次输入"
            autocomplete="new-password"
          />
        </MlField>
        <!-- 改密必须带一张 scene 3 的码：后端 `code` 必填且会实际核销 -->
        <MlField label="手机验证码" required :error="codeError" class="acct-code-field">
          <div class="code-row">
            <input
              id="setPasswordCode"
              aria-label="手机验证码"
              v-model.trim="code"
              class="ml-input"
              type="text"
              inputmode="numeric"
              maxlength="6"
              placeholder="请输入验证码"
            />
            <button
              id="getPasswordCodeBtn"
              class="btn-cart code-btn"
              type="button"
              :disabled="!canGetCode"
              @click="onGetCode"
            >
              {{ countdown > 0 ? `${countdown} 秒后重发` : '获取验证码' }}
            </button>
          </div>
        </MlField>
        <p v-if="ok" class="acct-ok">{{ ok }}</p>
        </div>
        <template #foot>
        <button
          id="setPasswordBtn"
          class="btn-primary"
          type="button"
          :disabled="submitting"
          @click="onSetPassword"
        >
          {{ submitting ? '提交中…' : '保存密码' }}
        </button>
        </template>
      </MlModal>

      <div class="acct-bottom">
        <p class="acct-links">
          <RouterLink to="/agreement/user">《用户协议》</RouterLink>
          <RouterLink to="/agreement/privacy">《隐私政策》</RouterLink>
        </p>
        <button class="btn-cart" type="button" @click="onLogout">退出登录</button>
      </div>
    </div>

    <!-- 编辑资料（FR-011b）。保存成功后要重拉会员信息，顶栏才会跟着变 -->
    <ProfileEditDialog
      :open="profileOpen"
      :member="userStore.member ?? null"
      @close="profileOpen = false"
      @saved="onProfileSaved"
    />

    <!-- 发短信前的图形验证码闸门（开关由服务端决定，关着时不会被打开） -->
    <CaptchaSlider :open="captchaOpen" @close="captchaOpen = false" @success="onCaptchaPassed" />
  </div>
  </div>
</template>

<style scoped>
.account-page { background: #F5F8FF; min-height: 70vh; }
.account-page > .crumbs { max-width: 1240px; margin: 0 auto; padding: 22px 24px; }
.account-dashboard { display: grid; gap: 20px; }
.account-dashboard > .ml-card { margin: 0; padding: 28px 32px; border-radius: 14px; }
.acct-heading { display: flex; justify-content: space-between; align-items: center; gap: 20px; padding: 2px 0 4px; }
.acct-heading h1 { color: #0C2148; font-size: 26px; line-height: 1.4; letter-spacing: -.02em; }
.acct-heading p { margin-top: 8px; font-size: 13px; color: var(--ml-text-sub); line-height: 1.7; }
.acct-shop-link { color: var(--ml-primary); font-size: 13px; white-space: nowrap; }
.acct-shop-link span { margin-left: 10px; }
.account-dashboard .ml-card-title { font-size: 16px; margin-bottom: 20px; }
.account-dashboard .acct-profile-card { background: linear-gradient(110deg, #FFFFFF 35%, #F0F4FB); }
.acct-profile { display: flex; align-items: center; gap: 20px; }
.acct-avatar, .acct-avatar-ph { width: 64px; height: 64px; border-radius: 50%; flex-shrink: 0; }
.acct-avatar { object-fit: cover; border: 1px solid var(--ml-border); }
.acct-avatar-ph { display: inline-flex; align-items: center; justify-content: center; background: #EAF1FF; color: var(--ml-primary); }
.acct-avatar-ph svg { width: 32px; height: 32px; }
.acct-profile-info { flex: 1; min-width: 0; }
.acct-name { font-size: 22px; font-weight: 600; color: var(--ml-text); overflow-wrap: anywhere; }
.acct-mobile { margin-top: 6px; font-size: 13px; color: var(--ml-text-sub); }
.acct-profile-info .ml-hint { margin-top: 8px; line-height: 1.6; }
.acct-profile .btn-cart { background: #FFFFFF; white-space: nowrap; padding: 10px 18px; font-size: 13px; }
.acct-profile-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; }
.acct-password-link { border: 0; background: transparent; color: var(--ml-text-sub); padding: 10px 0; font: inherit; font-size: 13px; white-space: nowrap; cursor: pointer; }
.acct-password-link:hover { color: var(--ml-primary); }
.acct-password-link:focus-visible { outline: 2px solid var(--ml-primary); outline-offset: 3px; border-radius: 4px; }
.acct-entries { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.acct-entries .entry-card { display: flex; flex-direction: row; align-items: center; gap: 14px; padding: 24px 20px; text-align: left; border: 1px solid var(--ml-border); border-radius: 14px; background: #FFFFFF; transition: border-color .15s, box-shadow .15s; }
.acct-entries .entry-card:hover { border-color: var(--ml-primary); box-shadow: 0 6px 18px rgba(46,124,214,.07); }
.acct-entry-icon { display: flex; align-items: center; justify-content: center; flex: 0 0 40px; height: 40px; border-radius: 12px; background: #F5F8FF; color: var(--ml-primary); }
.acct-entry-icon svg { width: 23px; height: 23px; }
.acct-entries .entry-card h3 { font-size: 15px; line-height: 1.5; }
.acct-entries .entry-card p { margin-top: 6px; font-size: 11px; color: var(--ml-text-sub); line-height: 1.6; }
.acct-entry-arrow { margin-left: auto; color: #B4C0D8; font-size: 18px; }
.acct-section-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
.acct-section-head .ml-card-title { margin: 0; }
.acct-detail-link { color: var(--ml-primary); font-size: 12px; }
.acct-detail-link span { margin-left: 8px; }
.acct-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
.acct-stat { padding: 0 28px; border-left: 1px solid var(--ml-border); }
.acct-stat:first-child { padding-left: 0; border-left: 0; }
.acct-stat .k { display: block; font-size: 12px; color: var(--ml-text-sub); margin-bottom: 10px; }
.acct-stat b { display: block; font-size: 24px; line-height: 1.4; font-weight: 600; color: #0C2148; overflow-wrap: anywhere; }
.acct-stat:last-child b { font-size: 18px; line-height: 1.85; }
.acct-password-hint { margin-bottom: 24px; color: var(--ml-text-sub); line-height: 1.8; font-size: 13px; }
.acct-password-form { display: grid; grid-template-columns: 1fr; gap: 20px; align-content: start; }
.acct-password-form :deep(.ml-field) { margin: 0; min-width: 0; }
.acct-code-field { grid-column: 1 / -1; }
.code-row { display: flex; gap: 10px; }
.code-row .ml-input { flex: 1; min-width: 0; }
.code-btn { flex: 0 0 auto; white-space: nowrap; font-size: 12px; padding: 10px 14px; }
.code-btn:disabled { opacity: .5; cursor: not-allowed; }
#setPasswordBtn { padding: 11px 28px; font-size: 13px; border: 0; font-family: inherit; cursor: pointer; }
.acct-ok { color: var(--ml-primary); font-size: 13px; line-height: 1.6; grid-column: 1 / -1; }
.acct-profile-card > .acct-ok { margin-top: 16px; }
.acct-bottom { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 0 4px; }
.acct-links { display: flex; flex-wrap: wrap; gap: 16px; font-size: 12px; }
.acct-links a { color: var(--ml-text-sub); }
.acct-links a:hover { color: var(--ml-primary); }
.acct-bottom .btn-cart { font-size: 12px; background: transparent; padding: 9px 16px; }
@media (max-width: 1100px) {
  .acct-entries .entry-card { gap: 10px; padding: 20px 14px; }
  .acct-entry-arrow { display: none; }
}
@media (max-width: 768px) {
  .account-page > .crumbs { padding-inline: 16px; }
  .account-dashboard { gap: 16px; }
  .account-dashboard > .ml-card { padding: 24px; }
  .acct-heading h1 { font-size: 23px; }
  .acct-heading p { font-size: 12px; }
  .acct-entries { gap: 10px; }
  .acct-entries .entry-card { flex-direction: column; align-items: flex-start; padding: 18px 14px; gap: 12px; }
  .acct-entries .entry-card h3 { font-size: 14px; }
  .acct-entries .entry-card p { font-size: 11px; }
  .acct-stat { padding-inline: 18px; }
}
@media (max-width: 480px) {
  .account-dashboard > .ml-card { padding: 22px 20px; }
  .acct-heading { align-items: flex-start; gap: 12px; }
  .acct-shop-link { padding-top: 7px; font-size: 12px; }
  .acct-profile { flex-wrap: wrap; gap: 14px; }
  .acct-avatar, .acct-avatar-ph { width: 52px; height: 52px; }
  .acct-name { font-size: 18px; }
  .acct-profile-info { flex-basis: calc(100% - 70px); }
  .acct-profile-actions { margin-left: 66px; gap: 20px; }
  .acct-profile-actions .btn-cart { width: auto; flex: none; }
  .acct-stats { gap: 12px; }
  .acct-stat { padding-inline: 12px 0; }
  .acct-stat b { font-size: 22px; }
  .acct-stat:last-child b { font-size: 15px; }
  .acct-section-head { gap: 10px; }
  .acct-detail-link { font-size: 11px; }
  .acct-password-form { grid-template-columns: 1fr; }
  .acct-bottom { align-items: flex-start; }
  .acct-links { gap: 10px; }
}
</style>
