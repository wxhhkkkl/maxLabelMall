<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { BANNER_POSITION, listBanners } from '@/api/banner'
import { buildCategoryTree, listCategories } from '@/api/category'
import { listFilterDimensions } from '@/api/filter'
import { pageProducts, SORT_FIELD } from '@/api/product'
import type { FilterDimension } from '@/types/filter'
import { retainApplicableSelections, sortDimensions } from '@/utils/filter'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import Pagination from '@/components/Pagination.vue'
import MallBanner from '@/components/MallBanner.vue'
import ProductCard from '@/components/ProductCard.vue'
import ProductFilterBar from '@/components/ProductFilterBar.vue'
import type { Banner, Category, CategoryNode, ProductSpu, ProductSortField } from '@/types'
import { deriveBadges } from '@/utils/badge'

/**
 * 商城页 —— 按设计稿 `www/mall.html` 还原，但**移除两处后端不支持的 UI**：
 *
 *   · 侧栏的「价格区间」与「服务」筛选（FR-004a）：后端检索只接受
 *     `categoryId(s) / keyword / sortField / sortAsc`，做不了这两组筛选。
 *   · 侧栏各类目的**商品数量计数**（FR-002a）：分类接口不返回数量。
 *
 * 搜索与排序都走**后端**：关键词是 `keyword` 参数在后台全量检索，
 * 而不是过滤已加载的那一页（FR-003）。列表头部的总数取自分页结果的 `total`。
 */

const PAGE_SIZE = 12

const products = ref<ProductSpu[]>([])
const total = ref(0)
const pageNo = ref(1)
const keyword = ref('')
const categoryId = ref<number | undefined>(undefined)
const sortKey = ref<'default' | 'sales' | 'priceAsc'>('default')
const tree = ref<CategoryNode[]>([])
/** 当前分类下要渲染的筛选维度。**空数组 = 整块不渲染**（FR-104） */
const filterDimensions = ref<FilterDimension[]>([])
/** 已选的筛选值（每个维度至多一个，Q9=B 单选） */
const filterValueIds = ref<number[]>([])
const flatCategories = ref<Category[]>([])
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
] as const

/** 设计稿的三种排序 → 后端参数（综合排序不传 sortField，由后端默认排序） */
function sortParams(): { sortField?: ProductSortField; sortAsc?: boolean } {
  if (sortKey.value === 'sales') return { sortField: SORT_FIELD.sales, sortAsc: false }
  if (sortKey.value === 'priceAsc') return { sortField: SORT_FIELD.price, sortAsc: true }
  return {}
}

/**
 * 加载序号：**只认最后一次请求**（FR-115 / Q14）。
 *
 * 顾客快速连点几个值时会并发多个请求，先发出的那个**可能后返回** ——
 * 不挡住的话，界面会停在"高亮是 100、列表却是 110 的结果"这种自相矛盾的状态，
 * 而且不报错。
 */
let loadSeq = 0

/** @returns 本次请求是否成功（失败时调用方要回退乐观更新） */
async function load(): Promise<boolean> {
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
  // 搜索要回到第 1 页，否则会停在一个可能为空的高页码上
  pageNo.value = 1
  load()
}

function onPickCategory(id?: number) {
  categoryId.value = id
  pageNo.value = 1
  // 切换分类时：重新拉该分类的筛选维度，并把**仍适用**的选择留住（FR-103）。
  // 维度是沿分类树向下继承的，所以兄弟分类之间同一个维度是同一条，选择理应保留；
  // 而新分类下根本不存在的那行选择必须清掉 —— 否则会带着一个无法解释的条件去查询。
  loadFilterDimensions(id).then(() => load())
}

/**
 * 拉某分类的筛选维度。
 *
 * ⚠️ 它**只依赖分类**，所以同一个分类内只请求一次（切分类才重取）—— 这是 Q11=A
 * 带来的简化：选项集合只由配置决定，不随关键词或已选值变化（FR-111）。
 *
 * 失败时**只让筛选条降级**（整块不渲染），商品列表照常（FR-106）——
 * 筛选条是辅助区块，不该带崩这一页。
 */
async function loadFilterDimensions(id?: number) {
  if (id === undefined) {
    filterDimensions.value = []
    filterValueIds.value = []
    return
  }
  try {
    filterDimensions.value = sortDimensions(await listFilterDimensions(id))
  } catch {
    filterDimensions.value = []
  }
  filterValueIds.value = retainApplicableSelections(filterValueIds.value, filterDimensions.value)
}

/**
 * 点筛选条（FR-115 / Q14）：**乐观更新** —— 先把高亮移过去，再发请求；
 * 失败则**退回原值**。中间不存在"选中态与列表不一致"的状态。
 */
async function onPickFilterValue(ids: number[]) {
  const previous = filterValueIds.value
  filterValueIds.value = ids
  pageNo.value = 1
  const ok = await load()
  if (!ok) {
    filterValueIds.value = previous
  }
}

function onPickSort(key: (typeof SORT_CHIPS)[number]['key']) {
  sortKey.value = key
  pageNo.value = 1
  load()
}

function onPage(n: number) {
  pageNo.value = n
  load()
}

/**
 * 商城页顶部横幅（FR-080）。**拉不到就是空的** —— 由 `MallBanner` 整块不渲染，
 * 商品列表照常从顶部开始，不会留空白（FR-082）。
 *
 * ⚠️ 横幅是**辅助区块**：它挂了不该影响商品列表，所以这里吞掉异常而不是置 `error`
 * （那个 error 是给"商品加载失败"用的，会整页变错误态）。
 */
const banners = ref<Banner[]>([])

