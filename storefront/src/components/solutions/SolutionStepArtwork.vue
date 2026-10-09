<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ industry?: string; index?: number; kind?: string }>()
const workflow: Record<string, string[]> = {
  warehouse: ['document', 'shelf', 'check', 'box'],
  manufacturing: ['boxes', 'gear', 'check', 'box'],
  apparel: ['hanger', 'tag', 'box', 'store'],
  medical: ['document', 'check', 'printer', 'archive'],
  food: ['bottle', 'check', 'barcode', 'box'],
  retail: ['store', 'tag', 'printer', 'check'],
  crossborder: ['document', 'globe', 'tag', 'box'],
  bakery: ['wheat', 'clock', 'bag', 'store'],
  catering: ['truck', 'bowl', 'snowflake', 'check'],
  semiconductor: ['chip', 'reel', 'gear', 'box'],
}
const drawings: Record<string, string> = {
  document: 'M7 3h7l4 4v14H6V3h1m7 0v5h4M9 12h6M9 16h6',
  tag: 'M3 4h8l10 10-7 7L3 10V4m4 2v.1',
  printer: 'M7 9V3h10v6M7 17H3V9h18v8h-4M7 14h10v7H7v-7m10-2h1',
  check: 'M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6l-9-4m-5 10 3 3 7-7',
  box: 'm3 7 9-5 9 5v10l-9 5-9-5V7m0 0 9 5 9-5M12 12v10M7 4.5l10 5.5',
  boxes: 'M8 3h8v7H8V3M3 12h8v9H3v-9m10 0h8v9h-8v-9M12 3v3M7 12v3m10-3v3',
  shelf: 'M4 3v18m16-18v18M4 11h16M4 20h16M7 5h4v6H7V5m6 0h4v6h-4V5M7 14h10v6H7v-6',
  gear: 'm9 2-1 3-3-1-2 3 2 2-1 3-2 1 1 4 3 0 2 2 0 3h4l1-3 3 1 2-3-2-2 1-3 2-1-1-4-3 0-2-2V2H9m3 6a4 4 0 1 1 0 8 4 4 0 0 1 0-8',
  hanger: 'M9 5a3 3 0 1 1 5 2l-2 2v2L2 18v2h20v-2l-10-7',
  archive: 'M3 3h18v5H3V3m2 5v13h14V8M9 12h6',
  bottle: 'M9 2h6v4l2 3v12H7V9l2-3V2M9 6h6M7 12h10M7 17h10',
  barcode: 'M3 4v16M6 4v16M10 4v16m3-16v16m3-16v16m5-16v16m-3-16v16',
  store: 'M4 10v11h16V10M2 10l3-7h14l3 7M2 10c1 3 4 3 5 0 1 3 4 3 5 0 1 3 4 3 5 0 1 3 4 3 5 0M9 21v-6h6v6',
  globe: 'M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20m0 0c-6 5-6 15 0 20 6-5 6-15 0-20M2 12h20M4 6h16M4 18h16',
  wheat: 'M12 22V4m0 5C4 9 4 3 4 3s8 0 8 6m0 6C4 15 4 9 4 9s8 0 8 6m0-6c8 0 8-6 8-6s-8 0-8 6m0 6c8 0 8-6 8-6s-8 0-8 6',
  clock: 'M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20m0 4v6l4 3',
  bag: 'M5 7h14l2 14H3L5 7m3 0V5a4 4 0 0 1 8 0v2',
  truck: 'M2 4h12v13H2V4m12 5h4l4 4v4h-8M7 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0m14 0a2 2 0 1 1-4 0 2 2 0 0 1 4 0',
  bowl: 'M2 11h20c0 7-4 10-10 10S2 18 2 11m5-3c3-3-2-3 1-6m4 6c3-3-2-3 1-6m4 6c3-3-2-3 1-6',
  snowflake: 'M12 2v20M3 7l18 10M3 17 21 7M8 4l4 4 4-4M8 20l4-4 4 4M3 11l5-1-1-5m10 14-1-5 5-1M3 13l5 1-1 5m10-14-1 5 5 1',
  chip: 'M6 6h12v12H6V6m3 3h6v6H9V9M9 2v4m6-4v4M9 18v4m6-4v4M2 9h4m-4 6h4m12-6h4m-4 6h4',
  reel: 'M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20m0 7a3 3 0 1 1 0 6 3 3 0 0 1 0-6M12 2v3m0 14v3M2 12h3m14 0h3',
  link: 'm10 8 3-3a5 5 0 0 1 7 7l-3 3M14 16l-3 3a5 5 0 0 1-7-7l3-3m1 7 8-8',
}
const drawing = computed(() => drawings[props.kind ?? workflow[props.industry ?? '']?.[props.index ?? 0] ?? 'document'])
</script>

<template>
  <svg
    class="solution-step-art"
    viewBox="0 0 180 120"
    aria-hidden="true"
  >
    <g
      transform="translate(63 30) scale(2.4)"
      fill="none"
      stroke="#2E7CD6"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <path :d="drawing" />
    </g>
  </svg>
</template>
