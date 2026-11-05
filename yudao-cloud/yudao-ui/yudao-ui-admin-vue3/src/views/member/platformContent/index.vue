<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="80px">
      <el-form-item label="名称">
        <el-input v-model="queryParams.keyword" clearable placeholder="名称 / 分类 / 关键词" class="!w-220px" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.contentType" clearable placeholder="全部" class="!w-140px">
          <el-option label="模板" :value="1" />
          <el-option label="纯文本" :value="2" />
          <el-option label="Excel" :value="3" />
          <el-option label="CSV" :value="4" />
          <el-option label="图片" :value="5" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" class="!w-120px">
          <el-option label="上架" :value="1" />
          <el-option label="下架" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="primary" plain @click="openCreate"
                   v-hasPermi="['member:platform-content:create']">
          <Icon icon="ep:plus" class="mr-5px" />上传内容
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="示意图" width="120">
        <template #default="{ row }">
          <el-image v-if="row.thumb" :src="row.thumb" :preview-src-list="[row.thumb]"
                    preview-teleported fit="contain" class="pc-thumb" />
          <span v-else class="pc-thumb-empty">—</span>
        </template>
      </el-table-column>
      <el-table-column label="名称" prop="name" min-width="200" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeText(row.contentType) }}</template>
      </el-table-column>
      <el-table-column label="大小" width="110">
        <template #default="{ row }">{{ sizeText(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column label="分类" prop="category" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上架时间" prop="publishedTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row.id)"
                     v-hasPermi="['member:platform-content:update']">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)"
                     v-hasPermi="['member:platform-content:update']">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="remove(row.id)"
                     v-hasPermi="['member:platform-content:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <PlatformContentForm v-model:visible="formVisible" :id="formId" @success="getList" />
</template>

<script setup lang="ts">
import { PlatformContentApi, type PlatformContentPageReqVO, type PlatformContentVO } from '@/api/member/platformContent'
import { dateFormatter } from '@/utils/formatTime'
import PlatformContentForm from './PlatformContentForm.vue'

defineOptions({ name: 'MemberPlatformContent' })

const message = useMessage()
const loading = ref(false)
const list = ref<PlatformContentVO[]>([])
const total = ref(0)
const formVisible = ref(false)
const formId = ref<number>()

const queryParams = reactive<PlatformContentPageReqVO>({ pageNo: 1, pageSize: 10 })

const getList = async () => {
  loading.value = true
  try {
    const data = await PlatformContentApi.getPage(queryParams)
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
  queryParams.keyword = undefined
  queryParams.contentType = undefined
  queryParams.status = undefined
  handleQuery()
}

const openCreate = () => {
  formId.value = undefined
  formVisible.value = true
}

const openEdit = (id: number) => {
  formId.value = id
  formVisible.value = true
}

const toggleStatus = async (row: PlatformContentVO) => {
  const next = row.status === 1 ? 0 : 1
  await message.delConfirm(next === 1 ? '确认上架该内容？' : '确认下架该内容？下架后用户不可见。')
  await PlatformContentApi.updateStatus(row.id!, next)
  message.success(next === 1 ? '已上架' : '已下架')
  await getList()
}

const remove = async (id: number) => {
  await message.delConfirm('确认删除该平台内容？已下载的副本不受影响。')
  await PlatformContentApi.delete(id)
  message.success('已删除')
  await getList()
}

const typeText = (type: number) =>
  ({ 1: '模板', 2: '纯文本', 3: 'Excel', 4: 'CSV', 5: '图片' } as Record<number, string>)[type] ?? '未知'

const sizeText = (bytes?: number) => {
  if (!bytes) return '0 B'
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}

onMounted(getList)
</script>

<style scoped>
.pc-thumb {
  width: 96px;
  height: 60px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: #ffffff;
}

.pc-thumb-empty {
  color: var(--el-text-color-placeholder);
}
</style>
