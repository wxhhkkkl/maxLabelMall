<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { login as apiLogin, smsLogin, sendSmsCode, SMS_SCENE_MEMBER_LOGIN } from '@/api/member'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'

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
 */

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; success: [] }>()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()

type Method = 'sms' | 'password'

const method = ref<Method>('sms')
const mobile = ref('')
const code = ref('')
const password = ref('')
const agreed = ref(false)
const error = ref('')
const submitting = ref(false)
const countdown = ref(0)

const MOBILE_RE = /^1[3-9]\d{9}$/
const mobileValid = computed(() => MOBILE_RE.test(mobile.value))

/** 门禁：未勾选协议 → 一切动作不可执行 */
const canGetCode = computed(() => agreed.value && mobileValid.value && countdown.value === 0)
const canSubmit = computed(() => {
  if (!agreed.value || !mobileValid.value || submitting.value) return false
  return method.value === 'sms' ? code.value.length >= 4 : password.value.length > 0
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

async function onGetCode() {
  if (!canGetCode.value) return
  error.value = ''
  try {
    await sendSmsCode(mobile.value, SMS_SCENE_MEMBER_LOGIN)
    startCountdown()
  } catch (e) {
    // 频率限制要告知还需等待多久，而不是笼统报错
    const raw = messageOf(e, '验证码发送失败，请稍后重试')
    error.value = /频繁|频率|过于/.test(raw) ? raw : `验证码发送失败：${raw}`
  }
}

async function onSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  error.value = ''
  try {
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
    if (method.value === 'password') {
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

    <MlField v-else label="密码" required>
      <input
        id="loginPassword"
        v-model="password"
        class="ml-input"
        type="password"
        placeholder="请输入密码"
        autocomplete="current-password"
      />
    </MlField>

    <p v-if="error" class="login-error">{{ error }}</p>

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
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </template>

    <p class="login-tip">未注册的手机号验证后将自动创建账号</p>
  </MlModal>
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
