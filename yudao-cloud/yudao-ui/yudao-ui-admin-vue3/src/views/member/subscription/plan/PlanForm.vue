<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item label="SPU 编号" prop="spuId">
        <el-input-number
          v-model="formData.spuId"
          :disabled="!!formData.id"
          :min="1"
          :controls="false"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="SKU 编号" prop="skuId">
        <el-input-number
          v-model="formData.skuId"
          :disabled="!!formData.id"
          :min="1"
          :controls="false"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="授予周期（月）" prop="periodMonths">
        <el-input-number v-model="formData.periodMonths" :min="1" :max="120" class="!w-240px" />
      </el-form-item>
      <el-form-item label="启用" prop="enabled">
        <el-switch v-model="formData.enabled" />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" class="!w-240px" />
      </el-form-item>
      <el-alert
        v-if="coverageHint"
        :title="coverageHint"
        type="warning"
        :closable="false"
        show-icon
      />
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="formLoading" type="primary" @click="submit">确定</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import {
  createSubscriptionPlan,
  getSubscriptionPlan,
  updateSubscriptionPlan,
  type SubscriptionPlanSaveVO
} from '@/api/mall/product/subscriptionPlan'

const emit = defineEmits(['success'])
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formRef = ref()
const formData = reactive<SubscriptionPlanSaveVO>({
  spuId: undefined as never,
  skuId: undefined as never,
  periodMonths: 12,
  enabled: true,
  sort: 0
})
const formRules = reactive({
  spuId: [{ required: true, message: 'SPU 编号不能为空', trigger: 'blur' }],
  skuId: [{ required: true, message: 'SKU 编号不能为空', trigger: 'blur' }],
  periodMonths: [{ required: true, message: '授予周期不能为空', trigger: 'blur' }]
})
const coverageHint = computed(() => {
  if (!formData.enabled) return ''
  if (formData.periodMonths === 1 || formData.periodMonths === 12) return ''
  return '当前启用周期既不等于 1 个月也不等于 12 个月；上架会员商品时建议同时提供月卡与年卡。'
})

const resetForm = () => {
  formData.id = undefined
  formData.spuId = undefined as never
  formData.skuId = undefined as never
  formData.periodMonths = 12
  formData.enabled = true
  formData.sort = 0
  formRef.value?.resetFields()
}

const open = async (id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = id ? '编辑会员授权计划' : '新增会员授权计划'
  resetForm()
  if (!id) return
  formLoading.value = true
  try {
    Object.assign(formData, await getSubscriptionPlan(id))
  } finally {
    formLoading.value = false
  }
}

const submit = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formData.id) {
      await updateSubscriptionPlan(formData)
      message.success('更新成功')
    } else {
      await createSubscriptionPlan(formData)
      message.success('创建成功')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

defineExpose({ open })
</script>
