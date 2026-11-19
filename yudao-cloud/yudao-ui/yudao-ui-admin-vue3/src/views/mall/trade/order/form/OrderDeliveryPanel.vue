<!--
  订单详情页的**内联**发货表单（2026-11-18 所有者要求）

  待发货订单进详情就能直接填物流/发货地址并发货，不用再点「发货」二次打开弹窗。
  字段与弹窗共用 OrderDeliveryFields，口径只有一份；这里的逻辑与 OrderDeliveryForm 对齐：
  · 快递发货必须有发货地址（010）；「无需发货」清空物流
  · 发货地址缺省时取后台配置的默认发货仓
-->
<template>
  <div v-loading="loading">
    <OrderDeliveryFields
      v-if="formData"
      v-model:express-type="expressType"
      :area-list="areaList"
      :delivery-express-list="deliveryExpressList"
      :form-data="formData"
    />
    <el-button :loading="submitting" type="primary" @click="submitForm">确认发货</el-button>
  </div>
</template>
<script lang="ts" setup>
import * as DeliveryExpressApi from '@/api/mall/trade/delivery/express'
import * as TradeOrderApi from '@/api/mall/trade/order'
import { getAreaTree } from '@/api/system/area'
import OrderDeliveryFields from './OrderDeliveryFields.vue'

defineOptions({ name: 'OrderDeliveryPanel' })

const props = defineProps<{
  order: TradeOrderApi.OrderVO
}>()
const emit = defineEmits(['success'])
const message = useMessage()

const loading = ref(false)
const submitting = ref(false)
const expressType = ref('express') // express = 快递物流；none = 无需发货
const formData = ref<TradeOrderApi.DeliveryVO | null>(null)
const deliveryExpressList = ref<DeliveryExpressApi.DeliveryExpressVO[]>([])
const areaList = ref<any[]>([])
/** 只在订单切换时初始化一次：详情刷新（如改备注）不能把运营填到一半的内容冲掉 */
let initializedOrderId: number | undefined

const init = async (order: TradeOrderApi.OrderVO | undefined) => {
  if (!order || order.id === initializedOrderId) {
    return
  }
  initializedOrderId = order.id ?? undefined
  formData.value = {
    id: order.id ?? undefined,
    logisticsId: order.logisticsId ?? null,
    logisticsNo: order.logisticsNo ?? '',
    deliveryFromAreaId: null,
    deliveryFromAddress: ''
  }
  const data = formData.value
  // 物流公司编号为 0 = 无需发货（历史单可能这样存）
  expressType.value = order.logisticsId === 0 ? 'none' : 'express'
  loading.value = true
  try {
    if (deliveryExpressList.value.length === 0) {
      deliveryExpressList.value = await DeliveryExpressApi.getSimpleDeliveryExpressList()
    }
    if (areaList.value.length === 0) {
      areaList.value = await getAreaTree()
    }
    // 010：发货地址默认取配置的默认发货仓
    const def = await TradeOrderApi.getDeliveryDefaultAddress()
    if (data.deliveryFromAreaId == null) {
      data.deliveryFromAreaId = def?.areaId ?? null
    }
    if (!data.deliveryFromAddress) {
      data.deliveryFromAddress = def?.address ?? ''
    }
  } catch {
    // 拿不到默认地址就让运营自己填；提交时前后端都会校验
  } finally {
    loading.value = false
  }
}
watch(() => props.order, init, { immediate: true })

const submitForm = async () => {
  if (!formData.value) {
    return
  }
  if (expressType.value === 'none') {
    formData.value.logisticsId = 0
    formData.value.logisticsNo = ''
  } else if (formData.value.deliveryFromAreaId == null || !formData.value.deliveryFromAddress?.trim()) {
    message.warning('请填写发货地址')
    return
  }
  submitting.value = true
  try {
    await TradeOrderApi.deliveryOrder(formData.value)
    message.success('发货成功')
    emit('success', true)
  } finally {
    submitting.value = false
  }
}
</script>
