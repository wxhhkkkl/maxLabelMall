<template>
  <Dialog v-model="dialogVisible" title="订单调价" width="25%">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" label-width="100px">
      <el-form-item label="应付金额(总)">
        <el-input v-model="formData.payPrice" disabled />
      </el-form-item>
      <el-form-item label="累计调价">
        <el-input-number v-model="formData.adjustPrice" :precision="2" :step="0.1" class="w-100%" />
        <el-tag class="ml-10px" type="warning">累计调价金额。 正数，加价；负数，减价</el-tag>
      </el-form-item>
      <el-form-item label="调价后">
        <el-input v-model="formData.newPayPrice" disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as TradeOrderApi from '@/api/mall/trade/order'
import { convertToInteger, floatToFixed2, formatToFraction } from '@/utils'
import { cloneDeep } from 'lodash-es'

defineOptions({ name: 'OrderUpdatePriceForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formData = ref({
  id: undefined as number | undefined, // 订单编号
  adjustPrice: 0, // 累计调价（元）
  payPrice: '', // 当前应付金额(总)
  originPayPrice: 0, // 未调价前的应付金额(元) = 当前应付 - 当前累计调价
  newPayPrice: '' // 调价后应付金额(总)
})
watch(
  () => formData.value.adjustPrice,
  (adjustPrice) => {
    formData.value.newPayPrice =
      (formData.value.originPayPrice + (adjustPrice || 0)).toFixed(2) + '元'
  }
)

const formRef = ref() // 表单 Ref

/** 打开弹窗 */
const open = async (row: TradeOrderApi.OrderVO) => {
  resetForm()
  formData.value.id = row.id!
  // 设置数据：adjustPrice 是**累计调价总额**，回填后再次提交表示「调整到这个总额」
  const adjustPrice = Number(formatToFraction(row.adjustPrice || 0))
  formData.value.originPayPrice = Number(formatToFraction(row.payPrice || 0)) - adjustPrice
  formData.value.adjustPrice = adjustPrice
  formData.value.payPrice = floatToFixed2(row.payPrice!) + '元'
  formData.value.newPayPrice = formData.value.payPrice
  dialogVisible.value = true
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 提交请求
  formLoading.value = true
  try {
    const {
      payPrice: _payPrice,
      originPayPrice: _originPayPrice,
      newPayPrice: _newPayPrice,
      ...data
    } = cloneDeep(unref(formData))
    data.adjustPrice = convertToInteger(data.adjustPrice)
    await TradeOrderApi.updateOrderPrice(data)
    message.success(t('common.updateSuccess'))
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success', true)
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined, // 订单编号
    adjustPrice: 0, // 累计调价（元）
    payPrice: '', // 当前应付金额(总)
    originPayPrice: 0, // 未调价前的应付金额(元)
    newPayPrice: '' // 调价后应付金额(总)
  }
  formRef.value?.resetFields()
}
</script>
