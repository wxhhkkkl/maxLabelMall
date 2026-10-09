<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import { checkCaptcha, getCaptcha, type CaptchaData } from '@/api/captcha'
import {
  ORIGINAL_WIDTH,
  buildCaptchaVerification,
  encryptSlidePoint,
  slideOffset,
} from '@/utils/captcha'

import MlModal from './base/MlModal.vue'

/**
 * 滑块图形验证码 —— 发短信前的闸门。
 *
 * 协议与上游管理端的 `Verifition/VerifySlide.vue` 一致（服务端用的是同一套
 * aj-captcha），这里只保留本项目需要的那部分：
 *
 *   取图 → 拖动 → 把**作答的密文**交给 `/system/captcha/check` →
 *   通过后算出 `captchaVerification` 交给调用方 → 调用方带它去发短信。
 *
 * 三条要点：
 *
 * 1. **作答要换算回原图坐标**。底图原宽 310，界面里是缩放渲染的，服务端比的是
 *    原图里的缺口位置 —— 直接报渲染像素会「看着对准了、后端说不对」。
 * 2. **两种密文都由本组件算**（`@/utils/captcha`）：`check` 用的是 `{x,y}` 的密文，
 *    交给业务接口的 `captchaVerification` 是 `token---{x,y}` 的密文。服务端在
 *    `check` 通过时就把后者算好存缓存了，所以这一串必须**逐字一致**。
 * 3. **不通过要刷新重来**：aj-captcha 的验证码是一次性的，同一张图不能二次作答。
 *
 * 校验通过后 emit `success`（带凭据）并**由调用方负责关闭**（它持有 `open`）。
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; success: [verification: string] }>()

const data = ref<CaptchaData | null>(null)
const loading = ref(false)
const error = ref('')
/** 手柄左边缘（渲染像素）—— 拼图块跟着它走 */
const drag = ref(0)
const dragging = ref(false)
const checking = ref(false)
const passed = ref(false)

// 模板 refs —— 拖动要用到实际渲染宽度，而渲染宽度 jsdom 里拿不到（offsetWidth 为 0），
// 所以下面处处对 0 做了兜底，组件测试才有意义。
const stageEl = ref<HTMLElement | null>(null)
const trackEl = ref<HTMLElement | null>(null)
const handleEl = ref<HTMLElement | null>(null)
let startClientX = 0
let startDrag = 0

function reset() {
  data.value = null
  error.value = ''
  drag.value = 0
  dragging.value = false
  checking.value = false
  passed.value = false
}

async function load() {
  loading.value = true
  error.value = ''
  drag.value = 0
  passed.value = false
  try {
    data.value = await getCaptcha()
  } catch (e) {
    data.value = null
    error.value = (e as { message?: string })?.message || '验证码加载失败，请重试'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      reset()
      void load()
    } else {
      stopTracking()
    }
  },
  { immediate: true },
)

/** 手柄能滑到的最右位置 */
function maxDrag(): number {
  const track = trackEl.value?.offsetWidth ?? 0
  const handle = handleEl.value?.offsetWidth ?? 0
  return Math.max(track - handle, 0)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const next = startDrag + (e.clientX - startClientX)
  drag.value = Math.min(Math.max(next, 0), maxDrag())
}

function onPointerUp() {
  if (!dragging.value) return
  stopTracking()
  void submit()
}

function stopTracking() {
  dragging.value = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
}

function onPointerDown(e: PointerEvent) {
  if (checking.value || passed.value || !data.value) return
  dragging.value = true
  startClientX = e.clientX
  startDrag = drag.value
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

onBeforeUnmount(stopTracking)

async function submit() {
  const current = data.value
  if (!current || checking.value) return
  checking.value = true
  error.value = ''
  try {
    // 渲染宽度拿不到时（极少）退化成原图宽度，宁可坐标有偏差也不要算成 NaN
    const renderedWidth = stageEl.value?.offsetWidth || ORIGINAL_WIDTH
    const x = slideOffset(drag.value, renderedWidth)
    const resp = await checkCaptcha({
      token: current.token,
      pointJson: encryptSlidePoint(x, current.secretKey),
    })
    if (resp.success) {
      passed.value = true
      emit('success', buildCaptchaVerification(current.token, x, current.secretKey))
      return
    }
    // 一次性验证码，失败必须换一张；提示写在重新取图**之后** —— `load()` 会清空 error
    await load()
    error.value = resp.msg || '验证不通过，请重试'
  } catch (e) {
    await load()
    error.value = (e as { message?: string })?.message || '验证失败，请重试'
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <MlModal :open="open" title="安全验证" :close-on-mask="!checking" @close="emit('close')">
    <div id="captchaSlider" class="cap-wrap">
      <div ref="stageEl" class="cap-stage" :style="{ position: 'relative' }" aria-label="图形验证码">
        <img v-if="data" class="cap-bg" :src="`data:image/png;base64,${data.originalImageBase64}`" alt="" />
        <img
          v-if="data"
          class="cap-piece"
          :src="`data:image/png;base64,${data.jigsawImageBase64}`"
          alt=""
          :style="{ left: `${drag}px` }"
        />
        <div v-if="loading" class="cap-loading">加载中…</div>
      </div>

      <div ref="trackEl" class="cap-track">
        <span class="cap-hint">{{ passed ? '验证通过' : '拖动滑块完成拼图' }}</span>
        <button
          ref="handleEl"
          id="captchaHandle"
          class="cap-handle"
          type="button"
          :disabled="checking || passed || !data"
          :style="{ left: `${drag}px` }"
          aria-label="拖动滑块"
          @pointerdown="onPointerDown"
        >
          {{ passed ? '✓' : '→' }}
        </button>
      </div>

      <p v-if="error" class="cap-error">{{ error }}</p>
      <button class="cap-refresh" type="button" :disabled="checking" @click="load">换一张</button>
    </div>
  </MlModal>
</template>

<style scoped>
.cap-wrap {
  width: 100%;
}
.cap-stage {
  width: 100%;
  max-width: 310px;
  margin: 0 auto;
  border-radius: 4px;
  overflow: hidden;
  background: #f2f3f5;
}
.cap-bg {
  display: block;
  width: 100%;
}
.cap-piece {
  position: absolute;
  top: 0;
  height: 100%;
  width: auto;
}
.cap-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ml-text-weak, #888);
  font-size: 13px;
}
.cap-track {
  position: relative;
  max-width: 310px;
  height: 40px;
  margin: 10px auto 0;
  border-radius: 4px;
  background: #f2f3f5;
  border: 1px solid #e5e6eb;
}
.cap-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ml-text-weak, #888);
  font-size: 13px;
  user-select: none;
}
.cap-handle {
  position: absolute;
  top: -1px;
  left: 0;
  width: 40px;
  height: 40px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fff;
  cursor: grab;
  touch-action: none;
  font-size: 16px;
}
.cap-handle:disabled {
  cursor: not-allowed;
  color: #bbb;
}
.cap-error {
  margin-top: 8px;
  color: var(--ml-orange);
  font-size: 13px;
  text-align: center;
}
.cap-refresh {
  display: block;
  margin: 8px auto 0;
  border: 0;
  background: none;
  color: var(--ml-primary, #2b6cb0);
  font-size: 13px;
  cursor: pointer;
}
</style>
