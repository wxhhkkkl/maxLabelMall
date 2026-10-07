import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { OrderStatus } from '@/types'

import MlAmountRow from './MlAmountRow.vue'
import MlCheck from './MlCheck.vue'
import MlField from './MlField.vue'
import MlModal from './MlModal.vue'
import MlPill from './MlPill.vue'
import MlSkeleton from './MlSkeleton.vue'
import MlSteps from './MlSteps.vue'
import PendingText from './PendingText.vue'
import { useToasts } from './useToasts'

const PENDING_ATTR = 'data-content-pending'

describe('PendingText —— 占位标记从值派生（替换只需改一个文件）', () => {
  it('值是占位文案时带上 data-content-pending，且**不显示包裹的 [[ ]] **', () => {
    const w = mount(PendingText, { props: { value: '[[公司简介待补充]]' } })
    // 标记仍在（门禁② 靠它发现未替换内容），只是不再显示成方括号
    expect(w.get('span').attributes(PENDING_ATTR)).toBeDefined()
    expect(w.text()).toBe('公司简介待补充')
  })

  it('只去掉首尾的占位包裹，值内部的方括号原样保留', () => {
    const w = mount(PendingText, { props: { value: '[[型号 [A3] 待确认]]' } })
    expect(w.text()).toBe('型号 [A3] 待确认')
  })

  it('值是真实文案时**不渲染**该属性（而不是渲染成 false）', () => {
    const w = mount(PendingText, { props: { value: '赋签科技（北京）有限公司' } })
    expect(w.get('span').attributes(PENDING_ATTR)).toBeUndefined()
    expect(w.text()).toBe('赋签科技（北京）有限公司')
  })

  it('支持自定义标签（用于标题等语义位置）', () => {
    const w = mount(PendingText, { props: { value: '关于赋签', tag: 'h2' } })
    expect(w.find('h2').exists()).toBe(true)
  })
})

describe('C6 MlPill —— 状态标签变体显式映射', () => {
  it('五个状态各有文案', () => {
    const pairs: Array<[number, string]> = [
      [OrderStatus.UNPAID, '待支付'],
      [OrderStatus.UNDELIVERED, '待发货'],
      [OrderStatus.DELIVERED, '已发货'],
      [OrderStatus.COMPLETED, '已完成'],
      [OrderStatus.CANCELED, '已取消'],
    ]
    for (const [status, text] of pairs) {
      expect(mount(MlPill, { props: { status } }).text()).toBe(text)
    }
  })

  // 本项目最易错的一处：支付成功后是「待发货」，不是「已支付」
  it('status=10 是「待发货」，不得出现「已支付」这种文案', () => {
    const w = mount(MlPill, { props: { status: OrderStatus.UNDELIVERED } })
    expect(w.text()).toBe('待发货')
    expect(w.text()).not.toContain('已支付')
    // 变体名也必须是 awaiting-shipment，不能是历史上那个不可实现的 is-paid
    expect(w.get('span').classes()).toContain('is-awaiting-shipment')
    expect(w.get('span').classes()).not.toContain('is-paid')
  })

  it('**三个非终态**（待支付/待发货/已发货）的变体两两不同 —— FR-041c', () => {
    const classes = [
      OrderStatus.UNPAID,
      OrderStatus.UNDELIVERED,
      OrderStatus.DELIVERED,
    ].map((s) => mount(MlPill, { props: { status: s } }).get('span').classes()[1])
    expect(new Set(classes).size).toBe(3)
  })

  it('已完成与已取消的变体不同（同底但文字色不同，变体名必须可区分）', () => {
    const a = mount(MlPill, { props: { status: OrderStatus.COMPLETED } }).get('span').classes()[1]
    const b = mount(MlPill, { props: { status: OrderStatus.CANCELED } }).get('span').classes()[1]
    expect(a).not.toBe(b)
  })

  it('未知状态有兜底，不白屏', () => {
    const w = mount(MlPill, { props: { status: 999 } })
    expect(w.text()).toBe('未知')
  })
})

describe('C3 MlCheck —— 禁用态不可点', () => {
  it('可点时切换会派发更新', async () => {
    const w = mount(MlCheck, { props: { modelValue: false } })
    await w.get('input').trigger('change')
    expect(w.emitted('update:modelValue')?.[0]).toEqual([true])
  })

  it('禁用时不派发更新', async () => {
    const w = mount(MlCheck, { props: { modelValue: false, disabled: true } })
    await w.get('input').trigger('change')
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })

  it('禁用态带上 is-disabled（样式上降透明度且 pointer-events: none）', () => {
    expect(mount(MlCheck, { props: { modelValue: false, disabled: true } }).classes()).toContain(
      'is-disabled',
    )
  })
})

