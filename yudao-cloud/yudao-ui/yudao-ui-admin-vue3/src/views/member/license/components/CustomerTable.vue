<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="客户类型">
        <el-select v-model="queryParams.targetType" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option label="个人" :value="1" />
          <el-option label="企业" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="授权状态">
        <el-select v-model="queryParams.status" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option label="有效" :value="0" />
          <el-option label="已过期" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="权益类型">
        <el-select v-model="queryParams.rightsType" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option label="订阅会员" :value="1" />
          <el-option label="席位会员" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input
          v-model="queryParams.keyword"
          class="!w-240px"
          clearable
          placeholder="昵称 / 手机号 / 企业名"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="客户" min-width="180">
        <template #default="{ row }">
          <span>{{ row.targetName || '—' }}</span>
          <el-tag v-if="row.targetType === 2" class="ml-8px" size="small" type="info">企业</el-tag>
          <div v-if="row.orgName" class="text-12px text-gray-500">{{ row.orgName }}</div>
        </template>
      </el-table-column>
      <el-table-column label="手机号" prop="targetMobile" width="130" />
      <el-table-column label="来源" min-width="150">
        <template #default="{ row }">
          <el-tag v-for="source in row.sources || []" :key="source" class="mr-4px" size="small">
            {{ SOURCE_LABEL[source] || source }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="授权开始" width="120">
        <template #default="{ row }">{{ formatDate(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="到期" width="120">
        <template #default="{ row }">{{ formatDate(row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="剩余 / 已过期" width="130">
        <template #default="{ row }">
          <span v-if="row.active">剩 {{ row.remainingDays }} 天</span>
          <!-- 已过期时剩余天数固定 0，另给"已过期 N 天"，不塞负数 -->
          <span v-else class="text-gray-500">已过期 {{ row.expiredDays }} 天</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '有效' : '已过期' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canGift" label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('grant', row)">赠送授权</el-button>
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
import * as LicenseApi from '@/api/member/license'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'LicenseCustomerTable' })

const SOURCE_LABEL: Record<string, string> = {
  ORDER: '订单购买',
  GRANT: '运营赠送',
  ORG_SEAT: '企业席位'
}

defineProps<{ canGift?: boolean }>()
const emit = defineEmits<{ (e: 'grant', row: LicenseApi.LicenseCustomerVO): void }>()

const loading = ref(false)
const list = ref<LicenseApi.LicenseCustomerVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  targetType: undefined as number | undefined,
  status: undefined as number | undefined,
  rightsType: undefined as number | undefined,
  keyword: ''
})

const getList = async () => {
  loading.value = true
  try {
    const data = await LicenseApi.getLicenseCustomerPage(queryParams)
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
