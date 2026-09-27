<script setup lang="ts">
import { computed, ref } from 'vue'

import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'

/**
 * 模板中心 —— 按 design-new-pages.md §3.12 实现（无设计稿）。
 *
 * 三条约束：
 *
 * 1. **静态页**：yudao 没有标签模板实体，本期**不调任何接口**；模板清单来自
 *    `placeholders.ts`，由业务方提供。
 *
 * 2. **搜索与筛选都在客户端**（FR-057）—— 没有后端可查，所以匹配的是**模板名与行业**
 *    两个字段，并必须给空结果态。
 *
 * 3. **不得沿用设计稿的「2,000+ 模板」**（FR-055）：那个数字未经业务方确认，
 *    数量表述一律走 `templates.countText` 的占位。
 */
const seed = RENDERED.templates

/** 去掉占位包裹，只用于**匹配**（展示仍然走 PendingText，保住 `data-content-pending`） */
const bare = (s: string) => s.replace(/^\[\[|\]\]$/g, '')

const PAGE_SIZE = 6

const keyword = ref('')
/** 当前生效的搜索词（点「搜索」才生效，与设计稿的搜索框行为一致） */
const applied = ref('')
const industry = ref(bare(seed.categories[0] ?? ''))
const pageNo = ref(1)

const categories = computed(() => seed.categories.map(bare))

/** 先按行业、再按关键词（模板名 **或** 行业）过滤 */
const filtered = computed(() => {
  const kw = applied.value.trim().toLowerCase()
  return seed.items.filter((t) => {
    if (industry.value !== categories.value[0] && bare(t.industry) !== industry.value) return false
    if (!kw) return true
    return (
      bare(t.name).toLowerCase().includes(kw) || bare(t.industry).toLowerCase().includes(kw)
    )
  })
})

const paged = computed(() =>
  filtered.value.slice((pageNo.value - 1) * PAGE_SIZE, pageNo.value * PAGE_SIZE),
)

function onSearch() {
  applied.value = keyword.value
  pageNo.value = 1
}

function onPickIndustry(label: string) {
  industry.value = label
  pageNo.value = 1
}
</script>

<template>
  <section class="sol-hero tpl-hero">
    <h1><PendingText :value="seed.title" /></h1>
    <p><PendingText :value="seed.subtitle" /></p>
    <div class="search-bar">
      <input
        id="tplSearch"
        v-model="keyword"
        type="text"
        placeholder="搜索模板名或行业"
        autocomplete="off"
        @keydown.enter="onSearch"
      />
      <button id="tplSearchBtn" class="btn-cyan" type="button" @click="onSearch">搜索</button>
    </div>
    <!-- 数量表述必须由业务方确认（FR-055）；占位期间带 data-content-pending -->
    <p class="tpl-count"><PendingText :value="seed.countText" /></p>
  </section>

  <div class="ml-wrap">
    <div class="cat-pills">
      <!-- 胶囊**原样渲染种子文案**（带 `[[ ]]`）：行业名也是待业务方确认的内容，
           抹掉占位标记会让它看起来像已经确认过 —— 匹配用的是去包裹后的值 -->
      <span
        v-for="raw in seed.categories"
        :key="raw"
        class="tab"
        :class="{ active: industry === bare(raw) }"
        @click="onPickIndustry(bare(raw))"
      >
        <PendingText :value="raw" />
      </span>
    </div>

    <EmptyState
      v-if="!filtered.length"
      title="没有匹配的模板"
      desc="换个关键词或行业试试"
      action-text="清空筛选"
      @action="((keyword = ''), (applied = ''), onPickIndustry(categories[0] ?? ''))"
    />

    <template v-else>
      <div class="grid-3 tpl-grid">
        <div v-for="t in paged" :key="bare(t.name)" class="tpl-card">
          <div class="ph tpl-thumb">
            <span class="tpl-size"><PendingText :value="t.size" /></span>
          </div>
          <div class="tpl-name"><PendingText :value="t.name" /></div>
          <div class="tpl-industry"><PendingText :value="t.industry" /></div>
          <!-- 模板要在标签软件里使用，所以这个 CTA 落到软件页（SaaS 之外无模板详情页） -->
          <RouterLink class="btn-cyan tpl-use" to="/software">使用此模板</RouterLink>
        </div>
      </div>

      <Pagination v-model:page-no="pageNo" :page-size="PAGE_SIZE" :total="filtered.length" />
    </template>
  </div>
</template>

<style scoped>
.tpl-hero {
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
}
.tpl-hero .search-bar {
  margin-top: 8px;
}
.tpl-count {
  font-size: 13px;
  color: var(--ml-text-sub);
}
.tpl-grid {
  margin-top: 20px;
}
.tpl-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #ffffff;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-card);
  padding: 14px;
}
.tpl-thumb {
  aspect-ratio: 4 / 3;
  border-radius: var(--ml-radius-field);
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding: 8px;
}
.tpl-size {
  font-size: 12px;
  color: var(--ml-text-ph);
}
.tpl-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ml-text);
}
.tpl-industry {
  font-size: 13px;
  color: var(--ml-text-sub);
}
.tpl-use {
  align-self: flex-start;
  border: none;
  cursor: pointer;
}
@media (max-width: 1100px) {
  .tpl-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 768px) {
  .tpl-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
