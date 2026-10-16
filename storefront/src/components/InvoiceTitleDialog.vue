<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { createInvoiceTitle, updateInvoiceTitle } from '@/api/invoice'
import InvoiceFieldsForm from '@/components/InvoiceFieldsForm.vue'
import MlCheck from '@/components/base/MlCheck.vue'
import MlModal from '@/components/base/MlModal.vue'
import { InvoiceTitleKind, InvoiceType, type InvoiceApplyForm, type InvoiceTitle, type InvoiceTypeValue } from '@/types'
import { invoiceFieldError } from '@/utils/invoice'

/**
 * 发票抬头的**新增 / 编辑**弹层（US23 / FR-164 + Q36）。
 *
 * 字段复用结算页那一套（`InvoiceFieldsForm`，**票种也在里面选**）——
 * Q36 之后一条抬头**只服务一种票**：普票抬头只要名称，专票抬头五项全填，
 * 校验统一走 `invoiceFieldError(form)`（与后端同一套规则）。
 */
const props = defineProps<{
  open: boolean
  title?: InvoiceTitle | null
  /** **预选票种**（Q36）：从结算页/补开弹层点「新增抬头」时，跟着当前选的票种走 */
  type?: InvoiceTypeValue
}>()
const emit = defineEmits<{ close: []; submitted: [] }>()

const emptyForm = (): InvoiceApplyForm => ({
  type: props.type ?? InvoiceType.NORMAL,
  titleKind: InvoiceTitleKind.COMPANY,
  titleName: '',
  taxNo: '',
  titleAddress: '',
  titlePhone: '',
  bankName: '',
})

const form = ref<InvoiceApplyForm>(emptyForm())
const isDefault = ref(false)
const saving = ref(false)
const error = ref('')

const isEdit = computed(() => !!props.title?.id)

// 每次打开都按"要编辑的那条"重置 —— 否则新增时会带着上一次编辑的内容。
// ⚠️ `immediate`：**不要依赖"父组件初始一定关着"这个隐含前提** —— 初始就 open 时
// 也要把表单填上（否则编辑弹层会显示一张空表，而点保存又会覆盖掉原数据）
watch(() => props.open, (value) => {
  if (!value) return
  const title = props.title
  form.value = title
    ? {
        type: title.type,
        titleKind: title.titleKind,
        titleName: title.titleName,
        taxNo: title.taxNo ?? '',
        titleAddress: title.titleAddress ?? '',
        titlePhone: title.titlePhone ?? '',
        bankName: title.bankName ?? '',
      }
    : emptyForm()
  isDefault.value = title?.defaultStatus === true
  error.value = ''
}, { immediate: true })

async function onSubmit() {
  if (saving.value) return
  error.value = ''
  // 按**抬头自己的票种**判（与后端 `InvoiceValidator#validate` 同一套规则）
  const invalid = invoiceFieldError(form.value)
  if (invalid) {
    error.value = invalid
    return
  }
  saving.value = true
  try {
    const payload = {
      type: form.value.type,
      titleKind: form.value.titleKind,
      titleName: form.value.titleName,
      taxNo: form.value.taxNo,
      titleAddress: form.value.titleAddress,
      titlePhone: form.value.titlePhone,
      bankName: form.value.bankName,
      defaultStatus: isDefault.value,
    }
    if (isEdit.value) {
      await updateInvoiceTitle(props.title!.id, payload)
    } else {
      await createInvoiceTitle(payload)
    }
    emit('submitted')
    emit('close')
  } catch (e) {
    error.value = (e as { message?: string })?.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <MlModal
    :open="props.open"
    :title="isEdit ? '编辑发票抬头' : '新增发票抬头'"
    @close="emit('close')"
  >
    <InvoiceFieldsForm v-model="form" />
    <MlCheck v-model="isDefault">
      设为默认抬头
    </MlCheck>
    <p class="ml-hint">
      结算页勾选开票时会替你预选默认的那条。
    </p>
    <p
      v-if="error"
      class="ml-error invoice-title-error"
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
        id="saveInvoiceTitle"
        class="btn-buy"
        type="button"
        :disabled="saving"
        @click="onSubmit"
      >
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </template>
  </MlModal>
</template>

<style scoped>
.invoice-title-error { margin-top: 12px; }
</style>
