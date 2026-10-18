<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { isCaptchaEnabled } from '@/api/captcha'
import { sendSmsCode, SMS_SCENE_MEMBER_LOGIN } from '@/api/member'
import { abandonWechatScanTicket, completeWechatScan } from '@/api/wechatLogin'

import CaptchaSlider from './CaptchaSlider.vue'
import MlField from './base/MlField.vue'
import MlModal from './base/MlModal.vue'

/**
 * 微信扫码登录的**补全页**（US17）。
 *
 * 它不是"注册表单"，而是**建号那一步**：客户在电脑上填完并提交成功，
 * 后端才建号（或绑到既有会员）、才签发令牌 —— 电脑端的二维码面板会轮询到「已完成」。
 *
 * ## 三条不能想当然的规矩
 *
 * 1. **手机号必填**，且**一律**过短信验证码（FR-147）—— 手打的号不等于客户拥有它。
 *    这里复用**登录场景（1）**的发码接口：那个模板是现成的，不用让运营再申请一张。
 * 2. **不在这里上传头像**。客户此刻**还没登录**（令牌要等电脑端轮询才拿到），
 *    而上传接口要求登录。所以自选头像只把 `File` 交给上层，由它在登录成功后传（FR-151）。
 * 3. **关掉 = 这次扫码彻底作废**（FR-148）。作废是幂等的，就算请求失败也不拦着客户走。
 * 4. **发码前要过图形验证码闸门**。服务端 `sendSmsCode` 的第一步就是它（线上开关是开的），
 *    不过这一道客户只会看到「图形验证码校验未通过」——开关同样由服务端给
 *    （`isCaptchaEnabled`），关闭时前端不弹滑块。
 */
const props = defineProps<{
  open: boolean
  ticket: string
  wxNickname?: string
  wxAvatar?: string
}>()
const emit = defineEmits<{ close: []; completed: [avatarFile: File | null] }>()

const MOBILE_RE = /^1[3-9]\d{9}$/

const mobile = ref('')
const code = ref('')
const company = ref('')
const avatarFile = ref<File | null>(null)
/** 自选头像的本地预览；没选时回落到微信头像 */
const avatarPreview = ref('')
const error = ref('')
const submitting = ref(false)
const sending = ref(false)
const countdown = ref(0)
/** 滑块弹层开关（发短信前的那道闸门） */
const captchaOpen = ref(false)

let timer: ReturnType<typeof setInterval> | null = null

const mobileValid = computed(() => MOBILE_RE.test(mobile.value))
const canSubmit = computed(() => mobileValid.value && code.value.length >= 4 && !submitting.value)
const shownAvatar = computed(() => avatarPreview.value || props.wxAvatar || '')

function stopCountdown() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) stopCountdown()
  }, 1000)
}

async function onGetCode() {
  if (!mobileValid.value || countdown.value > 0 || sending.value) return
  error.value = ''
  if (await isCaptchaEnabled()) {
    captchaOpen.value = true
    return
  }
  await doSendCode()
}

