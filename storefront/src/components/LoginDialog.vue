<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { isCaptchaEnabled } from '@/api/captcha'
import {
  login as apiLogin,
  resetPassword,
  sendSmsCode,
  smsLogin,
  SMS_SCENE_MEMBER_LOGIN,
  SMS_SCENE_RESET_PASSWORD,
} from '@/api/member'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'

import CaptchaSlider from './CaptchaSlider.vue'
import MlCheck from './base/MlCheck.vue'
import MlField from './base/MlField.vue'
import MlModal from './base/MlModal.vue'

/**
 * 登录弹层 —— 本项目**唯一的登录入口**（不设 `/login` 路由）。
 *
 * 四条关键约束：
 *
 * 1. **未勾选协议时，既不能发验证码也不能登录**（FR-050 / SC-015）。同意必须发生在
 *    手机号被收集**之前**，所以门禁卡在「获取验证码」这一步，而不只是提交时校验。
 *
 * 2. **切换登录方式会重置同意状态**。否则"在验证码页勾一下、再切到密码页"就绕过了门禁。
 *
 * 3. **注册是隐式的**：验证码登录时若手机号不存在，后端自动建号（`createUserIfAbsent`）。
 *    因此界面上没有注册表单，也不该出现"账号已存在"这类提示。
 *
 * 4. **密码登录的错误提示不得暴露账号是否存在**（FR-012）：后端会区分"账号不存在"
 *    与"密码错误"，但前端必须把它们收敛成**同一条**通用提示（未设密码除外 —— 那条
 *    要引导改用验证码，且它本身不泄露"是否注册"之外的信息）。
 *
 * 5. **协议门禁同样管着「忘记密码」**。它也收集手机号、也发短信，所以 `agreed`
 *    这一道闸门对它一视同仁；切到/切回重置模式同样会清掉同意状态。
 */

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; success: [] }>()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()

type Method = 'sms' | 'password' | 'reset'

const method = ref<Method>('sms')
const mobile = ref('')
const code = ref('')
const password = ref('')
const agreed = ref(false)
const error = ref('')
/** 重置成功后的提示（不是错误，单独放一个，免得被错误样式吃掉） */
const okHint = ref('')
const submitting = ref(false)
const countdown = ref(0)
/** 滑块弹层开关（发短信前的那道闸门） */
const captchaOpen = ref(false)

const MOBILE_RE = /^1[3-9]\d{9}$/
const mobileValid = computed(() => MOBILE_RE.test(mobile.value))

/**
 * 发短信用的场景码。**重置密码(4)与登录(1)不是同一张码**，服务端按场景核销，
 * 拿错场景的码会在提交时被判「验证码不正确」。
 */
const smsScene = computed(() =>
  method.value === 'reset' ? SMS_SCENE_RESET_PASSWORD : SMS_SCENE_MEMBER_LOGIN,
)

/** 主按钮的文案（重置模式下不是「登录」） */
const submitText = computed(() => (method.value === 'reset' ? '重置密码' : '登录'))

/** 门禁：未勾选协议 → 一切动作不可执行 */
const canGetCode = computed(() => agreed.value && mobileValid.value && countdown.value === 0)
const canSubmit = computed(() => {
  if (!agreed.value || !mobileValid.value || submitting.value) return false
  if (method.value === 'sms') return code.value.length >= 4
  // 重置密码要多填一个新密码
  if (method.value === 'reset') return code.value.length >= 4 && password.value.length > 0
  return password.value.length > 0
})

function setAgreed(v: boolean) {
  agreed.value = v
  userStore.setAgreed(v)
  if (!v) error.value = ''
}

/** 切换登录方式时重置同意，避免"勾一次走两条路"绕过门禁 */
function switchMethod(m: Method) {
  method.value = m
  error.value = ''
  okHint.value = ''
  agreed.value = false
  userStore.setAgreed(false)
}

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
watch(
  () => props.open,
  (open) => {
    if (!open && timer) {
      clearInterval(timer)
      timer = null
      countdown.value = 0
    }
  },
)

/** 把后端的错误收敛成用户能看懂、且不泄露信息的文案 */
function messageOf(e: unknown, fallback: string): string {
  const msg = (e as { message?: string } | null)?.message ?? ''
  return msg || fallback
}

