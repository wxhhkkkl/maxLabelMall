import request from '@/config/axios'

/**
 * 商城统计（`/admin-api/statistics/**`，对应后端 `yudao-module-statistics`）。
 *
 * ⚠️ 这些接口都带 `@PreAuthorize("@ss.hasPermission('statistics:xxx:query')")`，
 * 当前登录账号需要有对应菜单权限（超级管理员天然具备）。
 */

/** 时间范围类型（后端 `TimeRangeTypeEnum`）：只决定**按天还是按月分组** */
export const TIME_RANGE_TYPE = {
  /** 按天分组 */
  DAY: 1,
  WEEK: 7,
  MONTH: 30,
  /** 按年统计时以月分组 */
  YEAR: 365
} as const

/** 会员终端统计 */
export interface MemberTerminalStatistics {
  /** 终端（后端 `TerminalEnum`：10 微信小程序 / 11 微信公众号 / 20 H5 / 31 手机 App）；可能为 null */
  terminal: number | null
  /** 会员数量 */
  userCount: number
}

/** 订单量趋势 */
export interface TradeOrderTrend {
  /** 日期，形如 2026-09-27（或按年时是月份） */
  date: string
  /** 订单数量 */
  orderPayCount: number
  /** 订单支付金额（单位：分） */
  orderPayPrice: number
}

/** 统计接口的统一返回：本期 value + 对照期 reference */
export interface DataComparison<T> {
  value: T | null
  reference: T | null
}

/** 按终端统计会员数量（实时，无参数） */
export const getMemberTerminalStatisticsList = () => {
  return request.get<MemberTerminalStatistics[]>({
    url: '/statistics/member/terminal-statistics-list'
  })
}

/**
 * 订单量趋势统计。
 *
 * ⚠️ 后端 `TradeOrderTrendReqVO.type` 标成 `requiredMode = REQUIRED`，但服务实现里
 * 真正用到的是 `beginTime`/`endTime`（`getBeginTime().minusDays(1)` 算对照期）——
 * **只传 type 会 NullPointerException**（实测「系统异常」）。所以这里必须把时间范围一并传上。
 */
export const getOrderCountTrend = (params: {
  type: number
  beginTime: string
  endTime: string
}) => {
  return request.get<DataComparison<TradeOrderTrend>[]>({
    url: '/statistics/trade/order-count-trend',
    params
  })
}