/** 真正发短信。`captchaVerification` 是滑块通过后拿到的凭据 */
async function doSendCode(captchaVerification?: string) {
  sending.value = true
  try {
    // 没凭据时**不传第三个参数**（而不是传 undefined）—— 与 `@/api/member.sendSmsCode`
    // 的 body 处理一致
    if (captchaVerification) {
      await sendSmsCode(mobile.value, SMS_SCENE_MEMBER_LOGIN, captchaVerification)
    } else {
      await sendSmsCode(mobile.value, SMS_SCENE_MEMBER_LOGIN)
    }
    startCountdown()
  } catch (e) {
    error.value = (e as { message?: string })?.message || '验证码发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

/** 滑块通过 → 关掉弹层并把凭据带上，继续刚才那次发码 */
async function onCaptchaPassed(verification: string) {
  captchaOpen.value = false
  await doSendCode(verification)
}

function onPickAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  avatarFile.value = file
  // jsdom 没有 createObjectURL：拿不到预览就退回微信头像，不该因此炸掉
  avatarPreview.value = file && typeof URL.createObjectURL === 'function'
    ? URL.createObjectURL(file)
    : ''
}

async function onSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  error.value = ''
  try {
    await completeWechatScan(props.ticket, mobile.value, code.value, company.value.trim() || undefined)
    // ⚠️ 这里**不上传头像** —— 客户此刻还没登录，令牌要等电脑端轮询才拿到。
    //    自选头像交给上层，登录成功之后再由它上传（FR-151）
    emit('completed', avatarFile.value)
  } catch (e) {
    error.value = (e as { message?: string })?.message || '补全失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function onClose() {
  // 关掉 = 这次扫码彻底作废（FR-148）。**作废失败也不拦着客户走** ——
  // 票据本来就有 TTL，最坏情况也只是它自己过期
  try {
    await abandonWechatScanTicket(props.ticket)
  } catch {
    /* 忽略：客户的"关闭"意愿优先 */
  }
  emit('close')
}

watch(
  () => props.open,
  (open) => {
    if (!open) {
      stopCountdown()
      // 补全页一关，滑块不该还浮在上面
      captchaOpen.value = false
      return
    }
    mobile.value = ''
    code.value = ''
    company.value = ''
    avatarFile.value = null
    avatarPreview.value = ''
    error.value = ''
    stopCountdown()
  },
)
</script>

<template>
  <MlModal
    :open="open"
    title="完善资料"
    @close="onClose"
  >
    <p class="pc-lead">
      首次微信登录需要补全手机号，之后就不用再填了。
    </p>
    <p
      v-if="wxNickname"
      class="pc-wx"
    >
      微信昵称：{{ wxNickname }}
    </p>

    <MlField
      label="手机号"
      required
    >
      <div class="pc-code-row">
        <input
          id="pcMobile"
          v-model.trim="mobile"
          class="ml-input"
          type="tel"
          maxlength="11"
          placeholder="请输入 11 位手机号"
        >
        <button
          id="pcGetCode"
          class="btn-cart pc-code-btn"
          type="button"
          :disabled="!mobileValid || countdown > 0 || sending"
          @click="onGetCode"
        >
          {{ countdown > 0 ? `${countdown} 秒后重发` : '获取验证码' }}
        </button>
      </div>
    </MlField>

    <MlField
      label="短信验证码"
      required
    >
      <input
        id="pcCode"
        v-model.trim="code"
        class="ml-input"
        type="text"
        inputmode="numeric"
        maxlength="6"
        placeholder="请输入验证码"
      >
    </MlField>

    <MlField
      label="公司名称"
      hint="选填"
    >
      <input
        id="pcCompany"
        v-model.trim="company"
        class="ml-input"
        maxlength="64"
        placeholder="选填，方便我们认出您"
      >
    </MlField>

    <MlField
      label="头像"
      hint="选填，默认用微信头像；也可以先做着，登录后再到个人资料里换"
    >
      <div class="pc-avatar-row">
        <img
          v-if="shownAvatar"
          id="pcAvatarPreview"
          :src="shownAvatar"
          alt="头像预览"
          class="pc-avatar"
        >
        <div
          v-else
          class="pc-avatar pc-avatar-ph"
        >
          暂无
        </div>
        <label
          class="btn-cart pc-avatar-pick"
          for="pcAvatarInput"
        >选择图片</label>
        <input
          id="pcAvatarInput"
          class="pc-avatar-input"
          type="file"
          accept="image/*"
          @change="onPickAvatar"
        >
      </div>
    </MlField>

    <p
      v-if="error"
      class="ml-error"
    >
      {{ error }}
    </p>

    <template #foot>
      <div class="pc-foot">
        <button
          id="pcClose"
          class="btn-cart pc-close"
          type="button"
          @click="onClose"
        >
          暂不登录
        </button>
        <button
          id="pcSubmit"
          class="btn-primary pc-submit"
          type="button"
          :disabled="!canSubmit"
          @click="onSubmit"
        >
          {{ submitting ? '提交中…' : '完成' }}
        </button>
      </div>
    </template>
  </MlModal>

  <!-- 发短信前的图形验证码闸门。开关由服务端决定，关着时它根本不会被打开 -->
  <CaptchaSlider
    :open="captchaOpen"
    @close="captchaOpen = false"
    @success="onCaptchaPassed"
  />
</template>

<style scoped>
.pc-lead {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--ml-text-sub);
}
.pc-wx {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--ml-text-weak);
}
.pc-code-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pc-code-row .ml-input {
  flex: 1;
}
.pc-code-btn {
  flex: 0 0 auto;
  white-space: nowrap;
  padding: 12px 18px;
  font-size: 14px;
}
.pc-code-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pc-avatar-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pc-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--ml-border);
}
.pc-avatar-ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--ml-text-ph);
}
/* 文件选择框藏起来，用 label 当按钮 —— 原生 input 在两端都很难看 */
.pc-avatar-input {
  display: none;
}
.pc-avatar-pick {
  padding: 10px 18px;
  font-size: 13px;
  cursor: pointer;
}
.pc-foot {
  display: flex;
  gap: 12px;
}
.pc-close {
  flex: 0 0 auto;
  padding: 14px 22px;
}
.pc-submit {
  flex: 1;
}
.pc-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
