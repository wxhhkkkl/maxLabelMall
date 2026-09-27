<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'

/**
 * 更新日志 —— 按 design-new-pages.md §3.13 实现（无设计稿）。
 *
 * 时间线：左侧版本号与日期（带竖轴线），右侧卡片列出该版本的更新项，每项带类型标签。
 *
 * ⚠️ 类型标签（新增 / 优化 / 修复）**不走 C6 `.ml-pill`** —— C6 的变体是与后端
 * 订单状态一一对应的映射，`store.css` 明确写了「MUST NOT 增删」。这里另立
 * `.cl-kind` 标签，仍复用 `--ml-radius-pill` 等令牌，保持同一套视觉语言。
 *
 * ⚠️ 版本号、日期、更新项内容**由业务方/研发提供**（§4），一律走 `placeholders.ts`。
 */
const entries = RENDERED.changelog

/** 三类标签各一个修饰类，底色两两不同（仅靠文案区分不算标签） */
const KIND_CLASS: Record<string, string> = {
  新增: 'is-added',
  优化: 'is-improved',
  修复: 'is-fixed',
}
const kindClass = (kind: string) => KIND_CLASS[kind] ?? 'is-improved'
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <span>更新日志</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ml-page-title">更新日志</h1>
    <p class="ml-page-sub">版本更新与改进记录</p>
  </div>

  <div class="ml-wrap">
    <EmptyState
      v-if="!entries.length"
      title="暂无更新记录"
      desc="有版本发布时会在这里列出"
      action-text="去商城逛逛"
      @action="$router.push('/mall')"
    />

    <ol v-else class="cl-list">
      <li v-for="e in entries" :key="e.version" class="cl-entry">
        <div class="cl-side">
          <span class="cl-version"><PendingText :value="e.version" /></span>
          <span class="cl-date"><PendingText :value="e.date" /></span>
        </div>
        <div class="cl-body">
          <ul class="cl-items">
            <li v-for="it in e.items" :key="it.text" class="cl-item">
              <span class="cl-dot"></span>
              <span class="cl-kind" :class="kindClass(it.kind)">{{ it.kind }}</span>
              <span class="cl-text"><PendingText :value="it.text" /></span>
            </li>
          </ul>
        </div>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.cl-list {
  display: flex;
  flex-direction: column;
  gap: 28px;
  list-style: none;
  margin-top: 8px;
}
.cl-entry {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 24px;
}
.cl-side {
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* 竖轴线用 border 画在右侧，视觉上把左边的时间信息串成时间线 */
  border-right: 2px solid #e3e9f4;
  padding-right: 20px;
  text-align: right;
}
.cl-version {
  font-size: 16px;
  font-weight: 700;
  color: var(--ml-text);
}
.cl-date {
  font-size: 13px;
  color: var(--ml-text-ph);
}
.cl-body {
  background: #ffffff;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-card);
  padding: 18px;
}
.cl-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
  list-style: none;
}
.cl-item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 14px;
  color: var(--ml-text-sub);
  line-height: 1.7;
}
.cl-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ml-primary);
  flex-shrink: 0;
  align-self: center;
}
.cl-kind {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: var(--ml-radius-pill);
  font-size: 12px;
  line-height: 1.6;
}
/* 只用 §1.1 表内色。新增与优化同底不同字色，修复用强调橙 —— 与 C6 的
   「同底不同文字色」是同一套做法（design-new-pages §1.1 明令不得引入表外色） */
.cl-kind.is-added {
  background: #eaf1ff;
  color: #2e7cd6;
}
.cl-kind.is-improved {
  background: #f0f4fb;
  color: #16233f;
}
.cl-kind.is-fixed {
  background: #f0f4fb;
  color: #ff5c22;
}
@media (max-width: 768px) {
  .cl-entry {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .cl-side {
    border-right: 0;
    border-left: 2px solid #e3e9f4;
    padding: 0 0 0 14px;
    text-align: left;
  }
}
</style>
