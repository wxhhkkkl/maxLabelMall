<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { createOrderInvoice } from '@/api/invoice'
import InvoiceFieldsForm from '@/components/InvoiceFieldsForm.vue'
import MlModal from '@/components/base/MlModal.vue'
import { InvoiceTitleKind, InvoiceType, type InvoiceApplyForm } from '@/types/invoice'
import { invoiceFieldError } from '@/utils/invoice'

/**
 * 订单详情**补开票**弹层（US20 / FR-163）。
 *
 * 字段与校验**复用结算页那一套**（`InvoiceFieldsForm` + `utils/invoice.ts`）——
 * 补开不该有一条更宽松的旁路，两处的必填提示也必须一模一样。
 *
 * ⚠️ 提交成功后只 `emit('success')`，**不在这里改订单状态**：状态一律以后端为准（FR-039），
 * 由父组件重新拉一次订单详情。
 */
const props = defineProps<{ open: boolean; orderId: number }>()
const emit = defineEmits<{ close: []; submitted: [] }>()

const emptyForm = (): InvoiceApplyForm => ({
  type: InvoiceType.NORMAL,
  titleKind: InvoiceTitleKind.COMPANY,
  titleName: '',
  taxNo: '',
  titleAddress: '',
  titlePhone: '',
  bankName: '',
  email: '',
})

const form = ref<InvoiceApplyForm>(emptyForm())
const submitting = ref(false)
const error = ref('')

/** 实时提示（客户边填边看）；提交时再拦一道 —— 两处同一个纯函数 */
const fieldError = computed(() => invoiceFieldError(form.value))

// 每次打开都是一张空表：上一次填了一半的抬头留着，客户会以为已经提交过
watch(() => props.open, (value) => {
  if (value) {
    form.value = emptyForm()
    error.value = ''
  }
})

async function onSubmit() {
  if (submitting.value) return
  error.value = ''
  if (fieldError.value) {
    error.value = fieldError.value
    return
  }
  submitting.value = true
  try {
    await createOrderInvoice({ orderId: props.orderId, ...form.value })
    emit('submitted')
    emit('close')
  } catch (e) {
    // 后端的拒绝理由要**原样透出**（例如「订单已取消，无法申请开票」）——
    // 换一句笼统的"提交失败"会让客户不知道该去做什么
    error.value = (e as { message?: string })?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <MlModal
    :open="props.open"
    title="申请开票"
    @close="emit('close')"
  >
    <p class="invoice-apply-note">
      发票 / 收据开出后可在这笔订单的详情里下载。信息提交后不可修改，请核对无误。
    </p>
    <!-- 抬头选择器在 InvoiceFieldsForm 内部：企业/个人 → 票种 → 抬头（Q64） -->
    <InvoiceFieldsForm v-model="form" />
    <p
      v-if="error"
      class="ml-error invoice-apply-error"
      role="alert"
    >
      {{ error }}
    </p>

    <template #foot>
      <button
        class="btn-cart"
        type="button"
        @click="emit('close')"
      >
        取消
      </button>
      <button
        id="submitInvoice"
        class="btn-buy"
        type="button"
        :disabled="submitting"
        @click="onSubmit"
      >
        {{ submitting ? '提交中…' : '提交申请' }}
      </button>
    </template>
  </MlModal>
</template>

<style scoped>
.invoice-apply-note { margin: 0 0 14px; color: #8a97b5; font-size: 12px; line-height: 1.8; }
.invoice-apply-error { margin-top: 12px; }
</style>
