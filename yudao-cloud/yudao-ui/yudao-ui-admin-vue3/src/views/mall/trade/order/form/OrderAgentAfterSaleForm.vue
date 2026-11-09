<template>
  <Dialog v-model="visible" title="代客发起售后" width="640px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="订单项" prop="orderItemId">
        <el-select v-model="form.orderItemId" class="!w-100%" placeholder="请选择商品">
          <el-option
            v-for="item in items"
            :key="item.id"
            :disabled="item.afterSaleStatus !== 0"
            :label="`${item.spuName}（实付 ${yuan(item.payPrice)}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="售后方式" prop="way">
        <el-radio-group v-model="form.way">
          <el-radio :value="1">仅退款</el-radio>
          <el-radio :value="2">退货退款</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="退款金额" prop="refundPriceYuan">
        <el-input-number
          v-model="form.refundPriceYuan"
          :max="maxRefundYuan"
          :min="0"
          :precision="2"
          :step="1"
        />
        <span class="ml-5px text-gray-500">元（最多 {{ maxRefundYuan.toFixed(2) }} 元）</span>
      </el-form-item>
      <el-form-item label="退款原因" prop="applyReason">
        <el-input v-model="form.applyReason" :maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="补充描述">
        <el-input
          v-model="form.applyDescription"
          :maxlength="500"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
      <el-form-item label="凭证图片">
        <UploadImgs v-model="form.applyPicUrls" :limit="5" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      <el-button @click="visible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as AfterSaleApi from '@/api/mall/trade/afterSale'
import { UploadImgs } from '@/components/UploadFile'
import type { FormInstance, FormRules } from 'element-plus'

defineOptions({ name: 'OrderAgentAfterSaleForm' })

const emit = defineEmits<{
  (e: 'success'): void
}>()

const message = useMessage()
const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const orderId = ref<number>()
const items = ref<Array<{ id: number; spuName: string; payPrice?: number; afterSaleStatus?: number }>>([])
const form = reactive({
  orderItemId: undefined as number | undefined,
  way: 1,
  refundPriceYuan: 0,
  applyReason: '',
  applyDescription: '',
  applyPicUrls: [] as string[]
})

const rules: FormRules = {
  orderItemId: [{ required: true, message: '请选择订单项', trigger: 'change' }],
  way: [{ required: true, message: '请选择售后方式', trigger: 'change' }],
  applyReason: [{ required: true, message: '请填写退款原因', trigger: 'blur' }]
}

const yuan = (fen?: number) => `¥${((fen || 0) / 100).toFixed(2)}`

const selectedItem = computed(() => items.value.find((item) => item.id === form.orderItemId))
const maxRefundYuan = computed(() => (selectedItem.value?.payPrice || 0) / 100)

const open = (order: { id: number; items?: any[] }) => {
  orderId.value = order.id
  items.value = order.items || []
  form.orderItemId = items.value.find((item) => item.afterSaleStatus === 0)?.id
  form.way = 1
  form.refundPriceYuan = (items.value.find((item) => item.id === form.orderItemId)?.payPrice || 0) / 100
  form.applyReason = ''
  form.applyDescription = ''
  form.applyPicUrls = []
  visible.value = true
}
defineExpose({ open })

watch(
  () => form.orderItemId,
  (id) => {
    const item = items.value.find((it) => it.id === id)
    form.refundPriceYuan = (item?.payPrice || 0) / 100
  }
)

const submit = async () => {
  await formRef.value?.validate()
  if (!orderId.value || !form.orderItemId) {
    return
  }
  if (form.refundPriceYuan * 100 > (selectedItem.value?.payPrice || 0)) {
    message.warning('退款金额不能超过该商品实付金额')
    return
  }
  submitting.value = true
  try {
    await AfterSaleApi.agentCreate({
      orderItemId: form.orderItemId,
      way: form.way,
      refundPrice: Math.round(form.refundPriceYuan * 100),
      applyReason: form.applyReason,
      applyDescription: form.applyDescription || undefined,
      applyPicUrls: form.applyPicUrls.length ? form.applyPicUrls : undefined
    })
    message.success('售后单已创建')
    visible.value = false
    emit('success')
  } catch (e) {
    message.error((e as { message?: string })?.message || '创建售后单失败')
  } finally {
    submitting.value = false
  }
}
</script>
