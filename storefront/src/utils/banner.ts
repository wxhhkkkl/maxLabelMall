/**
 * 横幅文案的拆解（纯函数，便于穷举测试）。
 *
 * 后台那个 Banner 表单能填的**文本字段只有两个**（`标题` / `描述`），而设计稿的横幅有
 * 4 段文案，所以约定**按换行拆**：
 *
 * | 设计的段落 | 来源 |
 * |---|---|
 * | 品牌行「赋签 \| MaxLabel」 | **前端固定** —— 站点自己的名字，页头页脚本来就写着 |
 * | 主标题（可两行） | `标题`，按换行分 |
 * | 副标题 | `描述` 第 1 行 |
 * | 胶囊（如「软件开发中」） | `描述` 第 2 行 —— **没有第 2 行就不渲染胶囊** |
 *
 * 约定确实隐晦，换来的是**零数据结构改动**、且内容仍全部由后台下发（FR-080）。
 * 所以这里对"运营写错行数"的行为定得很死：多余的行**忽略**、空行**跳过**，
 * 不报错也不悄悄拼进别的段落。
 */
export interface BannerCopy {
  /** 主标题，已按换行分成多行（空行已剔除） */
  titleLines: string[]
  /** 副标题（`描述` 第 1 行）；没有则空串 */
  subtitle: string
  /** 胶囊文案（`描述` 第 2 行）；没有则空串 —— 调用方据此不渲染胶囊 */
  badge: string
}

/** 按换行拆，裁掉每行首尾空白，丢掉空行 */
function linesOf(text?: string): string[] {
  return (text ?? '')
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
}

export function parseBannerCopy(banner: { title?: string; memo?: string }): BannerCopy {
  const memoLines = linesOf(banner.memo)
  return {
    titleLines: linesOf(banner.title),
    subtitle: memoLines[0] ?? '',
    // 只认前两行：第 3 行及以后忽略（写多了不报错，也不拼进胶囊）
    badge: memoLines[1] ?? '',
  }
}
