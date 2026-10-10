/**
 * 横幅（`AppBannerRespVO`）。运营在后台「营销中心 → 内容管理 → Banner」维护。
 *
 * ⚠️ 响应里**没有** `status`：后端那个接口只按 `position` 等值查询，**停用的也会返回**。
 * 所以前端无法、也不该做"停用即不展示"的判断（见 `contracts/app-api.md` §5.1）。
 */
export interface Banner {
  id: number
  /** 标题 —— 用来当图片的 `alt`，**不是**展示文案（横幅是图片型的） */
  title: string
  /** 跳转地址。**可为空** —— 为空时点击不得跳转、也不得报错（FR-083） */
  url?: string
  /** 横幅图片 */
  picUrl: string
}
