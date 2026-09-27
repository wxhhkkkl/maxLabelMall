<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 轮播 —— 设计稿的 `.carousel` 结构（`.car-track` / `.car-slide` / `.car-dots`
 * / `.car-arrow`），行为取自 `www/js/main.js`：
 *
 *   · 默认每 5 秒自动切换，到末张后循环回第一张
 *   · 圆点与箭头切换，**切换后重置计时**（否则刚点完就被自动播放推走）
 *   · 鼠标移入暂停、移出恢复
 *   · 触摸横滑超过 40px 才切换
 *
 * 首页 Hero 与商城横幅共用本组件，幻灯片由默认插槽传入，故两侧版式互不影响。
 *
 * `count` 由父组件**显式传入**：父组件本就持有幻灯片数组，比挂载后查 DOM
 * 数元素更可靠（也让测试不依赖插槽的渲染方式）。
 */
const props = withDefaults(defineProps<{ count: number; interval?: number }>(), { interval: 5000 })

const index = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

function go(idx: number) {
  if (props.count <= 0) return
  index.value = ((idx % props.count) + props.count) % props.count
}

function restart() {
  stop()
  if (props.count <= 1) return
  timer = setInterval(() => go(index.value + 1), props.interval)
}

function stop() {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function goAndRestart(idx: number) {
  go(idx)
  restart()
}

// ── 触摸滑动：阈值 40px，与设计稿一致 ────────────────────────────────
let touchStartX = 0
const SWIPE_THRESHOLD = 40

function onTouchStart(e: TouchEvent) {
  touchStartX = e.touches[0]?.clientX ?? 0
}

function onTouchEnd(e: TouchEvent) {
  const dx = (e.changedTouches[0]?.clientX ?? 0) - touchStartX
  if (Math.abs(dx) > SWIPE_THRESHOLD) goAndRestart(dx < 0 ? index.value + 1 : index.value - 1)
}

onMounted(restart)

onBeforeUnmount(stop)
</script>

<template>
  <section
    class="carousel"
    @mouseenter="stop"
    @mouseleave="restart"
    @touchstart.passive="onTouchStart"
    @touchend.passive="onTouchEnd"
  >
    <div class="car-track" :style="{ transform: `translateX(-${index * 100}%)` }">
      <slot />
    </div>

    <button class="car-arrow prev" type="button" aria-label="上一张" @click="goAndRestart(index - 1)">
      ‹
    </button>
    <button class="car-arrow next" type="button" aria-label="下一张" @click="goAndRestart(index + 1)">
      ›
    </button>

    <div class="car-dots">
      <button
        v-for="i in props.count"
        :key="i"
        class="car-dot"
        :class="{ active: i - 1 === index }"
        type="button"
        :aria-label="`切换到第 ${i} 张`"
        @click="goAndRestart(i - 1)"
      />
    </div>
  </section>
</template>
