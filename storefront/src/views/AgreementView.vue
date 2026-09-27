<script setup lang="ts">
import { computed } from 'vue'

import PendingText from '@/components/base/PendingText.vue'
import { RENDERED } from '@/data/placeholders'

/**
 * 用户协议 / 隐私政策 —— 同一组件两种内容（路由 props 决定）。
 *
 * ⚠️ **纯静态，不依赖任何接口**（FR-051 / SC-016）：后端不可用时也必须打得开，
 * 因为注册时用户正是在"还没连上后端"或"后端偶发故障"时需要查阅条款。
 * 所以本组件不 import 任何 `@/api/*`，也不发起请求。
 *
 * ⚠️ **条款正文由法务/业务提供，实现者不得代拟**（FR-054）。因此这里只渲染
 * 标题、生效日期与要点清单，正文位置用占位符标记，业务方替换后即可。
 */
const props = withDefaults(defineProps<{ kind?: 'user' | 'privacy' }>(), { kind: 'user' })

const doc = computed(() =>
  props.kind === 'privacy' ? RENDERED.legal.privacyPolicy : RENDERED.legal.userAgreement,
)
const isPrivacy = computed(() => props.kind === 'privacy')
const h1 = computed(() => (isPrivacy.value ? '隐私政策' : '用户协议'))
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink><span>/ {{ h1 }}</span>
  </div>

  <article class="agr-wrap">
    <h1 class="agr-title">
      <PendingText :value="doc.title" />
    </h1>
    <p class="agr-meta">
      生效日期：<PendingText :value="doc.effectiveDate" />
    </p>

    <!-- 正文占位：由法务提供后替换（FR-054） -->
    <p class="agr-pending">
      <PendingText :value="doc.bodyPending" />
    </p>

    <template v-if="isPrivacy">
      <h2 class="agr-h2">本政策必须说明的内容</h2>
      <ul class="agr-list">
        <li v-for="s in RENDERED.legal.privacyPolicy.sections" :key="s">
          <PendingText :value="s" />
        </li>
      </ul>
      <p class="agr-note">
        以上四项为隐私政策必须覆盖的要点（FR-052）。正文由法务提供，实现者不得代拟。
      </p>
    </template>
  </article>
</template>

<style scoped>
.agr-wrap {
  max-width: 760px;
  margin: 0 auto;
  padding: 8px 24px 72px;
}
.agr-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--ml-text);
  margin: 20px 0 10px;
}
.agr-meta {
  font-size: 13px;
  color: var(--ml-text-ph);
  margin-bottom: 28px;
}
.agr-pending {
  font-size: 15px;
  line-height: 1.75;
  color: var(--ml-text-sub);
  padding: 18px;
  background: var(--ml-bg-card-3);
  border-radius: var(--ml-radius-card);
}
.agr-h2 {
  font-size: 18px;
  color: var(--ml-text);
  margin: 28px 0 12px;
}
.agr-list {
  list-style: disc;
  padding-left: 22px;
}
.agr-list li {
  font-size: 14px;
  line-height: 1.9;
  color: var(--ml-text-sub);
}
.agr-note {
  margin-top: 24px;
  font-size: 12px;
  color: var(--ml-text-ph);
}
</style>
