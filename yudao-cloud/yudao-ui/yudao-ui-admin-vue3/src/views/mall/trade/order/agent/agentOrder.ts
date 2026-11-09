import type { AgentCreateReq, AgentOrderItem } from '@/api/mall/trade/order'

/** 支付方式：两种真实扫码 + 对公转账 */
export type AgentPaymentMode = 'wx_native' | 'alipay_pc' | 'offline'

/** 商品行：在提交所需的 skuId/count 之外，带展示字段 */
export interface AgentOrderLine extends AgentOrderItem {
  spuId?: number
  spuName?: string
  picUrl?: string
  price?: number
  stock?: number
  properties?: string
}

export const DELIVERY_TYPE_EXPRESS = 1
export const DELIVERY_TYPE_VIRTUAL = 3

export function isOnlinePayment(mode?: AgentPaymentMode): boolean {
  return mode === 'wx_native' || mode === 'alipay_pc'
}

/** 轮询支付状态时的下一步：已支付 / 已关闭 / 继续等 */
export type AgentPayPollingAction = 'paid' | 'closed' | 'continue'

export function payPollingAction(status: {
  payStatus?: boolean
  orderStatus?: number
}): AgentPayPollingAction {
  if (status.payStatus) return 'paid'
  // 订单状态 0 = 待支付；其余（已取消/待发货等）都不应继续展示二维码
  if (status.orderStatus !== undefined && status.orderStatus !== 0) return 'closed'
  return 'continue'
}

export function paymentModeLabel(mode?: AgentPaymentMode): string {
  if (mode === 'wx_native') return '微信扫码'
  if (mode === 'alipay_pc') return '支付宝扫码'
  if (mode === 'offline') return '对公转账'
  return '未选择'
}

/**
 * 提交前的前端校验（只做「明知会被拒就别发」的减少；权威判定在服务端）
 */
export function validateAgentOrder(params: {
  userId?: number
  items: AgentOrderLine[]
  deliveryType?: number
  addressId?: number
  paymentMode?: AgentPaymentMode
}): string | null {
  if (!params.userId) return '请先选择客户'
  if (!params.items.length) return '请至少添加一件商品'
  if (params.items.some((item) => !item.count || item.count < 1)) return '商品数量必须大于 0'
  if (params.deliveryType === DELIVERY_TYPE_EXPRESS && !params.addressId) {
    return '请选择客户的收货地址'
  }
  if (!params.paymentMode) return '请选择支付方式'
  return null
}

/**
 * 构造创建请求：在线支付时**不传** `payChannelCode`（与 C 端「不传 = 在线支付」一致），
 * 对公转账传 `offline`；券/积分字段从不出现。
 */
export function buildAgentCreateReq(params: {
  userId: number
  items: AgentOrderLine[]
  addressId?: number
  userRemark?: string
  paymentMode: AgentPaymentMode
}): AgentCreateReq {
  return {
    userId: params.userId,
    items: params.items.map((item) => ({ skuId: item.skuId, count: item.count })),
    addressId: params.addressId,
    userRemark: params.userRemark || undefined,
    payChannelCode: params.paymentMode === 'offline' ? 'offline' : undefined
  }
}

/** 代下单人展示：昵称优先；账号不存在时用编号兜底 */
export function agentDisplayName(name?: string | null, id?: number | null): string {
  if (name) return name
  if (id) return `后台用户#${id}`
  return '—'
}
