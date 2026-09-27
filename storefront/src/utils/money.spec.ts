import { describe, expect, it } from 'vitest'

import { fenToYuan, formatYuan, reconcile, yuanToFen } from './money'
import type { OrderPrice } from '@/types'

/** 构造一个六项自洽的金额对象；未指定的项按 0 处理 */
function price(over: Partial<OrderPrice> = {}): OrderPrice {
  const p: OrderPrice = {
    totalPrice: 0,
    discountPrice: 0,
    deliveryPrice: 0,
    couponPrice: 0,
    pointPrice: 0,
    vipPrice: 0,
    payPrice: 0,
    ...over,
  }
  if (over.payPrice === undefined) {
    p.payPrice =
      p.totalPrice - p.couponPrice - p.pointPrice - p.discountPrice + p.deliveryPrice - p.vipPrice
  }
  return p
}

describe('分 → 元', () => {
  it('整数分转两位小数字符串', () => {
    expect(fenToYuan(1290)).toBe('12.90')
    expect(fenToYuan(100)).toBe('1.00')
    expect(fenToYuan(1)).toBe('0.01')
    expect(fenToYuan(19900)).toBe('199.00')
  })

  it('零与负数', () => {
    expect(fenToYuan(0)).toBe('0.00')
    expect(fenToYuan(-1290)).toBe('-12.90')
  })

  it('不做浮点近似：999999 分就是 9999.99', () => {
    expect(fenToYuan(999999)).toBe('9999.99')
    // 0.29 * 100 在浮点下是 28.999...，绝不能因此少一分
    expect(fenToYuan(29)).toBe('0.29')
  })
})

describe('元 → 分', () => {
  it('常规换算', () => {
    expect(yuanToFen(12.9)).toBe(1290)
    expect(yuanToFen(0.01)).toBe(1)
    expect(yuanToFen(199)).toBe(19900)
  })

  it('四舍五入到整数分（0.29 这类浮点误差不得少一分）', () => {
    expect(yuanToFen(0.29)).toBe(29)
    expect(yuanToFen(19.99)).toBe(1999)
    expect(yuanToFen(1.004)).toBe(100)
    // 注：`1.005` 不在断言里 —— 它在 IEEE 754 下实际存为 1.00499999…，
    // Math.round 得 100 而非 101。本站金额一律来自后端（整数分），
    // 元→分只用于用户手输，故按 number 实际值取整即可，不做十进制字符串修复。
  })
})

describe('格式化展示', () => {
  it('带人民币符号与两位小数', () => {
    expect(formatYuan(1290)).toBe('¥12.90')
    expect(formatYuan(0)).toBe('¥0.00')
  })

  it('**负号在货币符号之前**——优惠行是「-¥30.00」而不是「¥-30.00」', () => {
    expect(formatYuan(-3000)).toBe('-¥30.00')
    expect(formatYuan(-1)).toBe('-¥0.01')
  })
})

describe('reconcile —— 金额不变量（SC-007）', () => {
  it('只有商品小计时成立', () => {
    expect(reconcile(price({ totalPrice: 1290 }))).toBe(true)
  })

  it('运费为加项', () => {
    expect(reconcile(price({ totalPrice: 1290, deliveryPrice: 800 }))).toBe(true)
  })

  it('券与促销折扣为减项，两者可叠加', () => {
    expect(reconcile(price({ totalPrice: 10000, couponPrice: 3000, discountPrice: 500 }))).toBe(true)
  })

  it('促销折扣与优惠券是两笔独立的减免', () => {
    const p = price({ totalPrice: 10000, couponPrice: 3000, discountPrice: 500 })
    expect(p.payPrice).toBe(6500)
    expect(reconcile(p)).toBe(true)
  })

  it('pointPrice 恒为 0（本期不使用积分）时不影响', () => {
    const p = price({ totalPrice: 10000, couponPrice: 1000, pointPrice: 0 })
    expect(reconcile(p)).toBe(true)
  })

  // ── 以下两条是本项目最容易出错的地方 ─────────────────────────────
  it('vipPrice（会员折扣）必须计入 —— 漏掉它会在会员折扣生效时失配', () => {
    // 若实现漏掉 vipPrice，会把这条算成“不成立”而误报
    const p = price({ totalPrice: 10000, vipPrice: 1500 })
    expect(p.payPrice).toBe(8500)
    expect(reconcile(p)).toBe(true)
  })

  it('vipPrice 与 pointPrice 同时非零也成立', () => {
    const p = price({ totalPrice: 10000, couponPrice: 2000, pointPrice: 500, vipPrice: 1500, deliveryPrice: 800 })
    expect(p.payPrice).toBe(6800)
    expect(reconcile(p)).toBe(true)
  })

  it('按四项（漏 vipPrice/pointPrice）核对会误判为不成立 —— 反向验证', () => {
    const p = price({ totalPrice: 10000, vipPrice: 1500 })
    const fourTerm =
      p.totalPrice - p.discountPrice - p.couponPrice + p.deliveryPrice
    expect(fourTerm).not.toBe(p.payPrice)
  })

  it('总额与各项不符时返回 false', () => {
    expect(reconcile(price({ totalPrice: 10000, payPrice: 9999 }))).toBe(false)
    expect(reconcile(price({ totalPrice: 10000, couponPrice: 1000, payPrice: 9500 }))).toBe(false)
  })
})
