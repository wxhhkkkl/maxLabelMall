<template>
  <SpuSelect
    ref="spuSelectRef"
    :is-select-sku="true"
    :radio="true"
    @confirm="onConfirm"
  />
</template>

<script lang="ts" setup>
import * as ProductSpuApi from '@/api/mall/product/spu'
import SpuSelect from '@/views/mall/promotion/components/SpuSelect.vue'
import type { AgentOrderLine } from './agentOrder'

defineOptions({ name: 'AgentProductSelect' })

const emit = defineEmits<{
  (e: 'select', line: AgentOrderLine): void
}>()

const spuSelectRef = ref<InstanceType<typeof SpuSelect>>()

const open = () => {
  spuSelectRef.value?.open()
}
defineExpose({ open })

const onConfirm = async (spuId: number, skuIds?: number[]) => {
  const skuId = skuIds?.[0]
  if (!skuId) {
    return
  }
  const spu = await ProductSpuApi.getSpu(spuId)
  const sku = spu.skus?.find((item) => item.id === skuId)
  if (!sku) {
    return
  }
  emit('select', {
    skuId,
    count: 1,
    spuId: spu.id,
    spuName: spu.name,
    picUrl: sku.picUrl || spu.picUrl,
    price: Number(sku.price ?? 0),
    stock: sku.stock,
    properties: sku.properties?.map((p) => p.valueName).filter(Boolean).join(' ')
  })
}
</script>
