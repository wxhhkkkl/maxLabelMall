<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" class="!w-130px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-select v-model="queryParams.source" class="!w-130px" clearable placeholder="全部" @change="handleQuery">
          <el-option label="客户自助" :value="1" />
          <el-option label="客服代建" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input
          v-model="queryParams.keyword"
          class="!w-240px"
          clearable
          placeholder="昵称 / 手机号 / 设备型号"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="提交时间">
        <el-date-picker
          v-model="dateRange"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-280px"
          type="daterange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          @change="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
        <el-button v-if="canHandle" type="primary" @click="emit('create')">代建工单</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="会员" min-width="150">
        <template #default="{ row }">
          <span>{{ row.memberNickname || '—' }}</span>
          <div v-if="row.memberMobile" class="text-12px text-gray-500">{{ row.memberMobile }}</div>
        </template>
      </el-table-column>
      <el-table-column label="设备型号" min-width="150" prop="deviceModel" />
      <el-table-column label="故障描述" min-width="220" show-overflow-tooltip prop="faultDesc" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="支付状态" width="110">
        <template #default="{ row }">{{ row.payStatusName || '—' }}</template>
      </el-table-column>
      <el-table-column label="来源" width="100">
        <template #default="{ row }">{{ row.sourceName }}</template>
      </el-table-column>
      <el-table-column label="开单人" width="110">
        <template #default="{ row }">{{ row.creatorName || '—' }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('view', row)">查看</el-button>
          <el-button
            v-if="canHandle && (row.status === 0 || row.status === 10)"
            link
            type="primary"
            @click="emit('contact', row)"
          >联系</el-button>
          <el-button
            v-if="canHandle && (row.status === 0 || row.status === 10)"
            link
            type="primary"
            @click="emit('order', row)"
          >开单</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'CustomerServiceRepairTable' })
defineProps<{ canHandle?: boolean }>()
const emit = defineEmits<{
  (e: 'view', row: Api.CustomerServiceRepairVO): void
  (e: 'contact', row: Api.CustomerServiceRepairVO): void
  (e: 'order', row: Api.CustomerServiceRepairVO): void
  (e: 'create'): void
}>()

const STATUS_OPTIONS = [
  { label: '待联系', value: 0 },
  { label: '已联系', value: 10 },
  { label: '已开单', value: 20 },
  { label: '已寄出', value: 30 },
  { label: '维修中', value: 40 },
  { label: '已完成', value: 50 },
  { label: '已取消', value: 60 }
]

const loading = ref(false)
const list = ref<Api.CustomerServiceRepairVO[]>([])
const total = ref(0)
const dateRange = ref<string[]>()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  status: undefined as number | undefined,
  source: undefined as number | undefined,
  keyword: ''
})

const statusType = (status: number): 'success' | 'info' | 'warning' | undefined => {
  if (status === 50) return 'success'
  if (status === 60) return 'info'
  if (status === 40) return 'warning'
  return undefined
}

const getList = async () => {
  loading.value = true
  try {
    const data = await Api.getCustomerServiceRepairPage({
      ...queryParams,
      createTime: dateRange.value?.length === 2 ? dateRange.value : undefined
    })
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

defineExpose({ getList, queryParams })
onMounted(getList)
</script>
