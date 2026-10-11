<script setup lang="ts">
import { computed } from 'vue'

import type { FilterDimension } from '@/types/filter'
import { sortDimensions } from '@/utils/filter'

/**
 * 商城页筛选条（特性 003 / US15 / T299）。
 *
 * ## 它只由 props 决定渲染什么
 * 一条网络请求都不发、也不自己保存选中态 —— 那些归 `MallView` 管。这样它的行为就是
 * **纯函数式**的，几条最容易跑偏的需求于是天然成立：
 *
 *   · **选项集合稳定**（FR-111 / Q11=A）：选项来自 props，不因为"当前选了什么"增减。
 *     这是本特性**最容易被实现坏**的一条 —— "没货的选项就不显示"是电商里的常见反射，
 *     而在这里它是**明确被否掉**的（见 research.md R6 留的档）。
 *   · **单选替换**（FR-099 / Q9=B）：点一个新值 = 换掉这一行的旧值，不需要先点「全部」。
 *   · **没有全局「清除筛选」按钮**（FR-114 / Q12）：清除靠每行行首的「全部」。
 *
 * ## 窄屏（Q13）
 * 行首的「宽度：」与「全部」**固定**，值区自己**横向滚动**、**不折行** ——
 * 这样屏幕再窄、值再多，筛选条的**行数也不变**（= 高度可预测），
 * 不会把商品网格整个挤出屏幕。注意页面本身不产生横向滚动，滑的是行内。
 */
const props = defineProps<{
  dimensions: FilterDimension[]
  selectedValueIds: number[]
}>()

const emit = defineEmits<{ 'update:selectedValueIds': [number[]] }>()

/** 只渲染**有可选值**的维度；全都没有时整块不渲染（FR-090 / FR-104） */
const rows = computed(() =>
  sortDimensions(props.dimensions).filter((dimension) => dimension.values.length > 0)
)

const selected = computed(() => new Set(props.selectedValueIds))

function rowValueIds(row: FilterDimension): Set<number> {
  return new Set(row.values.map((value) => value.valueId))
}

function isRowActive(row: FilterDimension): boolean {
  return row.values.some((value) => selected.value.has(value.valueId))
}

/** 点一个值：**先摘掉这一行原有的选择，再放上新的** —— 这就是"单选替换" */
function pickValue(row: FilterDimension, valueId: number) {
  const own = rowValueIds(row)
  const kept = props.selectedValueIds.filter((id) => !own.has(id))
  emit('update:selectedValueIds', [...kept, valueId])
}

/** 点「全部」：只清这一行，别的行不动 */
function clearRow(row: FilterDimension) {
  const own = rowValueIds(row)
  emit(
    'update:selectedValueIds',
    props.selectedValueIds.filter((id) => !own.has(id))
  )
}
</script>

<template>
  <div
    v-if="rows.length"
    class="product-filter-bar"
  >
    <div
      v-for="row in rows"
      :key="row.dimensionId"
      class="filter-row"
    >
      <span class="filter-row__label">{{ row.dimensionName }}：</span>
      <button
        class="filter-row__all"
        type="button"
        :aria-label="`${row.dimensionName}：全部`"
        :aria-pressed="!isRowActive(row)"
        :class="{ active: !isRowActive(row) }"
        @click="clearRow(row)"
      >
        全部
      </button>
      <!-- 值区自己横向滚动、不折行：行数因此不随数据的多少变化（Q13） -->
      <div class="filter-row__values">
        <button
          v-for="value in row.values"
          :key="value.valueId"
          class="filter-value"
          type="button"
          :aria-label="`${row.dimensionName}：${value.name}`"
          :aria-pressed="selected.has(value.valueId)"
          :class="{ active: selected.has(value.valueId) }"
          @click="pickValue(row, value.valueId)"
        >
          {{ value.name }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-filter-bar {
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
  padding: 4px 18px;
  margin-bottom: 16px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f4fb;
}
.filter-row:last-child {
  border-bottom: none;
}

.filter-row__label {
  flex: none;
  font-size: 12px;
  color: #8a97b5;
}

/* 「全部」固定不动，不随值区滚出去 */
.filter-row__all {
  border: 0;
  font-family: inherit;
  flex: none;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #5b6c8f;
  background: #f0f4fb;
  cursor: pointer;
}

.filter-row__values {
  display: flex;
  gap: 8px;
  /* Q13：值区横向滚动，不折行 —— 行数 = 维度数，与值的多少无关 */
  overflow-x: auto;
  white-space: nowrap;
  scrollbar-width: thin;
}

.filter-value {
  border: 0;
  font-family: inherit;
  flex: none;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #5b6c8f;
  background: #f0f4fb;
  cursor: pointer;
}

.filter-row__all.active,
.filter-value.active {
  background: #eaf1ff;
  color: #2e7cd6;
  font-weight: 600;
}

.filter-row__all:hover,
.filter-value:hover {
  color: #2e7cd6;
}

@media (max-width: 768px) {
  .product-filter-bar {
    padding: 2px 12px;
  }
}

@media (max-width: 480px) {
  .filter-row {
    gap: 8px;
  }
}
</style>
