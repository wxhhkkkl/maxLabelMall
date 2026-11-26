<template>
  <Dialog v-model="dialogVisible" title="修改发票信息" width="600px">
    <el-form label-width="120px">
      <el-form-item label="订单号">
        <span>{{ formData.orderNo || '—' }}</span>
      </el-form-item>
    </el-form>
    <InvoiceFieldsForm ref="fieldsRef" :form="formData" />
    <p class="tip">只有「待开票」的申请可以修改；已开票的发票文件已经生成，请先重新上传。</p>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="saving" type="primary" @click="submitForm">提交</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as InvoiceApi from '@/api/mall/trade/invoice/index'

import InvoiceFieldsForm from './InvoiceFieldsForm.vue'

defineOptions({ name: 'InvoiceEditForm' })
const emit = defineEmits(['success'])
const message = useMessage()

const dialogVisible = ref(false)
const saving = ref(false)
const formData = ref<InvoiceApi.TradeOrderInvoiceVO>({})
const fieldsRef = ref<InstanceType<typeof InvoiceFieldsForm>>()

/** 打开弹窗：从订单详情已带出的 `invoice` 回填，不额外查一次接口 */
const open = async (row: InvoiceApi.TradeOrderInvoiceVO) => {
  formData.value = {
    id: row.id,
    orderNo: row.orderNo,
    type: row.type,
    titleKind: row.titleKind,
    titleName: row.titleName,
    taxNo: row.taxNo,
    titleAddress: row.titleAddress,
    titlePhone: row.titlePhone,
    bankName: row.bankName,
    bankAccount: row.bankAccount,
    bankCode: row.bankCode,
    email: row.email
  }
  dialogVisible.value = true
  // 上一次的红字别带过来
  await nextTick()
  fieldsRef.value?.clearValidate()
}
defineExpose({ open })

const submitForm = async () => {
  try {
    await fieldsRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await InvoiceApi.updateInvoice(formData.value)
    message.success('发票信息已修改')
    dialogVisible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
</style>
