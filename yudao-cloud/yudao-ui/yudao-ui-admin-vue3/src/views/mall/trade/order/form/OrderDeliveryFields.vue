<!-- 订单发货的表单字段（2026-11-18）
     弹窗（OrderDeliveryForm）与订单详情页内联面板（OrderDeliveryPanel）共用，避免两处字段各写一份、口径走样。 -->
<template>
  <el-form :model="formData" label-width="80px">
    <el-form-item label="发货方式">
      <el-radio-group v-model="expressType">
        <el-radio border value="express">快递物流</el-radio>
        <el-radio border value="none">无需发货</el-radio>
      </el-radio-group>
    </el-form-item>
    <template v-if="expressType === 'express'">
      <el-form-item label="物流公司">
        <el-select v-model="formData.logisticsId" placeholder="请选择" style="width: 100%">
          <el-option
            v-for="item in deliveryExpressList"
            :key="item.id"
            :label="item.name"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="物流单号">
        <el-input v-model="formData.logisticsNo" />
      </el-form-item>
      <el-form-item label="发货地址">
        <el-tree-select
          v-model="formData.deliveryFromAreaId"
          :data="areaList"
          :props="defaultProps"
          :render-after-expand="true"
          placeholder="请选择省/市/区"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="发货详细地址">
        <el-input
          v-model="formData.deliveryFromAddress"
          :rows="2"
          placeholder="请输入发货详细地址"
          type="textarea"
        />
      </el-form-item>
    </template>
  </el-form>
</template>
<script lang="ts" setup>
import type * as DeliveryExpressApi from '@/api/mall/trade/delivery/express'
import type * as TradeOrderApi from '@/api/mall/trade/order'
import { defaultProps } from '@/utils/tree'

defineOptions({ name: 'OrderDeliveryFields' })

defineProps<{
  formData: TradeOrderApi.DeliveryVO
  deliveryExpressList: DeliveryExpressApi.DeliveryExpressVO[]
  areaList: any[]
}>()

/** express = 快递物流；none = 无需发货 */
const expressType = defineModel<string>('expressType', { required: true })
</script>
