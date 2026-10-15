/**
 * 发票类型（票种）—— 与后端 `InvoiceTypeEnum` **逐字对齐**，不得自定义命名（006 Q27）。
 *
 * 两者**都是电子票**：本特性不提供纸质发票、也不涉及邮寄。
 */
export const InvoiceType = {
  /** 电子普通发票。只要求抬头名称，税号选填 */
  NORMAL: 1,
  /** 电子增值税专用发票。抬头名称、税号、地址、电话、开户行及账号全必填 */
  SPECIAL: 2,
} as const

export type InvoiceTypeValue = (typeof InvoiceType)[keyof typeof InvoiceType]

/** 票种的中文名（与后端枚举的 name 一致，用于展示） */
export const INVOICE_TYPE_NAME: Record<InvoiceTypeValue, string> = {
  [InvoiceType.NORMAL]: '电子普通发票',
  [InvoiceType.SPECIAL]: '电子增值税专用发票',
}

/**
 * 开票申请状态 —— 与后端 `TradeOrderInvoiceStatusEnum` 一一对应（006 data-model §1.1）。
 *
 * ⚠️ **没有"待支付"这一档**：申请在下单时就存在，"是否已支付"由**订单**裁决。
 * 所以「待开票」的申请可能挂在一笔还没付款的订单上 —— 界面要看成订单状态再说。
 */
export const InvoiceStatus = {
  WAITING: 0,
  ISSUED: 10,
  CANCELED: 20,
} as const

export type InvoiceStatusValue = (typeof InvoiceStatus)[keyof typeof InvoiceStatus]

/** 抬头五件套 —— 提交（下单 / 补开 / 抬头簿保存）与展示共用 */
export interface InvoiceTitleFields {
  titleName: string
  taxNo?: string | null
  titleAddress?: string | null
  titlePhone?: string | null
  bankName?: string | null
}

/**
 * 发票抬头簿里的一条（会员可维护多条）。
 *
 * ⚠️ **带票种**（Q36，2026-10-15 所有者要求"普票与专票分开管理"）：
 * 一条抬头只服务一种票 —— 普票抬头只要名称，专票抬头五项全填。
 * （此前是"不绑票种、一条两用"，已推翻。）
 */
export interface InvoiceTitle extends InvoiceTitleFields {
  id: number
  type: InvoiceTypeValue
  defaultStatus: boolean
}

/** 抬头簿的新增 / 编辑提交体（无 id） */
export type InvoiceTitleForm = Omit<InvoiceTitle, 'id'>

/**
 * 订单详情响应上的 `invoice` 对象。**没有申请时为 `null`**（不是空对象）。
 *
 * ⚠️ `createTime` / `issueTime` 是 **epoch 毫秒数**，渲染必须过 `utils/time.ts`。
 */
export interface OrderInvoice extends InvoiceTitleFields {
  status: InvoiceStatusValue
  type: InvoiceTypeValue
  typeName: string
  /** 开票金额，单位：分。= 申请时的订单实付快照 */
  amount: number
  /** 已开票才有值；链接**公网可读**（spec 已记录为显式接受的风险） */
  fileUrl: string | null
  issueTime: number | null
  createTime: number
}

/** 开票申请提交体：下单时的 `invoice` 子对象，与订单详情补开的 body（多一个 orderId） */
export interface InvoiceApplyForm extends InvoiceTitleFields {
  type: InvoiceTypeValue
}
