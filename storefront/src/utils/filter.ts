import type { FilterDimension } from '@/types/filter'

/**
 * 筛选项的**展示顺序**。
 *
 * 接口不承诺数组顺序，所以这里显式按 `sort` 排（FR-112）——
 * 顺序是运营定的，不按商品数、也不按任何运行时数据重排。
 */
export function sortDimensions(dimensions: FilterDimension[]): FilterDimension[] {
  return [...dimensions]
    .sort((a, b) => a.sort - b.sort)
    .map((dimension) => ({
      ...dimension,
      values: [...dimension.values].sort((a, b) => a.sort - b.sort),
    }))
}

/**
 * 切换分类时，只留下**在新分类下仍然适用**的选择（FR-103）。
 *
 * 维度是沿分类树向下继承的（Q10=B），所以同一个维度在兄弟分类间是**同一条**：
 * 在「蜡基」选了「宽度=110」，切到同样继承「宽度」的「混合基」时，这个选择该留着。
 * 但切到「打印机」这种跟「宽度」没关系的地方，它就必须被清掉 ——
 * 留着的话，顾客会带着一个前台根本无法解释的条件去查询。
 */
export function retainApplicableSelections(
  selectedValueIds: number[],
  dimensions: FilterDimension[]
): number[] {
  const available = new Set(
    dimensions.flatMap((dimension) => dimension.values.map((value) => value.valueId))
  )
  return selectedValueIds.filter((valueId) => available.has(valueId))
}
