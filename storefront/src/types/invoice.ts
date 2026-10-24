/**
 * 票种（发票 / 收据）—— 与后端 `InvoiceTypeEnum` **逐字对齐**，不得自定义命名。
 *
 * 全部是电子票，不提供纸质发票、也不涉及邮寄。
 */
export const InvoiceType = {
  /** 电子普通发票。企业与个人都能开 */
  NORMAL: 1,
  /** 电子增值税专用发票。**只能开给企业**（Q38）——个人抬头选不了它 */
  SPECIAL: 2,
  /** 收据（Q61）：不是税票，**只要抬头名称**；企业与个人都能开 */
  RECEIPT: 3,
} as const

export type InvoiceTypeValue = (typeof InvoiceType)[keyof typeof InvoiceType]

/** 票种的中文名（与后端枚举的 name 一致，用于展示） */
export const INVOICE_TYPE_NAME: Record<InvoiceTypeValue, string> = {
  [InvoiceType.NORMAL]: '电子普通发票',
  [InvoiceType.SPECIAL]: '电子增值税专用发票',
  [InvoiceType.RECEIPT]: '收据',
}

/**
 * 发票**抬头类型** —— 开给公司还是开给个人。与后端 `InvoiceTitleKindEnum` **逐字对齐**（006 Q38）。
 *
 * 两者的必填不同：**企业**要「名称 + 税号」，**个人只要名称**（个人本来就没有纳税人识别号）。
 */
export const InvoiceTitleKind = {
  /** 企业：抬头名称 + 纳税人识别号都必填 */
  COMPANY: 1,
  /** 个人：**只要名称**；税号选填（可填身份证号），且**只能开普票** */
  PERSONAL: 2,
} as const

export type InvoiceTitleKindValue = (typeof InvoiceTitleKind)[keyof typeof InvoiceTitleKind]

/** 抬头类型的中文名（与后端枚举的 name 一致，用于展示） */
export const INVOICE_TITLE_KIND_NAME: Record<InvoiceTitleKindValue, string> = {
  [InvoiceTitleKind.COMPANY]: '企业',
  [InvoiceTitleKind.PERSONAL]: '个人',
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

/**
 * 抬头的字段 —— 提交（下单 / 补开 / 抬头簿保存）与展示共用。
 *
 * ⚠️ **抬头类型在里面**（Q38）：它与票种一样，是"这一份抬头怎么填"的一部分，三处共用
 * —— 后端同名基类 `AppInvoiceApplyBaseVO` 也是这个形状（票种 + 抬头类型 + 五件套）。
 */
export interface InvoiceTitleFields {
  titleKind: InvoiceTitleKindValue
  titleName: string
  taxNo?: string | null
  titleAddress?: string | null
  titlePhone?: string | null
  bankName?: string | null
  /** 接收发票 / 收据的邮箱（选填；Q62/Q63：申请与抬头簿都存） */
  email?: string | null
}

/**
 * 发票抬头簿里的一条（会员可维护多条）。
 *
 * ⚠️ **带票种**（Q36，2026-10-15 所有者要求"普票与专票分开管理"）：
 * 一条抬头只服务一种票。（此前是"不绑票种、一条两用"，已推翻。）
 *
 * ⚠️ **也带抬头类型**（Q38）：企业要「名称 + 税号」，个人只要名称 —— 所以个人抬头
 * 只会出现在普票那一段里（个人开不了专票）。
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
  /** 抬头类型的中文名（由后端枚举给出，与 `typeName` 同一处派生） */
  titleKindName: string
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
