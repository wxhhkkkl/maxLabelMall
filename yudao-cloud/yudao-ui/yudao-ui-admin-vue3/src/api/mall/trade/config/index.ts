import request from '@/config/axios'

export interface ConfigVO {
  id?: number | null
  afterSaleRefundReasons: string[]
  afterSaleReturnReasons: string[]
  deliveryExpressFreeEnabled: boolean
  deliveryExpressFreePrice: number
  deliveryPickUpEnabled: boolean
  brokerageEnabled: boolean
  brokerageEnabledCondition?: number
  brokerageBindMode?: number
  brokeragePosterUrls: string[]
  brokerageFirstPercent: number
  brokerageSecondPercent: number
  brokerageWithdrawMinPrice: number
  brokerageWithdrawFeePercent: number
  brokerageFrozenDays: number
  brokerageWithdrawTypes: number[]
  tencentLbsKey?: string

  // ========== 008 对公转账 ==========
  /** 是否启用「对公转账」——关掉后**结算页不再出现**，但已产生的待收款单照旧能确认/取消 */
  offlinePayEnabled: boolean
  /** 收款户名（**业务事实，由所有者填**，代码里不编造） */
  offlinePayAccountName?: string
  /** 收款账号 */
  offlinePayAccountNo?: string
  /** 开户行 */
  offlinePayBankName?: string
  /** 给客户的提示语（如「请在转账附言里写订单号」） */
  offlinePayTip?: string
}

// 查询交易中心配置详情
export const getTradeConfig = async () => {
  return await request.get<ConfigVO>({ url: `/trade/config/get` })
}

// 保存交易中心配置
export const saveTradeConfig = async (data: ConfigVO) => {
  return await request.put({ url: `/trade/config/save`, data })
}
