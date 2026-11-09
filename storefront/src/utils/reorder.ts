/**
 * 「再次购买」带到结算页的商品条目。
 *
 * 订单列表拿着历史订单的 `skuId + count` 直接进结算（不经购物车，所有者 2026-11-04 拍板），
 * 这些值通过 URL query 传给 `/checkout`：`source=reorder&skuIds=100,200&counts=2,3`。
 *
 * 用两个平行数组而不是 JSON：URL 更短，也不会因为 `%5B` 这类编码在路由层出问题。
 * 解析在路由层做（`router/index.ts`），校验规则集中在这一个纯函数里，便于穷举。
 */
export interface ReorderItem {
  skuId: number
  count: number
}

/**
 * 解析 `skuIds` / `counts` 两个 query 参数。
 *
 * - 非法 / 缺失的 skuId 直接丢弃（`NaN`、0、负数、小数）
 * - 数量缺失或不是正整数时按 1 处理；真正的上下限（库存、限购）仍由后端结算裁决
 * - 两个数组按下标一一对应
 */
export function parseReorderItems(skuIdsRaw: unknown, countsRaw: unknown): ReorderItem[] {
  // ⚠️ **不要过滤空串再解析**：两个数组按下标配对，提前删元素会让后面的 count 错位，
  //    出现「SKU 200 用了 SKU 100 的数量」。空 / 非法值统一变成 0/NaN，由下面的
  //    skuId 校验整条丢弃，下标保持原样。
  const toNumbers = (raw: unknown): number[] =>
    String(raw ?? '')
      .split(',')
      .map(Number)

  const skuIds = toNumbers(skuIdsRaw)
  const counts = toNumbers(countsRaw)
  const items: ReorderItem[] = []
  skuIds.forEach((skuId, index) => {
    if (!Number.isInteger(skuId) || skuId <= 0) return
    const rawCount = counts[index]
    const count = Number.isInteger(rawCount) && rawCount > 0 ? rawCount : 1
    items.push({ skuId, count })
  })
  return items
}
