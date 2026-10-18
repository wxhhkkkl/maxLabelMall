import { del, get, post, put } from '@/config/http'
import type { InvoiceApplyForm, InvoiceTitle, InvoiceTitleForm } from '@/types/invoice'

/**
 * 发票抬头簿与订单补开（`/app-api/trade/**`，见 006 contracts/app-api.md §1/§2）。
 *
 * ⚠️ 抬头簿的四个接口**一律按当前登录会员**过滤（后端用 `getLoginUserId()`），
 * 前端不传 `userId` —— 传了也没用，反而给人"可以查别人"的错觉。
 *
 * ⚠️ 下单时的开票信息**不在这里**：它是下单请求体里的 `invoice` 子对象
 * （与订单同一事务落库，见 `api/order.ts` 的 `createOrder`）。
 */

export function listInvoiceTitles(): Promise<InvoiceTitle[]> {
  return get<InvoiceTitle[]>('/trade/invoice-title/list')
}

export function createInvoiceTitle(form: InvoiceTitleForm): Promise<number> {
  return post<number>('/trade/invoice-title/create', form)
}

export function updateInvoiceTitle(id: number, form: InvoiceTitleForm): Promise<boolean> {
  return put<boolean>('/trade/invoice-title/update', { id, ...form })
}

export function deleteInvoiceTitle(id: number): Promise<boolean> {
  return del<boolean>('/trade/invoice-title/delete', { id })
}

/** 订单详情**补开**（US20）：订单已经存在，所以必须带 `orderId` */
export function createOrderInvoice(body: InvoiceApplyForm & { orderId: number }): Promise<number> {
  return post<number>('/trade/order-invoice/create', body)
}
