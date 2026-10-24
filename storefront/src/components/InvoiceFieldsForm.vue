<script setup lang="ts">
import { computed } from 'vue'

import InvoiceTitlePicker from '@/components/InvoiceTitlePicker.vue'
import MlField from '@/components/base/MlField.vue'
import { InvoiceTitleKind, InvoiceType, type InvoiceApplyForm, type InvoiceTitle } from '@/types/invoice'
import { INVOICE_TITLE_KIND_OPTIONS, INVOICE_TYPE_OPTIONS } from '@/utils/invoice'

/**
 * 开票信息的字段表单：**票种 + 抬头类型 + 抬头五件套**。
 *
 * 四个地方共用它：结算页的发票卡片、订单详情的补开弹层、抬头簿的新增/编辑弹层、
 * 以及"从已保存抬头里选一条"之后的手改 —— 各写一份必然在某次改动后分叉。
 *
 * ⚠️ **只负责"填"，不负责"判"**：必填与格式的判定统一在 `utils/invoice.ts` 的
 * `invoiceFieldError(form)`（与后端 `InvoiceValidator` 同一套规则）。
 */
const model = defineModel<InvoiceApplyForm>({ required: true })

/**
 * 是否展示「从已保存抬头里选一条」。
 * 开票申请（结算页 / 补开弹层）展示；抬头簿的编辑弹层不展示（它就是在编辑某一条）。
 */
const props = withDefaults(defineProps<{ showPicker?: boolean }>(), { showPicker: true })

/**
 * 专票才**显示**地址 / 电话 / 开户行三栏（**选填**，见 `invoiceFieldError`）。
 * 必填只有「名称 + 税号」两项 —— **企业**抬头是这样，**个人**只要名称（Q38）。
 */
const isSpecial = computed(() => model.value.type === InvoiceType.SPECIAL)
const isPersonal = computed(() => model.value.titleKind === InvoiceTitleKind.PERSONAL)

/**
 * **「个人 + 专票」不成立**（专票按税法只能开给企业）—— 一条不变量，在两个选择器上各表达一次：
 * 选了专票就把"个人"置灰，选了个人就把"专票"置灰。这样客户**根本没机会**选出那个组合，
 * 而不是填完一屏再被后端拒（后端 `InvoiceValidator` 仍然拒，那是权威）。
 */
function typeDisabled(value: number): boolean {
  return value === InvoiceType.SPECIAL && isPersonal.value
}
function kindDisabled(value: number): boolean {
  return value === InvoiceTitleKind.PERSONAL && isSpecial.value
}

/** 选中一条已保存抬头：覆盖抬头字段与邮箱，**票种保留当前选择** */
function fillFrom(title: InvoiceTitle) {
  model.value = {
    ...model.value,
    titleKind: title.titleKind,
    titleName: title.titleName,
    taxNo: title.taxNo ?? '',
    titleAddress: title.titleAddress ?? '',
    titlePhone: title.titlePhone ?? '',
    bankName: title.bankName ?? '',
    email: title.email ?? '',
  }
}
</script>

<template>
  <div class="invoice-fields">
    <div
      class="invoice-kinds"
      role="radiogroup"
      aria-label="抬头类型"
    >
      <button
        v-for="option in INVOICE_TITLE_KIND_OPTIONS"
        :key="option.value"
        type="button"
        class="invoice-kind"
        role="radio"
        :aria-checked="model.titleKind === option.value"
        :disabled="kindDisabled(option.value)"
        :class="{ 'is-active': model.titleKind === option.value }"
        :data-invoice-kind="option.value"
        @click="model.titleKind = option.value"
      >
        {{ option.label }}
      </button>
    </div>
    <p class="invoice-kind-hint">
      {{ INVOICE_TITLE_KIND_OPTIONS.find(o => o.value === model.titleKind)?.hint }}
    </p>

    <div
      class="invoice-types"
      role="radiogroup"
      aria-label="票种"
    >
      <button
        v-for="option in INVOICE_TYPE_OPTIONS"
        :key="option.value"
        type="button"
        class="invoice-type"
        role="radio"
        :aria-checked="model.type === option.value"
        :disabled="typeDisabled(option.value)"
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

    <!--
      选择抬头的顺序（Q64）：企业/个人 → 票种（普票/专票/收据）→ 再选抬头。
      所以选择器放在这两个选择器**之后**，且按「抬头类型 + 票种」双重过滤。
    -->
    <InvoiceTitlePicker
      v-if="props.showPicker"
      :type="model.type"
      :title-kind="model.titleKind"
      @picked="fillFrom"
    />

    <MlField
      label="抬头名称"
      required
    >
      <input
        id="invoiceTitleName"
        v-model="model.titleName"
        class="ml-input"
        :placeholder="isPersonal ? '姓名' : '公司名称'"
        autocomplete="off"
      >
    </MlField>

    <MlField
      label="纳税人识别号"
      :required="!isPersonal"
    >
      <input
        id="invoiceTaxNo"
        v-model="model.taxNo"
        class="ml-input"
        :placeholder="isPersonal ? '可不填；有身份证号也可填这里' : '15 / 18 / 20 位数字或大写字母'"
        autocomplete="off"
      >
    </MlField>

    <template v-if="isSpecial">
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

    <MlField label="邮箱">
      <input
        id="invoiceEmail"
        v-model="model.email"
        class="ml-input"
        type="email"
        placeholder="选填"
        autocomplete="off"
      >
    </MlField>
  </div>
</template>

<style scoped>
.invoice-kinds { display: flex; gap: 10px; flex-wrap: wrap; }
.invoice-kind { border: 1px solid #e3e9f4; background: #ffffff; color: #16233f; border-radius: 4px; padding: 8px 14px; font-size: 13px; cursor: pointer; }
.invoice-kind.is-active { border-color: #2e7cd6; color: #2e7cd6; background: #f0f4fb; }
.invoice-kind-hint { margin: 8px 0 14px; color: #8a97b5; font-size: 12px; }
.invoice-types { display: flex; gap: 10px; flex-wrap: wrap; }
.invoice-type { border: 1px solid #e3e9f4; background: #ffffff; color: #16233f; border-radius: 4px; padding: 8px 14px; font-size: 13px; cursor: pointer; }
.invoice-type.is-active { border-color: #2e7cd6; color: #2e7cd6; background: #f0f4fb; }
/* 置灰的那一个（「个人」在专票下 / 「专票」在个人下）—— 要看得出是**不可选**，而不是坏了 */
.invoice-kind:disabled, .invoice-type:disabled { color: #b4c0d8; border-color: #eef2fa; cursor: not-allowed; }
.invoice-type-hint { margin: 8px 0 14px; color: #8a97b5; font-size: 12px; }
@media (max-width: 480px) {
  .invoice-kind, .invoice-type { flex: 1 1 100%; text-align: center; }
}
</style>
