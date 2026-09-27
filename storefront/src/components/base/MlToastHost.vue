<script setup lang="ts">
import { useToasts } from './useToasts'

/** C2 轻提示的渲染宿主，全站挂一个即可 */
const { items, dismiss } = useToasts()
</script>

<template>
  <div class="ml-toast-wrap" aria-live="polite">
    <div
      v-for="t in items"
      :key="t.id"
      class="ml-toast"
      :class="`is-${t.kind}`"
      @click="dismiss(t.id)"
    >
      {{ t.text }}
    </div>
  </div>
</template>
