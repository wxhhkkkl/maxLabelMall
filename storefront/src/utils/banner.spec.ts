import { describe, expect, it } from 'vitest'

import { parseBannerCopy } from './banner'

/**
 * 横幅文案的拆解口径（2026-10-10 与所有者约定）。
 *
 * 后台那个 Banner 表单只有**两个**文本字段能填（`标题` / `描述`），而设计稿的横幅有
 * 4 段文案。所以约定是**按换行拆**：
 *
 * | 设计的段落 | 来自 |
 * |---|---|
 * | 品牌行「赋签 \| MaxLabel」 | 前端固定（站点自己的名字，页头页脚本来就写着） |
 * | 主标题（可两行） | `标题`，按换行分 |
 * | 副标题 | `描述` 第 1 行 |
 * | 胶囊（如「软件开发中」） | `描述` 第 2 行 —— **没有第 2 行就不渲染胶囊** |
 *
 * 这个约定确实隐晦（运营得知道"第二行会变成胶囊"），但换来的是**零数据结构改动**、
 * 且内容仍然全部由后台下发（FR-080）。所以拆解逻辑单独成纯函数、穷举测：
 * 运营写错行数时的行为必须**可预期**，不能时灵时不灵。
 */
describe('parseBannerCopy —— 标题按换行分多行', () => {
  it('单行标题就是一行', () => {
    expect(parseBannerCopy({ title: '从设计到打印' }).titleLines).toEqual(['从设计到打印'])
  })

  it('两行标题分成两行（设计稿的主标题就是两行）', () => {
    const r = parseBannerCopy({ title: '从设计到打印\n每一步，都有好搭档' })
    expect(r.titleLines).toEqual(['从设计到打印', '每一步，都有好搭档'])
  })

  it('空行与首尾空格被丢掉 —— 运营手滑多敲几个回车不该多出一行空的', () => {
    const r = parseBannerCopy({ title: '\n  从设计到打印 \n\n 每一步 \n' })
    expect(r.titleLines).toEqual(['从设计到打印', '每一步'])
  })

  it('标题为空时给空数组（调用方据此不渲染标题块）', () => {
    expect(parseBannerCopy({}).titleLines).toEqual([])
    expect(parseBannerCopy({ title: '   ' }).titleLines).toEqual([])
  })
})

describe('parseBannerCopy —— 描述拆成副标题 + 胶囊', () => {
  it('没有描述时两者都为空', () => {
    const r = parseBannerCopy({ title: 'x' })
    expect(r.subtitle).toBe('')
    expect(r.badge).toBe('')
  })

  it('只有一行 → 只当副标题，**不渲染胶囊**', () => {
    const r = parseBannerCopy({ title: 'x', memo: '标签软件 · 打印设备 · 标签耗材' })
    expect(r.subtitle).toBe('标签软件 · 打印设备 · 标签耗材')
    expect(r.badge).toBe('')
  })

  it('两行 → 第一行副标题、第二行胶囊', () => {
    const r = parseBannerCopy({ title: 'x', memo: '标签软件 · 打印设备\nMaxLabel 软件开发中' })
    expect(r.subtitle).toBe('标签软件 · 打印设备')
    expect(r.badge).toBe('MaxLabel 软件开发中')
  })

  it('**超过两行只取前两行**（多写的行忽略，不报错也不拼进胶囊）', () => {
    const r = parseBannerCopy({ title: 'x', memo: '副标题\n胶囊\n多写的一行' })
    expect(r.subtitle).toBe('副标题')
    expect(r.badge).toBe('胶囊')
  })

  it('描述里的空行被跳过（写「副标题\\n\\n胶囊」也能拿到胶囊）', () => {
    const r = parseBannerCopy({ title: 'x', memo: '副标题\n\n胶囊' })
    expect(r.subtitle).toBe('副标题')
    expect(r.badge).toBe('胶囊')
  })

  it('首尾空格被裁掉', () => {
    const r = parseBannerCopy({ title: 'x', memo: '  副标题  \n  胶囊  ' })
    expect(r.subtitle).toBe('副标题')
    expect(r.badge).toBe('胶囊')
  })

  it('描述只有空白 → 两者都为空', () => {
    const r = parseBannerCopy({ title: 'x', memo: '   \n  ' })
    expect(r.subtitle).toBe('')
    expect(r.badge).toBe('')
  })
})
