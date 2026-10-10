<script setup lang="ts">
import QRCode from 'qrcode'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { getPayOrder } from '@/api/pay'
import { PayOrderStatus } from '@/types'

import MlModal from './base/MlModal.vue'

/**
 * 电脑端微信 Native 扫码支付的二维码弹层。
 *
 * 后端 `WxNativePayClient` 返回 `displayMode = 'qr_code'`、`displayContent` 是**裸的
 * `code_url`**（`weixin://wxpay/bizpayurl?pr=xxx`），这里把它渲染成二维码并盯着支付单。
 *
 * ## 三条不能想当然的规则
 *
 * 1. **二维码只用 SVG 输出，不用 `toDataURL`/`toCanvas`** —— 后两者走
 *    `canvas.getContext('2d')`，jsdom 没实现，组件测试一进来就抛。
 * 2. **轮询用递归 `setTimeout`，不用 `setInterval`** —— `sync=true` 每次都会真的
 *    去打一次微信；`setInterval` 在响应变慢时会把请求叠起来，等于骚扰外部系统。
 * 3. **本组件不判断订单是否已支付** —— 它只把后端说的状态翻译成事件。页面最终显示
 *    什么，由 OrderDetailView 重新拉一次详情决定（FR-039）。
 */
const props = defineProps<{
  open: boolean
  /** 后端 `displayContent`，即微信的 `code_url` 原文 */
  codeUrl: string
  /** 支付单编号（不是交易订单号） */
  payOrderId: number
  /** 支付截止时间（epoch 毫秒），取自订单详情的 `payExpireTime` */
  expireAt?: number
  orderNo?: string
}>()

const emit = defineEmits<{ close: []; paid: []; expired: [] }>()

/** 轮询间隔。`sync=true` 会实打实查一次微信，3 秒是「够快」与「别太打扰」之间的折中。 */
const POLL_MS = 3000

const imgSrc = ref('')
const qrError = ref('')
const expired = ref(false)
const retryHint = ref(false)
const remaining = ref(0)

let pollTimer: ReturnType<typeof setTimeout> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null
let stopped = true

const countdownText = computed(() => {
  if (remaining.value <= 0) return ''
  const m = Math.floor(remaining.value / 60)
  const s = remaining.value % 60
  return `${m}:${String(s).padStart(2, '0')}`
})

function stopAll() {
  stopped = true
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
}

function expireNow() {
  if (expired.value) return
  expired.value = true
  emit('expired')
  stopAll()
}

function startCountdown() {
  const deadline = props.expireAt
  if (typeof deadline !== 'number') return
  remaining.value = Math.max(0, Math.floor((deadline - Date.now()) / 1000))
  clockTimer = setInterval(() => {
    remaining.value = Math.max(0, Math.floor((deadline - Date.now()) / 1000))
    if (remaining.value <= 0) expireNow()
  }, 1000)
}

async function tick() {
  if (stopped) return
  try {
    const order = await getPayOrder(props.payOrderId, true)
    if (stopped) return
    retryHint.value = false
    if (order.status === PayOrderStatus.SUCCESS) {
      emit('paid')
      stopAll()
      return
    }
    // WAITING 之外全是终态（CLOSED 涵盖取消/过期/渠道报错）。具体是什么，让页面重拉详情去说。
    if (order.status !== PayOrderStatus.WAITING) {
      expireNow()
      return
    }
  } catch {
    // 一次查询失败不该掐死整条支付 —— 提示一下，下个周期接着查
    if (stopped) return
    retryHint.value = true
  }
  if (!stopped) pollTimer = setTimeout(tick, POLL_MS)
}

async function renderQr(text: string): Promise<string> {
  const svg = await QRCode.toString(text, { type: 'svg', margin: 1, width: 240 })
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

async function start() {
  stopAll()
  imgSrc.value = ''
  qrError.value = ''
  expired.value = false
  retryHint.value = false
  remaining.value = 0

  // 空内容要明说，绝不渲染一个扫不出东西的空白二维码
  if (!props.codeUrl) {
    qrError.value = '二维码获取失败，请关闭后换一个渠道重试'
    return
  }

  // 有效期已过就别开张了 —— 扫了也付不成
  if (typeof props.expireAt === 'number' && props.expireAt <= Date.now()) {
    expired.value = true
    emit('expired')
    return
  }

  try {
    imgSrc.value = await renderQr(props.codeUrl)
  } catch {
    qrError.value = '二维码生成失败，请关闭后重试'
    return
  }

  stopped = false
  startCountdown()
  void tick()
}

watch(
  () => props.open,
  (open) => {
    if (!open) {
      stopAll()
      return
    }
    void start()
  },
  { immediate: true },
)

onBeforeUnmount(stopAll)
</script>

<template>
  <MlModal :open="open" title="微信扫码支付" :close-on-mask="false" @close="emit('close')">
    <p v-if="qrError" class="ml-error qr-invalid">{{ qrError }}</p>

    <p v-else-if="expired" class="ml-error qr-expired">
      二维码已失效，请关闭后重新支付
    </p>

    <template v-else>
      <img v-if="imgSrc" class="qr-img" :src="imgSrc" alt="微信支付二维码" />
      <p class="qr-hint">请使用微信「扫一扫」完成支付</p>
      <p v-if="orderNo" class="qr-no">订单号 {{ orderNo }}</p>
      <p v-if="countdownText" class="qr-countdown">支付剩余时间 {{ countdownText }}</p>
      <p v-if="retryHint" class="qr-retry">支付结果确认中，请稍候…</p>
    </template>

    <template #foot>
      <button class="btn-cart" type="button" @click="emit('close')">关闭</button>
    </template>
  </MlModal>
</template>

<style scoped>
.qr-img {
  display: block;
  width: 200px;
  height: 200px;
  margin: 0 auto;
  background: var(--ml-white);
}
.qr-hint {
  margin: 12px 0 0;
  text-align: center;
  font-size: 14px;
  color: var(--ml-text);
}
.qr-no,
.qr-countdown,
.qr-retry {
  margin: 6px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--ml-text-sub);
}
</style>
