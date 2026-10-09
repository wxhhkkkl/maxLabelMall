<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { createAfterSale } from '@/api/afterSale'
import { AfterSaleWay, type AfterSaleWayValue } from '@/types'
import { allowsReturnRefund } from '@/utils/afterSale'

import MlAmountRow from './base/MlAmountRow.vue'
import MlField from './base/MlField.vue'
import MlModal from './base/MlModal.vue'

/**
 * 申请退款的弹层。
 *
 * 走后端**既有**的售后接口，弹层只负责表单；审核完全在管理端「售后退款」页完成
 * （那套是现成的，前端不用管）。提交成功后售后单进入**待商家审核**。
 *
 * 三个约束来自后端 `AfterSaleServiceImpl.validateOrderItemApplicable`：
 *
 * 1. **退款金额不得超过该项实付** —— 所以金额取 `payPrice` 且**只读**，
 *    用 `MlAmountRow` 展示而不是 `<input disabled>`（后者看着能改，也可能被篡改）。
 * 2. **「退货退款」要求已发货** —— 未发货时置灰并说明原因，而不是藏起来：
 *    用户该知道有这个选项、也知道为什么现在不能用。
 * 3. **申请原因必填** —— 用**自由文本**，不做预设理由清单：
 *    后端该字段就是裸字符串，"哪些理由可选"是一条**业务规则**，不该由前端编。
 *
 * 提交前**双保险**：点选时拦住被绕过的「退货退款」，提交时再拦一次。
 */
interface RefundItem {
  id: number
  spuName: string
  properties?: Array<{ propertyName: string; valueName: string }>
  payPrice?: number
  afterSaleStatus?: number
}

const props = defineProps<{
  open: boolean
  item: RefundItem | null
  /** 订单状态 —— 决定「退货退款」能不能选（未发货不行） */
  orderStatus: number
}>()
const emit = defineEmits<{ close: []; submitted: [] }>()

const way = ref<AfterSaleWayValue>(AfterSaleWay.REFUND_ONLY)
const reason = ref('')
const error = ref('')
const submitting = ref(false)

/** 只有已发货 / 已完成才允许「退货退款」（与后端一致，判定复用 utils/afterSale） */
const returnRefundAllowed = computed(() => allowsReturnRefund(props.orderStatus))

const refundPrice = computed(() => props.item?.payPrice ?? 0)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    // 每次打开都重置：默认「仅退款」（任何订单状态下都合法，保证一打开就有可提交状态）
    way.value = AfterSaleWay.REFUND_ONLY
    reason.value = ''
    error.value = ''
    submitting.value = false
  },
)

function pick(next: AfterSaleWayValue) {
  // 双保险的第一道：未发货时点不动「退货退款」
  if (next === AfterSaleWay.RETURN_AND_REFUND && !returnRefundAllowed.value) return
  way.value = next
}

async function submit() {
  const item = props.item
  if (!item) return
  error.value = ''
  if (!reason.value.trim()) {
    error.value = '请填写退款原因'
    return
  }
  if (refundPrice.value <= 0) {
    error.value = '该商品无可退金额'
    return
  }
  // 双保险的第二道：绕过界面也拦得住
  if (way.value === AfterSaleWay.RETURN_AND_REFUND && !returnRefundAllowed.value) {
    error.value = '订单未发货，无法申请退货退款'
    return
  }

  submitting.value = true
  try {
    await createAfterSale({
      orderItemId: item.id,
      way: way.value,
      refundPrice: refundPrice.value,
      applyReason: reason.value.trim(),
    })
    emit('submitted')
    emit('close')
  } catch (e) {
    // 原样展示后端文案（「订单项已申请售后，无法重复申请」这类要用户看得到），弹层不关
    error.value = (e as { message?: string })?.message || '申请失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <MlModal :open="open" title="申请退款" @close="emit('close')">
    <!-- 商品行：只读快照 -->
    <MlField label="商品">
      <div class="rf-goods">
        <div class="rf-name">{{ item?.spuName }}</div>
        <div v-if="item?.properties?.length" class="rf-spec">
          <template v-for="p in item.properties" :key="p.valueName">
            {{ p.propertyName }}：{{ p.valueName }}
          </template>
        </div>
      </div>
    </MlField>

    <!-- 金额只读：以后端给的该项实付为准，不允许改（否则必被后端拒） -->
    <MlAmountRow label="退款金额" :fen="refundPrice" total />

    <MlField label="售后方式" required>
      <div class="rf-ways">
        <button
          class="rf-way"
          :class="{ 'is-active': way === AfterSaleWay.REFUND_ONLY }"
          type="button"
          data-way="10"
          @click="pick(AfterSaleWay.REFUND_ONLY)"
        >
          仅退款
        </button>
        <button
          class="rf-way"
          :class="{
            'is-active': way === AfterSaleWay.RETURN_AND_REFUND,
            'is-disabled': !returnRefundAllowed,
          }"
          type="button"
          data-way="20"
          :disabled="!returnRefundAllowed"
          @click="pick(AfterSaleWay.RETURN_AND_REFUND)"
        >
          退货退款
        </button>
      </div>
      <p v-if="!returnRefundAllowed" class="ml-hint">订单未发货，暂不支持退货退款</p>
    </MlField>

    <MlField label="退款原因" required>
      <textarea
        id="refundReason"
        v-model="reason"
        class="ml-textarea"
        placeholder="请说明退款原因"
      />
    </MlField>

    <p v-if="error" class="ml-error">{{ error }}</p>

    <template #foot>
      <button
        id="refundSubmit"
        class="btn-primary"
        type="button"
        :disabled="submitting"
        @click="submit"
      >
        {{ submitting ? '提交中…' : '提交申请' }}
      </button>
    </template>
  </MlModal>
</template>

<style scoped>
.rf-goods {
  padding: 8px 10px;
  background: var(--ml-bg-soft);
  border-radius: var(--ml-radius-field);
}
.rf-name {
  font-size: 14px;
  color: var(--ml-text);
}
.rf-spec {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ml-text-sub);
}
.rf-ways {
  display: flex;
  gap: 10px;
}
.rf-way {
  flex: 1;
  padding: 8px 0;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-field);
  background: var(--ml-bg-card);
  color: var(--ml-text);
  font-size: 14px;
  cursor: pointer;
}
.rf-way.is-active {
  border-color: var(--ml-primary);
  color: var(--ml-primary);
}
.rf-way.is-disabled {
  color: var(--ml-text-weak);
  cursor: not-allowed;
}
</style>
