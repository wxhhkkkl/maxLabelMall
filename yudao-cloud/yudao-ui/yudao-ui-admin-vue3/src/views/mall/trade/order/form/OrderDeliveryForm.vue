<template>
  <Dialog v-model="dialogVisible" title="订单发货" width="25%">
    <div v-loading="formLoading">
      <OrderDeliveryFields
        v-model:express-type="expressType"
        :area-list="areaList"
        :delivery-express-list="deliveryExpressList"
        :form-data="formData"
      />
    </div>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as DeliveryExpressApi from '@/api/mall/trade/delivery/express'
import * as TradeOrderApi from '@/api/mall/trade/order'
import { getAreaTree } from '@/api/system/area'
import { copyValueToTarget } from '@/utils'
import OrderDeliveryFields from './OrderDeliveryFields.vue'

defineOptions({ name: 'OrderDeliveryForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const expressType = ref('express') // 如果值是 express，则是快递；none 则是无；未来做同城配送；
const formData = ref<TradeOrderApi.DeliveryVO>({
  id: undefined, // 订单编号
  logisticsId: null, // 物流公司编号
  logisticsNo: '', // 物流编号
  deliveryFromAreaId: null, // 发货地址地区
  deliveryFromAddress: '' // 发货地址详情
})
const areaList = ref([]) // 地区列表

/** 打开弹窗 */
const open = async (row: TradeOrderApi.OrderVO) => {
  resetForm()
  // 设置数据
  copyValueToTarget(formData.value, row)
  if (row.logisticsId === 0) {
    expressType.value = 'none'
  }
  dialogVisible.value = true
  formLoading.value = true
  try {
    if (areaList.value.length === 0) {
      areaList.value = await getAreaTree()
    }
    // 010 追加：发货地址默认取配置的默认发货仓；订单已填过则用订单的
    if (formData.value.deliveryFromAreaId == null || !formData.value.deliveryFromAddress) {
      const def = await TradeOrderApi.getDeliveryDefaultAddress()
      if (formData.value.deliveryFromAreaId == null) {
        formData.value.deliveryFromAreaId = def?.areaId ?? null
      }
      if (!formData.value.deliveryFromAddress) {
        formData.value.deliveryFromAddress = def?.address ?? ''
      }
    }
  } catch {
    // 拿不到默认地址就让运营自己填；提交时前后端都会校验
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 提交请求
  formLoading.value = true
  try {
    const data = unref(formData)
    if (expressType.value === 'none') {
      // 无需发货的情况
      data.logisticsId = 0
      data.logisticsNo = ''
    } else if (data.deliveryFromAreaId == null || !data.deliveryFromAddress?.trim()) {
      // 010 追加：快递发货必须有发货地址（地图的 from）
      message.warning('请填写发货地址')
      formLoading.value = false
      return
    }
    await TradeOrderApi.deliveryOrder(data)
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
    logisticsId: null, // 物流公司编号
    logisticsNo: '', // 物流编号
    deliveryFromAreaId: null, // 发货地址地区
    deliveryFromAddress: '' // 发货地址详情
  }
}
const deliveryExpressList = ref<DeliveryExpressApi.DeliveryExpressVO[]>([])
onMounted(async () => {
  deliveryExpressList.value = await DeliveryExpressApi.getSimpleDeliveryExpressList()
})
</script>
