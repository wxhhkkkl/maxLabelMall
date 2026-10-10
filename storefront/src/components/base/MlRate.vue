<script setup lang="ts">
import { computed } from 'vue'

/**
 * C11 五星评分。两种模式共用一个组件：**只读**（展示别人给的分数）与**可交互**（自己打分）。
 *
 * 只读时星是 `disabled` 的**按钮**而不是 `span` —— 只是样式变灰的话，
 * 键盘 Tab 与自动化仍能激活它，那等于"看起来点不动、实际点得动"。
 */
const props = withDefaults(
  defineProps<{ modelValue: number; readonly?: boolean; max?: number }>(),
  { readonly: false, max: 5 },
)

const emit = defineEmits<{ 'update:modelValue': [number] }>()

const stars = computed(() => Array.from({ length: props.max }, (_, i) => i + 1))

function pick(v: number) {
  if (props.readonly) return
  emit('update:modelValue', v)
}
</script>

<template>
  <span class="ml-rate" :class="{ 'is-readonly': readonly }">
    <button
      v-for="s in stars"
      :key="s"
      class="ml-star"
      :class="{ 'is-on': s <= modelValue }"
      type="button"
      :disabled="readonly"
      :aria-label="`${s} 分`"
      @click="pick(s)"
    >
      ★
    </button>
  </span>
</template>

<style scoped>
.ml-rate {
  display: inline-flex;
  gap: 2px;
  align-items: center;
}
.ml-star {
  padding: 0;
  border: none;
  background: none;
  font-size: 16px;
  line-height: 1;
  color: var(--ml-border-2);
  cursor: pointer;
}
.ml-star.is-on {
  color: var(--ml-orange);
}
.ml-rate.is-readonly .ml-star {
  cursor: default;
}
</style>
