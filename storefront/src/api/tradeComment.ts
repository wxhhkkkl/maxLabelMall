import { post } from '@/config/http'

/**
 * 写评价（`/app-api/trade/order/item/create-comment`）。
 *
 * **与 `api/comment.ts` 分开放**：那个读的是 product 模块的评价，这个写的是 **trade** 模块
 * 的订单项评价 —— 不是同一个域，混在一个文件里会让人以为它们是同一套接口。
 *
 * 后端**前置条件**（不满足会拒绝，错误码见数据模型的 §2.3）：
 * 订单项属于本人 · 订单属于本人 · **订单状态 = 已完成** · **订单整体尚未评价** ·
 * 该订单项未评价过。
 *
 * ⚠️ **提交成功 ≠ 立刻可见**：会员提交的评价默认**不可见**，须运营在后台点"显示"
 * 才出现在前台。所以提交成功的提示里必须说明这一点（FR-078），否则用户会重复提交。
 */
export interface CommentCreateReq {
  orderItemId: number
  /** 商品质量评分 1~5 */
  descriptionScores: number
  /** 服务态度评分 1~5 */
  benefitScores: number
  content: string
  picUrls: string[]
  anonymous: boolean
}

/** 返回新建评价的编号 */
export function createOrderItemComment(req: CommentCreateReq): Promise<number> {
  return post<number>('/trade/order/item/create-comment', req)
}
