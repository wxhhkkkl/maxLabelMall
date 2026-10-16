<!-- 商品发布 - 物流设置 -->
<template>
  <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px" :disabled="isDetail">
    <el-form-item label="配送方式" prop="deliveryTypes">
      <el-checkbox-group v-model="formData.deliveryTypes" class="w-80">
        <el-checkbox
          v-for="dict in getIntDictOptions(DICT_TYPE.TRADE_DELIVERY_TYPE)"
          :key="dict.value"
          :value="dict.value"
        >
          {{ dict.label }}
        </el-checkbox>
      </el-checkbox-group>
      <!--
        ⚠️ 虚拟商品请**只勾这一项**：判定"整单虚拟"的条件是所有商品都**只支持**虚拟商品 ——
        同时勾了「快递发货」会被当成实物，下单时仍然要收货地址。
      -->
      <div
        v-if="formData.deliveryTypes?.includes(DeliveryTypeEnum.VIRTUAL.type)"
        class="text-12px text-[var(--el-text-color-placeholder)]"
      >
        软件等不发货的商品请<strong>只勾「虚拟商品」</strong>：客户下单时就不用填收货地址。
        若同时勾了「快递发货」，会被当成实物、仍需地址。
      </div>
    </el-form-item>
    <el-form-item
      label="运费模板"
      prop="deliveryTemplateId"
      v-if="formData.deliveryTypes?.includes(DeliveryTypeEnum.EXPRESS.type)"
    >
      <el-select placeholder="请选择运费模板" v-model="formData.deliveryTemplateId" class="w-80">
        <el-option
          v-for="item in deliveryTemplateList"
          :key="item.id"
          :label="item.name"
          :value="item.id!"
        />
      </el-select>
    </el-form-item>
  </el-form>
</template>
<script lang="ts" setup>
import { PropType } from 'vue'
import { copyValueToTarget } from '@/utils'
import { propTypes } from '@/utils/propTypes'
import type { Spu } from '@/api/mall/product/spu'
import * as ExpressTemplateApi from '@/api/mall/trade/delivery/expressTemplate'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { DeliveryTypeEnum } from '@/utils/constants'

defineOptions({ name: 'ProductDeliveryForm' })

const message = useMessage() // 消息弹窗

const props = defineProps({
  propFormData: {
    type: Object as PropType<Spu>,
    default: () => {}
  },
  isDetail: propTypes.bool.def(false) // 是否作为详情组件
})
const formRef = ref() // 表单 Ref
const formData = reactive<Spu>({
  deliveryTypes: [], // 配送方式
  deliveryTemplateId: undefined // 运费模版
})
const rules = reactive({
  deliveryTypes: [required],
  /**
   * ⚠️ 运费模板**只在勾了「快递发货」时才必填** —— 输入框本身就是按这个条件 `v-if` 渲染的。
   *
   * 原来这里写的是无条件的 `[required]`，于是**只勾「用户自提」或「虚拟商品」的商品存不下来**：
   * 字段被 `v-if` 隐藏了，校验却照样跑，报「【物流设置】不完善」而看不出缺什么。
   * （2026-10-16 加虚拟商品时一并修掉 —— 不修则「虚拟商品」这个选项根本用不了。）
   */
  deliveryTemplateId: [
    {
      validator: (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
        const needTemplate = formData.deliveryTypes?.includes(DeliveryTypeEnum.EXPRESS.type)
        if (needTemplate && !value) {
          callback(new Error('请选择运费模板'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
})

/** 将传进来的值赋值给 formData */
watch(
  () => props.propFormData,
  (data) => {
    if (!data) {
      return
    }
    copyValueToTarget(formData, data)
  },
  {
    immediate: true
  }
)

/** 表单校验 */
const emit = defineEmits(['update:activeName'])
const validate = async () => {
  if (!formRef) return
  try {
    await unref(formRef)?.validate()
    // 校验通过更新数据
    Object.assign(props.propFormData, formData)
  } catch (e) {
    message.error('【物流设置】不完善，请填写相关信息')
    emit('update:activeName', 'delivery')
    throw e // 目的截断之后的校验
  }
}
defineExpose({ validate })

/** 初始化 */
const deliveryTemplateList = ref<ExpressTemplateApi.DeliveryExpressTemplateVO[]>([]) // 运费模版
onMounted(async () => {
  deliveryTemplateList.value = await ExpressTemplateApi.getSimpleTemplateList()
})
</script>
