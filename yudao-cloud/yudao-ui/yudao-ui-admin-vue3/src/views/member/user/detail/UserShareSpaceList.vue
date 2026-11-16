<template>
  <ContentWrap>
    <div class="member-space-layout">
      <aside class="member-space-tree">
        <div class="folder-row" :class="{ active: selectedFolderId === null }" @click="selectFolder(null)">
          <span>全部内容</span>
        </div>
        <div class="folder-row" :class="{ active: selectedFolderId === 0 }" @click="selectFolder(0)">
          <span>默认目录</span>
        </div>
        <div
          v-for="row in folderRows"
          :key="row.folder.id"
          class="folder-row"
          :class="{ active: selectedFolderId === row.folder.id }"
          :style="{ paddingLeft: `${10 + row.depth * 14}px` }"
          @click="selectFolder(row.folder.id)"
        >
          <span class="folder-name">{{ row.folder.name }}</span>
          <span class="folder-count">{{ row.folder.contentCount ?? 0 }}</span>
        </div>
        <div v-if="folderRows.length === 0" class="folder-empty">暂无文件夹</div>
      </aside>
      <div class="member-space-main">
        <div v-if="overview" class="mb-10px text-13px text-gray-600">
          容量占用：{{ formatSize(overview.usedBytes) }} / {{ formatSize(overview.quotaBytes) }}
          · 剩余 {{ formatSize(overview.remainingBytes) }}
        </div>

    <el-form inline class="mb-10px">
      <el-form-item label="类型">
        <el-select v-model="queryParams.contentType" clearable placeholder="全部" class="!w-140px">
          <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          clearable
          placeholder="名称/分类/关键词"
          class="!w-220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="名称" prop="name" min-width="200" />
      <el-table-column label="文件夹" width="150">
        <template #default="{ row }">{{ row.folderName || '—' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ contentTypeText(row.contentType) }}</template>
      </el-table-column>
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column label="尺寸" width="140">
        <template #default="{ row }">
          {{ row.widthMm && row.heightMm ? `${row.widthMm} × ${row.heightMm} mm` : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="分类" prop="category" width="120" />
      <el-table-column label="上传时间" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="preview(row)">预览</el-button>
          <el-button link type="primary" @click="download(row)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
      </div>
    </div>
  </ContentWrap>

  <Dialog v-model="previewVisible" title="内容预览" width="760px">
    <el-input v-model="previewContent" :rows="14" readonly type="textarea" />
  </Dialog>
</template>

<script setup lang="ts">
import {
  getMemberShareContent,
  getMemberShareContentPage,
  getMemberShareFolders,
  getMemberShareOverview,
  type OrgShareContentVO,
  type OrgShareFolderVO,
  type OrgShareOverviewVO
} from '@/api/member/orgShareContent'
import { dateFormatter } from '@/utils/formatTime'

const props = defineProps<{ userId: number }>()

const TYPE_OPTIONS = [
  { label: '模板', value: 1 },
  { label: '纯文本', value: 2 },
  { label: 'Excel', value: 3 },
  { label: 'CSV', value: 4 },
  { label: '图片', value: 5 }
]
const TYPE_TEXT: Record<number, string> = Object.fromEntries(
  TYPE_OPTIONS.map((item) => [item.value, item.label])
)

const loading = ref(false)
const overview = ref<OrgShareOverviewVO>()
const folders = ref<OrgShareFolderVO[]>([])
const selectedFolderId = ref<number | null>(null)
const list = ref<OrgShareContentVO[]>([])
const total = ref(0)
const previewVisible = ref(false)
const previewContent = ref('')
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  contentType: undefined as number | undefined,
  keyword: ''
})

const contentTypeText = (type: number) => TYPE_TEXT[type] ?? '未知'
const formatSize = (bytes?: number) => {
  if (!bytes) return '0 B'
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}

const folderRows = computed(() => {
  const rows: { folder: OrgShareFolderVO; depth: number }[] = []
  const walk = (parentId: number | null, depth: number) => {
    folders.value
      .filter((folder) => (folder.parentId ?? null) === parentId)
      .slice()
      .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
      .forEach((folder) => {
        rows.push({ folder, depth })
        walk(folder.id, depth + 1)
      })
  }
  walk(null, 0)
  return rows
})

const selectFolder = (folderId: number | null) => {
  selectedFolderId.value = folderId
  queryParams.pageNo = 1
  getList()
}

const loadFolders = async () => {
  folders.value = await getMemberShareFolders(props.userId)
}

const getList = async () => {
  loading.value = true
  try {
    const data = await getMemberShareContentPage({
      memberId: props.userId,
      folderId: selectedFolderId.value ?? undefined,
      contentType: queryParams.contentType,
      keyword: queryParams.keyword.trim() || undefined,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const loadOverview = async () => {
  overview.value = await getMemberShareOverview(props.userId)
}

const handleSearch = () => {
  queryParams.pageNo = 1
  getList()
}

const preview = async (row: OrgShareContentVO) => {
  const detail = await getMemberShareContent(props.userId, row.id)
  if (detail.storageType === 1 && detail.content) {
    previewContent.value = detail.content
    previewVisible.value = true
  } else if (detail.downloadUrl) {
    window.open(detail.downloadUrl, '_blank')
  }
}

const download = async (row: OrgShareContentVO) => {
  const detail = await getMemberShareContent(props.userId, row.id)
  if (detail.storageType === 1 && detail.content != null) {
    const blob = new Blob([detail.content], { type: detail.mimeType || 'text/plain' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = detail.name || 'content'
    anchor.click()
    URL.revokeObjectURL(url)
    return
  }
  if (detail.downloadUrl) window.open(detail.downloadUrl, '_blank')
}

onMounted(async () => {
  await Promise.all([loadFolders(), getList(), loadOverview()])
})

defineExpose({ reload: async () => Promise.all([loadFolders(), getList(), loadOverview()]) })
</script>

<style scoped>
.member-space-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.member-space-tree {
  width: 220px;
  flex-shrink: 0;
  max-height: 620px;
  overflow: auto;
  padding-right: 12px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.folder-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.folder-row:hover {
  background: var(--el-fill-color-light);
}

.folder-row.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.folder-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.folder-empty {
  padding: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.member-space-main {
  flex: 1;
  min-width: 0;
}
</style>
