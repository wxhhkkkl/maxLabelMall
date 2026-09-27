import { get } from '@/config/http'
import type { Category, CategoryNode } from '@/types'

/**
 * 商品分类（`/app-api/product/category/list`）。
 *
 * ⚠️ 后端**不返回**各分类的商品数量 —— 侧栏不展示计数（FR-002a），
 * 列表头部的「共 N 件商品」取自分页结果的 total。
 */
export function listCategories(): Promise<Category[]> {
  return get<Category[]>('/product/category/list')
}

/**
 * 把扁平列表按 `parentId` 组装成树。根节点为 `parentId` 不在列表中的那些
 * （后端通常用 0 表示根）。顺序保持后端返回的顺序。
 */
export function buildCategoryTree(list: Category[]): CategoryNode[] {
  const ids = new Set(list.map((c) => c.id))
  const nodes = new Map<number, CategoryNode>()
  for (const c of list) nodes.set(c.id, { ...c, children: [] })

  const roots: CategoryNode[] = []
  for (const c of list) {
    const node = nodes.get(c.id)
    if (!node) continue
    const parent = c.parentId && ids.has(c.parentId) ? nodes.get(c.parentId) : undefined
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}
