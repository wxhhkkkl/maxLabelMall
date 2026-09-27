import { describe, expect, it } from 'vitest'

import { couponAmountText, couponThresholdText, couponValidText } from './coupon'

/**
 * 券的展示文案。
 *
 * ⚠️ 最容易错的是**折扣的语义**。后端 `PromotionDiscountTypeEnum`：
 *   · 1 PRICE（满减）—— `discountPrice` 是**减掉的金额（分）**
 *   · 2 PERCENT（折扣）—— `discountPercent` 是**用户仍需支付的百分比**，
 *     因为 `TradeCouponPriceCalculator#getCouponPrice` 算的是
 *     `couponPrice = total - total * discountPercent / 100`。
 *
 * 所以 `discountPercent = 85` 是**打 8.5 折**（用户付 85%），
 * **不是**"减 85%"、也不是"打 85 折"。
 */
describe('券面额文案', () => {
  it('满减券显示减掉的金额', () => {
    expect(couponAmountText({ discountType: 1, discountPrice: 3000, discountPercent: 0 })).toBe(
      '¥30.00',
    )
  })

  it('**折扣券显示"打几折"** —— 85 是 8.5 折，不是 85 折', () => {
    expect(couponAmountText({ discountType: 2, discountPrice: 0, discountPercent: 85 })).toBe(
      '8.5折',
    )
  })

  it('整数折扣不拖一个多余的 .0', () => {
    expect(couponAmountText({ discountType: 2, discountPrice: 0, discountPercent: 90 })).toBe('9折')
  })

  it('零金额满减券也照实显示', () => {
    expect(couponAmountText({ discountType: 1, discountPrice: 0, discountPercent: 0 })).toBe('¥0.00')
  })

  it('**满减券的 discountPercent 是 null**（线上数据实测如此），不该算成「0折」', () => {
    expect(couponAmountText({ discountType: 1, discountPrice: 1000, discountPercent: null })).toBe(
      '¥10.00',
    )
  })

  it('折扣券缺百分比时回落到金额，不显示假折扣', () => {
    expect(couponAmountText({ discountType: 2, discountPrice: 1000, discountPercent: null })).toBe(
      '¥10.00',
    )
  })
})

describe('券有效期文案', () => {
  it('**「领取之后」型没有固定日期** —— 不能渲染成「有效期 至 」', () => {
    // 线上三张券都是这种：validStartTime / validEndTime 都是 null
    expect(
      couponValidText({
        validityType: 2,
        validStartTime: null,
        validEndTime: null,
        fixedStartTerm: 0,
        fixedEndTerm: 11,
      }),
    ).toBe('领取后 11 天内有效')
  })

  it('「领取之后」型带生效天数时说清第几天生效', () => {
    expect(
      couponValidText({ validityType: 2, fixedStartTerm: 3, fixedEndTerm: 11 }),
    ).toBe('领取后第 3 天生效，11 天内有效')
  })

  it('固定日期型显示起止日期', () => {
    expect(
      couponValidText({
        validityType: 1,
        validStartTime: '2026-09-01 00:00:00',
        validEndTime: '2026-12-31 23:59:59',
      }),
    ).toBe('有效期 2026-09-01 至 2026-12-31')
  })

  it('两个日期都没有时不给空文案', () => {
    expect(couponValidText({ validityType: 1 })).toBe('')
  })
})

describe('券使用门槛文案', () => {
  it('有门槛时显示「满 X 可用」', () => {
    expect(couponThresholdText({ usePrice: 10000 })).toBe('满¥100.00可用')
  })

  it('`usePrice` 为 0 是**无门槛**（不是「满 0 元可用」）', () => {
    expect(couponThresholdText({ usePrice: 0 })).toBe('无门槛')
  })
})
