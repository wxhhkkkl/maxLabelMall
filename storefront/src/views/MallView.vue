<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { BANNER_POSITION, listBanners } from '@/api/banner'
import { listFilterDimensions } from '@/api/filter'
import type { FilterDimension } from '@/types/filter'
import { retainApplicableSelections, sortDimensions } from '@/utils/filter'
import MallBanner from '@/components/MallBanner.vue'
import ProductFilterBar from '@/components/ProductFilterBar.vue'
import { buildCategoryTree, listCategories } from '@/api/category'
import { pageProducts, SORT_FIELD } from '@/api/product'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import Pagination from '@/components/Pagination.vue'
import ProductCard from '@/components/ProductCard.vue'
import MallHome from '@/components/MallHome.vue'
import type { Banner, Category, CategoryNode, ProductSpu, ProductSortField } from '@/types'
import { deriveBadges } from '@/utils/badge'
import '@/styles/mall.css'

// 分类、搜索、排序及分页均由后端处理；展示方式只改变当前商品的布局。

const PAGE_SIZE = 12
const route = useRoute()
const router = useRouter()
const initialCategoryId = Number(route.query.category)

const products = ref<ProductSpu[]>([])
const total = ref(0)
const pageNo = ref(1)
const keyword = ref('')
const searchInput = ref('')
const categoryId = ref<number | undefined>(Number.isSafeInteger(initialCategoryId) && initialCategoryId > 0 ? initialCategoryId : undefined)
const sortKey = ref<'default' | 'sales' | 'priceAsc' | 'priceDesc'>('default')
const viewMode = ref<'grid' | 'list'>('grid')
const categoriesOpen = ref(false)
const categoryError = ref(false)
const browsingAll = ref(route.query.view === 'all')
const categoriesLoading = ref(true)
// 页面布局由选购状态决定，不能随商品请求的 loading/error 来回切换。
const showDepartments = computed(() => !categoryError.value && !browsingAll.value && categoryId.value === undefined && !keyword.value && sortKey.value === 'default' && viewMode.value === 'grid' && !filterValueIds.value.length)
const selectedCategory = computed(() => flatCategories.value.find(c => c.id === categoryId.value))
const categoryName = (id: number) => flatCategories.value.find(c => c.id === id)?.name ?? ''
const tree = ref<CategoryNode[]>([])
const filterDimensions = ref<FilterDimension[]>([])
const filterValueIds = ref<number[]>([])
const filterLoading = ref(false)
const filterError = ref(false)
const flatCategories = ref<Category[]>([])
const activeRoot = computed(() => {
  const selected = selectedCategory.value
  return selected && flatCategories.value.find(c => c.id === (selected.parentId || selected.id))
})
const childCategories = computed(() => activeRoot.value
  ? flatCategories.value.filter(c => c.parentId === activeRoot.value!.id)
  : [])
const isParentCategory = computed(() => childCategories.value.length > 0 && activeRoot.value?.id === categoryId.value)
const loading = ref(true)
const error = ref(false)

const badges = computed(() => deriveBadges(products.value))
function badgeOf(id: number): 'hot' | 'fresh' | null {
  if (badges.value.hot.includes(id)) return 'hot'
  if (badges.value.fresh.includes(id)) return 'fresh'
  return null
}

const SORT_CHIPS = [
  { key: 'default', label: '综合排序' },
  { key: 'sales', label: '销量优先' },
  { key: 'priceAsc', label: '价格从低到高' },
  { key: 'priceDesc', label: '价格从高到低' },
] as const

/** 综合排序不传 sortField，由后端默认排序。 */
function sortParams(): { sortField?: ProductSortField; sortAsc?: boolean } {
  if (sortKey.value === 'sales') return { sortField: SORT_FIELD.sales, sortAsc: false }
  if (sortKey.value === 'priceAsc') return { sortField: SORT_FIELD.price, sortAsc: true }
  if (sortKey.value === 'priceDesc') return { sortField: SORT_FIELD.price, sortAsc: false }
  return {}
}

let loadSeq = 0

