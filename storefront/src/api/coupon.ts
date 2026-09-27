import { get, post } from '@/config/http'
import type { Coupon, CouponTemplate, PageResult } from '@/types'

/**
 * 优惠券（`/app-api/promotion/coupon*`）。
 *
 * ⚠️ 两张**不同**的接口族，不要混用：
 *   · `coupon-template/*` —— 可**领取**的券模板（领券中心），免登录
 *   · `coupon/*`         —— 我**已领取**的券（我的券），需登录
 *
 * ⚠️ 结算页**不能**用这里的接口自行判断券是否可用 —— 可用性只由结算响应的
 * `coupons[].match` / `mismatchReason` 决定（FR-026b）。
 */

/** 券状态：1 未使用 / 2 已使用 / 3 已过期 */
export type CouponStatusFilter = 1 | 2 | 3

/** 我的券（分页） */
export function pageMyCoupons(params: {
  pageNo?: number
  pageSize?: number
  status?: CouponStatusFilter
}): Promise<PageResult<Coupon>> {
  return get<PageResult<Coupon>>('/promotion/coupon/page', params)
}

/** 未使用券数量（「我的券」角标） */
export function getUnusedCouponCount(): Promise<number> {
  return get<number>('/promotion/coupon/get-unused-count')
}

/** 可领取的券模板（领券中心）。`@PermitAll`，免登录可看 */
export function listCouponTemplates(params: {
  spuId?: number
  productScope?: number
  count?: number
}): Promise<CouponTemplate[]> {
  return get<CouponTemplate[]>('/promotion/coupon-template/list', params)
}

/** 领取一张券。返回是否还能继续领同一张模板 */
export function takeCoupon(templateId: number): Promise<boolean> {
  return post<boolean>('/promotion/coupon/take', { templateId })
}
