import { post } from '@/config/http'
import type { AfterSaleCreateReq } from '@/types'

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
