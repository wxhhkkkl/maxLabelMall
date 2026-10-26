<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item label="商品 SPU" prop="spuId">
        <el-select
          v-model="formData.spuId"
          :disabled="!!formData.id"
          :loading="spuLoading"
          class="!w-400px"
          clearable
          filterable
          placeholder="搜索并选择商品"
          @change="handleSpuChange"
        >
          <el-option
            v-for="spu in spuOptions"
            :key="spu.id"
            :label="spuOptionLabel(spu)"
            :value="spu.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="商品 SKU" prop="skuId">
        <el-select
          v-model="formData.skuId"
          :disabled="!!formData.id || !formData.spuId"
          :loading="skuLoading"
          class="!w-480px"
          clearable
          filterable
          placeholder="先选 SPU，再选择 SKU"
        >
          <el-option
            v-for="sku in skuOptions"
            :key="sku.id"
            :label="skuOptionLabel(sku)"
            :value="sku.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="类别" prop="scope">
        <el-select v-model="formData.scope" class="!w-240px">
          <el-option label="个人" :value="1" />
          <el-option label="企业" :value="2" />
        </el-select>
        <span class="ml-10px text-12px text-gray-500">
          企业类别必须绑定勾选「企业专享」的商品
        </span>
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
import { getSpu, getSpuSimpleList, type Sku } from '@/api/mall/product/spu'

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
  scope: 1,
  enabled: true,
  sort: 0
})
const formRules = reactive({
  spuId: [{ required: true, message: '请选择商品 SPU', trigger: 'change' }],
  skuId: [{ required: true, message: '请选择商品 SKU', trigger: 'change' }],
  scope: [{ required: true, message: '请选择类别', trigger: 'change' }],
  periodMonths: [{ required: true, message: '授予周期不能为空', trigger: 'blur' }]
})

interface SpuOption {
  id: number
  name: string
  price?: number
}
const spuOptions = ref<SpuOption[]>([])
const skuOptions = ref<Sku[]>([])
const spuLoading = ref(false)
const skuLoading = ref(false)
const spuOptionLabel = (spu: SpuOption) => `${spu.name}（ID ${spu.id}）`
const skuOptionLabel = (sku: Sku) => {
  const spec = (sku.properties ?? []).map((property) => property.valueName).filter(Boolean).join(' / ') || '默认'
  return `ID ${sku.id} · ${spec} · ¥${((Number(sku.price) || 0) / 100).toFixed(2)} · 库存 ${sku.stock ?? 0}`
}

const loadSpuOptions = async () => {
  if (spuOptions.value.length) return
  spuLoading.value = true
  try {
    const data = await getSpuSimpleList()
    spuOptions.value = Array.isArray(data) ? data : (data?.list ?? [])
  } finally {
    spuLoading.value = false
  }
}

const loadSkuOptions = async (spuId?: number) => {
  if (!spuId) {
    skuOptions.value = []
    return
  }
  skuLoading.value = true
  try {
    const spu = await getSpu(spuId)
    skuOptions.value = spu?.skus ?? []
  } finally {
    skuLoading.value = false
  }
}

const handleSpuChange = async (spuId?: number) => {
  formData.skuId = undefined as never
  await loadSkuOptions(spuId)
}
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
  await loadSpuOptions()
  if (!id) return
  formLoading.value = true
  try {
    Object.assign(formData, await getSubscriptionPlan(id))
    await loadSkuOptions(formData.spuId)
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
