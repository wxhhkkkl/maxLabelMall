/**
 * 横幅文案的拆解（纯函数，便于穷举测试）。
 *
 * 后台那个 Banner 表单里**只有「描述」是多行输入**（`type="textarea"`）；
 * 「标题」是**单行**输入框 —— 运营在标题里敲不出回车。所以文案这样分工：
 *
 * | 设计的段落 | 来源 | 能多行吗 |
 * |---|---|---|
 * | 品牌行「赋签 \| MaxLabel」 | **前端固定** —— 站点自己的名字，页头页脚本来就写着 | — |
 * | 主标题 | `标题`（**就一行**，直接渲染，不拆） | 否 |
 * | 副标题 | `描述` 第 1 行 | 是 |
 * | 胶囊（如「软件开发中」） | `描述` 第 2 行 —— **没有第 2 行就不渲染胶囊** | 是 |
 *
 * ⚠️ 早先还支持"标题按换行分多行"，那是**为不可能发生的情况写的代码** ——
 * 在选「主标题就一行」时删掉了。将来若给标题也换成 textarea，再补回来。
 *
 * 约定确实隐晦（运营得知道"描述第二行会变成胶囊"），换来的是**零数据结构改动**、
 * 且内容仍全部由后台下发（FR-080）。所以这里对"运营写错行数"的行为定得很死：
 * 多余的行**忽略**、空行**跳过**，不报错也不悄悄拼进别的段落。
 */
export interface BannerCopy {
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

export function parseBannerCopy(banner: { memo?: string }): BannerCopy {
  const memoLines = linesOf(banner.memo)
  return {
    subtitle: memoLines[0] ?? '',
    // 只认前两行：第 3 行及以后忽略（写多了不报错，也不拼进胶囊）
    badge: memoLines[1] ?? '',
  }
}