async function loadBanners() {
  try {
    banners.value = await listBanners(BANNER_POSITION.MALL)
  } catch {
    banners.value = []
  }
}

onMounted(async () => {
  // 分类请求也要在同一处兜错：失败时给出可重试提示，而不是抛出未处理的拒绝
  try {
    const cats = await listCategories()
    flatCategories.value = cats
    tree.value = buildCategoryTree(cats)
  } catch {
    error.value = true
  }
  // 与商品列表并行拉，不阻塞首屏
  void loadBanners()
  await load()
})
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink><span>/ 商城</span>
    <span class="current">全部商品</span>
  </div>

  <!-- 顶部横幅（FR-080）。0 条时 MallBanner 自己整块不渲染，不留空白 -->
  <MallBanner :banners="banners" />

  <div class="mall-page-main">
    <!-- 侧栏：分类树。**无价格区间/服务筛选，也无各类目计数** -->
    <aside class="sidebar sticky">
      <div class="cat-all" @click="onPickCategory(undefined)">
        <span>全部商品</span>
      </div>

      <details v-for="node in tree" :key="node.id" class="cat-group" open>
        <summary>{{ node.name }}</summary>
        <div class="cat-sub">
          <span
            class="s-item"
            :class="{ active: categoryId === node.id }"
            @click="onPickCategory(node.id)"
          >
            {{ node.name }}
          </span>
          <span
            v-for="child in node.children"
            :key="child.id"
            class="s-item"
            :class="{ active: categoryId === child.id }"
            @click="onPickCategory(child.id)"
          >
            {{ child.name }}
          </span>
        </div>
      </details>
    </aside>

    <div class="mall-right">
      <!-- 搜索：走**后端全量检索** -->
      <div id="mallSearchBox" class="mall-search" :class="{ 'has-value': !!keyword }">
        <span class="ms-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="2" />
            <path d="M20 20l-3.5-3.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </span>
        <input
          id="mallSearch"
          v-model="keyword"
          type="text"
          placeholder="搜索商品：如 热敏纸 / 碳带 / M3 Pro"
          autocomplete="off"
          @keydown.enter="onSearch"
        />
        <button
          v-if="keyword"
          id="mallSearchClear"
          class="ms-clear"
          aria-label="清空搜索"
          @click="((keyword = ''), onSearch())"
        >
          ×
        </button>
        <button id="mallSearchBtn" class="ms-btn" @click="onSearch">搜索</button>
      </div>

      <!-- 分类胶囊 -->
      <div class="cat-pills">
        <span class="tab" :class="{ active: categoryId === undefined }" @click="onPickCategory(undefined)">
          全部
        </span>
        <span
          v-for="c in flatCategories.filter((x) => x.parentId === 0)"
          :key="c.id"
          class="tab"
          :class="{ active: categoryId === c.id }"
          @click="onPickCategory(c.id)"
        >
          {{ c.name }}
        </span>
        <!-- 设计稿这一排最后一项是「企业采购」（`www/mall.html:166`）。
             它不是商品分类，也没有对应的后端 categoryId —— 按 FR-048 指向落地页。 -->
        <RouterLink class="tab" to="/enterprise">企业采购</RouterLink>
      </div>

      <!-- 筛选条（FR-098）：只在选中分类、且该分类（或其祖先）配了维度时才出现。
           没有配置时它自己整块不渲染，不留空白块（FR-104）。 -->
      <ProductFilterBar
        :dimensions="filterDimensions"
        :selected-value-ids="filterValueIds"
        @update:selected-value-ids="onPickFilterValue"
      />

      <!-- 排序 + 总数（总数来自分页结果） -->
      <div class="sort-bar">
        <span class="sort-count">{{ keyword ? `找到 ${total} 件相关商品` : `共 ${total} 件商品` }}</span>
        <div class="sort-chips">
          <span
            v-for="chip in SORT_CHIPS"
            :key="chip.key"
            class="sort-chip"
            :class="{ active: sortKey === chip.key }"
            @click="onPickSort(chip.key)"
          >
            {{ chip.label }}
          </span>
        </div>
      </div>

      <LoadingState v-if="loading" variant="grid" :count="6" />

      <EmptyState
        v-else-if="error"
        mode="error"
        title="商品加载失败"
        desc="网络或服务暂时不可用，请稍后重试"
        action-text="重新加载"
        @action="load"
      />

      <!-- `mall-grid`：列数自适应（卡片不再被撑大）。
           ⚠️ **不要同时挂 `grid-3`** —— 它的 base 规则 `repeat(3,1fr)` 在样式表里
           更靠后，同优先级下会覆盖掉 mall-grid 的 auto-fill（实测过：卡片又是 335px）。
           窄屏的 2 列 / 1 列阶梯已直接写进 `.mall-grid` 的媒体查询。 -->
      <div v-else-if="products.length" class="mall-grid">
        <ProductCard v-for="p in products" :key="p.id" :spu="p" :badge="badgeOf(p.id)" />
      </div>

      <EmptyState
        v-else-if="keyword"
        id="mallEmpty"
        title="没有找到相关商品"
        :desc="`没有找到与「${keyword}」相关的商品，换个关键词试试`"
        action-text="清空搜索，查看全部商品"
        @action="((keyword = ''), onSearch())"
      />

      <EmptyState
        v-else
        id="mallEmpty"
        title="该分类下暂无商品"
        desc="换个分类看看，或查看全部商品"
        action-text="查看全部商品"
        @action="onPickCategory(undefined)"
      />

      <Pagination
        v-if="!keyword"
        :page-no="pageNo"
        :page-size="PAGE_SIZE"
        :total="total"
        @update:page-no="onPage"
      />
    </div>
  </div>
</template>
