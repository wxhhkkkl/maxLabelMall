import { InvoiceStatus, InvoiceTitleKind, InvoiceType, type InvoiceStatusValue, type InvoiceTitleFields, type InvoiceTitleKindValue, type InvoiceTypeValue } from '@/types/invoice'

/**
 * 开票信息的**前端校验**（FR-155 / FR-156）。
 *
 * ⚠️ 这里的规则与后端 `InvoiceValidator` **必须一致**：前端只是提前拦住、少一次往返，
 * **权威始终在后端**（绕过界面的请求后端照样拒）。两处一旦不一致，客户看到的会是
 * "我按你要求的填了，却提交不了"。
 */

/** 票种选项 —— 顺序即界面顺序；`label` 与后端 `InvoiceTypeEnum.name` 逐字一致 */
export const INVOICE_TYPE_OPTIONS: Array<{ value: InvoiceTypeValue; label: string; hint: string }> = [
  { value: InvoiceType.NORMAL, label: '电子普通发票', hint: '企业与个人都能开；个人只需填姓名' },
  { value: InvoiceType.SPECIAL, label: '电子增值税专用发票', hint: '只能开给企业；名称与税号必填，地址、电话、开户行选填' },
]

/**
 * 抬头类型选项 —— 顺序即界面顺序；`label` 与后端 `InvoiceTitleKindEnum.name` 逐字一致。
 *
 * ⚠️ 「个人」在**票种选了专票时不可选**（专票按税法只能开给企业）——
 * 那条约束在 `InvoiceFieldsForm` 上，与"个人时专票不可选"是同一条不变量。
 */
export const INVOICE_TITLE_KIND_OPTIONS: Array<{ value: InvoiceTitleKindValue; label: string; hint: string }> = [
  { value: InvoiceTitleKind.COMPANY, label: '企业', hint: '名称与纳税人识别号都要填' },
  { value: InvoiceTitleKind.PERSONAL, label: '个人', hint: '只需填姓名；个人开不了增值税专用发票' },
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
 * 校验一张抬头（下单、补开、抬头簿三处共用）。
 *
 * **必填随「抬头类型」而变**（Q38）：
 * · **企业** → 抬头名称 + 纳税人识别号（两种票一致，Q37）
 * · **个人** → **只要抬头名称**（个人本来就没有纳税人识别号，且只开普票）
 * 地址 / 电话 / 开户行及账号一律选填。
 *
 * ⚠️ 个人抬头的税号是**选填、不是禁止** —— 自然人可以用身份证号当纳税人识别号。
 *
 * @param form 允许**字段缺省**（`Partial`）：客户可能一个字都还没填，
 *             而"没填"与"填了空"在界面上是同一件事 —— 两种都要能判
 * @returns 通过返回 `null`，否则返回**可直接展示给客户**的一句话，并点名缺哪一项（FR-156）
 */
export function invoiceFieldError(form: Partial<InvoiceTitleFields> & { type?: InvoiceTypeValue }): string | null {
  // 抬头类型决定下面哪些必填 —— 没选就无从判起（后端 `InvoiceValidator` 先判它）
  if (form.titleKind !== InvoiceTitleKind.COMPANY && form.titleKind !== InvoiceTitleKind.PERSONAL) {
    return '请选择抬头类型'
  }
  if (!(form.titleName ?? '').trim()) {
    return '请填写抬头名称'
  }
  // 个人只能开普票：增值税专用发票按税法只能开给一般纳税人企业，收下来财务也开不出
  if (form.titleKind === InvoiceTitleKind.PERSONAL && form.type === InvoiceType.SPECIAL) {
    return '增值税专用发票只能开给企业，请改选电子普通发票'
  }
  const taxNo = (form.taxNo ?? '').trim()
  if (form.titleKind === InvoiceTitleKind.COMPANY && !taxNo) {
    return '请填写纳税人识别号'
  }
  // 个人抬头的税号可以不填 —— 但**填了就要合法**（选填不等于不校验）
  if (taxNo && !isTaxNoValid(taxNo)) {
    return '纳税人识别号格式不正确（应为 15 / 18 / 20 位数字或大写字母）'
  }
  return null
}
