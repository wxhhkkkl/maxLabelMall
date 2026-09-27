/** 商品分类（AppCategoryRespVO）。**后端不返回商品数量**（FR-002a）。 */
export interface Category {
  id: number
  parentId: number
  name: string
  picUrl: string
}

/** 前端按 parentId 组装出的分类树 */
export interface CategoryNode extends Category {
  children: CategoryNode[]
}
