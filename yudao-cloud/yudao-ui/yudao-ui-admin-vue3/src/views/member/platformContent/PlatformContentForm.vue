<template>
  <Dialog :model-value="visible" :title="id ? '编辑平台共享内容' : '上传平台共享内容'" width="640px"
          @update:model-value="(value: boolean) => emit('update:visible', value)">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
      <el-form-item label="内容类型" prop="contentType">
        <el-select v-model="formData.contentType" :disabled="!!id" class="!w-240px">
          <el-option label="模板" :value="1" />
          <el-option label="纯文本" :value="2" />
          <el-option label="Excel" :value="3" />
          <el-option label="CSV" :value="4" />
          <el-option label="图片" :value="5" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="formData.name" placeholder="含扩展名，如 面单.msdx" maxlength="255" />
      </el-form-item>
      <el-form-item v-if="!id" label="文件">
        <el-upload :auto-upload="false" :limit="1" :on-change="onFileChange" :on-remove="onFileRemove">
          <el-button type="primary" plain>选择文件</el-button>
          <template #tip>
            <div class="el-upload__tip">单文件不超过 16 MB；模板 / txt / csv / Excel / 图片</div>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item v-if="id && isText" label="正文">
        <el-input v-model="formData.content" type="textarea" :rows="10"
                  placeholder="模板正文（.msdx JSON）或文本内容" />
      </el-form-item>
      <el-form-item label="分类">
        <el-input v-model="formData.category" maxlength="64" class="!w-240px" />
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="formData.keywords" maxlength="255" placeholder="多个关键词用逗号分隔" />
      </el-form-item>
      <el-form-item v-if="isTemplate" label="尺寸（mm）">
        <el-input-number v-model="formData.widthMm" :min="0" :precision="2" controls-position="right" />
        <span class="mx-8px">×</span>
        <el-input-number v-model="formData.heightMm" :min="0" :precision="2" controls-position="right" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { PlatformContentApi, type PlatformContentVO } from '@/api/member/platformContent'
import type { FormInstance, UploadFile } from 'element-plus'

defineOptions({ name: 'PlatformContentForm' })

const props = defineProps<{ visible: boolean; id?: number }>()
const emit = defineEmits<{ 'update:visible': [boolean]; success: [] }>()

const message = useMessage()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const file = ref<File>()
const formData = reactive<PlatformContentVO>({
  contentType: 1,
  name: '',
  category: '',
  keywords: '',
  content: '',
  widthMm: 0,
  heightMm: 0
})

const isText = computed(() => [1, 2, 4].includes(formData.contentType))
const isTemplate = computed(() => formData.contentType === 1)

const rules = {
  contentType: [{ required: true, message: '请选择内容类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    file.value = undefined
    if (props.id) {
      const detail = await PlatformContentApi.get(props.id)
      Object.assign(formData, detail)
    } else {
      Object.assign(formData, {
        contentType: 1, name: '', category: '', keywords: '', content: '', widthMm: 0, heightMm: 0
      })
    }
  }
)

const onFileChange = (uploadFile: UploadFile) => {
  file.value = uploadFile.raw
  if (!formData.name && uploadFile.name) {
    formData.name = uploadFile.name
  }
}

const onFileRemove = () => {
  file.value = undefined
}

const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (props.id) {
      await PlatformContentApi.update({ ...formData, id: props.id })
      message.success('已保存')
    } else {
      if (!file.value) {
        message.error('请选择文件')
        return
      }
      const data = new FormData()
      data.append('contentType', String(formData.contentType))
      data.append('name', formData.name)
      if (formData.category) data.append('category', formData.category)
      if (formData.keywords) data.append('keywords', formData.keywords)
      if (formData.widthMm != null) data.append('widthMm', String(formData.widthMm))
      if (formData.heightMm != null) data.append('heightMm', String(formData.heightMm))
      data.append('file', file.value)
      await PlatformContentApi.upload(data)
      message.success('已上传')
    }
    emit('update:visible', false)
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>
