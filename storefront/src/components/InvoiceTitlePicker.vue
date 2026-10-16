<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import { listInvoiceTitles } from '@/api/invoice'
import InvoiceTitleDialog from '@/components/InvoiceTitleDialog.vue'
import { InvoiceTitleKind, type InvoiceTitle, type InvoiceTypeValue } from '@/types'

/**
 * 「从已保存的抬头里选一条 / 没有就当场新增」的**选择器**（Q36）。
 *
 * 结算页的发票卡片与订单详情的补开弹层**共用它** —— 两处的行为必须一致
 * （选一条就把字段填上；没有合适的就弹框新增，存完**自动选中**它，不必先跳去个人中心）。
 *
 * ⚠️ **只列与当前票种相符的抬头**（Q36：普票与专票分开管理）——
 * 选专票时列出普票抬头，客户点一下会得到一条填不全的专票信息，然后被后端拒。
 */
const props = defineProps<{ type: InvoiceTypeValue }>()
const emit = defineEmits<{ picked: [InvoiceTitle] }>()

/** 与本票种相符的抬头（拉取失败就当作没有 —— 客户当场手填照样能开票） */
const titles = ref<InvoiceTitle[]>([])
const pickedId = ref<number | null>(null)
const dialogOpen = ref(false)

async function load() {
  try {
    const all = await listInvoiceTitles()
    titles.value = all.filter((t) => t.type === props.type)
  } catch {
    titles.value = []
  }
}

/** 换票种要重新筛，并把选中态清掉（上一条多半不属于新票种） */
watch(() => props.type, () => {
  pickedId.value = null
  void load()
})

function pick(title: InvoiceTitle) {
  pickedId.value = title.id
  emit('picked', title)
}

/** 新增成功：重拉列表（后端为准），并把新存的那条**自动选中** */
async function onCreated() {
  const previousIds = new Set(titles.value.map((t) => t.id))
  await load()
  const created = titles.value.find((t) => !previousIds.has(t.id))
  if (created) {
    pick(created)
  }
}

onMounted(async () => {
  await load()
  // 有本票种的默认抬头 → **预先填好**（US23 场景 2）。只填字段，不替客户勾"要开票"
  const preferred = titles.value.find((t) => t.defaultStatus)
  if (preferred) {
    pick(preferred)
  }
})
</script>

<template>
  <!-- 一条已保存的抬头都没有时，也要给出「+ 新增抬头」——否则客户第一次来无路可走 -->
  <div
    class="invoice-picker"
    aria-label="已保存的发票抬头"
  >
    <div
      v-if="titles.length"
      class="invoice-picker-list"
    >
      <button
        v-for="title in titles"
        :key="title.id"
        type="button"
        class="invoice-picker-item"
        :class="{ 'is-active': pickedId === title.id }"
        :data-invoice-title="title.id"
        @click="pick(title)"
      >
        {{ title.titleName }}<span v-if="title.titleKind === InvoiceTitleKind.PERSONAL" class="invoice-picker-tag">个人</span><span v-if="title.defaultStatus" class="invoice-picker-tag">默认</span>
      </button>
    </div>
    <button
      type="button"
      class="invoice-picker-add"
      data-add-invoice-title
      @click="dialogOpen = true"
    >
      + 新增抬头
    </button>

    <InvoiceTitleDialog
      :open="dialogOpen"
      :type="props.type"
      @close="dialogOpen = false"
      @submitted="onCreated"
    />
  </div>
</template>

<style scoped>
.invoice-picker { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; align-items: center; }
.invoice-picker-list { display: flex; flex-wrap: wrap; gap: 8px; }
.invoice-picker-item { display: inline-flex; align-items: center; gap: 6px; border: 1px solid #e3e9f4; background: #ffffff; color: #16233f; border-radius: 4px; padding: 6px 12px; font-size: 12px; cursor: pointer; }
.invoice-picker-item.is-active { border-color: #2e7cd6; color: #2e7cd6; background: #f0f4fb; }
.invoice-picker-tag { color: #8a97b5; font-size: 11px; }
.invoice-picker-add { border: 1px dashed #b4c0d8; background: transparent; color: #5b6c8f; border-radius: 4px; padding: 6px 12px; font-size: 12px; cursor: pointer; }
.invoice-picker-add:hover { border-color: #2e7cd6; color: #2e7cd6; }
@media (max-width: 480px) {
  .invoice-picker-item { flex: 1 1 48%; justify-content: center; }
}
</style>
