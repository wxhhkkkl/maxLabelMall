<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="名称" prop="name">
        <el-input
          v-model="queryParams.name"
          class="!w-200px"
          clearable
          placeholder="请输入分类名称"
          @keyup.enter="handleQuery"
        />
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
        <el-button v-hasPermi="['software:category:create']" type="primary" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" />新增分类
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="编号" prop="id" width="80" />
      <el-table-column label="名称" prop="name" min-width="200" />
      <el-table-column label="排序" prop="sort" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['software:category:update']"
            link
            type="primary"
            @click="openForm(row)"
          >
            编辑
          </el-button>
          <el-button
            v-hasPermi="['software:category:delete']"
            link
            type="danger"
            @click="handleDelete(row)"
          >
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

  <Dialog v-model="formVisible" :title="formTitle">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="formData.name" placeholder="例如 打印驱动" />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :value="0">启用</el-radio>
          <el-radio :value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button :loading="formLoading" type="primary" @click="submitForm">确定</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import {
  createSoftwareCategory,
  deleteSoftwareCategory,
  getSoftwareCategoryPage,
  updateSoftwareCategory,
  type SoftwareCategoryVO
} from '@/api/mall/software'

defineOptions({ name: 'MallSoftwareCategory' })

const message = useMessage()
const loading = ref(false)
const list = ref<SoftwareCategoryVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive<{ pageNo: number; pageSize: number; name?: string; status?: number }>({
  pageNo: 1,
  pageSize: 10,
  name: '',
  status: undefined
})

const formVisible = ref(false)
const formTitle = ref('')
const formLoading = ref(false)
const formRef = ref()
const formData = reactive<Partial<SoftwareCategoryVO>>({
  id: undefined,
  name: '',
  sort: 0,
  status: 0
})
const formRules = reactive({
  name: [{ required: true, message: '分类名称不能为空', trigger: 'blur' }]
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getSoftwareCategoryPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.name = ''
  queryParams.status = undefined
  handleQuery()
}

const openForm = (row?: SoftwareCategoryVO) => {
  formVisible.value = true
  formTitle.value = row ? '编辑分类' : '新增分类'
  Object.assign(formData, row ?? { id: undefined, name: '', sort: 0, status: 0 })
}

const submitForm = async () => {
  await formRef.value?.validate()
  formLoading.value = true
  try {
    if (formData.id) await updateSoftwareCategory(formData)
    else await createSoftwareCategory(formData)
    message.success('保存成功')
    formVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

const handleDelete = async (row: SoftwareCategoryVO) => {
  await message.delConfirm('删除后不可恢复；分类下仍有软件时无法删除。')
  await deleteSoftwareCategory(row.id)
  message.success('已删除')
  await getList()
}

onMounted(getList)
</script>
