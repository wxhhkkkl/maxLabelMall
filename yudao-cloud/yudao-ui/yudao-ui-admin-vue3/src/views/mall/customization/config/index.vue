<template>
  <ContentWrap>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="160px" class="max-w-720px">
      <el-form-item label="打印初始价（元）" prop="printBasePriceYuan">
        <el-input-number v-model="formData.printBasePriceYuan" :min="0" :precision="2" :step="10" />
        <span class="ml-10px text-gray-500">低于该值的打印计算价按此收取</span>
      </el-form-item>
      <el-form-item label="印刷初始价（元）" prop="printingBasePriceYuan">
        <el-input-number v-model="formData.printingBasePriceYuan" :min="0" :precision="2" :step="10" />
        <span class="ml-10px text-gray-500">仅勾选印刷时生效</span>
      </el-form-item>
      <el-form-item label="模切初始价（元）" prop="dieCutBasePriceYuan">
        <el-input-number v-model="formData.dieCutBasePriceYuan" :min="0" :precision="2" :step="10" />
        <span class="ml-10px text-gray-500">仅勾选模切时生效；区间系数为 0 时仍按此收取</span>
      </el-form-item>
      <el-form-item label="加急初始价（元）" prop="urgentBasePriceYuan">
        <el-input-number v-model="formData.urgentBasePriceYuan" :min="0" :precision="2" :step="10" />
        <span class="ml-10px text-gray-500">仅勾选加急时生效</span>
      </el-form-item>
      <el-form-item label="加急系数" prop="urgentCoefficient">
        <el-input-number v-model="formData.urgentCoefficient" :min="1" :precision="4" :step="0.1" />
        <span class="ml-10px text-gray-500">加急费 = 小计 × (系数 − 1)，不足初始价时按初始价</span>
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['trade:customization-config:update']" type="primary" :loading="saving" @click="save">
          保存
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'

import { getConfig, saveConfig } from '@/api/mall/customization'

defineOptions({ name: 'CustomizationConfig' })

interface FormModel {
  printBasePriceYuan: number
  printingBasePriceYuan: number
  dieCutBasePriceYuan: number
  urgentBasePriceYuan: number
  urgentCoefficient: number
}

const formRef = ref<FormInstance>()
const saving = ref(false)
const formData = ref<FormModel>({
  printBasePriceYuan: 200,
  printingBasePriceYuan: 600,
  dieCutBasePriceYuan: 400,
  urgentBasePriceYuan: 50,
  urgentCoefficient: 1.1,
})

const rules: FormRules = {
  printBasePriceYuan: [{ required: true, message: '请输入打印初始价', trigger: 'blur' }],
  printingBasePriceYuan: [{ required: true, message: '请输入印刷初始价', trigger: 'blur' }],
  dieCutBasePriceYuan: [{ required: true, message: '请输入模切初始价', trigger: 'blur' }],
  urgentBasePriceYuan: [{ required: true, message: '请输入加急初始价', trigger: 'blur' }],
  urgentCoefficient: [{ required: true, message: '请输入加急系数', trigger: 'blur' }],
}

async function load() {
  const data = await getConfig()
  formData.value = {
    printBasePriceYuan: data.printBasePrice / 100,
    printingBasePriceYuan: data.printingBasePrice / 100,
    dieCutBasePriceYuan: data.dieCutBasePrice / 100,
    urgentBasePriceYuan: data.urgentBasePrice / 100,
    urgentCoefficient: data.urgentCoefficient,
  }
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await saveConfig({
      printBasePrice: Math.round(formData.value.printBasePriceYuan * 100),
      printingBasePrice: Math.round(formData.value.printingBasePriceYuan * 100),
      dieCutBasePrice: Math.round(formData.value.dieCutBasePriceYuan * 100),
      urgentBasePrice: Math.round(formData.value.urgentBasePriceYuan * 100),
      urgentCoefficient: formData.value.urgentCoefficient,
    })
    ElMessage.success('保存成功，下一次报价立即生效')
  } catch (err) {
    ElMessage.error((err as { message?: string })?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
