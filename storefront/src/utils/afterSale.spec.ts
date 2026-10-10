import { describe, expect, it } from 'vitest'

import { AfterSaleItemStatus, AfterSaleWay } from '@/types'
import { OrderStatus } from '@/types'

import {
  afterSaleItemStatusText,
  canCancelAfterSale,
  canCancelAfterSaleByStatus,
  afterSaleWayLabel,
  allowsReturnRefund,
  canApplyRefund,
} from './afterSale'

/**
 * 「能不能申请退款」的判定 —— 抽成纯函数就是为了能把它**穷举**掉。
 *
 * 口径全部来自后端 `AfterSaleServiceImpl.validateOrderItemApplicable`：
 *   · 订单必须**已支付且未取消**（待支付、已取消都会被拒）；
 *   · 该**订单项**必须未被申请过（`afterSaleStatus !== 0` 会被拒）；
 *   · 退款金额不得超过该项实付 → 所以**实付为 0 的项不给入口**；
 *   · 「退货退款」要求**已发货**。
 */

/** 造一个订单项，只关心判定用到的那两个字段 */
function item(over: { id?: number; payPrice?: number; afterSaleStatus?: number } = {}) {
  return { id: 11, payPrice: 7000, afterSaleStatus: 0, ...over }
}

describe('canApplyRefund —— 订单项上该不该出现「申请退款」', () => {
  it('待发货 / 已发货 / 已完成都可以申请（都是已支付且未取消）', () => {
    for (const status of [OrderStatus.UNDELIVERED, OrderStatus.DELIVERED, OrderStatus.COMPLETED]) {
      expect(canApplyRefund(item(), status)).toBe(true)
    }
  })

  it('**待支付不行** —— 后端对未支付订单直接拒', () => {
    expect(canApplyRefund(item(), OrderStatus.UNPAID)).toBe(false)
  })

  it('**已取消不行** —— 后端对已取消订单直接拒', () => {
    expect(canApplyRefund(item(), OrderStatus.CANCELED)).toBe(false)
  })

  it('**已经申请过的项不行**（售后中 / 售后成功）', () => {
    expect(canApplyRefund(item({ afterSaleStatus: AfterSaleItemStatus.APPLY }), OrderStatus.UNDELIVERED)).toBe(false)
    expect(canApplyRefund(item({ afterSaleStatus: AfterSaleItemStatus.SUCCESS }), OrderStatus.UNDELIVERED)).toBe(false)
  })

  it('状态字段缺省时按「未售后」处理（老数据/字段缺失不该让入口消失）', () => {
    expect(canApplyRefund(item({ afterSaleStatus: undefined }), OrderStatus.UNDELIVERED)).toBe(true)
  })

  it('**实付为 0 的项不给入口** —— 后端 refundPrice 必须 > 0，没有可退金额', () => {
    expect(canApplyRefund(item({ payPrice: 0 }), OrderStatus.UNDELIVERED)).toBe(false)
  })

  it('实付字段缺省时也不给入口（宁可少一个入口，也不发一枪必被拒的请求）', () => {
    expect(canApplyRefund(item({ payPrice: undefined }), OrderStatus.UNDELIVERED)).toBe(false)
  })

  it('未知的售后状态按「已申请过」保守处理 —— 不发请求', () => {
    expect(canApplyRefund(item({ afterSaleStatus: 99 }), OrderStatus.UNDELIVERED)).toBe(false)
  })
})

describe('allowsReturnRefund —— 「退货退款」只在已发货之后可选', () => {
  it('**未发货（待发货）不可选** —— 后端会拒「订单未发货，无法申请【退货退款】售后」', () => {
    expect(allowsReturnRefund(OrderStatus.UNDELIVERED)).toBe(false)
  })

  it('已发货 / 已完成为可选', () => {
    expect(allowsReturnRefund(OrderStatus.DELIVERED)).toBe(true)
    expect(allowsReturnRefund(OrderStatus.COMPLETED)).toBe(true)
  })

  it('待支付 / 已取消也不可选（这两个状态本就不给退款入口）', () => {
    expect(allowsReturnRefund(OrderStatus.UNPAID)).toBe(false)
    expect(allowsReturnRefund(OrderStatus.CANCELED)).toBe(false)
  })
})

