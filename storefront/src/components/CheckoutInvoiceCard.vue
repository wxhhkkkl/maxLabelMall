<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { listInvoiceTitles } from '@/api/invoice'
import InvoiceFieldsForm from '@/components/InvoiceFieldsForm.vue'
import MlCheck from '@/components/base/MlCheck.vue'
import type { InvoiceApplyForm, InvoiceTitle } from '@/types'
import { invoiceFieldError } from '@/utils/invoice'

/**
 * 结算页的「发票」卡片（US19 / FR-153 + US23 / FR-164）。
 *
 * **默认不开票** —— 不勾选时下单请求里没有 `invoice` 字段，行为与改动前逐字一致（FR-159）。
 *
 * ⚠️ **实付 0 元（全额券抵扣）时整块由调用方决定不渲染**：0 元订单没有可开票金额
 * （spec 的 Edge Case），而"实付多少"只有结算响应知道 —— 所以这个判断在 CheckoutView 里，
 * 不在本组件里（组件不该自己去算钱）。
 */
const enabled = defineModel<boolean>('enabled', { required: true })
const form = defineModel<InvoiceApplyForm>('form', { required: true })

/** 已保存的抬头（US23）。**拉不到就当作没有** —— 客户当场填照样能开票，不能因此卡住下单 */
const titles = ref<InvoiceTitle[]>([])
const pickedTitleId = ref<number | null>(null)

const error = computed(() => (enabled.value ? invoiceFieldError(form.value, form.value.type) : null))

/** 把一条抬头填进表单（票种保留客户当前的选择 —— 抬头不绑票种） */
function fillFrom(title: InvoiceTitle) {
  form.value = {
    type: form.value.type,
    titleName: title.titleName,
    taxNo: title.taxNo ?? '',
    titleAddress: title.titleAddress ?? '',
    titlePhone: title.titlePhone ?? '',
    bankName: title.bankName ?? '',
  }
}

/** 选中已保存的一条 */
function pickTitle(title: InvoiceTitle) {
  pickedTitleId.value = title.id
  fillFrom(title)
}

/** 改回"自己填"：清空字段，避免看起来还选着什么 */
function fillManually() {
  pickedTitleId.value = null
  form.value = {
    type: form.value.type,
    titleName: '',
    taxNo: '',
    titleAddress: '',
    titlePhone: '',
    bankName: '',
  }
}

onMounted(async () => {
  try {
    titles.value = await listInvoiceTitles()
  } catch {
    titles.value = [] // 降级：不给选，但照常能手填
  }
  // 有默认抬头 → 预先填好（US23 场景 2）。**只填字段，不替客户勾开票**
  const preferred = titles.value.find(t => t.defaultStatus)
  if (preferred) {
    pickTitle(preferred)
  }
})
</script>

<template>
  <div class="ml-card invoice-card">
    <div class="ml-card-title">发票</div>
    <MlCheck v-model="enabled">
      需要开发票
    </MlCheck>
    <p class="invoice-note">
      默认不开票。需要发票请勾选并填写开票信息，电子发票开出后可在订单详情里下载。
    </p>

    <template v-if="enabled">
      <!-- 已保存的抬头（有才显示整块）：点一条就填好，省得每次重打 -->
      <div
        v-if="titles.length"
        class="invoice-saved"
        aria-label="已保存的发票抬头"
      >
        <button
          v-for="title in titles"
          :key="title.id"
          type="button"
          class="invoice-saved-item"
          :class="{ 'is-active': pickedTitleId === title.id }"
          :data-invoice-title="title.id"
          @click="pickTitle(title)"
        >
          {{ title.titleName }}<span v-if="title.defaultStatus" class="invoice-saved-tag">默认</span>
        </button>
        <button
          type="button"
          class="invoice-saved-item"
          :class="{ 'is-active': pickedTitleId === null }"
          data-invoice-title="none"
          @click="fillManually"
        >
          重新填写
        </button>
      </div>

      <InvoiceFieldsForm v-model="form" />
      <p
        v-if="error"
        class="ml-error invoice-error"
        role="alert"
      >
        {{ error }}
      </p>
    </template>
  </div>
</template>

<style scoped>
.invoice-note { margin: 10px 0 16px; color: #8a97b5; font-size: 12px; line-height: 1.8; }
.invoice-error { margin-top: 12px; }
.invoice-saved { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.invoice-saved-item { display: inline-flex; align-items: center; gap: 6px; border: 1px solid #e3e9f4; background: #ffffff; color: #16233f; border-radius: 4px; padding: 6px 12px; font-size: 12px; cursor: pointer; }
.invoice-saved-item.is-active { border-color: #2e7cd6; color: #2e7cd6; background: #f0f4fb; }
.invoice-saved-tag { color: #8a97b5; font-size: 11px; }
@media (max-width: 480px) {
  .invoice-saved-item { flex: 1 1 48%; justify-content: center; }
}
</style>
