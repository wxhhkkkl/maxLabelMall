<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="88px">
      <el-form-item label="审核状态">
        <el-select v-model="queryParams.auditStatus" class="!w-160px" clearable placeholder="全部">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="已下架" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="queryParams.keyword" class="!w-220px" clearable placeholder="模板名称" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="名称" prop="name" min-width="180" />
      <el-table-column label="发布会员" width="160">
        <template #default="{ row }">{{ row.publisherNickname || row.publisherMobile || row.publisherMemberId }}</template>
      </el-table-column>
      <el-table-column label="尺寸" width="140">
        <template #default="{ row }">{{ row.widthMm }} × {{ row.heightMm }} mm</template>
      </el-table-column>
      <el-table-column label="分类" prop="category" width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.auditStatus)">{{ statusText(row.auditStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="preview(row.id)">预览</el-button>
          <el-button
            v-if="row.auditStatus === 0"
            v-hasPermi="['member:shared-template:audit']"
            link
            type="success"
            @click="approve(row.id)"
          >
            通过
          </el-button>
          <el-button
            v-if="row.auditStatus === 0"
            v-hasPermi="['member:shared-template:audit']"
            link
            type="danger"
            @click="reject(row.id)"
          >
            驳回
          </el-button>
          <el-button
            v-if="row.auditStatus === 1"
            v-hasPermi="['member:shared-template:offline']"
            link
            type="warning"
            @click="offline(row.id)"
          >
            下架
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

  <Dialog v-model="previewVisible" title="共享模板预览" width="760px">
    <el-input v-model="previewContent" :rows="20" readonly type="textarea" />
  </Dialog>
</template>

<script setup lang="ts">
import {
  approveSharedTemplate,
  getSharedTemplate,
  getSharedTemplatePage,
  offlineSharedTemplate,
  rejectSharedTemplate,
  type SharedTemplateVO
} from '@/api/member/template'
import { dateFormatter } from '@/utils/formatTime'
import { ElMessageBox } from 'element-plus'

defineOptions({ name: 'MemberSharedTemplateReview' })

const message = useMessage()
const loading = ref(false)
const list = ref<SharedTemplateVO[]>([])
const total = ref(0)
const previewVisible = ref(false)
const previewContent = ref('')
const queryParams = reactive<{
  pageNo: number
  pageSize: number
  auditStatus?: number
  keyword?: string
}>({ pageNo: 1, pageSize: 10, auditStatus: undefined, keyword: undefined })

const STATUS_TEXT: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已驳回', 3: '已下架' }
const statusText = (status: number) => STATUS_TEXT[status] ?? '未知'
const statusTag = (status: number) =>
  status === 1 ? 'success' : status === 2 ? 'danger' : status === 3 ? 'info' : 'warning'

const getList = async () => {
  loading.value = true
  try {
    const data = await getSharedTemplatePage(queryParams)
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
  queryParams.auditStatus = undefined
  queryParams.keyword = undefined
  handleQuery()
}

const preview = async (id: number) => {
  previewContent.value = (await getSharedTemplate(id)).content ?? ''
  previewVisible.value = true
}

const approve = async (id: number) => {
  await message.delConfirm('确认通过该共享模板？通过后将对所有会员可见。')
  await approveSharedTemplate(id)
  message.success('已通过')
  await getList()
}

const reject = async (id: number) => {
  const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回共享模板', {
    inputValidator: (value: string) => (value && value.trim() ? true : '驳回原因不能为空')
  })
  await rejectSharedTemplate(id, value.trim())
  message.success('已驳回')
  await getList()
}

const offline = async (id: number) => {
  await message.delConfirm('确认下架该共享模板？已下载的本地副本不受影响。')
  await offlineSharedTemplate(id)
  message.success('已下架')
  await getList()
}

onMounted(getList)
</script>
