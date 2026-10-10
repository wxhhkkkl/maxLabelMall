/**
 * 支付单状态（后端 `PayOrderStatusEnum`）。
 *
 * **只有 `WAITING` 需要继续轮询** —— 其余都是终态。注意没有单独的「已取消」：
 * 取消、过期、渠道报错在后端一律落成 `CLOSED`。
 */
export const PayOrderStatus = {
  WAITING: 0,
  SUCCESS: 10,
  REFUND: 20,
  CLOSED: 30,
} as const

export type PayOrderStatusValue = (typeof PayOrderStatus)[keyof typeof PayOrderStatus]

/** 提交支付（POST /pay/order/submit）。模拟通道下「提交即成功」。 */
export interface PaySubmitReq {
  /** 下单返回的 payOrderId */
  id: number
  channelCode: string
  displayMode?: string
  returnUrl?: string
  channelExtras?: Record<string, string>
}

/**
 * 提交支付的响应（`PayOrderSubmitRespVO`）。
 *
 * 前端**不用它判断交易订单是否已付款** —— 它只说明「支付单已提交给渠道」，
 * 交易订单的状态由后端异步回调推进（FR-039）。
 */
export interface PayOrderSubmitResp {
  /** 支付单状态，参见 PayOrderStatusEnum */
  status: number
  displayMode: string
  displayContent: string
}

/** 支付单（GET /pay/order/get） */
export interface PayOrder {
  id: number
  no: string
  status: number
  price: number
  channelCode: string
  payTime?: string
  /** 所属支付应用编号 —— 界面用它查「这个应用启用了哪些渠道」，避免前端记死应用编号 */
  appId: number
}
