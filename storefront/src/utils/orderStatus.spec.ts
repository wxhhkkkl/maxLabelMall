import { describe, expect, it } from 'vitest'

import { OrderStatus } from '@/types'

import {
  ORDER_STATUS_TEXT,
  ORDER_STATUS_VARIANT,
  canCancel,
  canPay,
  isTerminal,
  orderStatusFilters,
  orderStatusText,
  orderStatusVariant,
} from './orderStatus'

describe('订单状态映射 —— 与后端 TradeOrderStatusEnum 一一对应', () => {
  it('五个状态各有文案，不多不少', () => {
    expect(Object.keys(ORDER_STATUS_TEXT)).toHaveLength(5)
    expect(orderStatusText(OrderStatus.UNPAID)).toBe('待支付')
    expect(orderStatusText(OrderStatus.UNDELIVERED)).toBe('待发货')
    expect(orderStatusText(OrderStatus.DELIVERED)).toBe('已发货')
    expect(orderStatusText(OrderStatus.COMPLETED)).toBe('已完成')
    expect(orderStatusText(OrderStatus.CANCELED)).toBe('已取消')
  })

  // ── 本项目最容易实现错的一处 ─────────────────────────────────────
  it('**支付成功后是 status=10「待发货」，不是「已支付」**（FR-039）', () => {
    expect(orderStatusText(OrderStatus.UNDELIVERED)).toBe('待发货')
    expect(orderStatusText(10)).not.toBe('已支付')
    // 全表都不应出现「已支付」——后端五个状态里没有它
    expect(Object.values(ORDER_STATUS_TEXT)).not.toContain('已支付')
  })

  it('未知状态回落为「未知」，不抛错（后端加状态时不至于白屏）', () => {
    expect(orderStatusText(999)).toBe('未知')
    expect(orderStatusVariant(999)).toBe('is-done')
  })
})

describe('变体映射 —— C6 状态标签', () => {
  it('五个状态各有变体', () => {
    expect(Object.keys(ORDER_STATUS_VARIANT)).toHaveLength(5)
  })

  it('不存在历史上那个不可实现的 is-paid 变体', () => {
    // 早期设计稿写过 .is-paid 表示「已支付」，但后端没有「已支付」状态
    expect(Object.values(ORDER_STATUS_VARIANT)).not.toContain('is-paid')
  })

  it('**三个非终态的变体两两不同** —— FR-041c 要求它们在视觉上可区分', () => {
    const nonTerminal = [
      ORDER_STATUS_VARIANT[OrderStatus.UNPAID],
      ORDER_STATUS_VARIANT[OrderStatus.UNDELIVERED],
      ORDER_STATUS_VARIANT[OrderStatus.DELIVERED],
    ]
    expect(new Set(nonTerminal).size).toBe(3)
  })

  it('已完成的变体是 is-done，已取消是 is-cancelled（同底色但文字色不同）', () => {
    expect(orderStatusVariant(OrderStatus.COMPLETED)).toBe('is-done')
    expect(orderStatusVariant(OrderStatus.CANCELED)).toBe('is-cancelled')
  })
})

describe('用户侧动作 —— 仅「待支付」可用', () => {
  it('只有待支付能付款与取消', () => {
    expect(canPay(OrderStatus.UNPAID)).toBe(true)
    expect(canCancel(OrderStatus.UNPAID)).toBe(true)

    for (const s of [
      OrderStatus.UNDELIVERED,
      OrderStatus.DELIVERED,
      OrderStatus.COMPLETED,
      OrderStatus.CANCELED,
    ]) {
      expect(canPay(s)).toBe(false)
      expect(canCancel(s)).toBe(false)
    }
  })

  it('已付款的订单不展示支付入口（FR-040）', () => {
    // 待发货即已付款
    expect(canPay(OrderStatus.UNDELIVERED)).toBe(false)
  })
})

describe('终态判定', () => {
  it('已完成与已取消是终态', () => {
    expect(isTerminal(OrderStatus.COMPLETED)).toBe(true)
    expect(isTerminal(OrderStatus.CANCELED)).toBe(true)
  })
  it('待支付/待发货/已发货不是终态', () => {
    expect(isTerminal(OrderStatus.UNPAID)).toBe(false)
    expect(isTerminal(OrderStatus.UNDELIVERED)).toBe(false)
    expect(isTerminal(OrderStatus.DELIVERED)).toBe(false)
  })
})

describe('订单列表筛选胶囊', () => {
  it('六个选项，全部在最前，顺序与设计一致', () => {
    const f = orderStatusFilters()
    expect(f).toHaveLength(6)
    expect(f[0]).toEqual({ value: '', label: '全部' })
    expect(f.map((x) => x.label)).toEqual(['全部', '待支付', '待发货', '已发货', '已完成', '已取消'])
  })
})
