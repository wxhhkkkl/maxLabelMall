/**
 * 开票申请的状态映射与两个判定（006 管理端）
 *
 * ⚠️ 取值**必须与后端 `TradeOrderInvoiceStatusEnum` 逐字一致**：前端算错一位，
 * 筛选下拉就会筛出空列表，而且**不会报错**。
 *
 * 显示用的票种名 / 状态名**由后端返回**（`typeName` / `statusName`），这里只提供
 * **筛选下拉的选项**与两个判定 —— 不在前端再维护一份"状态 → 文案"的映射。
 */

export const INVOICE_STATUS = {
  WAITING: 0,
  ISSUED: 10,
  CANCELED: 20,
} as const

/** 筛选下拉的选项（顺序即展示顺序） */
export const INVOICE_STATUS_OPTIONS = [
  { value: INVOICE_STATUS.WAITING, label: '待开票' },
  { value: INVOICE_STATUS.ISSUED, label: '已开票' },
  { value: INVOICE_STATUS.CANCELED, label: '已作废' },
] as const

/**
 * 该申请现在能不能开具
 *
 * 已作废的不行（Q30）；**已开票的可以** —— 那是「重新上传」（开错时的补救，Q31）。
 * 与后端 `TradeOrderInvoiceService#issueInvoice` 的校验一致。
 */
export function canIssue(status?: number | null): boolean {
  return status !== INVOICE_STATUS.CANCELED
}

/**
 * 订单是否退过款
 *
 * 申请的 `amount` 是**提交那一刻**的实付快照，部分退款**不会**改它（Q33）——
 * 于是票面金额可能高于订单净额。列表把订单当前的实付 / 已退一起显示出来，
 * 运营开具前才看得见这个差异。
 */
export function hasRefund(invoice: { orderRefundPrice?: number | null }): boolean {
  return (invoice.orderRefundPrice ?? 0) > 0
}

/**
 * 发票文件在界面上显示的**短名**：只留时间戳。
 *
 * 为什么需要它：`UploadFile` 默认取 URL 的最后一段当文件名，而线上真实形态是
 * 「微信图片_20261012143256_109_114.png」被 URL 编码后的
 * `%E5%BE%AE%E4%BF%A1%E5%9B%BE%E7%89%87_20261012143256_109_114.png` ——
 * 又长又看不懂，运营一眼分不出是不是换过了。
 *
 * 规则：取文件名里的 14 位时间戳（`20261012143256`）；没有就退回**解码后**的原名。
 */
export function invoiceFileName(url?: string | null): string {
  if (!url) {
    return ''
  }
  // 去掉 query（预签名 URL 常见），再取最后一段
  const last = url.split('?')[0].split('/').pop() ?? ''
  let decoded = last
  try {
    decoded = decodeURIComponent(last)
  } catch {
    // 少量畸形编码（比如单独的 %）会让它抛错 —— 解不开就用原样，别让"显示个名字"把页面带崩
  }
  const timestamp = decoded.match(/\d{14}/)
  return timestamp ? timestamp[0] : decoded
}
