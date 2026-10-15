import { InvoiceStatus, InvoiceType, type InvoiceStatusValue, type InvoiceTitleFields, type InvoiceTypeValue } from '@/types/invoice'

/**
 * 开票信息的**前端校验**（FR-155 / FR-156）。
 *
 * ⚠️ 这里的规则与后端 `InvoiceValidator` **必须一致**：前端只是提前拦住、少一次往返，
 * **权威始终在后端**（绕过界面的请求后端照样拒）。两处一旦不一致，客户看到的会是
 * "我按你要求的填了，却提交不了"。
 */

/** 票种选项 —— 顺序即界面顺序；`label` 与后端 `InvoiceTypeEnum.name` 逐字一致 */
export const INVOICE_TYPE_OPTIONS: Array<{ value: InvoiceTypeValue; label: string; hint: string }> = [
  { value: InvoiceType.NORMAL, label: '电子普通发票', hint: '填公司名称与纳税人识别号即可' },
  { value: InvoiceType.SPECIAL, label: '电子增值税专用发票', hint: '名称与税号必填；地址、电话、开户行选填' },
]

/**
 * 开票申请状态的中文名 —— 用词与后端枚举**逐字一致**（`待开票 / 已开票 / 已作废`）。
 *
 * ⚠️ 界面**不自行造词**（不写"开票中""处理中"这类同义词）：同一状态在本站出现两种叫法，
 * 客户找客服描述问题时就对不上了 —— 与本仓库订单状态的既有做法（`utils/orderStatus.ts`）一致。
 */
export function invoiceStatusName(status: InvoiceStatusValue): string {
  if (status === InvoiceStatus.WAITING) return '待开票'
  if (status === InvoiceStatus.ISSUED) return '已开票'
  if (status === InvoiceStatus.CANCELED) return '已作废'
  return '未知状态'
}

/** 15 位（老税号）/ 18 位（统一社会信用代码）/ 20 位，数字与大写字母 —— 与后端同一个规则 */
const TAX_NO_PATTERN = /^[0-9A-Z]{15}$|^[0-9A-Z]{18}$|^[0-9A-Z]{20}$/

export function isTaxNoValid(taxNo: string): boolean {
  return TAX_NO_PATTERN.test(taxNo)
}

/**
 * 按票种校验一张抬头（下单、补开两处共用）。
 *
 * @param form 允许**字段缺省**（`Partial`）：客户可能一个字都还没填，
 *             而"没填"与"填了空"在界面上是同一件事 —— 两种都要能判
 * @returns 通过返回 `null`，否则返回**可直接展示给客户**的一句话，并点名缺哪一项（FR-156）
 */
export function invoiceFieldError(form: Partial<InvoiceTitleFields>): string | null {
  // **必填只有两项：抬头名称 + 纳税人识别号**（2026-10-15 所有者口径，两种票一致）。
  // 地址 / 电话 / 开户行及账号一律选填 —— 专票也一样：客户手边未必有开户行账号，
  // 不该因此存不下抬头、或者提交不了开票申请。
  // ⚠️ **不看票种**：票种只决定"界面上显示不显示那三栏"，不决定必填。
  // 后端 `InvoiceValidator.validate` 同此口径（它另外还要判票种本身的合法性）。
  if (!(form.titleName ?? '').trim()) {
    return '请填写抬头名称'
  }
  const taxNo = (form.taxNo ?? '').trim()
  if (!taxNo) {
    return '请填写纳税人识别号'
  }
  if (!isTaxNoValid(taxNo)) {
    return '纳税人识别号格式不正确（应为 15 / 18 / 20 位数字或大写字母）'
  }
  return null
}
