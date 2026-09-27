<script setup lang="ts">
import { computed, ref } from 'vue'

import EmptyState from '@/components/EmptyState.vue'
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'

/**
 * 加入我们 —— 按 design-new-pages.md §3.17 实现（无设计稿）。
 *
 * ⚠️ 岗位清单与投递邮箱**由业务方提供**（FR-054 / §4），一律走 `placeholders.ts`。
 * 投递邮箱还没填时渲染的是「投递邮箱待填写」这类可辨识占位，**不是**编造的邮箱。
 *
 * ⚠️ 职能胶囊以 `placeholders.ts` 的 `jobCategories` 为**唯一来源**（FR-062 要求，
 * 避免种子与页面漂移）。
 */
const seed = RENDERED.jobs
const ALL = '全部'

const active = ref(ALL)
/** 展开的岗位（同时只展开一个，避免整页被撑开） */
const openIndex = ref<number | null>(null)

const filtered = computed(() =>
  active.value === ALL ? seed : seed.filter((j) => j.category === active.value),
)

function onPick(category: string) {
  active.value = category
  openIndex.value = null
}

function toggle(index: number) {
  openIndex.value = openIndex.value === index ? null : index
}
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <span>加入我们</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ml-page-title">加入我们</h1>
    <p class="ml-page-sub">和一群把标签这件小事做到底的人一起工作</p>
  </div>

  <div class="ml-wrap">
    <div class="cat-pills">
      <span class="tab" :class="{ active: active === ALL }" @click="onPick(ALL)">{{ ALL }}</span>
      <span
        v-for="c in RENDERED.jobCategories"
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
      :title="active === ALL ? '暂无在招岗位' : `暂无${active}类岗位`"
      desc="新的岗位会在这里发布"
      action-text="看看全部"
      @action="onPick(ALL)"
    />

    <div v-else class="job-list">
      <div v-for="(j, i) in filtered" :key="j.title" class="job-card">
        <div class="job-head">
          <div class="job-main">
            <h3 class="job-title"><PendingText :value="j.title" /></h3>
            <p class="job-meta">
              <PendingText :value="j.city" /> · <PendingText :value="j.experience" />
            </p>
          </div>
          <button class="btn-cyan job-toggle" type="button" @click="toggle(i)">
            {{ openIndex === i ? '收起' : '查看详情' }}
          </button>
        </div>

        <div v-if="openIndex === i" class="job-detail">
          <div class="job-block">
            <h4>岗位职责</h4>
            <ul>
              <li v-for="r in j.responsibilities" :key="r"><PendingText :value="r" /></li>
            </ul>
          </div>
          <div class="job-block">
            <h4>任职要求</h4>
            <ul>
              <li v-for="r in j.requirements" :key="r"><PendingText :value="r" /></li>
            </ul>
          </div>
          <div class="job-block">
            <h4>投递方式</h4>
            <p class="job-apply"><PendingText :value="j.applyTo" /></p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.job-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 20px;
}
.job-card {
  background: #ffffff;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-card);
  padding: 20px;
}
.job-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.job-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--ml-text);
}
.job-meta {
  margin-top: 6px;
  font-size: 13px;
  color: var(--ml-text-sub);
}
.job-toggle {
  flex-shrink: 0;
  border: none;
  cursor: pointer;
}
.job-detail {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--ml-border);
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.job-block h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--ml-text);
  margin-bottom: 8px;
}
.job-block ul {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.job-block li,
.job-apply {
  font-size: 14px;
  color: var(--ml-text-sub);
  line-height: 1.7;
}
.job-block li::before {
  content: '· ';
  color: var(--ml-primary);
}
</style>
