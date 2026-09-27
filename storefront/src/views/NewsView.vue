<script setup lang="ts">
import { computed, ref } from 'vue'

import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'

/**
 * 新闻动态 —— 按 design-new-pages.md §3.15 实现（无设计稿）。
 *
 * ⚠️ 文章条目**由业务方提供**（FR-054），一律走 `placeholders.ts`。
 * **在业务方提供至少 3 条真实文章前，本页不应发布**（FR-055）—— 上线门禁里的
 * 占位符巡检会挡住它，因为种子里的标题/摘要/日期都还带 `[[ ]]`。
 *
 * ⚠️ 分类胶囊以 `placeholders.ts` 的 `newsCategories` 为**唯一来源**（FR-060 要求，
 * 避免种子与页面漂移），不是在本页另写一份。
 */
const seed = RENDERED.news
const ALL = '全部'

const PAGE_SIZE = 4

const active = ref(ALL)
const pageNo = ref(1)

const filtered = computed(() =>
  active.value === ALL ? seed : seed.filter((n) => n.category === active.value),
)
const paged = computed(() =>
  filtered.value.slice((pageNo.value - 1) * PAGE_SIZE, pageNo.value * PAGE_SIZE),
)

function onPick(category: string) {
  active.value = category
  pageNo.value = 1
}
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <span>新闻动态</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ml-page-title">新闻动态</h1>
    <p class="ml-page-sub">公司新闻、行业资讯与产品动态</p>
  </div>

  <div class="ml-wrap">
    <div class="cat-pills">
      <span class="tab" :class="{ active: active === ALL }" @click="onPick(ALL)">{{ ALL }}</span>
      <span
        v-for="c in RENDERED.newsCategories"
        :key="c"
        class="tab"
        :class="{ active: active === c }"
        @click="onPick(c)"
      >
        {{ c }}
      </span>
    </div>

    <EmptyState
      v-if="!filtered.length"
      :title="active === ALL ? '暂无新闻' : `暂无${active}的文章`"
      desc="有新的内容会在这里发布"
      action-text="看看全部"
      @action="onPick(ALL)"
    />

    <template v-else>
      <div class="news-list">
        <article v-for="n in paged" :key="n.title" class="news-card">
          <div class="ph news-thumb"></div>
          <div class="news-body">
            <h3 class="news-title"><PendingText :value="n.title" /></h3>
            <!-- 摘要两行截断：列表页只做导览，不铺全文 -->
            <p class="news-summary is-clamp-2"><PendingText :value="n.summary" /></p>
            <span class="news-date"><PendingText :value="n.date" /></span>
          </div>
        </article>
      </div>

      <Pagination v-model:page-no="pageNo" :page-size="PAGE_SIZE" :total="filtered.length" />
    </template>
  </div>
</template>

<style scoped>
.news-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 20px;
}
.news-card {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 20px;
  background: #ffffff;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-card);
  padding: 16px;
}
.news-thumb {
  width: 160px;
  height: 100px;
  border-radius: var(--ml-radius-field);
}
.news-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.news-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ml-text);
  line-height: 1.5;
}
.news-summary {
  font-size: 13px;
  color: #5b6c8f;
  line-height: 1.7;
}
/* 两行截断（§3.15） */
.is-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.news-date {
  font-size: 12px;
  color: #8a97b5;
}
@media (max-width: 768px) {
  .news-card {
    grid-template-columns: 1fr;
  }
  .news-thumb {
    width: 100%;
    height: 160px;
  }
}
</style>
