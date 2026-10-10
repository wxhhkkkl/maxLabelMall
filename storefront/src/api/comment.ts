import { get } from '@/config/http'
import type { PageResult, ProductComment } from '@/types'

/**
 * 商品评价读取（`/app-api/product/comment/page`）。
 *
 * 三条契约要点（见 `contracts/app-api.md` §2.1）：
 *
 * 1. **`type` 是必传参数**，后端没有默认值 —— 本期只做"全部"，所以固定传 `0`。
 * 2. 后端**只返回可见的评价**，被隐藏的不在返回里。**前端不再过滤一次** ——
 *    加一层过滤等于把"什么是可见的"这条业务规则的真相方从前端复制一份。
 * 3. ⚠️ **返回顺序不确定**：后端该接口**没有 ORDER BY**（已查证）。
 *    所以**不要**按"第一页就是最新的"来写文案 —— 详情页用中性的「用户评价」。
 */

/** 评价筛选类型。本期只用 `ALL`，其余三个留作以后做筛选 tab */
export const COMMENT_TYPE = {
  ALL: 0,
  GOOD: 1,
  MEDIOCRE: 2,
  NEGATIVE: 3,
} as const

export function pageComments(params: {
  spuId: number
  type?: number
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<ProductComment>> {
  return get<PageResult<ProductComment>>('/product/comment/page', {
    type: COMMENT_TYPE.ALL,
    pageNo: 1,
    pageSize: 10,
    ...params,
  })
}