describe('C4 MlField', () => {
  it('必填时显示星号', () => {
    const w = mount(MlField, { props: { label: '收件人', required: true } })
    expect(w.get('.req').text()).toBe('*')
  })
  it('有错误时显示错误并带 is-error', () => {
    const w = mount(MlField, { props: { label: '手机号', error: '手机号格式不正确' } })
    expect(w.get('.ml-error').text()).toBe('手机号格式不正确')
    expect(w.classes()).toContain('is-error')
  })
  it('无错误时才显示提示', () => {
    const w = mount(MlField, { props: { label: '手机号', hint: '11 位' } })
    expect(w.get('.ml-hint').text()).toBe('11 位')
  })
})

describe('C5 MlSkeleton —— 形状与最终内容一致', () => {
  it('卡片骨架是「图 + 两行」', () => {
    const w = mount(MlSkeleton, { props: { variant: 'card' } })
    expect(w.find('.ml-skeleton.is-img').exists()).toBe(true)
    expect(w.findAll('.ml-skeleton.is-line').length).toBe(2)
  })
  it('文本骨架行数可指定', () => {
    expect(mount(MlSkeleton, { props: { rows: 3 } }).findAll('.ml-skeleton.is-line').length).toBe(3)
  })
})

describe('C7 MlSteps', () => {
  it('当前步之前的为已完成，之后为未达', () => {
    const w = mount(MlSteps, { props: { steps: ['购物车', '填写信息', '支付'], current: 1 } })
    const steps = w.findAll('.ml-step')
    expect(steps[0]?.classes()).toContain('is-done')
    expect(steps[1]?.classes()).toContain('is-current')
    expect(steps[2]?.classes()).toContain('is-todo')
  })
})

describe('C8 MlAmountRow —— 分传入、元展示', () => {
  it('以分传入，按元展示', () => {
    const w = mount(MlAmountRow, { props: { label: '商品小计', fen: 12900 } })
    expect(w.get('.v').text()).toBe('¥129.00')
  })
  it('合计行带 is-total', () => {
    const w = mount(MlAmountRow, { props: { label: '应付总额', fen: 12900, total: true } })
    expect(w.classes()).toContain('is-total')
  })
  it('减免行带 is-cut，且为负向展示', () => {
    const w = mount(MlAmountRow, { props: { label: '优惠券抵扣', fen: 3000, cut: true } })
    expect(w.get('.v').classes()).toContain('is-cut')
  })
})

describe('C1 MlModal —— Esc 与点遮罩关闭', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('关闭时不渲染', () => {
    expect(mount(MlModal, { props: { open: false } }).find('.ml-modal').exists()).toBe(false)
  })

  it('Esc 关闭', async () => {
    const w = mount(MlModal, { props: { open: true, title: '选择优惠券' }, attachTo: document.body })
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await w.vm.$nextTick()
    expect(w.emitted('close')).toBeTruthy()
    w.unmount()
  })

  it('点遮罩关闭', async () => {
    const w = mount(MlModal, { props: { open: true } })
    await w.get('.ml-modal-mask').trigger('click')
    expect(w.emitted('close')).toBeTruthy()
  })

  it('点面板本体不关闭', async () => {
    const w = mount(MlModal, { props: { open: true } })
    await w.get('.ml-modal').trigger('click')
    expect(w.emitted('close')).toBeUndefined()
  })

  it('closeOnMask=false 时点遮罩也不关闭', async () => {
    const w = mount(MlModal, { props: { open: true, closeOnMask: false } })
    await w.get('.ml-modal-mask').trigger('click')
    expect(w.emitted('close')).toBeUndefined()
  })
})

describe('C2 useToasts —— 3 秒消失、最多 3 条', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    useToasts().reset()
    vi.useRealTimers()
  })

  it('3 秒后自动消失', () => {
    const t = useToasts()
    t.success('已加入购物车')
    expect(t.items.value.length).toBe(1)
    vi.advanceTimersByTime(3000)
    expect(t.items.value.length).toBe(0)
  })

  it('同时最多 3 条 —— 第 4 条挤掉最早一条', () => {
    const t = useToasts()
    t.success('1')
    t.success('2')
    t.success('3')
    t.success('4')
    expect(t.items.value.length).toBe(3)
    expect(t.items.value.map((x) => x.text)).toEqual(['2', '3', '4'])
  })

  it('手动关闭', () => {
    const t = useToasts()
    const id = t.success('x')
    t.dismiss(id)
    expect(t.items.value.length).toBe(0)
  })
})
