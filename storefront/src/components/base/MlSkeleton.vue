<script setup lang="ts">
/**
 * C5 骨架屏。契约见 design-new-pages.md §2：
 * **形状 MUST 与最终内容一致**，避免加载完成时跳版。
 * 支持 `variant="card|text|row"`。
 */
withDefaults(defineProps<{ variant?: 'card' | 'text' | 'row'; rows?: number }>(), {
  variant: 'text',
  rows: 2,
})
</script>

<template>
  <div v-if="variant === 'card'" class="ml-skeleton-card">
    <div class="ml-skeleton is-img" />
    <div class="ml-skeleton is-line" />
    <div class="ml-skeleton is-line short" />
  </div>
  <div v-else-if="variant === 'row'" class="ml-skeleton is-row" />
  <div v-else>
    <div v-for="i in rows" :key="i" class="ml-skeleton is-line" :class="{ short: i === rows }" />
  </div>
</template>
