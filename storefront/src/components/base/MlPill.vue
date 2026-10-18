<script setup lang="ts">
import { computed } from 'vue'

import type { OrderStatusValue } from '@/types'
import { orderStatusText, orderStatusVariant } from '@/utils/orderStatus'

/**
 * C6 状态标签。契约见 design-new-pages.md §2 与 utils/orderStatus.ts：
 * 变体由后端状态**显式映射**，不得自行命名。
 */
const props = defineProps<{
  status: OrderStatusValue | number
  /**
   * 文案覆盖。**只有一个用法**：对公转账「待收款」（008）—— 底层状态仍是
   * `0 未支付`（所以**变体不变**），只是客户侧的说辞不同，见 `utils/orderStatus.ts`
   * 的 `OFFLINE_WAITING_TEXT`。其余情况一律留空，走 `status` 的映射。
   */
  text?: string
}>()

const variant = computed(() => orderStatusVariant(props.status))
const text = computed(() => props.text ?? orderStatusText(props.status))
</script>

<template>
  <span class="ml-pill" :class="variant">{{ text }}</span>
</template>
