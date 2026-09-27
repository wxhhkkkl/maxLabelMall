<script setup lang="ts">
import { computed } from 'vue'

import { formatYuan } from '@/utils/money'

/**
 * C8 金额行。契约见 design-new-pages.md §2：
 * 左标签 + 右数值（等宽数字）；合计行加粗橙色。
 * **金额一律以「分」传入**，展示由 money.ts 统一换算。
 */
const props = withDefaults(
  defineProps<{ label: string; fen: number; total?: boolean; cut?: boolean }>(),
  { total: false, cut: false },
)

const text = computed(() => formatYuan(props.fen))
</script>

<template>
  <div class="ml-amount-row" :class="{ 'is-total': total }">
    <span class="k">{{ label }}</span>
    <span class="v" :class="{ 'is-cut': cut }">{{ text }}</span>
  </div>
</template>
