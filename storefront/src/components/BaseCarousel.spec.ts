import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import BaseCarousel from './BaseCarousel.vue'

/**
 * 轮播的行为契约 —— 取自设计稿 `www/js/main.js` 的实际实现：
 * 5 秒自动播放、圆点与箭头切换、悬停暂停、40px 触摸滑动切换。
 *
 * 设计稿的这段交互此前在计划里被完全遗漏（`main.js` 在四份规划产物中 0 次命中），
 * 这里把它的行为固化下来。
 */
const SLIDES = ['第一张', '第二张', '第三张']

function mountCarousel() {
  return mount(BaseCarousel, {
    props: { count: SLIDES.length, interval: 5000 },
    slots: { default: SLIDES.map((s) => `<div class="car-slide">${s}</div>`).join('') },
  })
}

beforeEach(() => {
  vi.useFakeTimers()
})
afterEach(() => {
  vi.useRealTimers()
})

describe('BaseCarousel —— 设计稿轮播的行为', () => {
  it('按每张幻灯片生成一个圆点，第一个默认选中', () => {
    const w = mountCarousel()
    const dots = w.findAll('.car-dot')
    expect(dots).toHaveLength(3)
    expect(dots[0]?.classes()).toContain('active')
  })

  it('默认每 5 秒自动切换到下一张', async () => {
    const w = mountCarousel()
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-0%)')
    vi.advanceTimersByTime(5000)
    await nextTick() // Vue 的 DOM 更新是异步的，推进定时器后必须等一拍
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-100%)')
    vi.advanceTimersByTime(5000)
    await nextTick()
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-200%)')
  })

  it('自动播放在最后一张后回到第一张（循环）', async () => {
    const w = mountCarousel()
    vi.advanceTimersByTime(5000)
    await nextTick()
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-100%)')
    vi.advanceTimersByTime(5000 * 2)
    await nextTick()
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-0%)') // 3 张后回到第一张
  })

  it('点圆点切换，并重置计时（不是切换后立刻又被自动播放推走）', async () => {
    const w = mountCarousel()
    await w.findAll('.car-dot')[2]?.trigger('click')
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-200%)')
    // 重置后不满 5 秒不应再动
    vi.advanceTimersByTime(4999)
    await nextTick()
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-200%)')
  })

  it('箭头上一张 / 下一张', async () => {
    const w = mountCarousel()
    await w.get('.car-arrow.next').trigger('click')
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-100%)')
    await w.get('.car-arrow.prev').trigger('click')
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-0%)')
  })

  it('在第一张点「上一张」会循环到最后一张', async () => {
    const w = mountCarousel()
    await w.get('.car-arrow.prev').trigger('click')
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-200%)')
  })

  it('鼠标移入暂停自动播放，移出恢复', async () => {
    const w = mountCarousel()
    await w.trigger('mouseenter')
    vi.advanceTimersByTime(5000 * 2)
    await nextTick()
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-0%)')

    await w.trigger('mouseleave')
    vi.advanceTimersByTime(5000)
    await nextTick()
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-100%)')
  })

  it('触摸横滑超过 40px 才切换（小于阈值不动）', async () => {
    const w = mountCarousel()
    await w.trigger('touchstart', { touches: [{ clientX: 200 }] } as never)
    await w.trigger('touchend', { changedTouches: [{ clientX: 240 }] } as never)
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-0%)')

    await w.trigger('touchstart', { touches: [{ clientX: 200 }] } as never)
    await w.trigger('touchend', { changedTouches: [{ clientX: 100 }] } as never)
    expect(w.get('.car-track').attributes('style')).toContain('translateX(-100%)')
  })

  it('圆点的 active 状态跟随当前张', async () => {
    const w = mountCarousel()
    await w.get('.car-arrow.next').trigger('click')
    const dots = w.findAll('.car-dot')
    expect(dots[0]?.classes()).not.toContain('active')
    expect(dots[1]?.classes()).toContain('active')
  })
})
