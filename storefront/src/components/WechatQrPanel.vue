<script setup lang="ts">
import QRCode from 'qrcode'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { createWechatScanTicket, queryWechatScanTicket } from '@/api/wechatLogin'

/**
 * 微信扫码登录面板（电脑端）。
 *
 * ## 三条照 `QrPayDialog` 搬过来的规矩（那边已经踩过一遍）
 *
 * 1. **二维码只用 SVG 输出**（`QRCode.toString(..., {type:'svg'})`）—— `toDataURL`/`toCanvas`
 *    走 `canvas.getContext('2d')`，jsdom 没实现，组件测试一进来就抛。
 * 2. **轮询用递归 `setTimeout`，不用 `setInterval`** —— 后者在响应变慢时会把请求**叠起来**，
 *    等于一直打后端。递归写法下"上一个没回来就不排下一个"，天然不叠。
 * 3. **三条停止条件一个都不能漏**：完成、过期、组件卸载。漏了不会报错，只会一直轮询。
 *
 * ## 它不决定"登录之后去哪"
 *
 * 它只把后端说的状态翻译成事件：`loggedIn`（令牌）、`needProfile`（该补全了）、`close`。
 * 上层（`LoginDialog`）决定接下来发生什么。
 */

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  close: []
  loggedIn: [accessToken: string, refreshToken: string]
  needProfile: [ticket: string, wxNickname: string | undefined, wxAvatar: string | undefined]
}>()

/** 轮询间隔：3 秒是"够快"与"别太打扰后端"之间的折中 */
const POLL_MS = 3000

const imgSrc = ref('')
const qrError = ref('')
const expired = ref(false)
const remaining = ref(0)

/** 票据号。**只留在内存里，不进 URL、不落 localStorage**（它等价于登录凭据） */
let ticket = ''

let pollTimer: ReturnType<typeof setTimeout> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null
let stopped = true
let deadline = 0
/** 是否已经就「待补全」通知过上层 —— 见 tick() 里的说明 */
let profileNotified = false

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
  remaining.value = 0
  stopAll()
}

function startCountdown(expiresIn: number) {
  deadline = Date.now() + expiresIn * 1000
  remaining.value = Math.max(0, Math.floor((deadline - Date.now()) / 1000))
  clockTimer = setInterval(() => {
    remaining.value = Math.max(0, Math.floor((deadline - Date.now()) / 1000))
    if (remaining.value <= 0) expireNow()
  }, 1000)
}

async function renderQr(text: string): Promise<string> {
  const svg = await QRCode.toString(text, { type: 'svg', margin: 1, width: 240 })
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

async function tick() {
  if (stopped) return
  try {
    const res = await queryWechatScanTicket(ticket)
    if (stopped) return // 等结果的这段时间里可能已经被停掉了
    if (res.status === '已完成') {
      stopAll()
      emit('loggedIn', res.accessToken ?? '', res.refreshToken ?? '')
      return
    }
    if (res.status === '待补全') {
      // ⚠️ **不能 stopAll()** —— 客户正在**同一台电脑**上填补全页，
      //    提交成功后要靠这个轮询去把令牌取回来。停了就永远等不到登录成功了。
      //    （「已完成」「已过期」才是终态，那两个必须停。）
      //
      // 只在**第一次**进入这个状态时通知上层：之后每一轮都还是「待补全」，
      // 每次都 emit 会让上层反复打开同一个补全页（幂等，但很脏）。
      if (!profileNotified) {
        profileNotified = true
        emit('needProfile', ticket, res.wxNickname, res.wxAvatar)
      }
    }
    if (res.status === '已过期' || res.status === '已作废') {
      expireNow()
      return
    }
    // 「待扫」→ 继续等
  } catch {
    // 单次查询失败不终止整个流程：网络抖一下不该让客户重新扫一遍。
    // 真的过期了，下一次查询会如实告诉我们的。
  }
  if (!stopped) pollTimer = setTimeout(tick, POLL_MS)
}

async function start() {
  stopAll()
  imgSrc.value = ''
  qrError.value = ''
  expired.value = false
  remaining.value = 0
  profileNotified = false

  let created
  try {
    created = await createWechatScanTicket()
  } catch {
    qrError.value = '二维码获取失败，请稍后重试'
    return
  }
  ticket = created.ticket

  try {
    imgSrc.value = await renderQr(created.qrContent)
  } catch {
    qrError.value = '二维码生成失败，请稍后重试'
    return
  }

  stopped = false
  startCountdown(created.expiresIn)
  void tick()
}

/** 失效之后重新拿一张（旧票据会被服务端按 TTL 自己清掉） */
function refresh() {
  void start()
}

// `immediate: true` —— 否则以 open=true 挂载时永远不开始（与 AddressFormDialog 那次踩的坑同源）
watch(
  () => props.open,
  (open) => {
    if (open) {
      void start()
    } else {
      stopAll()
    }
  },
  { immediate: true },
)

onBeforeUnmount(stopAll)
</script>

<template>
  <div class="wx-qr-panel">
    <template v-if="!expired">
      <div class="wx-qr-box">
        <img
          v-if="imgSrc"
          id="wxQrImage"
          :src="imgSrc"
          alt="微信登录二维码"
        >
        <div
          v-else
          class="wx-qr-ph"
        >
          二维码准备中…
        </div>
      </div>
      <p class="wx-qr-tip">
        请用微信「扫一扫」，并在手机上点确认
      </p>
      <p
        v-if="countdownText"
        class="wx-qr-count"
      >
        二维码 {{ countdownText }} 后失效
      </p>
      <p
        v-if="qrError"
        class="wx-qr-err"
      >
        {{ qrError }}
      </p>
    </template>

    <template v-else>
      <p class="wx-qr-expired">
        二维码已失效
      </p>
      <button
        id="wxQrRefresh"
        class="btn-cart wx-qr-refresh"
        type="button"
        @click="refresh"
      >
        重新获取
      </button>
    </template>
  </div>
</template>

<style scoped>
.wx-qr-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 8px 0 4px;
}
.wx-qr-box {
  width: 200px;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ml-white);
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-field);
}
.wx-qr-box img {
  width: 184px;
  height: 184px;
}
.wx-qr-ph {
  color: var(--ml-text-ph);
  font-size: 13px;
}
.wx-qr-tip {
  margin: 0;
  font-size: 13px;
  color: var(--ml-text-sub);
}
.wx-qr-count {
  margin: 0;
  font-size: 12px;
  color: var(--ml-text-weak);
}
.wx-qr-err,
.wx-qr-expired {
  margin: 0;
  font-size: 13px;
  color: var(--ml-orange);
}
.wx-qr-refresh {
  padding: 10px 22px;
  font-size: 14px;
}
</style>