/**
 * 点「获取验证码」。
 *
 * 发短信是不可逆的花钱动作，所以先过一道图形验证码（服务端在 `send-sms-code`
 * 里同样会校验 —— 前端这道只是为了让用户能拿到凭据）。
 * **开关由服务端说了算**：关了就直接发，不弹滑块，本地开发与 e2e 因此不受影响。
 */
async function onGetCode() {
  if (!canGetCode.value) return
  error.value = ''
  okHint.value = ''
  if (await isCaptchaEnabled()) {
    captchaOpen.value = true
    return
  }
  await doSendCode()
}

/** 真正发短信。`captchaVerification` 是滑块通过后拿到的凭据 */
async function doSendCode(captchaVerification?: string) {
  try {
    // 没凭据时**不传第三个参数**（而不是传 undefined）—— 与 `@/api/member.sendSmsCode`
    // 的 body 处理一致，也让「开关关闭时的调用与从前一字不差」这条断言站得住
    if (captchaVerification) {
      await sendSmsCode(mobile.value, smsScene.value, captchaVerification)
    } else {
      await sendSmsCode(mobile.value, smsScene.value)
    }
    startCountdown()
  } catch (e) {
    // 频率限制要告知还需等待多久，而不是笼统报错
    const raw = messageOf(e, '验证码发送失败，请稍后重试')
    error.value = /频繁|频率|过于/.test(raw) ? raw : `验证码发送失败：${raw}`
  }
}

/** 滑块通过 → 关掉弹层并把凭据带上，继续刚才那次发码 */
async function onCaptchaPassed(verification: string) {
  captchaOpen.value = false
  await doSendCode(verification)
}

async function onSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  error.value = ''
  okHint.value = ''
  try {
    if (method.value === 'reset') {
      await resetPassword(mobile.value, code.value, password.value)
      // 重置接口不发令牌，所以不自动登录：回到密码登录页，让用户用新密码登一次
      switchMethod('password')
      okHint.value = '密码已重置，请用新密码登录'
      return
    }
    if (method.value === 'sms') {
      await smsLogin(mobile.value, code.value)
    } else {
      await apiLogin(mobile.value, password.value)
    }
    await userStore.syncAfterLogin()
    // 登录前被中断的加购在这里补上 —— 不要求用户重新点一次（FR-015）
    await cartStore.flushPendingIntent()
    emit('success')
    redirectBack()
  } catch (e) {
    const raw = messageOf(e, '')
    if (method.value === 'reset') {
      // 重置场景的错误是可操作的（「手机号未注册用户」「验证码不正确」），直接透出
      error.value = raw || '重置失败，请稍后重试'
    } else if (method.value === 'password') {
      // 「未设密码」要引导改用验证码 —— 这是可操作的提示
      error.value = /未设置|未设密码|没有密码/.test(raw)
        ? '该账号尚未设置密码，请改用验证码登录'
        : // 其余一律收敛为同一条通用提示，不区分"账号不存在"与"密码错误"（FR-012）
          '手机号或密码不正确'
    } else {
      error.value = raw || '验证码校验失败，请重新获取'
    }
  } finally {
    submitting.value = false
  }
}

/** 登录后回到登录前的页面继续操作（FR-010 / FR-015） */
function redirectBack() {
  const redirect = route.query.redirect
  const target = typeof redirect === 'string' ? redirect : ''
  if (target) router.push(target)
  else if (route.query.login) router.replace({ query: {} })
}
</script>

