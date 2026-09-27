import { formatYuan } from './money'
import { formatDate } from './time'

/**
 * 券的展示文案 —— 「领券中心」与「我的券」共用，避免两处各写一份。
 *
 * 这里只做**展示**。券是否可用**不在这里判断**：可用性由结算响应的
 * `coupons[].match` / `mismatchReason` 决定（FR-026b）。
 */

/** 折扣类优惠：用户仍需支付的百分比（后端 `PromotionDiscountTypeEnum.PERCENT`） */
const DISCOUNT_TYPE_PERCENT = 2

/** 算文案所需的最小字段集。用户券与券模板都结构上满足它 */
export interface CouponDisplayable {
  /** 使用门槛，单位：分；0 = 无门槛 */
  usePrice: number
  /** 优惠类型：1 满减 / 2 折扣 */
  discountType: number
  /** 折扣百分比（仅 discountType=2）。**满减券这里是 null**（线上数据实测如此） */
  discountPercent: number | null
  /** 满减金额，单位：分（仅 discountType=1） */
  discountPrice: number
}

/**
 * 券的**面额文案**。
 *
 * ⚠️ 折扣的语义容易反：`TradeCouponPriceCalculator#getCouponPrice` 算的是
 * `couponPrice = total - total * discountPercent / 100`，即 `discountPercent`
 * 是**用户仍需支付的百分比**。所以 `85` 要显示成 **8.5 折**，
 * 不是"减 85%"、也不是"85 折"。
 */
export function couponAmountText(
  c: Pick<CouponDisplayable, 'discountType' | 'discountPercent' | 'discountPrice'>,
): string {
  if (c.discountType === DISCOUNT_TYPE_PERCENT && c.discountPercent != null) {
    // 85 → 8.5折；90 → 9折（整数不拖 .0）
    return `${(c.discountPercent / 10).toFixed(1).replace(/\.0$/, '')}折`
  }
  // 折扣百分比缺失时按满减金额显示，不显示成「0折」这种假信息
  return formatYuan(c.discountPrice)
}

/**
 * 券的**有效期文案**。
 *
 * ⚠️ `validityType=2`（领取之后生效）时后端**不返回** `validStartTime`/`validEndTime`
 * （线上三张券都是这种，且两个时间字段都是 null），只有相对天数 —— 照常渲染会得到
 * 「有效期 至 」这种空文案。
 */
export function couponValidText(c: {
  validityType?: number
  /** epoch 毫秒数（见 `utils/time.ts`）；`validityType=2` 时为 null */
  validStartTime?: number | string | null
  validEndTime?: number | string | null
  fixedStartTerm?: number | null
  fixedEndTerm?: number | null
}): string {
  if (c.validityType === 2) {
    const end = c.fixedEndTerm
    if (!end) return '领取后生效'
    const start = c.fixedStartTerm
    return start && start > 0 ? `领取后第 ${start} 天生效，${end} 天内有效` : `领取后 ${end} 天内有效`
  }
  // ⚠️ 时间字段是 epoch 毫秒数，不是字符串 —— 必须过 formatDate，
  // 否则这里会抛 `s.slice is not a function`（线上实测）
  const from = formatDate(c.validStartTime)
  const to = formatDate(c.validEndTime)
  if (!from && !to) return ''
  return `有效期 ${from} 至 ${to}`
}

/** 券的**使用门槛文案**。`usePrice` 为 0 表示无门槛，不要显示成「满 ¥0.00 可用」 */
export function couponThresholdText(c: Pick<CouponDisplayable, 'usePrice'>): string {
  return c.usePrice > 0 ? `满${formatYuan(c.usePrice)}可用` : '无门槛'
}
