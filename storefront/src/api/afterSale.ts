import { del, get, post } from '@/config/http'
import type { AfterSaleCreateReq, AfterSaleListItem, PageResult } from '@/types'

/**
 * 售后接口（`/app-api/trade/after-sale/**`）。
 *
 * 本期只用到「申请售后」这一个 —— 提交后售后单进入**待商家审核**，
 * 由运营在管理端「售后退款」页同意或拒绝（那套完全现成，前端不用管）。
 *
 * ⚠️ `orderItemId` 是**订单项**编号，不是订单编号 —— 传错会「订单项不存在」。
 * 一笔订单里每个商品各是一条售后单。
 *
 * ⚠️ 可选键**没传时不塞进 body**（与 `api/pay.ts` 的 `submitPay` 同一口径）。
 */
export function createAfterSale(req: AfterSaleCreateReq): Promise<number> {
  const body: AfterSaleCreateReq = {
    orderItemId: req.orderItemId,
    way: req.way,
    refundPrice: req.refundPrice,
    applyReason: req.applyReason,
  }
  if (req.applyDescription) {
    body.applyDescription = req.applyDescription
  }
  // 本期不做图片上传；留这条是为了以后接上时不必改调用方的判断
  if (req.applyPicUrls?.length) {
    body.applyPicUrls = req.applyPicUrls
  }
  return post<number>('/trade/after-sale/create', body)
}

/**
 * 撤销退款申请（买家自己撤）。
 *
 * ⚠️ `id` 是**售后单编号**（订单项上的 `afterSaleId`）—— 不是订单项编号、更不是订单编号。
 * ⚠️ 走 **DELETE**，参数在 **query** 上（后端是 `@RequestParam`）。
 *
 * 后端只在售后单处于「申请中 / 卖家同意 / 待卖家收货」时允许撤销；
 * 再往后（商家已收货待退款等）会拒，把它的文案透出来即可。
 * 撤销成功后**订单项的售后状态会回到未售后**，所以两个入口都要跟着重生。
 */
export function cancelAfterSale(id: number): Promise<boolean> {
  return del<boolean>('/trade/after-sale/cancel', { id })
}

/**
 * 「我的售后」列表（FR-011c）。
 *
 * ⚠️ 返回项里的 `status` 是**售后单的精确状态**（10/20/30/40/50/61/62/63）——
 * 这正是列表页能**准确**判断"哪些还能撤销"的依据，比订单详情页只有订单项的
 * 粗粒度状态强。判断函数用 `utils/afterSale` 的 `canCancelAfterSaleByStatus`。
 */
export function pageAfterSales(
  params: { pageNo?: number; pageSize?: number } = {},
): Promise<PageResult<AfterSaleListItem>> {
  return get<PageResult<AfterSaleListItem>>('/trade/after-sale/page', params)
}
