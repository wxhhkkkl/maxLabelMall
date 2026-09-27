<script setup lang="ts">
import { computed } from 'vue'

import { isPending } from '@/data/placeholders'

/**
 * 渲染业务文案，并**从值本身派生** `data-content-pending`。
 *
 * 视图 MUST 用它渲染任何来自 `@/data/placeholders` 的文案 —— 不得直接插值、
 * 更不得把业务文案写死在模板里。这样：
 *   · 替换业务方文案时**只改 placeholders.ts 一个文件**，视图一行都不用动；
 *   · 门禁② 扫描 DOM 的 `[data-content-pending]` 就能发现未替换的内容。
 */
const props = withDefaults(defineProps<{ value: string; tag?: string }>(), { tag: 'span' })

const pending = computed(() => isPending(props.value))
</script>

<template>
  <component :is="tag" :data-content-pending="pending || undefined">{{ value }}</component>
</template>
