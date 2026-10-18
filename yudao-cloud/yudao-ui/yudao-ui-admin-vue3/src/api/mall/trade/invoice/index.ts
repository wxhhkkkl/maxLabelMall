import request from '@/config/axios'
import type { PageParam, PageResult } from '@/api/common'

/**
 * 开票申请（006 管理端）
 *
 * 契约见 `specs/006-order-invoice/contracts/admin-api.md`：
 * · 两个接口都要权限点（`trade:order-invoice:query` / `:issue`），
 *   而权限点必须在 `system_menu` 里（否则连超管都看不到按钮，004 踩过）
 */
export interface TradeOrderInvoiceVO {
  id?: number // 申请编号
  orderId?: number // 订单编号
  orderNo?: string // 订单号
  userId?: number // 会员编号
  userNickname?: string // 会员昵称（后端回填）
  type?: number // 发票类型：1 电子普通发票，2 电子增值税专用发票
  typeName?: string // 发票类型名（后端给，前端不自行命名）
  titleKind?: number // 抬头类型：1 企业，2 个人（Q38）
  titleKindName?: string // 抬头类型名（后端给）
  status?: number // 状态：0 待开票，10 已开票，20 已作废
  statusName?: string // 状态名（后端给）
  amount?: number // 开票金额（申请时的订单实付快照），单位：分
  orderPayPrice?: number // 订单**当前**实付，单位：分
  orderRefundPrice?: number // 订单**当前**已退，单位：分
  titleName?: string // 抬头名称
  taxNo?: string // 纳税人识别号
  titleAddress?: string // 注册地址
  titlePhone?: string // 注册电话
  bankName?: string // 开户行及账号
  invoiceFileUrl?: string // 发票文件地址（已开票才有）
  issueTime?: Date // 开票时间
  issueUserId?: number // 开具人
  cancelTime?: Date // 作废时间
  createTime?: Date // 申请时间
}

/** 获得开票申请分页（待开票列表只含已支付订单的申请） */
export const getInvoicePage = async (params: PageParam & { status?: number; orderNo?: string }) => {
  return await request.get<PageResult<TradeOrderInvoiceVO[]>>({ url: '/trade/order-invoice/page', params })
}

/**
 * 开具发票：把已开好的发票文件挂到申请上
 *
 * ⚠️ 文件先传到既有文件服务拿到 URL，本接口只收 URL。
 * 重新传一次就是**覆盖**（只留最新一份，Q31）。
 */
export const issueInvoice = async (id: number, fileUrl: string) => {
  return await request.put({ url: '/trade/order-invoice/issue', params: { id, fileUrl } })
}
