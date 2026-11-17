<template>
  <ContentWrap>
    <!-- FR-553：按被赠对象 / 操作人 / 时间范围筛 -->
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="对象类型">
        <el-select v-model="queryParams.targetType" class="!w-120px" clearable placeholder="全部">
          <el-option label="个人" :value="1" />
          <el-option label="企业" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="对象编号">
        <el-input v-model="queryParams.targetId" class="!w-140px" clearable placeholder="会员/企业编号" />
      </el-form-item>
      <el-form-item label="操作人编号">
        <el-input v-model="queryParams.operatorId" class="!w-140px" clearable placeholder="后台用户编号" />
      </el-form-item>
      <el-form-item label="赠送时间">
        <el-date-picker
          v-model="queryParams.createTime"
          :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
          class="!w-240px"
          end-placeholder="结束日期"
          range-separator="-"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="赠送时间" width="170">
        <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="对象" min-width="140">
        <template #default="{ row }">
          {{ row.targetName || row.targetId }}
          <el-tag class="ml-4px" size="small" type="info">
            {{ row.targetType === 2 ? '企业' : '个人' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容" width="140">
        <template #default="{ row }">
          <span v-if="row.contentType === 1">{{ row.months }} 个月</span>
          <span v-else>{{ row.seats }} 个席位</span>
        </template>
      </el-table-column>
      <el-table-column label="到期变化" width="220">
        <template #default="{ row }">
          {{ formatDate(row.beforeEndTime) || '无' }} → {{ formatDate(row.afterEndTime) }}
        </template>
      </el-table-column>
      <el-table-column label="理由" prop="reason" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作人" prop="operatorName" width="120" />
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
import * as LicenseApi from '@/api/member/license'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'LicenseGrantRecordTable' })

const loading = ref(false)
const list = ref<LicenseApi.LicenseGrantVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  targetType: undefined as number | undefined,
  targetId: undefined as number | string | undefined,
  operatorId: undefined as number | string | undefined,
  createTime: undefined as string[] | undefined
})

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const getList = async () => {
  loading.value = true
  try {
    const data = await LicenseApi.getLicenseGrantPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

defineExpose({ getList })
onMounted(getList)
</script>
