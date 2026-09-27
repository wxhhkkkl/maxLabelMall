import type { OrderPrice } from '@/types'

/**
 * 金额工具 —— **分 ↔ 元换算与格式化的唯一实现处**。
 *
 * 后端所有金额字段均为 `Integer`，单位「分」。任何地方都不得散落 `* 0.01`
 * 或 `toFixed(2)`；一律经本模块。
 *
 * 实现上用**整数运算**拆分元与分，不依赖浮点，因此不会出现 0.29 元被算成
 * 「少一分」这类问题。
 */

/** 分 → 元字符串（两位小数），如 1290 → '12.90' */
export function fenToYuan(fen: number): string {
  const n = Math.round(fen)
  const neg = n < 0
  const abs = Math.abs(n)
  const yuan = Math.floor(abs / 100)
  const cents = abs % 100
  return `${neg ? '-' : ''}${yuan}.${String(cents).padStart(2, '0')}`
}

/**
 * 元 → 分（四舍五入到整数分），如 12.9 → 1290。
 *
 * 注意：入参是 JS number，本身可能带有无法精确表示的十进制（如 `1.005`）。
 * 本函数按 number 的实际值四舍五入，不做十进制字符串修复 —— 这只影响「用户
 * 手输金额」的场景，而本站的金额一律来自后端（已是整数分），故不构成风险。
 */
export function yuanToFen(yuan: number): number {
  return Math.round(yuan * 100)
}

/**
 * 分 → 带符号的展示串，如 1290 → '¥12.90'，-3000 → '-¥30.00'。
 *
 * **负号放在货币符号之前**（'-¥30.00' 而不是 '¥-30.00'）。优惠行（促销优惠、
 * 优惠券抵扣）传的就是负数，符号位置错了用户会读成「-¥」。
 */
export function formatYuan(fen: number): string {
  return fen < 0 ? `-¥${fenToYuan(-fen)}` : `¥${fenToYuan(fen)}`
}

/**
 * 金额不变量核对（SC-007）。**六项，不是四项**：
 *
 *   payPrice === totalPrice - couponPrice - pointPrice - discountPrice
 *                          + deliveryPrice - vipPrice
 *
 * 上式取自后端 `TradePriceCalculateRespBO.payPrice` 的 javadoc 与实际计算
 * （`TradePriceCalculatorHelper`）。本期 `pointPrice` 恒为 0（`pointStatus`
 * 固定 false），但 **`vipPrice`（会员折扣）不会被置零** —— 漏掉它会在运营
 * 配置会员折扣时误报失配。
 *
 * 以**分**为单位做整数比较，避免浮点误差。
 */
export function reconcile(price: OrderPrice): boolean {
  const expected =
    price.totalPrice -
    price.couponPrice -
    price.pointPrice -
    price.discountPrice +
    price.deliveryPrice -
    price.vipPrice
  return expected === price.payPrice
}
