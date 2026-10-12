<script setup lang="ts">
defineProps<{ name: string }>()
const paths: Record<string, string> = {
  menu: 'M4 6h16M4 12h16M4 18h16',
  chevron: 'm9 5 7 7-7 7',
  labels: 'M15 5H9a7 7 0 1 0 0 14h6M9 5c4 0 7 3 7 7s-3 7-7 7M16 12h5v7h-6M12 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  ribbon: 'M7 4h10M7 8h10M5 6v12c0 2 14 2 14 0V6M19 6c0 3-14 3-14 0s14-3 14 0ZM8 17h8',
  printer: 'M6 8V3h12v5M6 17H3V9h18v8h-3M6 13h12v8H6ZM17 11h1M9 16h6M9 18h4',
  sheet: 'M6 3h9l4 4v14H5V3h1ZM14 3v5h5M8 11h8M8 14h8M8 17h6',
  scanner: 'm5 3 13 3 2 6-7 2-2 7H6l3-9-4-3ZM6 6l11 3M4 17l-2 2M17 16l3 2M16 20l3 1',
  software: 'M3 4h18v13H3ZM8 21h8M12 17v4M6 7h6M6 10h4M14 7h4v7h-4ZM6 13h4',
  rfid: 'M9 7h6v10H9ZM11 10h2v4h-2M6 8c-2 2-2 6 0 8M3 5c-4 4-4 10 0 14M18 8c2 2 2 6 0 8M21 5c4 4 4 10 0 14',
  cart: 'M2 3h3l3 13h11l3-10H6M9 20h.01M18 20h.01',
  order: 'M6 3h12v18H6ZM9 7h6M9 11h6M9 15h6M9 18h4',
  payment: 'M3 5h18v14H3ZM3 9h18M6 15h4',
  truck: 'M2 5h12v13H2ZM14 10h4l4 5v3h-8M7 18a2 2 0 1 0 0 .01M18 18a2 2 0 1 0 0 .01',
  support: 'M4 13v-2a8 8 0 0 1 16 0v2M4 11H2v7h4v-7ZM20 11h2v7h-4v-7ZM20 18c0 3-3 3-7 3',
}
</script>

<template>
  <svg
    class="mall-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  ><path :d="paths[name] || paths.labels" /></svg>
</template>
