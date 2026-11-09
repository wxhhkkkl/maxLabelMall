<template>
  <div v-loading="loading">
    <el-radio-group
      v-if="list.length"
      :model-value="modelValue"
      class="flex flex-col items-start gap-10px"
      @update:model-value="onChange"
    >
      <el-radio v-for="address in list" :key="address.id" :value="address.id" border>
        <div class="font-medium">
          {{ address.name }}　{{ address.mobile }}
          <el-tag v-if="address.defaultStatus" class="ml-5px" size="small" type="success">
            默认
          </el-tag>
        </div>
        <div class="text-gray-500">
          {{ address.areaName || '' }} {{ address.detailAddress }}
        </div>
      </el-radio>
    </el-radio-group>
    <el-empty
      v-else-if="!loading"
      :image-size="60"
      description="该客户没有收货地址，请先在 C 端添加"
    />
  </div>
</template>

<script lang="ts" setup>
import * as AddressApi from '@/api/member/address'

defineOptions({ name: 'AgentAddressSelect' })

const props = defineProps<{
  userId?: number
  modelValue?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value?: number): void
}>()

const loading = ref(false)
const list = ref<AddressApi.AddressVO[]>([])

const onChange = (value: number) => {
  emit('update:modelValue', value)
}

const getList = async () => {
  if (!props.userId) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = await AddressApi.getAddressList({ userId: props.userId })
  } finally {
    loading.value = false
  }
}

watch(
  () => props.userId,
  () => {
    emit('update:modelValue', undefined)
    getList()
  },
  { immediate: true }
)
</script>
