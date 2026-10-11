/**
 * 筛选标签的前端纯逻辑（特性 003 / US14）。
 *
 * 服务端**不返回**"剔除了几个"（见 contracts/admin-api.md §3.3：不为一个只有商品编辑页
 * 用的信息去改全局共用的返回结构）。所以那句话由这里**对比保存前后**算出来。
 */

/**
 * 算出保存过程中**被移除**的筛选值 id。
 *
 * 为什么会移除：维度是沿分类树向下继承的（Q10=B），商品可挂的值限定在
 * 「它所属分类自身 + 全部祖先分类」上（FR-095）。商品一改分类，原先挂的标签就可能
 * 掉出这个范围，服务端会**静默**清理掉它们（FR-097）——**不报错**。
 *
 * 正因为不报错，这句提示才有存在的必要：不说的话，运营会以为标签还在，
 * 直到顾客反馈"筛选里搜不到这件商品"。
 *
 * @param before 保存前该商品挂着的值 id
 * @param after  保存后后端返回的值 id。⚠️ **为 `null` / `undefined` 时返回空数组**
 *               —— 那表示"后端没返回这个字段"（旧版后端），此时我们无从判断，
 *               **不能把"不知道"当成"全被删了"**
 */
export function diffRemovedFilterValues(
  before?: number[] | null,
  after?: number[] | null
): number[] {
  if (!before?.length) {
    return []
  }
  if (!after) {
    // 后端没给结果 → 不报（容错，见上）
    return []
  }
  const remaining = new Set(after)
  return before.filter((id) => !remaining.has(id))
}
