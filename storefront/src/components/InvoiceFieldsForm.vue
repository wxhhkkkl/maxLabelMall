<script setup lang="ts">
import { computed } from 'vue'

import MlField from '@/components/base/MlField.vue'
import { InvoiceType, type InvoiceApplyForm } from '@/types/invoice'
import { INVOICE_TYPE_OPTIONS } from '@/utils/invoice'

/**
 * 开票信息的字段表单：**票种 + 抬头五件套**。
 *
 * 三个地方共用它：结算页的发票卡片、订单详情的补开弹层、个人中心的抬头簿弹层 ——
 * 各写一份必然在某次改动后分叉（placeholder、必填提示都不一样了）。
 *
 * ⚠️ **只负责"填"，不负责"判"**：必填与格式的判定在 `utils/invoice.ts`
 * （`invoiceFieldError` 按票种判、`invoiceTitleFieldError` 只判名称），由调用方决定何时展示与拦截。
 */
const props = withDefaults(defineProps<{ showType?: boolean }>(), { showType: true })
const model = defineModel<InvoiceApplyForm>({ required: true })

/** 专票才要那四项 —— 与后端「专票五项全必填」的矩阵一致（Q27） */
const isSpecial = computed(() => model.value.type === InvoiceType.SPECIAL)
/** 票种被隐藏时（抬头簿），税号不设为必填：一条抬头两种票都能用 */
const taxNoRequired = computed(() => props.showType && isSpecial.value)
/** 地址 / 电话 / 开户行**始终显示**（抬头可以不完整，客户愿意填就该让他填） */
const showExtraFields = computed(() => !props.showType || isSpecial.value)
</script>

<template>
  <div class="invoice-fields">
    <template v-if="showType">
      <div
        class="invoice-types"
        role="radiogroup"
        aria-label="发票类型"
      >
        <button
          v-for="option in INVOICE_TYPE_OPTIONS"
          :key="option.value"
          type="button"
          class="invoice-type"
          role="radio"
          :aria-checked="model.type === option.value"
          :class="{ 'is-active': model.type === option.value }"
          :data-invoice-type="option.value"
          @click="model.type = option.value"
        >
          {{ option.label }}
        </button>
      </div>
      <p class="invoice-type-hint">
        {{ INVOICE_TYPE_OPTIONS.find(o => o.value === model.type)?.hint }}
      </p>
    </template>

    <MlField
      label="抬头名称"
      required
    >
      <input
        id="invoiceTitleName"
        v-model="model.titleName"
        class="ml-input"
        placeholder="公司名称或个人姓名"
        autocomplete="off"
      >
    </MlField>

    <MlField
      :label="taxNoRequired ? '纳税人识别号' : '纳税人识别号（选填）'"
      :required="taxNoRequired"
    >
      <input
        id="invoiceTaxNo"
        v-model="model.taxNo"
        class="ml-input"
        placeholder="15 / 18 / 20 位数字或大写字母"
        autocomplete="off"
      >
    </MlField>

    <template v-if="showExtraFields">
      <MlField label="注册地址">
        <input
          id="invoiceTitleAddress"
          v-model="model.titleAddress"
          class="ml-input"
          placeholder="营业执照上的注册地址"
          autocomplete="off"
        >
      </MlField>
      <MlField label="注册电话">
        <input
          id="invoiceTitlePhone"
          v-model="model.titlePhone"
          class="ml-input"
          placeholder="注册预留电话"
          autocomplete="off"
        >
      </MlField>
      <MlField label="开户行及账号">
        <input
          id="invoiceBankName"
          v-model="model.bankName"
          class="ml-input"
          placeholder="例如：工商银行南京分行 6222 0000 0000 0000"
          autocomplete="off"
        >
      </MlField>
    </template>
  </div>
</template>

<style scoped>
.invoice-types { display: flex; gap: 10px; flex-wrap: wrap; }
.invoice-type { border: 1px solid #e3e9f4; background: #ffffff; color: #16233f; border-radius: 4px; padding: 8px 14px; font-size: 13px; cursor: pointer; }
.invoice-type.is-active { border-color: #2e7cd6; color: #2e7cd6; background: #f0f4fb; }
.invoice-type-hint { margin: 8px 0 14px; color: #8a97b5; font-size: 12px; }
@media (max-width: 480px) {
  .invoice-type { flex: 1 1 100%; text-align: center; }
}
</style>
