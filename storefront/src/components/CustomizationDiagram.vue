<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(defineProps<{
  kind?: 'label' | 'material' | 'layout' | 'core' | 'corner'
  mode?: number | null
  material?: string
  form?: number | null
  width?: number | null
  height?: number | null
  columns?: number
  rows?: number
  rounded?: boolean
  tearLine?: boolean
  sheetSpec?: string
  axis?: string
}>(), { kind: 'label', mode: 1, material: '', form: 2, width: 60, height: 40,
  columns: 2, rows: 3, sheetSpec: 'A4', rounded: true, tearLine: false, axis: '25.4mm' })

const patternId = useId()
const silver = computed(() => /银/.test(props.material))
const transparent = computed(() => /透明/.test(props.material))
const cols = computed(() => Math.min(8, Math.max(1, props.columns)))
const rows = computed(() => Math.min(6, Math.max(1, props.form === 1 ? 3 : props.rows)))
// 卷筒为连续展开示意，横向始终铺满；平张按实际毫米尺寸缩放到纸张中。
const paperWidth = computed(() => props.sheetSpec === 'A4' ? 210 : props.columns * ((props.width || 60) + 3))
const paperHeight = computed(() => props.sheetSpec === 'A4' ? 297 : props.rows * ((props.height || 40) + 3))
const sheetScale = computed(() => Math.min(184 / paperWidth.value, 140 / paperHeight.value))
const paper = computed(() => props.form === 1
  ? { x: 37, y: 43, width: 206, height: 149 }
  : { x: 140 - paperWidth.value * sheetScale.value / 2, y: 43, width: paperWidth.value * sheetScale.value, height: paperHeight.value * sheetScale.value })
const cellWidth = computed(() => props.form === 1 ? (184 - (cols.value - 1) * 5) / cols.value : (props.width || 60) * sheetScale.value)
const cellHeight = computed(() => props.form === 1 ? (137 - (rows.value - 1) * 5) / rows.value : (props.height || 40) * sheetScale.value)
const gap = computed(() => props.form === 1 ? 5 : 3 * sheetScale.value)
const startX = computed(() => props.form === 1 ? 48 : paper.value.x + 1.5 * sheetScale.value)
const startY = computed(() => props.form === 1 ? 49 : paper.value.y + 1.5 * sheetScale.value)
const labelHeight = computed(() => Math.max(38, Math.min(104, 140 * ((props.height || 40) / (props.width || 60)))))
const title = computed(() => props.kind === 'layout' ? `${props.form === 1 ? '卷筒' : '平张'}排版：${props.columns}列${props.form === 2 ? `，${props.rows}行` : ''}，${props.width || '待填写'}乘${props.height || '待填写'}毫米${props.tearLine ? '，含撕裂线' : ''}` : props.kind === 'core' ? `轴芯内径 ${props.axis}` : props.kind === 'material' ? `${props.material}材质示意` : props.kind === 'corner' ? `${props.rounded ? '圆角' : '直角'}标签` : ['','彩色内容标签','黑色可变数据标签','彩色底加黑色可变数据','空白标签'][props.mode || 4])
</script>

