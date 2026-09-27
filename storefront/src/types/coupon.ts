/** 优惠券状态：1 未使用 / 2 已使用 / 3 已过期 */
export type CouponStatus = 1 | 2 | 3

/** 用户券（AppCouponRespVO） */
export interface Coupon {
  id: number
  name: string
  status: CouponStatus
  usePrice: number
  productScope: number
  productScopeValues: number[]
  /** ⚠️ epoch 毫秒数，不是字符串（详见 `utils/time.ts`）。渲染 MUST 过 `formatDate` */
  validStartTime: number | string | null
  validEndTime: number | string | null
  discountType: number
  /** 折扣百分比（仅 discountType=2）。**满减券这里是 null** */
  discountPercent: number | null
  /** 单位：分 */
  discountPrice: number
  /** 单位：分 */
  discountLimitPrice: number
}

/** 结算响应里的券 —— 可用性**由后端判定**，前端不得自算（FR-026b） */
export interface SettlementCoupon extends Coupon {
  match: boolean
  mismatchReason?: string
}

/**
 * 可领取的券模板（`/promotion/coupon-template/list`）。
 *
 * `canTake` 由**后端**按当前登录用户算好（`getUserCanCanTakeMap`）—— 前端不自己
 * 判断"这人还能不能领"。未登录时后端按匿名算，故未登录会看到可领状态，
 * 点领取时再引导登录（design-new-pages §3.7 的状态定义）。
 */
export interface CouponTemplate {
  id: number
  name: string
  description?: string
  /** 使用门槛，单位：分；0 = 无门槛 */
  usePrice: number
  /** 每人限领个数；-1 表示不限 */
  takeLimitCount?: number
  /** 优惠类型：1 满减 / 2 折扣 */
  discountType: number
  /** 折扣百分比（仅 discountType=2）—— 85 表示用户付 85%，即 8.5 折。满减券为 null */
  discountPercent: number | null
  /** 满减金额，单位：分（仅 discountType=1） */
  discountPrice: number
  /** 折扣上限，单位：分（仅 discountType=2） */
  discountLimitPrice?: number
  /**
   * 生效日期类型（后端 `CouponTemplateValidityTypeEnum`）：
   * 1 固定日期 / 2 领取之后 N 天。
   * 为 2 时 `validStartTime` 是没有的，**不能照常渲染成"有效期 至 X"**。
   */
  validityType?: number
  /** epoch 毫秒数（`validityType=2` 时为 null）。渲染 MUST 过 `formatDate` */
  validStartTime?: number | string | null
  validEndTime?: number | string | null
  /** 领取后 N 天生效（仅 validityType=2） */
  fixedStartTerm?: number
  /** 领取后 N 天失效（仅 validityType=2） */
  fixedEndTerm?: number
  /** 当前登录用户是否还能领。后端给，前端不自行判断 */
  canTake?: boolean
}
