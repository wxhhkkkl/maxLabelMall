import { describe, expect, it } from 'vitest'

import { parseBannerCopy } from './banner'

/**
 * 横幅文案的拆解口径（2026-10-10 与所有者约定）。
 *
 * 后台那个 Banner 表单里**只有「描述」是多行输入**（`type="textarea"`），
 * 「标题」是**单行**输入框 —— 运营在标题里敲不出回车。所以文案这样分工：
 *
 * | 设计的段落 | 来源 | 能不能多行 |
 * |---|---|---|
 * | 品牌行「赋签 \| MaxLabel」 | 前端固定（站点自己的名字） | — |
 * | 主标题 | `标题` | **不能**，就一行 |
 * | 副标题 | `描述` 第 1 行 | 可以 |
 * | 胶囊（如「软件开发中」） | `描述` 第 2 行 —— **没有第 2 行就不渲染胶囊** | 可以 |
 *
 * ⚠️ 早先的版本还支持"标题按换行分多行"，那是**为不可能发生的情况写的代码** ——
 * 已经在选「主标题就一行」时删掉了。将来若给标题也换成 textarea，再补回来。
 *
 * 约定确实隐晦（运营得知道"描述第二行会变成胶囊"），换来的是**零数据结构改动**、
 * 且内容仍全部由后台下发（FR-080）。所以拆解逻辑单独成纯函数、穷举测：
 * 运营写错行数时的行为必须**可预期**，不能时灵时不灵。
 */
describe('parseBannerCopy —— 描述拆成副标题 + 胶囊', () => {
  it('没有描述时两者都为空', () => {
    const r = parseBannerCopy({})
    expect(r.subtitle).toBe('')
    expect(r.badge).toBe('')
  })

  it('只有一行 → 只当副标题，**不渲染胶囊**', () => {
    const r = parseBannerCopy({ memo: '标签软件 · 打印设备 · 标签耗材' })
    expect(r.subtitle).toBe('标签软件 · 打印设备 · 标签耗材')
    expect(r.badge).toBe('')
  })

  it('两行 → 第一行副标题、第二行胶囊', () => {
    const r = parseBannerCopy({ memo: '标签软件 · 打印设备\nMaxLabel 软件开发中' })
    expect(r.subtitle).toBe('标签软件 · 打印设备')
    expect(r.badge).toBe('MaxLabel 软件开发中')
  })

  it('**超过两行只取前两行**（多写的行忽略，不报错也不拼进胶囊）', () => {
    const r = parseBannerCopy({ memo: '副标题\n胶囊\n多写的一行' })
    expect(r.subtitle).toBe('副标题')
    expect(r.badge).toBe('胶囊')
  })

  it('描述里的空行被跳过（写「副标题\\n\\n胶囊」也能拿到胶囊）', () => {
    const r = parseBannerCopy({ memo: '副标题\n\n胶囊' })
    expect(r.subtitle).toBe('副标题')
    expect(r.badge).toBe('胶囊')
  })

  it('首尾空格被裁掉', () => {
    const r = parseBannerCopy({ memo: '  副标题  \n  胶囊  ' })
    expect(r.subtitle).toBe('副标题')
    expect(r.badge).toBe('胶囊')
  })

  it('描述只有空白 → 两者都为空', () => {
    const r = parseBannerCopy({ memo: '   \n  ' })
    expect(r.subtitle).toBe('')
    expect(r.badge).toBe('')
  })
})
