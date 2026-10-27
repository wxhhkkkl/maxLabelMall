<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" scroll max-height="70vh">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item label="分类" prop="categoryId">
        <el-select v-model="formData.categoryId" class="!w-320px" placeholder="请选择分类">
          <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="formData.title" class="!w-320px" placeholder="例如 MaxLabel 标签打印软件" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-radio-group v-model="formData.type">
          <el-radio :value="1">桌面端软件</el-radio>
          <el-radio :value="2">打印驱动</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="介绍" prop="introduction">
        <el-input v-model="formData.introduction" :rows="4" class="!w-420px" type="textarea" />
      </el-form-item>
      <el-form-item label="服务支持页展示">
        <el-switch v-model="formData.showInSupport" />
        <span class="ml-10px text-12px text-gray-500">
          关闭后用户看不到，但客户端更新不受影响
        </span>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :value="0">启用</el-radio>
          <el-radio :value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="formLoading" type="primary" @click="submitForm">确定</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { createSoftware, getSoftwareCategoryList, getSoftware, updateSoftware, type SoftwareCategoryVO } from '@/api/mall/software'

defineOptions({ name: 'SoftwareInfoForm' })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formRef = ref()
const emit = defineEmits(['success'])
const message = useMessage()

const categories = ref<SoftwareCategoryVO[]>([])
const formData = ref({
  id: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  title: '',
  introduction: '',
  type: 1,
  showInSupport: true,
  status: 0
})
const formRules = reactive({
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  title: [{ required: true, message: '标题不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
})

const loadCategories = async () => {
  categories.value = await getSoftwareCategoryList()
}

const open = async (id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = id ? '编辑软件' : '新增软件'
  formLoading.value = true
  try {
    await loadCategories()
    if (id) {
      const detail = await getSoftware(id)
      Object.assign(formData.value, {
        id: detail.id,
        categoryId: detail.categoryId,
        title: detail.title,
        introduction: detail.introduction ?? '',
        type: detail.type,
        showInSupport: detail.showInSupport,
        status: detail.status
      })
    } else {
      Object.assign(formData.value, {
        id: undefined,
        categoryId: undefined,
        title: '',
        introduction: '',
        type: 1,
        showInSupport: true,
        status: 0
      })
    }
  } finally {
    formLoading.value = false
  }
}

const submitForm = async () => {
  await formRef.value?.validate()
  formLoading.value = true
  try {
    if (formData.value.id) await updateSoftware(formData.value)
    else await createSoftware(formData.value)
    message.success('保存成功')
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

defineExpose({ open })
</script>
