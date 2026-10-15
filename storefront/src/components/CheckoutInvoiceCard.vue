<script setup lang="ts">
import { computed } from 'vue'

import InvoiceFieldsForm from '@/components/InvoiceFieldsForm.vue'
import InvoiceTitlePicker from '@/components/InvoiceTitlePicker.vue'
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

/**
 * 实时校验提示：**只有勾了开票才提示**（没开票时不打扰客户）。
 *
 * 提交时的拦截在 CheckoutView —— 两处用的是**同一个纯函数**，不会各说各话。
 */
const error = computed(() => (enabled.value ? invoiceFieldError(form.value) : null))

/**
 * 把一条抬头填进表单（票种保留客户当前的选择 —— 选择器只列本票种的抬头）
 */
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
</script>

<template>
  <div class="ml-card invoice-card">
    <div class="ml-card-title">
      发票
    </div>
    <MlCheck v-model="enabled">
      需要开发票
    </MlCheck>
    <p class="invoice-note">
      默认不开票。需要发票请勾选并填写开票信息，电子发票开出后可在订单详情里下载。
    </p>

    <template v-if="enabled">
      <!-- 已保存的抬头（按当前票种筛）+ 没有就当场新增（Q36） -->
      <InvoiceTitlePicker
        :type="form.type"
        @picked="fillFrom"
      />

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
</style>
