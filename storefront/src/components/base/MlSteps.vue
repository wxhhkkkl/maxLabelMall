<script setup lang="ts">
/**
 * C7 步骤条。契约见 design-new-pages.md §2：
 * 序号圆点 24px；已完成实心、当前描边、未达灰；连接线 1px。
 */
const props = defineProps<{ steps: string[]; current: number }>()

type State = 'done' | 'current' | 'todo'
function stateOf(i: number): State {
  if (i < props.current) return 'done'
  if (i === props.current) return 'current'
  return 'todo'
}
</script>

<template>
  <div class="ml-steps">
    <template v-for="(s, i) in steps" :key="s">
      <div class="ml-step" :class="`is-${stateOf(i)}`">
        <span class="ml-step-dot">{{ i + 1 }}</span>
        <span>{{ s }}</span>
      </div>
      <span v-if="i < steps.length - 1" class="ml-step-line" />
    </template>
  </div>
</template>
