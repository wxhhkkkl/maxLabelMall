<script setup lang="ts">
import type { LabelSample } from '@/data/industrySolutions'

defineProps<{ sample: LabelSample; image?: string }>()
// Decorative sample bars only: production barcodes must be generated from actual data.
const bars = [2, 1, 3, 1, 2, 4, 1, 2, 1, 3, 2, 1, 4, 1, 2, 3, 1, 2, 4, 2, 1, 3, 1, 2, 2, 1, 4]
</script>

<template>
  <figure class="solution-label-example">
    <div
      v-if="image"
      class="solution-label-photo"
    >
      <img
        :src="image"
        :alt="sample.name + '在实际包装或商品上的应用示意'"
        loading="lazy"
        decoding="async"
      >
    </div>
    <div
      v-else
      class="solution-label-paper"
    >
      <strong>{{ sample.title }}</strong>
      <dl>
        <div
          v-for="[label, value] in sample.fields"
          :key="label"
        >
          <dt>{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>
      <div
        class="solution-label-bars"
        aria-hidden="true"
      >
        <span
          v-for="(width, index) in bars"
          :key="index"
          :style="{ flexGrow: width }"
        />
      </div>
      <span class="solution-label-code">{{ sample.code }}</span>
    </div>
    <figcaption>{{ sample.name }} <span>示意标签 · 条码仅作展示</span></figcaption>
  </figure>
</template>
