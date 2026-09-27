<script setup lang="ts">
import { computed } from 'vue'

import type { OrderStatusValue } from '@/types'
import { orderStatusText, orderStatusVariant } from '@/utils/orderStatus'

/**
 * C6 状态标签。契约见 design-new-pages.md §2 与 utils/orderStatus.ts：
 * 变体由后端状态**显式映射**，不得自行命名。
 */
const props = defineProps<{ status: OrderStatusValue | number }>()

const variant = computed(() => orderStatusVariant(props.status))
const text = computed(() => orderStatusText(props.status))
</script>

<template>
  <span class="ml-pill" :class="variant">{{ text }}</span>
</template>
