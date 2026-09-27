<script setup lang="ts">
/**
 * 空状态与错误态（FR-044 / FR-045）。
 *
 * 两种模式共用一套版式：`empty` 用于「确实没有数据」，`error` 用于「请求失败」
 * 且必须给出**可重试**入口 —— 本站不允许出现白屏或永久加载态。
 */
withDefaults(
  defineProps<{
    mode?: 'empty' | 'error'
    icon?: string
    title: string
    desc?: string
    actionText?: string
  }>(),
  { mode: 'empty', icon: '🔍', title: '', desc: '', actionText: '' },
)
const emit = defineEmits<{ action: [] }>()
</script>

<template>
  <div class="empty-state ml-empty">
    <div class="es-icon">{{ mode === 'error' ? '⚠️' : icon }}</div>
    <div class="es-title">{{ title }}</div>
    <p v-if="desc" class="es-desc">{{ desc }}</p>
    <button v-if="actionText" class="es-reset" type="button" @click="emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>
