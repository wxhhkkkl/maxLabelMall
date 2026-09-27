import type { ProductSpu } from '@/types'

/**
 * 商品卡片角标 —— **由真实数据算出，不得写死文案**（FR-008a）。
 *
 * 能派生的只有两个：
 *   · 「热销」—— 当前列表内销量最高的前 HOT_LIMIT 个
 *   · 「新品」—— 当前列表内**编号最大**的若干（`id` 单调递增，作为创建顺序的近似）
 *
 * **永远不产出「旗舰」「订阅」**：后端 SPU 没有角标字段，也没有任何可推导依据
 * （早期设计稿有这四个角标，其中两个无法实现，已确认不展示）。
 *
 * 边界（FR-008b / FR-008c）：
 *   · 销量全为 0 时不产出「热销」，**不得把整页都标成热销**
 *   · 商品数少于名额时“有几件算几件”，不铺满
 *   · 同一商品不得同时挂两个角标 —— 冲突时**「热销」优先**
 *
 * 作用域：只作用于**传入的这一个列表分片**（通常是当前页），
 * 因此同一商品在不同页面的判定可以不同。
 */

export const HOT_LIMIT = 3
export const NEW_LIMIT = 2

export interface Badges {
  hot: number[]
  fresh: number[]
}

export function deriveBadges(list: ProductSpu[]): Badges {
  if (list.length === 0) return { hot: [], fresh: [] }

  // 「热销」：销量降序取前 N；相同销量按 id 升序，保证结果稳定。
  // 销量全为 0 时直接不产出 —— 否则整页都会被标成热销。
  const sellable = list.filter((s) => s.salesCount > 0)
  const hot = sellable
    .slice()
    .sort((a, b) => b.salesCount - a.salesCount || a.id - b.id)
    .slice(0, HOT_LIMIT)
    .map((s) => s.id)

  // 「新品」：编号最大（≈ 创建最晚）取前 N；已是热销的让位（热销优先）
  const fresh = list
    .slice()
    .sort((a, b) => b.id - a.id)
    .map((s) => s.id)
    .filter((id) => !hot.includes(id))
    .slice(0, NEW_LIMIT)

  return { hot, fresh }
}