describe('canCancelAfterSale —— 能不能撤销申请', () => {
  it('售后中且有售后单编号 → 可以撤销', () => {
    expect(canCancelAfterSale({ afterSaleStatus: AfterSaleItemStatus.APPLY, afterSaleId: 2048 })).toBe(true)
  })

  it('未售后 / 已退款都没有可撤销的东西', () => {
    expect(canCancelAfterSale({ afterSaleStatus: AfterSaleItemStatus.NONE, afterSaleId: 2048 })).toBe(false)
    expect(canCancelAfterSale({ afterSaleStatus: AfterSaleItemStatus.SUCCESS, afterSaleId: 2048 })).toBe(false)
  })

  it('**缺售后单编号时不给入口** —— 没有 id 根本发不出撤销请求', () => {
    expect(canCancelAfterSale({ afterSaleStatus: AfterSaleItemStatus.APPLY })).toBe(false)
    expect(canCancelAfterSale({ afterSaleStatus: AfterSaleItemStatus.APPLY, afterSaleId: 0 })).toBe(false)
  })
})

describe('文案映射', () => {
  it('订单项的售后状态：未售后无标签、售后中、已退款', () => {
    expect(afterSaleItemStatusText(AfterSaleItemStatus.NONE)).toBe('')
    expect(afterSaleItemStatusText(AfterSaleItemStatus.APPLY)).toBe('退款处理中')
    expect(afterSaleItemStatusText(AfterSaleItemStatus.SUCCESS)).toBe('已退款')
  })

  it('未知状态不抛错，回落成空串（后端新增状态时不至于白屏）', () => {
    expect(afterSaleItemStatusText(undefined)).toBe('')
    expect(afterSaleItemStatusText(99)).toBe('')
  })

  it('售后方式的中文名与后端 AfterSaleWayEnum 一致', () => {
    expect(afterSaleWayLabel(AfterSaleWay.REFUND_ONLY)).toBe('仅退款')
    expect(afterSaleWayLabel(AfterSaleWay.RETURN_AND_REFUND)).toBe('退货退款')
  })
})

/**
 * **列表页**侧的可撤销判定。
 *
 * ⚠️ 与订单详情侧那个 `canCancelAfterSale(item)` **不是同一个函数**：那边只有订单项的
 * 粗粒度状态（0/10/20），分不出"商家已收货待退款"这种不可撤销的情形；而「我的售后」
 * 列表拿到的是售后单的**精确状态**（10/20/30/40/50/61/62/63），所以能准确判断。
 */
describe('canCancelAfterSaleByStatus —— 列表页（精确状态）', () => {
  it('后端允许撤销的三个状态：申请中(10) / 卖家同意(20) / 待卖家收货(30)', () => {
    expect(canCancelAfterSaleByStatus(10)).toBe(true)
    expect(canCancelAfterSaleByStatus(20)).toBe(true)
    expect(canCancelAfterSaleByStatus(30)).toBe(true)
  })

  it('**再往后就不能撤了** —— 商家已收货待退款(40)、完成(50)', () => {
    expect(canCancelAfterSaleByStatus(40)).toBe(false)
    expect(canCancelAfterSaleByStatus(50)).toBe(false)
  })

  it('已取消(61) / 卖家拒绝(62,63) 无从撤销', () => {
    expect(canCancelAfterSaleByStatus(61)).toBe(false)
    expect(canCancelAfterSaleByStatus(62)).toBe(false)
    expect(canCancelAfterSaleByStatus(63)).toBe(false)
  })

  it('未知状态一律false —— 不赌', () => {
    expect(canCancelAfterSaleByStatus(99)).toBe(false)
    expect(canCancelAfterSaleByStatus(undefined)).toBe(false)
  })
})
