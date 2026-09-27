<script setup lang="ts">
/**
 * C3 复选框。契约见 design-new-pages.md §2：
 * 16×16、半径 2px、选中填充品牌色 + 白色对勾；**禁用态降透明度且不可点**。
 */
const props = withDefaults(defineProps<{ modelValue: boolean; disabled?: boolean }>(), {
  disabled: false,
})
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

function toggle() {
  if (props.disabled) return
  emit('update:modelValue', !props.modelValue)
}
</script>

<template>
  <label class="ml-check" :class="{ 'is-disabled': disabled }">
    <input
      type="checkbox"
      :checked="modelValue"
      :disabled="disabled"
      @change="toggle"
    />
    <span class="ml-check-box" />
    <span v-if="$slots.default" class="ml-check-label"><slot /></span>
  </label>
</template>
