<script setup lang="ts">
import MlSkeleton from './base/MlSkeleton.vue'

/**
 * 加载中骨架（FR-045）。形状与最终内容一致，避免加载完成时跳版。
 * 商品网格用 `grid`，普通列表用 `text`。
 */
withDefaults(defineProps<{ variant?: 'grid' | 'text'; count?: number }>(), {
  variant: 'text',
  count: 4,
})
</script>

<template>
  <div v-if="variant === 'grid'" class="grid-3">
    <MlSkeleton v-for="i in count" :key="i" variant="card" />
  </div>
  <div v-else>
    <MlSkeleton v-for="i in count" :key="i" variant="text" :rows="2" />
  </div>
</template>
