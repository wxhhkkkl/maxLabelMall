<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="88px">
      <el-form-item label="打印时间">
        <el-date-picker
          v-model="timeRange"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-320px"
          end-placeholder="结束时间"
          start-placeholder="开始时间"
          type="daterange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="打印时间" prop="printTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="模板" prop="templateTitle" min-width="180" />
      <el-table-column label="方式" prop="mode" width="100" />
      <el-table-column label="张数" prop="count" width="90" />
      <el-table-column label="拷贝" prop="copies" width="90" />
      <el-table-column label="实际/已发送" width="130">
        <template #default="{ row }">{{ row.physicalCount ?? '—' }} / {{ row.sentCount ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="110" />
      <el-table-column label="打印机" prop="printer" min-width="150" />
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { getPrintRecordPage, type PrintRecordVO } from '@/api/member/printRecord'
import { dateFormatter } from '@/utils/formatTime'

const props = defineProps<{ userId: number }>()

const loading = ref(false)
const list = ref<PrintRecordVO[]>([])
const total = ref(0)
const timeRange = ref<[string, string] | undefined>()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  userId: props.userId,
  startTime: undefined as string | undefined,
  endTime: undefined as string | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getPrintRecordPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  queryParams.startTime = timeRange.value?.[0]
  queryParams.endTime = timeRange.value?.[1]
  getList()
}

const resetQuery = () => {
  timeRange.value = undefined
  queryParams.startTime = undefined
  queryParams.endTime = undefined
  handleQuery()
}

onMounted(getList)
</script>
