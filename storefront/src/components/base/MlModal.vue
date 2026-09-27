<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

/**
 * C1 模态弹层。契约见 design-new-pages.md §2：
 * 遮罩点击与 `Esc` 关闭；含 head / body / foot 三段；关闭按钮在右上角。
 */
const props = withDefaults(
  defineProps<{ open: boolean; title?: string; closeOnMask?: boolean }>(),
  { title: '', closeOnMask: true },
)
const emit = defineEmits<{ close: [] }>()

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

watch(
  () => props.open,
  (open) => {
    if (open) window.addEventListener('keydown', onKeydown)
    else window.removeEventListener('keydown', onKeydown)
  },
  { immediate: true },
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

function onMaskClick() {
  if (props.closeOnMask) emit('close')
}
</script>

<template>
  <div v-if="open" class="ml-modal-mask" role="dialog" aria-modal="true" @click.self="onMaskClick">
    <div class="ml-modal">
      <div class="ml-modal-head">{{ title }}</div>
      <button class="ml-modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      <div class="ml-modal-body"><slot /></div>
      <div class="ml-modal-foot"><slot name="foot" /></div>
    </div>
  </div>
</template>
