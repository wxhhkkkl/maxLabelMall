<script setup lang="ts">
import { computed } from 'vue'

import { displayText, isPending } from '@/data/placeholders'

/**
 * 渲染业务文案，并**从值本身派生** `data-content-pending`。
 *
 * 视图 MUST 用它渲染任何来自 `@/data/placeholders` 的文案 —— 不得直接插值、
 * 更不得把业务文案写死在模板里。这样：
 *   · 替换业务方文案时**只改 placeholders.ts 一个文件**，视图一行都不用动；
 *   · 门禁② 扫描 DOM 的 `[data-content-pending]` 就能发现未替换的内容。
 *
 * ⚠️ 显示的是 `displayText(value)`，即**去掉种子文件里包裹的 `[[ ]]`** ——
 * 页面上不该看到方括号。去掉的是显示，"未确认"仍由 `data-content-pending` 属性
 * 表达，未替换的内容照样能被门禁② 扫出来。
 */
const props = withDefaults(defineProps<{ value: string; tag?: string }>(), { tag: 'span' })

const pending = computed(() => isPending(props.value))
const text = computed(() => displayText(props.value))
</script>

<template>
  <component :is="tag" :data-content-pending="pending || undefined">{{ text }}</component>
</template>
