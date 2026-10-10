import { formatDateTime } from './time'

/**
 * 商品评价相关的**纯函数与常量**。
 *
 * 评价的"读"走 `api/comment.ts`、"写"走 `api/tradeComment.ts`（不是一个模块的接口，
 * 所以分开放）；这里只放与**展示和校验**有关、不依赖任何接口的纯逻辑 —— 纯函数才能穷举测试。
 */

/**
 * 详情页上评价内容的**折叠阈值**（FR-072）。
 *
 * 挑字符数而不挑行数，是因为**字符数在组件测试里能直接断言**，行数要测量渲染结果。
 * 它是**展示口径**，与写评价时的内容上限（1024，见 `validateCommentContent`）是两回事。
 */
export const COMMENT_FOLD_CHARS = 120

/** 该条评价是否需要折叠（只有详情页折叠，「全部评价」页不折叠） */
export function shouldFoldComment(content: string): boolean {
  return content.length > COMMENT_FOLD_CHARS
}

/** 评价时间 → 可读文本。**必须走这里**，直接插值会把 epoch 毫秒原样显示给用户 */
export function formatCommentTime(v: number | string): string {
  return formatDateTime(v)
}

/** 评分范围 1~5（后端**不校验**，见 contracts §3.2） */
export const COMMENT_SCORE_MIN = 1
export const COMMENT_SCORE_MAX = 5

/** 内容上限 —— 与数据库列宽 `varchar(1024)` 一致 */
export const COMMENT_CONTENT_MAX = 1024

/** 图片上限 —— 与后端 `@Size(max = 9)` 一致 */
export const COMMENT_PIC_MAX = 9

export interface CommentForm {
  /** 商品质量评分 1~5 */
  descriptionScores: number
  /** 服务态度评分 1~5 */
  benefitScores: number
  content: string
  picUrls: string[]
}

/**
 * 提交前校验（FR-075）。
 *
 * ⚠️ **这不是"提前给点反馈"，而是唯一的校验** —— 后端对评分范围与内容长度
 * **一个都不校验**，真正兜底的只有数据库列宽。不在这里拦住，用户收到的会是一条
 * 数据库层报错，而不是"评分请选 1~5 星"（见 contracts/app-api.md §3.2）。
 */
export function validateComment(form: CommentForm): { ok: boolean; message: string } {
  const inRange = (v: number) => Number.isInteger(v) && v >= COMMENT_SCORE_MIN && v <= COMMENT_SCORE_MAX

  if (!inRange(form.descriptionScores) || !inRange(form.benefitScores)) {
    return { ok: false, message: `评分请选择 ${COMMENT_SCORE_MIN}~${COMMENT_SCORE_MAX} 星` }
  }
  if (!form.content.trim()) {
    return { ok: false, message: '请填写评价内容' }
  }
  if (form.content.length > COMMENT_CONTENT_MAX) {
    return { ok: false, message: `评价内容不能超过 ${COMMENT_CONTENT_MAX} 个字符` }
  }
  if (form.picUrls.length > COMMENT_PIC_MAX) {
    return { ok: false, message: `最多上传 ${COMMENT_PIC_MAX} 张图片` }
  }
  return { ok: true, message: '' }
}
