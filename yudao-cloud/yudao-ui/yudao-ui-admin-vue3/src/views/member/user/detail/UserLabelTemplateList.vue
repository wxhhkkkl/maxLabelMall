<template>
  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="名称" prop="name" min-width="180" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column label="尺寸" width="140">
        <template #default="{ row }">{{ row.widthMm }} × {{ row.heightMm }} mm</template>
      </el-table-column>
      <el-table-column label="分类" prop="category" width="120" />
      <el-table-column label="共享状态" width="110">
        <template #default="{ row }">
          <el-tag :type="shareTag(row.shareStatus)">{{ shareText(row.shareStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上传时间" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="preview(row.id)">预览</el-button>
          <el-button link type="primary" @click="download(row.id)">下载</el-button>
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

  <Dialog v-model="previewVisible" title="模板预览" width="760px">
    <el-input v-model="previewContent" :rows="20" readonly type="textarea" />
  </Dialog>
</template>

<script setup lang="ts">
import {
  getTemplateContent,
  getTemplatePage,
  type LabelTemplateVO
} from '@/api/member/template'
import { dateFormatter } from '@/utils/formatTime'

const props = defineProps<{ userId: number }>()

const loading = ref(false)
const list = ref<LabelTemplateVO[]>([])
const total = ref(0)
const previewVisible = ref(false)
const previewContent = ref('')
const queryParams = reactive({ pageNo: 1, pageSize: 10, userId: props.userId })

const SHARE_TEXT: Record<number, string> = { 0: '待审核', 1: '已公开', 2: '已驳回', 3: '已下架' }
const shareText = (status: number | null) => (status === null || status === undefined ? '未共享' : SHARE_TEXT[status] ?? '未知')
const shareTag = (status: number | null) =>
  status === 1 ? 'success' : status === 2 ? 'danger' : status === 3 ? 'info' : 'warning'

const formatSize = (bytes: number) => (bytes > 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(bytes / 1024)} KB`)

const getList = async () => {
  loading.value = true
  try {
    const data = await getTemplatePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const preview = async (id: number) => {
  previewContent.value = (await getTemplateContent(id)).content ?? ''
  previewVisible.value = true
}

const download = async (id: number) => {
  const template = await getTemplateContent(id)
  const blob = new Blob([template.content ?? ''], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${template.name || 'template'}.msdx`
  anchor.click()
  URL.revokeObjectURL(url)
}

onMounted(getList)
</script>
