<script setup lang="ts">
import { computed } from 'vue'

/**
 * 分页 —— 复用设计稿的 `.pager` / `.pg` / `.pg-next` 样式。
 * 总条数由后端分页结果提供（后端**不返回**分类数量，列表总数也取自分页结果，FR-002a）。
 */
const props = withDefaults(
  defineProps<{ pageNo: number; pageSize: number; total: number }>(),
  { pageNo: 1, pageSize: 12, total: 0 },
)
const emit = defineEmits<{ 'update:pageNo': [number] }>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const pages = computed(() => Array.from({ length: pageCount.value }, (_, i) => i + 1))
const hasPrev = computed(() => props.pageNo > 1)
const hasNext = computed(() => props.pageNo < pageCount.value)

function go(n: number): void {
  if (n < 1 || n > pageCount.value || n === props.pageNo) return
  emit('update:pageNo', n)
}
</script>

<template>
  <!-- 只有一页时不渲染分页（避免出现无意义的单页分页条） -->
  <div v-if="pageCount > 1" class="pager">
    <span v-if="hasPrev" class="pg-next" @click="go(pageNo - 1)">← 上一页</span>
    <span
      v-for="p in pages"
      :key="p"
      class="pg"
      :class="{ active: p === pageNo }"
      @click="go(p)"
    >
      {{ p }}
    </span>
    <span v-if="hasNext" class="pg-next" @click="go(pageNo + 1)">下一页 →</span>
  </div>
</template>