/** @returns 本次请求是否成功（失败时调用方要回退乐观更新） */
async function load(): Promise<boolean> {
  // 分类维度尚未确定时，等待分类切换流程发起查询，避免携带上一个分类的条件。
  if (filterLoading.value) return true
  const seq = ++loadSeq
  loading.value = true
  error.value = false
  try {
    const res = await pageProducts({
      pageNo: pageNo.value,
      pageSize: PAGE_SIZE,
      categoryId: categoryId.value,
      keyword: keyword.value || undefined,
      filterValueIds: filterValueIds.value,
      ...sortParams(),
    })
    if (seq !== loadSeq) return true // 已被更晚的请求取代：丢弃这次结果
    products.value = res.list
    total.value = res.total
    return true
  } catch {
    if (seq !== loadSeq) return true // 同上：过期请求的失败不该影响当前状态
    // 请求失败必须给出**可重试**的提示，不能白屏也不能永久停在加载态（FR-045）
    error.value = true
    products.value = []
    total.value = 0
    return false
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}


function onSearch() {
  browsingAll.value = true
  keyword.value = searchInput.value.trim()
  // 搜索要回到第 1 页，否则会停在一个可能为空的高页码上
  pageNo.value = 1
  load()
}

function onPickCategory(id?: number) {
  browsingAll.value = id === undefined
  categoryId.value = id
  pageNo.value = 1
  categoriesOpen.value = false
  void router.replace({ query: { ...route.query, category: id === undefined ? undefined : String(id), view: id === undefined ? 'all' : undefined } })
  void refreshCategory(id)
}

let dimensionSeq = 0
async function loadFilterDimensions(id?: number) {
  const seq = ++dimensionSeq
  filterError.value = false
  filterDimensions.value = []
  if (id === undefined || flatCategories.value.some(c => c.parentId === id)) {
    filterLoading.value = false
    filterValueIds.value = []
    return true
  }
  filterLoading.value = true
  let dimensions: FilterDimension[] = []
  try {
    dimensions = sortDimensions(await listFilterDimensions(id))
  } catch {
    if (seq === dimensionSeq) filterError.value = true
  }
  if (seq !== dimensionSeq) return false
  filterDimensions.value = dimensions
  filterValueIds.value = retainApplicableSelections(filterValueIds.value, dimensions)
  filterLoading.value = false
  return true
}

async function refreshCategory(id?: number) {
  ++loadSeq // 分类刚切换就让旧商品请求失效，不等新的维度请求结束。
  loading.value = true
  error.value = false
  if (await loadFilterDimensions(id)) {
    if (showDepartments.value) loading.value = false
    else await load()
  }
}

/**
 * 点筛选条（FR-115 / Q14）：**乐观更新** —— 先把高亮移过去，再发请求；
 * 失败则**退回原值**。中间不存在"选中态与列表不一致"的状态。
 */
async function onPickFilterValue(ids: number[]) {
  if (filterLoading.value) return
  const previous = filterValueIds.value
  filterValueIds.value = ids
  pageNo.value = 1
  const ok = await load()
  if (!ok) {
    filterValueIds.value = previous
  }
}


function onPickSort(key: (typeof SORT_CHIPS)[number]['key']) {
  browsingAll.value = true
  sortKey.value = key
  pageNo.value = 1
  load()
}

function changeView(mode: 'grid' | 'list') {
  browsingAll.value = true
  viewMode.value = mode
}

function onPage(n: number) {
  pageNo.value = n
  load()
}

function clearSearch() {
  searchInput.value = ''
  onSearch()
}

function browseAllProducts() {
  ++dimensionSeq
  filterLoading.value = false
  filterError.value = false
  browsingAll.value = true
  categoryId.value = undefined
  keyword.value = ''
  searchInput.value = ''
  sortKey.value = 'default'
  filterValueIds.value = []
  filterDimensions.value = []
  pageNo.value = 1
  void router.replace({ query: { ...route.query, category: undefined, view: 'all' } })
  void load()
}

async function loadCategories() {
  categoriesLoading.value = true
  categoryError.value = false
  // 分类请求也要在同一处兜错：失败时给出可重试提示，而不是抛出未处理的拒绝
  try {
    const cats = await listCategories()
    flatCategories.value = cats
    tree.value = buildCategoryTree(cats)
  } catch {
    categoryError.value = true
  } finally {
    categoriesLoading.value = false
  }
}

const banners = ref<Banner[]>([])

async function loadBanners() {
  try {
    banners.value = await listBanners(BANNER_POSITION.MALL)
  } catch {
    banners.value = []
  }
}


onMounted(async () => {
  void loadBanners()
  browsingAll.value = route.query.view === 'all'
  const queryId = Number(route.query.category)
  if (Number.isSafeInteger(queryId) && queryId > 0) categoryId.value = queryId
  await loadCategories()
  await loadFilterDimensions(categoryId.value)
  if (showDepartments.value) loading.value = false
  else await load()
})
watch([() => route.query.category, () => route.query.view], ([value, view]) => {
  const id = Number(value)
  const nextCategory = Number.isSafeInteger(id) && id > 0 ? id : undefined
  const nextAll = view === 'all'
  if (categoryId.value === nextCategory && browsingAll.value === nextAll) return
  categoryId.value = nextCategory
  browsingAll.value = nextAll
  keyword.value = ''
  searchInput.value = ''
  sortKey.value = 'default'
  pageNo.value = 1
  categoriesOpen.value = false
  void refreshCategory(nextCategory)
})
</script>

<template>
  <div class="mall-page">
    <div class="mall-container mall-topline">
      <div><h1>标签商城</h1><p>打印设备与标签耗材，按需挑选。</p></div>
      <form
        id="mallSearchBox"
        class="mall-search"
        role="search"
        @submit.prevent="onSearch"
      >
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          aria-hidden="true"
        ><circle
          cx="10"
          cy="10"
          r="7"
        /><path d="m15 15 6 6" /></svg>
        <input
          id="mallSearch"
          v-model="searchInput"
          type="search"
          aria-label="搜索商品"
          placeholder="搜索商品名称、型号或规格"
          autocomplete="off"
        >
        <button
          v-if="searchInput || keyword"
          id="mallSearchClear"
          class="ms-clear"
          type="button"
          aria-label="清空搜索"
          @click="clearSearch"
        >
          清除
        </button>
        <button
          id="mallSearchBtn"
          class="ms-btn"
          type="submit"
        >
          搜索
        </button>
      </form>
    </div>

    <LoadingState
      v-if="showDepartments && categoriesLoading"
      class="mall-container mall-home-loading"
      variant="grid"
      :count="4"
    />
    <MallHome
      v-else-if="showDepartments"
      :categories="tree"
      :banners="banners"
      @select="onPickCategory"
    />
    <MallBanner
      v-else
      :banners="banners"
      class="mall-container"
    />

    <div
      v-if="!showDepartments"
      class="mall-container mall-page-main"
    >
      <aside
        class="sidebar mall-categories"
        aria-label="商品分类"
      >
        <div class="mall-sidebar-heading">
          <h2>商品分类</h2><button
            class="mall-category-toggle"
            type="button"
            :aria-expanded="categoriesOpen"
            aria-controls="mall-category-tree"
            @click="categoriesOpen = !categoriesOpen"
          >
            {{ categoriesOpen ? '收起' : '展开' }} <span aria-hidden="true">⌄</span>
          </button>
        </div>
        <div
          id="mall-category-tree"
          :class="{ 'is-open': categoriesOpen }"
        >
          <button
            class="cat-all"
            :class="{ active: categoryId === undefined }"
            type="button"
            @click="onPickCategory(undefined)"
          >
            全部商品
          </button>
          <div
            v-if="categoryError"
            class="mall-category-error"
          >
            <p>分类暂时加载失败</p><button
              type="button"
              @click="loadCategories"
            >
              重新加载分类
            </button>
          </div>
          <details
            v-for="node in tree"
            :key="node.id"
            class="cat-group"
            open
          >
            <summary>{{ node.name }}</summary>
            <div class="cat-sub">
              <button
                class="s-item"
                type="button"
                :class="{ active: categoryId === node.id }"
                :aria-pressed="categoryId === node.id"
                @click="onPickCategory(node.id)"
              >
                全部{{ node.name }}
              </button>
              <button
                v-for="child in node.children"
                :key="child.id"
                class="s-item"
                type="button"
                :class="{ active: categoryId === child.id }"
                :aria-pressed="categoryId === child.id"
                @click="onPickCategory(child.id)"
              >
                {{ child.name }}
              </button>
            </div>
          </details>
        </div>
        <div class="mall-selection-help">
          <span
            class="mall-help-icon"
            aria-hidden="true"
          >?</span><h3>不确定该怎么选？</h3><p>设备型号、标签材质与使用场景，帮你一起确认。</p><RouterLink to="/support/contact">
            咨询选型 <span aria-hidden="true">→</span>
          </RouterLink>
        </div>
      </aside>

      <div class="mall-right">
        <div class="mall-browse-panel">
          <div class="mall-browse-heading">
            <h2>{{ showDepartments ? '按品类选购' : selectedCategory?.name || '全部商品' }}</h2><RouterLink to="/support/contact">
              企业采购咨询 <span aria-hidden="true">↗</span>
            </RouterLink>
          </div>
          <div class="mall-primary-categories">
            <span class="mall-primary-label">一级分类</span>
            <div
              class="cat-pills"
              aria-label="快捷分类"
            >
              <button
                class="tab"
                type="button"
                :class="{ active: categoryId === undefined }"
                :aria-pressed="categoryId === undefined"
                @click="onPickCategory(undefined)"
              >
                全部
              </button>
              <button
                v-for="c in flatCategories.filter(x => x.parentId === 0)"
                :key="c.id"
                class="tab"
                type="button"
                :class="{ active: activeRoot?.id === c.id }"
                :aria-pressed="activeRoot?.id === c.id"
                @click="onPickCategory(c.id)"
              >
                {{ c.name }}
              </button>
            </div>
          </div>
          <div
            v-if="childCategories.length"
            class="mall-child-categories"
            aria-label="二级分类"
          >
            <span class="mall-child-label">二级分类</span>
            <div class="mall-child-options">
              <button
                v-for="child in childCategories"
                :key="child.id"
                type="button"
                :class="{ active: categoryId === child.id }"
                :aria-pressed="categoryId === child.id"
                @click="onPickCategory(child.id)"
              >
                {{ child.name }}
              </button>
            </div>
          </div>
          <p
            v-if="isParentCategory"
            class="mall-category-hint"
          >
            选择二级分类，可按对应规格进一步筛选。
          </p>
          <p
            v-if="filterLoading"
            class="mall-filter-status"
            role="status"
          >
            正在加载分类筛选…
          </p>
          <p
            v-else-if="filterError"
            class="mall-filter-status"
          >
            筛选项暂时无法加载，仍可浏览商品。<button
              type="button"
              @click="refreshCategory(categoryId)"
            >
              重试
            </button>
          </p>
          <ProductFilterBar
            v-if="filterDimensions.length"
            :dimensions="filterDimensions"
            :selected-value-ids="filterValueIds"
            @update:selected-value-ids="onPickFilterValue"
          />
          <div class="sort-bar">
            <div
              class="sort-chips"
              aria-label="商品排序"
            >
              <button
                v-for="chip in SORT_CHIPS"
                :key="chip.key"
                class="sort-chip"
                type="button"
                :class="{ active: sortKey === chip.key }"
                :aria-pressed="sortKey === chip.key"
                @click="onPickSort(chip.key)"
              >
                {{ chip.label }}
              </button>
            </div>
            <div class="mall-result-tools">
              <span
                class="sort-count"
                role="status"
              >{{ loading ? '加载中…' : error ? '暂时无法获取商品' : keyword ? `找到 ${total} 件相关商品` : `共 ${total} 件商品` }}</span><div
                class="mall-view-switch"
                aria-label="商品展示方式"
              >
                <button
                  type="button"
                  aria-label="网格视图"
                  :aria-pressed="viewMode === 'grid'"
                  :class="{ active: viewMode === 'grid' }"
                  @click="changeView('grid')"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.5"
                    aria-hidden="true"
                  ><rect
                    x="3"
                    y="3"
                    width="7"
                    height="7"
                    rx="1"
                  /><rect
                    x="14"
                    y="3"
                    width="7"
                    height="7"
                    rx="1"
                  /><rect
                    x="3"
                    y="14"
                    width="7"
                    height="7"
                    rx="1"
                  /><rect
                    x="14"
                    y="14"
                    width="7"
                    height="7"
                    rx="1"
                  /></svg>
                </button><button
                  type="button"
                  aria-label="列表视图"
                  :aria-pressed="viewMode === 'list'"
                  :class="{ active: viewMode === 'list' }"
                  @click="changeView('list')"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.5"
                    aria-hidden="true"
                  ><rect
                    x="3"
                    y="4"
                    width="5"
                    height="5"
                    rx="1"
                  /><path d="M12 5h9M12 8h9M12 16h9M12 19h9" /><rect
                    x="3"
                    y="15"
                    width="5"
                    height="5"
                    rx="1"
                  /></svg>
                </button>
              </div>
            </div>
          </div>
        </div>

        <LoadingState
          v-if="loading"
          class="mall-loading"
          variant="grid"
          :count="6"
        />
        <EmptyState
          v-else-if="error"
          mode="error"
          title="商品加载失败"
          desc="网络或服务暂时不可用，请稍后重试"
          action-text="重新加载"
          @action="load"
        />
        <div
          v-else-if="products.length"
          class="mall-grid"
          :class="{ 'is-list': viewMode === 'list' }"
        >
          <ProductCard
            v-for="p in products"
            :key="p.id"
            :spu="p"
            :badge="badgeOf(p.id)"
            catalog
            :category-label="categoryName(p.categoryId)"
          />
        </div>
        <EmptyState
          v-else-if="keyword"
          id="mallEmpty"
          title="没有找到相关商品"
          :desc="`没有找到与「${keyword}」相关的商品，换个关键词或分类试试`"
          action-text="查看全部商品"
          @action="browseAllProducts"
        />
        <EmptyState
          v-else
          id="mallEmpty"
          title="该分类下暂无商品"
          desc="换个分类看看，或查看全部商品"
          action-text="查看全部商品"
          @action="browseAllProducts"
        />
        <Pagination
          v-if="!showDepartments && !loading && !error"
          :page-no="pageNo"
          :page-size="PAGE_SIZE"
          :total="total"
          @update:page-no="onPage"
        />
      </div>
    </div>
  </div>
</template>
