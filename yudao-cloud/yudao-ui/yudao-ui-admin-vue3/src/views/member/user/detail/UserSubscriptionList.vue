<template>
  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="周期" prop="periodMonths" width="90">
        <template #default="{ row }">{{ row.periodMonths }} 个月</template>
      </el-table-column>
      <el-table-column label="开始日期" prop="startDate" width="120" />
      <el-table-column label="到期日" prop="endDate" width="120" />
      <el-table-column label="剩余天数" prop="remainingDays" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.active ? 'success' : 'info'">
            {{ row.active ? '有效' : '已过期' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源订单" prop="sourceOrderNo" min-width="180" />
      <el-table-column label="退款" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.refunded" type="warning">来源已退款</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="180" :formatter="dateFormatter" />
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
import { getSubscriptionPage, type MemberSubscriptionVO } from '@/api/member/subscription'
import { dateFormatter } from '@/utils/formatTime'

const props = defineProps<{ userId: number }>()

const loading = ref(false)
const list = ref<MemberSubscriptionVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  userId: props.userId
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getSubscriptionPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

onMounted(getList)
</script>