<template>
  <svg class="customization-diagram" viewBox="0 0 280 210" role="img" :aria-label="title">
    <title>{{ title }}</title>
    <defs>
      <pattern :id="patternId" width="12" height="12" patternUnits="userSpaceOnUse"><rect width="12" height="12" fill="#ffffff" /><path d="M0 0h6v6H0zM6 6h6v6H6z" fill="#e3e9f4" /></pattern>
    </defs>
    <template v-if="kind === 'core'">
      <path d="M65 90v42c0 39 150 39 150 0V90" fill="#e3e9f4" stroke="#8a97b5" stroke-width="2" />
      <ellipse cx="140" cy="90" rx="75" ry="38" fill="#f0f4fb" stroke="#8a97b5" stroke-width="2" />
      <ellipse cx="140" cy="90" :rx="axis === '76.2mm' ? 44 : axis === '40mm' ? 30 : 20" ry="17" fill="#ffffff" stroke="#5b6c8f" stroke-width="2" />
      <path d="M112 90h56m-56-5v10m56-10v10" stroke="#2e7cd6" stroke-width="2" />
      <text x="140" y="185" text-anchor="middle" fill="#2e7cd6" font-size="15">内径 {{ axis }}</text>
    </template>
    <template v-else-if="kind === 'layout'">
      <path v-if="form === 1" d="M39 42C39 15 241 15 241 42v18H39z" fill="#e3e9f4" stroke="#b4c0d8" />
      <rect :x="paper.x" :y="paper.y" :width="paper.width" :height="paper.height" rx="3" fill="#f0f4fb" stroke="#b4c0d8" />
      <defs><clipPath :id="`${patternId}-paper`"><rect :x="paper.x" :y="paper.y" :width="paper.width" :height="paper.height" /></clipPath></defs>
      <g :clip-path="`url(#${patternId}-paper)`">
      <g v-for="r in rows" :key="r">
        <rect v-for="c in cols" :key="c" class="layout-label" :x="startX + (c - 1) * (cellWidth + gap)" :y="startY + (r - 1) * (cellHeight + gap)" :width="cellWidth" :height="cellHeight" :rx="rounded ? Math.min(6, cellWidth / 5) : 0" :fill="silver ? '#b4c0d8' : transparent ? `url(#${patternId})` : '#ffffff'" stroke="#8a97b5" />
        <path v-if="tearLine && r < rows" :d="`M${paper.x} ${startY + r * (cellHeight + gap) - gap / 2}h${paper.width}`" stroke="#2e7cd6" stroke-dasharray="4 3" />
      </g>
      </g>
      <path :d="`M${paper.x} 34h${paper.width}m-${paper.width}-4v8m${paper.width}-8v8`" stroke="#2e7cd6" />
      <text x="140" y="25" text-anchor="middle" fill="#2e7cd6" font-size="12">{{ form === 1 ? `${columns} 列 · 间距 3 mm` : `${sheetSpec === 'A4' ? 'A4 · ' : ''}${paperWidth} × ${paperHeight} mm` }}</text>
      <text x="140" y="207" text-anchor="middle" fill="#5b6c8f" font-size="11">{{ form === 1 ? '连续卷筒展开 · 非实际比例' : `平张按比例展示${props.rows > 6 ? ' · 仅示前6行' : ''}` }}</text>
    </template>
    <template v-else>
      <rect x="44" y="43" width="195" height="126" rx="10" fill="#e3e9f4" transform="rotate(-6 140 105)" />
      <rect x="50" :y="105 - labelHeight / 2" width="180" :height="labelHeight" :rx="rounded ? 12 : 0" :fill="silver ? '#b4c0d8' : transparent ? `url(#${patternId})` : '#ffffff'" stroke="#b4c0d8" stroke-width="1.5" />
      <template v-if="kind === 'material'">
        <path d="M198 48l32 32h-20q-12 0-12-12z" fill="#f0f4fb" stroke="#b4c0d8" />
        <path d="M74 90h96m-96 13h64" stroke="#b4c0d8" stroke-width="4" stroke-linecap="round" />
        <text x="75" y="133" fill="#5b6c8f" font-size="13">{{ transparent ? '透明质感' : silver ? '哑银质感' : /移除/.test(material) ? '可移除系列' : /热敏/.test(material) ? '热敏纸系列' : /合成/.test(material) ? '合成纸系列' : /PET/.test(material) ? 'PET 系列' : '纸质标签' }}</text>
      </template>
      <template v-else-if="kind === 'label' && mode !== 4">
        <rect v-if="mode !== 2" x="50" :y="105 - labelHeight / 2" width="49" :height="labelHeight" :rx="rounded ? 10 : 0" fill="#2e7cd6" />
        <circle v-if="mode !== 2" cx="74" cy="95" r="12" fill="#eaf1ff" />
        <path v-if="mode !== 2" d="M67 95l5 5 10-12" fill="none" stroke="#2e7cd6" stroke-width="3" />
        <text :x="mode === 2 ? 69 : 111" y="91" fill="#16233f" font-size="12" font-weight="600">{{ mode === 2 ? 'PRODUCT 001' : 'MAXLABEL' }}</text>
        <template v-if="mode === 2 || mode === 3">
          <path v-for="i in 24" :key="i" :d="`M${(mode === 2 ? 70 : 113) + i * 3.7} 103v24`" stroke="#16233f" :stroke-width="i % 3 === 0 ? 2.5 : 1" />
        </template>
        <template v-else><path d="M112 108h88m-88 12h58" stroke="#8a97b5" stroke-width="3" /><circle cx="203" cy="133" r="7" fill="#ff5c22" /></template>
      </template>
      <template v-if="kind === 'corner'">
        <path :d="rounded ? 'M50 94V69q0-16 16-16h25' : 'M50 94V53h41'" fill="none" stroke="#2e7cd6" stroke-width="4" />
        <text x="140" y="113" text-anchor="middle" fill="#5b6c8f" font-size="16">{{ rounded ? '圆角' : '直角' }}</text>
      </template>
      <template v-if="kind === 'label'">
        <path d="M50 182h180m-180-4v8m180-8v8" stroke="#2e7cd6" />
        <text x="140" y="202" text-anchor="middle" fill="#5b6c8f" font-size="12">{{ width || '宽' }} × {{ height || '高' }} mm · 示意</text>
      </template>
    </template>
  </svg>
</template>

<style scoped>
.customization-diagram { display: block; width: 100%; height: auto; }
</style>
