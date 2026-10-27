<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="queryParams.title"
          class="!w-220px"
          clearable
          placeholder="请输入标题"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="分类" prop="categoryId">
        <el-select v-model="queryParams.categoryId" class="!w-180px" clearable placeholder="全部">
          <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="queryParams.type" class="!w-140px" clearable placeholder="全部">
          <el-option label="桌面端软件" :value="1" />
          <el-option label="打印驱动" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button v-hasPermi="['software:info:create']" type="primary" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" />新增软件
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="编号" prop="id" width="80" />
      <el-table-column label="标题" prop="title" min-width="200" />
      <el-table-column label="分类" width="150">
        <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="row.type === 1 ? 'primary' : 'info'">
            {{ row.type === 1 ? '桌面端软件' : '打印驱动' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="服务支持页" width="110">
        <template #default="{ row }">
          <el-tag :type="row.showInSupport ? 'success' : 'info'">
            {{ row.showInSupport ? '展示' : '不展示' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openVersions(row)">版本</el-button>
          <el-button v-hasPermi="['software:info:update']" link type="primary" @click="openForm(row.id)">
            编辑
          </el-button>
          <el-button v-hasPermi="['software:info:delete']" link type="danger" @click="handleDelete(row)">
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

  <InfoForm ref="formRef" @success="getList" />
  <VersionPanel ref="versionPanelRef" />
</template>

<script lang="ts" setup>
import { deleteSoftware, getSoftwareCategoryList, getSoftwarePage, type SoftwareCategoryVO, type SoftwareVO } from '@/api/mall/software'
import InfoForm from './InfoForm.vue'
import VersionPanel from './VersionPanel.vue'

defineOptions({ name: 'MallSoftware' })

const message = useMessage()
const loading = ref(false)
const list = ref<SoftwareVO[]>([])
const total = ref(0)
const categories = ref<SoftwareCategoryVO[]>([])
const queryFormRef = ref()
const formRef = ref()
const versionPanelRef = ref()
const queryParams = reactive<{
  pageNo: number
  pageSize: number
  title?: string
  categoryId?: number
  type?: number
}>({ pageNo: 1, pageSize: 10, title: '', categoryId: undefined, type: undefined })

// 找不到名称时回落成「#id」而不是空白：便于区分「分类没加载」与「分类已删除」
const categoryName = (id?: number) => {
  if (id == null) return '—'
  return categories.value.find((c) => c.id === id)?.name ?? `#${id}`
}

const getList = async () => {
  loading.value = true
  try {
    const data = await getSoftwarePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    categories.value = await getSoftwareCategoryList()
  } catch {
    categories.value = []
    message.error('分类加载失败：请确认当前角色有「软件分类」查询权限')
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.title = ''
  queryParams.categoryId = undefined
  queryParams.type = undefined
  handleQuery()
}

const openForm = (id?: number) => formRef.value?.open(id)
const openVersions = (row: SoftwareVO) => versionPanelRef.value?.open(row.id, row.title)

const handleDelete = async (row: SoftwareVO) => {
  await message.delConfirm(`删除「${row.title}」？记录会消失，但已发出的下载链接仍然可用。`)
  await deleteSoftware(row.id)
  message.success('已删除')
  await getList()
}

onMounted(async () => {
  await loadCategories()
  await getList()
})
</script>
