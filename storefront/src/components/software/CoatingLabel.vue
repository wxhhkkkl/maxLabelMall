<script setup lang="ts">
import type { CoatingExample } from '@/data/softwareIntroduction'
defineProps<{ product: CoatingExample; detailed?: boolean }>()
</script>
<template>
  <article
    class="coating-label"
    :class="{ 'is-detailed': detailed }"
    :aria-label="`${product.name}标签示例`"
  >
    <h3>{{ product.name }}</h3>
    <svg
      v-if="!detailed"
      class="label-pail"
      viewBox="0 0 100 90"
      aria-hidden="true"
    >
      <path
        d="M24 20h52l-4 59q-22 9-44 0Z"
        :fill="product.swatch"
        stroke="#8A97B5"
        stroke-width="1.2"
      />
      <path
        d="M27 25q-18 35 1 39M73 25q18 35-1 39"
        fill="none"
        stroke="#8A97B5"
        stroke-width="2"
      />
      <ellipse
        cx="50"
        cy="20"
        rx="28"
        ry="7"
        fill="#2E7CD6"
      /><ellipse
        cx="50"
        cy="18"
        rx="25"
        ry="4"
        fill="#35A5FF"
      />
      <path
        d="M33 30v42"
        stroke="#FFFFFF"
        stroke-width="4"
        opacity=".8"
      />
    </svg>
    <dl>
      <template v-if="detailed">
        <dt>型号</dt><dd>WX-210</dd>
      </template><dt>色号</dt><dd>{{ product.color }}</dd><dt>批次</dt><dd>{{ product.batch }}</dd><dt>净含量</dt><dd>20 kg</dd>
    </dl>
    <div
      class="label-barcode"
      aria-hidden="true"
    /><p class="label-code">
      {{ product.batch }}
    </p>
  </article>
</template>
<style scoped>
.coating-label { margin: 0; background: #FFFFFF; padding: 20px 16px 14px; border: 1px solid #E3E9F4; border-radius: 14px; color: #16233F; box-shadow: 0 6px 24px rgb(46 124 214 / 8%); }
.coating-label h3 { margin: 0; text-align: center; font-size: 21px; font-weight: 750; letter-spacing: -.04em; }
.label-pail { display: block; width: 88px; height: 78px; margin: 8px auto; }
.coating-label dl { display: grid; grid-template-columns: auto 1fr; gap: 6px 10px; margin: 12px 0; font-size: 13px; line-height: 1.6; }
.coating-label dd { margin: 0; white-space: nowrap; }
.label-barcode { height: 37px; background: repeating-linear-gradient(90deg, #16233F 0 2px, transparent 2px 4px, #16233F 4px 5px, transparent 5px 8px, #16233F 8px 12px, transparent 12px 14px, #16233F 14px 16px, transparent 16px 19px); }
.label-code { margin: 4px 0 0; text-align: center; font-size: 11px; letter-spacing: .12em; }
.is-detailed { width: 242px; padding: 24px 22px 14px; border-radius: 10px; transform: rotate(-3deg); box-shadow: 0 14px 34px rgb(12 33 72 / 18%); }
.is-detailed h3 { text-align: left; font-size: 26px; padding-bottom: 14px; border-bottom: 1px solid #E3E9F4; }
.is-detailed dl { font-size: 15px; gap: 7px 18px; }
.is-detailed .label-barcode { height: 48px; }
</style>
