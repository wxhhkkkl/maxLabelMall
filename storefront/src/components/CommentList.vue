<script setup lang="ts">
import { reactive } from 'vue'

import MlRate from '@/components/base/MlRate.vue'
import type { ProductComment } from '@/types'

import { formatCommentTime, shouldFoldComment } from '@/utils/comment'

/**
 * 评价列表。**详情页与「全部评价」页共用**（FR-067~072）—— 两处差在两点：
 * 详情页折叠超长内容、并给"查看全部"入口；「全部评价」页都不需要。
 *
 * 数据由调用方拉好传进来：本组件**不发请求**。这样两处拉取策略（详情页异步、失败降级？
 * 「全部评价」页带分页？）各自决定，组件只负责渲染，也好测。
 */
const props = withDefaults(
  defineProps<{
    comments: ProductComment[]
    /** 是否折叠超长内容。详情页 true，「全部评价」页 false */
    collapsible?: boolean
    /** 是否给"查看全部"入口。只有详情页给 */
    showViewAll?: boolean
    emptyText?: string
  }>(),
  { collapsible: true, showViewAll: false, emptyText: '暂无评价' },
)

defineEmits<{ 'view-all': [] }>()

/** 每条各自的展开状态（key = 评价 id）—— 展开一条不该把别的也展开 */
const expanded = reactive<Record<number, boolean>>({})

function isFolded(c: ProductComment): boolean {
  return props.collapsible && shouldFoldComment(c.content) && !expanded[c.id]
}

function toggle(id: number) {
  expanded[id] = !expanded[id]
}
</script>

<template>
  <div class="cm">
    <p v-if="!comments.length" class="cm-empty">{{ emptyText }}</p>

    <div v-for="c in comments" :key="c.id" class="cm-item">
      <div class="cm-head">
        <span class="cm-name">{{ c.userNickname }}</span>
        <MlRate :model-value="c.scores" readonly />
      </div>

      <p class="cm-content" :class="{ 'is-folded': isFolded(c) }">{{ c.content }}</p>
      <button
        v-if="collapsible && shouldFoldComment(c.content)"
        class="cm-toggle"
        type="button"
        @click="toggle(c.id)"
      >
        {{ isFolded(c) ? '展开' : '收起' }}
      </button>

      <div v-if="c.picUrls?.length" class="cm-pics">
        <img v-for="(u, i) in c.picUrls" :key="i" :src="u" alt="评价图片" >
      </div>

      <!-- 商家回复在**同一条评价之内**，不另起一条 -->
      <div v-if="c.replyContent" class="cm-reply">
        <span class="cm-reply-label">商家回复</span>
        <span class="cm-reply-text">{{ c.replyContent }}</span>
      </div>

      <p class="cm-time">{{ formatCommentTime(c.createTime) }}</p>
    </div>

    <button v-if="showViewAll && comments.length" class="cm-view-all" type="button" @click="$emit('view-all')">
      查看全部评价
    </button>
  </div>
</template>

<style scoped>
.cm-item {
  padding: 14px 0;
  border-bottom: 1px solid var(--ml-border);
}
.cm-item:last-of-type {
  border-bottom: none;
}
.cm-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cm-name {
  font-size: 14px;
  color: var(--ml-text);
}
.cm-content {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--ml-text);
  white-space: pre-wrap;
  word-break: break-word;
}
/* 折叠：只影响展示高度，文本仍在 DOM 里 —— 「全部评价」页要能看到完整内容 */
.cm-content.is-folded {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}
.cm-toggle {
  margin-top: 4px;
  padding: 0;
  border: none;
  background: none;
  color: var(--ml-primary);
  font-size: 13px;
  cursor: pointer;
}
.cm-pics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.cm-pics img {
  width: 84px;
  height: 84px;
  object-fit: cover;
  border-radius: var(--ml-radius-field);
}
.cm-reply {
  margin-top: 10px;
  padding: 8px 10px;
  background: var(--ml-bg-soft);
  border-radius: var(--ml-radius-field);
  font-size: 13px;
}
.cm-reply-label {
  color: var(--ml-primary);
  margin-right: 6px;
}
.cm-reply-text {
  color: var(--ml-text-sub);
}
.cm-time {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--ml-text-weak);
}
.cm-empty {
  margin: 0;
  padding: 20px 0;
  text-align: center;
  font-size: 14px;
  color: var(--ml-text-sub);
}
.cm-view-all {
  display: block;
  width: 100%;
  margin-top: 12px;
  padding: 10px 0;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-pill);
  background: var(--ml-bg-card);
  color: var(--ml-text);
  font-size: 14px;
  cursor: pointer;
}
</style>