<template>
  <MlModal :open="open" title="登录赋签" @close="emit('close')">
    <!-- 两种登录方式。**没有注册表单** —— 注册由验证码登录隐式完成 -->
    <div class="switch-tabs">
      <span
        class="switch-tab"
        :class="{ active: method === 'sms' }"
        @click="switchMethod('sms')"
      >
        验证码登录
      </span>
      <span
        class="switch-tab"
        :class="{ active: method === 'password' }"
        @click="switchMethod('password')"
      >
        密码登录
      </span>
    </div>

    <MlField label="手机号" required>
      <input
        id="loginMobile"
        v-model.trim="mobile"
        class="ml-input"
        type="tel"
        maxlength="11"
        placeholder="请输入 11 位手机号"
        autocomplete="tel"
      />
    </MlField>

    <MlField v-if="method === 'sms'" label="短信验证码" required>
      <div class="code-row">
        <input
          id="loginCode"
          v-model.trim="code"
          class="ml-input"
          type="text"
          inputmode="numeric"
          maxlength="6"
          placeholder="请输入验证码"
        />
        <button
          id="getCodeBtn"
          class="btn-cart code-btn"
          type="button"
          :disabled="!canGetCode"
          @click="onGetCode"
        >
          {{ countdown > 0 ? `${countdown} 秒后重发` : '获取验证码' }}
        </button>
      </div>
    </MlField>

    <template v-else-if="method === 'password'">
      <MlField label="密码" required>
        <input
          id="loginPassword"
          v-model="password"
          class="ml-input"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
        />
      </MlField>
      <p class="link-row">
        <a class="link-btn" href="#" @click.prevent="switchMethod('reset')">忘记密码？</a>
      </p>
    </template>

    <!-- 忘记密码：同一张弹层里的第三种形态（不新增路由） -->
    <template v-else>
      <MlField label="短信验证码" required>
        <div class="code-row">
          <input
            id="resetCode"
            v-model.trim="code"
            class="ml-input"
            type="text"
            inputmode="numeric"
            maxlength="6"
            placeholder="请输入验证码"
          />
          <button
            id="resetCodeBtn"
            class="btn-cart code-btn"
            type="button"
            :disabled="!canGetCode"
            @click="onGetCode"
          >
            {{ countdown > 0 ? `${countdown} 秒后重发` : '获取验证码' }}
          </button>
        </div>
      </MlField>
      <MlField label="新密码" required>
        <input
          id="resetPassword"
          v-model="password"
          class="ml-input"
          type="password"
          placeholder="请设置新密码"
          autocomplete="new-password"
        />
      </MlField>
      <p class="link-row">
        <a class="link-btn" href="#" @click.prevent="switchMethod('password')">返回登录</a>
      </p>
    </template>

    <p v-if="error" class="login-error">{{ error }}</p>
    <p v-if="okHint" class="login-ok">{{ okHint }}</p>

    <!-- 协议门禁：不勾选则上面的发码与登录都不可执行 -->
    <MlCheck :model-value="agreed" @update:model-value="setAgreed">
      我已阅读并同意
      <span class="agree-links">
        <RouterLink to="/agreement/user">《用户协议》</RouterLink>
        与
        <RouterLink to="/agreement/privacy">《隐私政策》</RouterLink>
      </span>
    </MlCheck>

    <template #foot>
      <button
        id="loginSubmit"
        class="btn-primary login-submit"
        type="button"
        :disabled="!canSubmit"
        @click="onSubmit"
      >
        {{ submitting ? (method === 'reset' ? '重置中…' : '登录中…') : submitText }}
      </button>
    </template>

    <p class="login-tip">
      {{ method === 'reset' ? '重置后可用新密码登录；重置需要该手机号已注册' : '未注册的手机号验证后将自动创建账号' }}
    </p>
  </MlModal>

  <!-- 发短信前的图形验证码闸门。开关由服务端决定，关着时它根本不会被打开 -->
  <CaptchaSlider :open="captchaOpen" @close="captchaOpen = false" @success="onCaptchaPassed" />
</template>

<style scoped>
.switch-tabs {
  display: flex;
  gap: 20px;
  margin-bottom: 18px;
}
.switch-tab {
  font-size: 15px;
  color: var(--ml-text-sub);
  cursor: pointer;
  padding-bottom: 6px;
}
.switch-tab.active {
  color: var(--ml-primary);
  font-weight: 600;
  border-bottom: 2px solid var(--ml-primary);
}
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
.code-btn:disabled,
.login-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.login-error {
  color: var(--ml-orange);
  font-size: 13px;
  margin: 4px 0 10px;
}
/* 重置成功的提示 —— 与错误分开，别让用户以为又出错了 */
.login-ok {
  color: var(--ml-primary);
  font-size: 13px;
  margin: 4px 0 10px;
}
/* 「忘记密码？」/「返回登录」——右侧对齐的文字按钮 */
.link-row {
  margin: -6px 0 10px;
  text-align: right;
}
.link-btn {
  color: var(--ml-primary);
  font-size: 13px;
}
.agree-links a {
  color: var(--ml-primary);
}
.login-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--ml-text-ph);
  text-align: center;
}
</style>
