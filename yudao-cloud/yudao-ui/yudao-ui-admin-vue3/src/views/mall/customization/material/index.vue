<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="材质名称" prop="name">
        <el-input v-model="queryParams.name" class="!w-200px" clearable placeholder="请输入材质名称" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-140px" clearable placeholder="全部">
          <el-option label="启用" :value="0" />
          <el-option label="停用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button v-hasPermi="['trade:customization-material:create']" type="primary" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" />新增材质
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="编号" prop="id" width="80" />
      <el-table-column label="材质名称" prop="name" min-width="160" />
      <el-table-column label="基价（元/㎡）" width="120">
        <template #default="{ row }">{{ (row.basePrice / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="打印系数" prop="printCoefficient" width="100" />
      <el-table-column label="印刷系数" prop="printingCoefficient" width="100" />
      <el-table-column label="数量范围" width="150">
        <template #default="{ row }">{{ row.minQuantity }} ~ {{ row.maxQuantity }}</template>
      </el-table-column>
      <el-table-column label="宽高范围" width="160">
        <template #default="{ row }">
          {{ row.minWidth }}~{{ row.maxWidth }} × {{ row.minHeight }}~{{ row.maxHeight }} mm
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['trade:customization-material:update']" link type="primary" @click="openForm(row)">
            编辑
          </el-button>
          <el-button
            v-hasPermi="['trade:customization-material:update']"
            link
            type="warning"
            @click="toggleStatus(row)"
          >
            {{ row.status === 0 ? '停用' : '启用' }}
          </el-button>
          <el-button v-hasPermi="['trade:customization-material:delete']" link type="danger" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="formVisible" :title="formTitle" width="900px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="材质名称" prop="name">
            <el-input v-model="formData.name" placeholder="例如 可移除铜版" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="材质说明" prop="description">
            <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="展示给客户的材质说明（选填）" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="基价（元/㎡）" prop="basePriceYuan">
            <el-input-number v-model="formData.basePriceYuan" :min="0.01" :precision="2" :step="0.1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="材质打印系数" prop="printCoefficient">
            <el-input-number v-model="formData.printCoefficient" :min="0.01" :precision="4" :step="0.1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="印刷系数" prop="printingCoefficient">
            <el-input-number v-model="formData.printingCoefficient" :min="0.01" :precision="4" :step="0.1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数量范围" prop="minQuantity">
            <el-input-number v-model="formData.minQuantity" :min="1" /> ~
            <el-input-number v-model="formData.maxQuantity" :min="1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="宽度范围（mm）">
            <el-input-number v-model="formData.minWidth" :min="1" /> ~
            <el-input-number v-model="formData.maxWidth" :min="1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="高度范围（mm）">
            <el-input-number v-model="formData.minHeight" :min="1" /> ~
            <el-input-number v-model="formData.maxHeight" :min="1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-radio-group v-model="formData.status">
              <el-radio :value="0">启用</el-radio>
              <el-radio :value="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">面积区间（按整单总面积，㎡；必须连续覆盖）</el-divider>
      <el-table :data="formData.areaRules" size="small">
        <el-table-column label="下界（含）" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.minArea" :min="0" :precision="4" :controls="false" />
          </template>
        </el-table-column>
        <el-table-column label="上界（不含，0=无上限）" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.maxArea" :min="0" :precision="4" :controls="false" />
          </template>
        </el-table-column>
        <el-table-column label="打印系数" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.printCoefficient" :min="0.01" :precision="4" :controls="false" />
          </template>
        </el-table-column>
        <el-table-column label="模切系数" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.dieCutCoefficient" :min="0" :precision="4" :controls="false" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeRule($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="mt-10px" size="small" @click="addRule">添加区间</el-button>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  createMaterial,
  deleteMaterial,
  getMaterial,
  getMaterialPage,
  updateMaterial,
  updateMaterialStatus,
  type CustomizationMaterialVO,
} from '@/api/mall/customization'

defineOptions({ name: 'CustomizationMaterial' })

interface FormModel extends Omit<CustomizationMaterialVO, 'basePrice'> {
  basePriceYuan: number
}

const loading = ref(false)
const list = ref<CustomizationMaterialVO[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()
const queryParams = reactive({ pageNo: 1, pageSize: 10, name: '', status: undefined as number | undefined })

const formVisible = ref(false)
const formTitle = ref('')
const formRef = ref<FormInstance>()
const saving = ref(false)
const formData = ref<FormModel>(emptyForm())

function emptyForm(): FormModel {
  return {
    name: '',
    description: '',
    basePriceYuan: 0,
    printCoefficient: 1,
    printingCoefficient: 1,
    minQuantity: 1,
    maxQuantity: 100000,
    minWidth: 10,
    maxWidth: 2000,
    minHeight: 10,
    maxHeight: 2000,
    status: 0,
    sort: 0,
    areaRules: [{ minArea: 0, maxArea: 0, printCoefficient: 1, dieCutCoefficient: 0 }],
  }
}

const formRules: FormRules = {
  name: [{ required: true, message: '请输入材质名称', trigger: 'blur' }],
  basePriceYuan: [{ required: true, message: '请输入材质基价', trigger: 'blur' }],
}

async function getList() {
  loading.value = true
  try {
    const data = await getMaterialPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNo = 1
  getList()
}

function resetQuery() {
  queryFormRef.value?.resetFields()
  queryParams.name = ''
  queryParams.status = undefined
  handleQuery()
}

async function openForm(row?: CustomizationMaterialVO) {
  formTitle.value = row ? '编辑材质' : '新增材质'
  if (row) {
    const detail = await getMaterial(row.id!)
    formData.value = { ...detail, basePriceYuan: detail.basePrice / 100, areaRules: detail.areaRules ?? [] }
  } else {
    formData.value = emptyForm()
  }
  formVisible.value = true
}

function addRule() {
  formData.value.areaRules.push({ minArea: 0, maxArea: 0, printCoefficient: 1, dieCutCoefficient: 0 })
}

function removeRule(index: number) {
  formData.value.areaRules.splice(index, 1)
}

function buildPayload(): CustomizationMaterialVO {
  const { basePriceYuan, ...rest } = formData.value
  return { ...rest, basePrice: Math.round(basePriceYuan * 100), areaRules: rest.areaRules }
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = buildPayload()
    if (payload.id) await updateMaterial(payload)
    else await createMaterial(payload)
    ElMessage.success('保存成功')
    formVisible.value = false
    await getList()
  } catch (err) {
    ElMessage.error((err as { message?: string })?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: CustomizationMaterialVO) {
  await updateMaterialStatus(row.id!, row.status === 0 ? 1 : 0)
  ElMessage.success('已更新状态')
  await getList()
}

async function handleDelete(row: CustomizationMaterialVO) {
  await ElMessageBox.confirm(`确认删除材质「${row.name}」？历史订单不受影响。`, '提示', { type: 'warning' })
  await deleteMaterial(row.id!)
  ElMessage.success('已删除')
  await getList()
}

onMounted(getList)
</script>
