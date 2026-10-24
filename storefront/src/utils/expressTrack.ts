/**
 * 物流查询失败的站内文案（010 / US37）。
 *
 * 后端 `ErrorCodeConstants.EXPRESS_QUERY_NOT_SUPPORTED` 的值；前端**只认这一个**，
 * 其余一律走通用降级文案 —— 第三方原文绝不能漏给客户（FR-259）。
 */
export const EXPRESS_QUERY_NOT_SUPPORTED = 1_011_004_103

/** 物流地图：未配置发货地址/模板（010 追加） */
export const EXPRESS_MAP_NOT_CONFIGURED = 1_011_004_104
/** 物流地图：订单缺少收件地区（历史单 / 无 area_id） */
export const EXPRESS_MAP_NOT_AVAILABLE = 1_011_004_105

const GENERIC_TEXT = '暂时无法获取物流信息，请稍后重试'

/**
 * 把 catch 到的错误翻成客户看得懂的一句话。
 *
 * 只区分「快递公司暂不支持」和「其它（三方/网络/未知）」两类；
 * **绝不**把后端的 `msg` 或第三方原文透出去（FR-259）。
 */
export function trackErrorText(err: unknown): string {
  const code = (err as { code?: unknown })?.code
  return code === EXPRESS_QUERY_NOT_SUPPORTED ? '该快递公司暂不支持查询' : GENERIC_TEXT
}

/**
 * 物流地图失败的站内文案（010 追加）。
 *
 * 地图需要「发货地址 + 地图模板」；未配置/历史单没有地区时要说清楚，
 * 不能统一成「稍后重试」——那会让人一直重试一个永远不会成功的操作。
 */
export function mapErrorText(err: unknown): string {
  const code = (err as { code?: unknown })?.code
  if (code === EXPRESS_MAP_NOT_CONFIGURED) return '物流地图暂未配置，请联系管理员'
  if (code === EXPRESS_MAP_NOT_AVAILABLE) return '该订单暂无法生成物流地图'
  if (code === EXPRESS_QUERY_NOT_SUPPORTED) return '该快递公司暂不支持查询'
  return '暂时无法获取物流地图，请稍后重试'
}
