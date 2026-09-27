<script setup lang="ts">
/**
 * C4 表单字段外壳：标签 + 控件 + 错误/提示。契约见 design-new-pages.md §2。
 * 控件由调用方以默认插槽传入（input / select / textarea）。
 */
withDefaults(defineProps<{ label?: string; required?: boolean; error?: string; hint?: string }>(), {
  label: '',
  required: false,
  error: '',
  hint: '',
})
</script>

<template>
  <div class="ml-field" :class="{ 'is-error': !!error }">
    <label v-if="label" class="ml-label">
      {{ label }}<span v-if="required" class="req">*</span>
    </label>
    <slot />
    <p v-if="error" class="ml-error">{{ error }}</p>
    <p v-else-if="hint" class="ml-hint">{{ hint }}</p>
  </div>
</template>
