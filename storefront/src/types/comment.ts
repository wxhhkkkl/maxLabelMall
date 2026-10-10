/**
 * 商品评价（`AppProductCommentRespVO`）。
 *
 * **只建模前台要展示的字段**，不把后端返回体照抄一份 —— 抄多了以后后端加字段就得跟着改，
 * 而多出来的字段前台根本不用。
 *
 * ⚠️ 关于匿名：后端返回里有 `anonymous` 字段，但**昵称打码已经在后端做完了** ——
 * 拿到的 `userNickname` 就是该展示的值。前端**不得**根据它再改写，
 * 也不得试图反向还原真实身份（FR-079）。
 */
export interface ProductComment {
  id: number
  /** 评价人昵称。匿名评价时后端已替换成「匿名用户」，前端原样展示 */
  userNickname: string
  userAvatar?: string
  /** 总评分（后端由下面两个维度算出） */
  scores: number
  /** 商品质量评分 1~5 */
  descriptionScores?: number
  /** 服务态度评分 1~5 */
  benefitScores?: number
  content: string
  /** 评价图片 */
  picUrls?: string[]
  /** 商家回复（有则展示，且要显示在同一条评价之内） */
  replyContent?: string
  /**
   * 评价时间 —— **epoch 毫秒或可读字符串**，一律过 `utils/time.ts` 的 `formatDateTime`。
   * 直接插值会把这串数字原样显示给用户（本项目踩过这个坑）。
   */
  createTime: number | string
}
