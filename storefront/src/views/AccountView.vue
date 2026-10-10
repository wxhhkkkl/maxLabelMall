<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { isCaptchaEnabled } from '@/api/captcha'
import { sendSmsCode, SMS_SCENE_UPDATE_PASSWORD, updatePassword } from '@/api/member'
import AccountSidebar from '@/components/AccountSidebar.vue'
import ProfileEditDialog from '@/components/ProfileEditDialog.vue'
import CaptchaSlider from '@/components/CaptchaSlider.vue'
import MlField from '@/components/base/MlField.vue'
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
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink><span>/ 个人中心</span>
  </div>

  <div class="account-wrap">
    <AccountSidebar />

    <div class="account-main">
      <div class="ml-card">
        <div class="ml-card-title">账号信息</div>
        <div class="acct-profile">
          <img
            v-if="userStore.member?.avatar"
            class="acct-avatar"
            :src="userStore.member.avatar"
            :alt="userStore.displayName || '头像'"
          />
          <span v-else class="acct-avatar-ph">未设置</span>
          <div class="acct-profile-info">
            <p class="acct-name">
              {{ userStore.displayName || '未登录' }}
            </p>
            <p class="acct-mobile">{{ userStore.member?.mobile }}</p>
            <p v-if="!userStore.member?.nickname" class="ml-hint">
              尚未设置昵称，当前展示脱敏手机号
            </p>
          </div>
          <button id="editProfile" class="btn-cart" type="button" @click="profileOpen = true">
            编辑资料
          </button>
        </div>
        <p v-if="profileOk" class="acct-ok">{{ profileOk }}</p>
      </div>

      <!-- 快捷入口三卡（design-new-pages.md §3.5）—— 订单 / 地址 / 券 -->
      <div class="acct-entries">
        <RouterLink class="entry-card" to="/order">
          <h3>我的订单</h3>
          <p>查看订单状态与物流</p>
        </RouterLink>
        <RouterLink class="entry-card" to="/account/address">
          <h3>收货地址</h3>
          <p>管理收货信息</p>
        </RouterLink>
        <RouterLink class="entry-card" to="/coupon/mine">
          <h3>我的券</h3>
          <p>未使用 / 已使用 / 已过期</p>
        </RouterLink>
      </div>

      <div class="ml-card">
        <div class="ml-card-title">积分与等级</div>
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
        <RouterLink class="acct-detail-link" to="/account/points">查看积分明细</RouterLink>
      </div>

      <div class="ml-card">
        <div class="ml-card-title">设置登录密码</div>
        <p class="ml-hint">
          注册是通过验证码登录完成的，初始没有密码。设置之后即可用手机号 + 密码登录。
        </p>
        <MlField label="新密码" required :error="error">
          <input
            id="newPassword"
            v-model="password"
            class="ml-input"
            type="password"
            placeholder="至少 6 位"
            autocomplete="new-password"
          />
        </MlField>
        <MlField label="确认新密码" required>
          <input
            id="newPassword2"
            v-model="password2"
            class="ml-input"
            type="password"
            placeholder="再次输入"
            autocomplete="new-password"
          />
        </MlField>
        <!-- 改密必须带一张 scene 3 的码：后端 `code` 必填且会实际核销 -->
        <MlField label="手机验证码" required :error="codeError">
          <div class="code-row">
            <input
              id="setPasswordCode"
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
        <button
          id="setPasswordBtn"
          class="btn-primary"
          type="button"
          :disabled="submitting"
          @click="onSetPassword"
        >
          {{ submitting ? '提交中…' : '保存密码' }}
        </button>
      </div>

      <div class="ml-card">
        <div class="ml-card-title">其他</div>
        <p class="acct-links">
          <RouterLink to="/agreement/user">《用户协议》</RouterLink>
          · <RouterLink to="/agreement/privacy">《隐私政策》</RouterLink>
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
</template>

<style scoped>
.acct-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--ml-text);
  margin-bottom: 6px;
}
/* 快捷入口三卡。`.entry-card` 的版式（含悬停阴影）来自 design.css，这里只排布 */
.acct-entries {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.acct-entries .entry-card {
  display: block;
  text-align: center;
}
/* 输入框 + 「获取验证码」并排。与 LoginDialog 的 `.code-row` 同形 ——
   边距与按钮尺寸跟随 design.css / store.css 的令牌，不另立一套视觉 */
.code-row {
  display: flex;
  gap: 10px;
}
.code-row .ml-input {
  flex: 1;
}
.code-btn {
  flex: 0 0 auto;
  white-space: nowrap;
}
.code-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.acct-ok {
  color: var(--ml-primary);
  font-size: 13px;
  margin-bottom: 12px;
}
.acct-links {
  margin-bottom: 14px;
  font-size: 14px;
}
.acct-links a {
  color: var(--ml-primary);
}
/* 账号信息：头像 + 资料 + 「编辑资料」 */
.acct-profile {
  display: flex;
  align-items: center;
  gap: 14px;
}
.acct-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--ml-border);
}
.acct-avatar-ph {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 1px dashed var(--ml-border);
  color: var(--ml-text-ph);
  font-size: 12px;
}
.acct-profile-info {
  flex: 1;
  min-width: 0;
}
.acct-mobile {
  margin-top: 2px;
  font-size: 13px;
  color: var(--ml-text-sub);
}
/* 积分与等级 */
.acct-stats {
  display: flex;
  gap: 28px;
}
.acct-stat .k {
  display: block;
  font-size: 12px;
  color: var(--ml-text-ph);
}
.acct-stat b {
  font-size: 18px;
}
.acct-detail-link {
  display: inline-block;
  margin-top: 12px;
  font-size: 13px;
  color: var(--ml-primary);
}
@media (max-width: 768px) {
  .acct-entries {
    grid-template-columns: 1fr;
  }
}
</style>
