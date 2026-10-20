<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="88px"
    >
      <el-form-item label="SPU 编号" prop="spuId">
        <el-input-number
          v-model="queryParams.spuId"
          :controls="false"
          class="!w-180px"
          placeholder="全部"
          clearable
        />
      </el-form-item>
      <el-form-item label="启用" prop="enabled">
        <el-select v-model="queryParams.enabled" class="!w-160px" clearable placeholder="全部">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button v-hasPermi="['product:subscription-plan:create']" type="primary" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" />新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-alert
      v-if="coverageWarning"
      :title="coverageWarning"
      type="warning"
      :closable="false"
      show-icon
      class="mb-10px"
    />
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="编号" prop="id" width="80" />
      <el-table-column label="SPU / SKU" width="160">
        <template #default="{ row }">{{ row.spuId }} / {{ row.skuId }}</template>
      </el-table-column>
      <el-table-column label="商品" prop="spuName" min-width="180" />
      <el-table-column label="规格" prop="skuName" min-width="140" />
      <el-table-column label="周期" width="100">
        <template #default="{ row }">{{ row.periodMonths }} 个月</template>
      </el-table-column>
      <el-table-column label="价格（分）" prop="price" width="110" />
      <el-table-column label="启用" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sort" width="80" />
      <el-table-column label="创建时间" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['product:subscription-plan:update']"
            link
            type="primary"
            @click="openForm(row.id)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.enabled"
            v-hasPermi="['product:subscription-plan:update']"
            link
            type="danger"
            @click="disable(row)"
          >
            停用
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

  <PlanForm ref="formRef" @success="getList" />
</template>

<script setup lang="ts">
import {
  getSubscriptionPlanPage,
  updateSubscriptionPlan,
  type SubscriptionPlanVO
} from '@/api/mall/product/subscriptionPlan'
import { dateFormatter } from '@/utils/formatTime'
import PlanForm from './PlanForm.vue'

defineOptions({ name: 'MemberSubscriptionPlan' })

const message = useMessage()
const loading = ref(false)
const list = ref<SubscriptionPlanVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const formRef = ref()
const queryParams = reactive<{
  pageNo: number
  pageSize: number
  spuId?: number
  enabled?: boolean
}>({
  pageNo: 1,
  pageSize: 10,
  spuId: undefined,
  enabled: undefined
})

const coverageWarning = computed(() => {
  const enabled = list.value.filter((plan) => plan.enabled)
  if (enabled.length === 0) return '当前没有启用的会员授权计划，商城将无法购买会员。'
  const months = new Set(enabled.map((plan) => plan.periodMonths))
  if (!months.has(1) || !months.has(12)) {
    return '启用的会员计划缺少 1 个月或 12 个月周期；上架前建议同时提供月卡与年卡。'
  }
  return ''
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getSubscriptionPlanPage(queryParams)
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
  queryFormRef.value.resetFields()
  handleQuery()
}

const openForm = (id?: number) => {
  formRef.value.open(id)
}

const disable = async (row: SubscriptionPlanVO) => {
  await message.delConfirm('确认停用该会员授权计划？停用只影响之后的上架与购买，不影响已发放订阅。')
  await updateSubscriptionPlan({
    id: row.id,
    spuId: row.spuId,
    skuId: row.skuId,
    periodMonths: row.periodMonths,
    enabled: false,
    sort: row.sort
  })
  message.success('已停用')
  await getList()
}

onMounted(getList)
</script>
