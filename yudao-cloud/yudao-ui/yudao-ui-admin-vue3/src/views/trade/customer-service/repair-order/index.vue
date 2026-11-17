<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="订单编号">
        <el-input v-model="queryParams.id" class="!w-140px" clearable placeholder="编号" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付状态">
        <el-select v-model="queryParams.payStatus" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="item in PAY_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="退款状态">
        <el-select v-model="queryParams.refundStatus" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="item in REFUND_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
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
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" width="90" prop="id" />
      <el-table-column label="会员" min-width="150">
        <template #default="{ row }">
          <span>{{ row.memberNickname || '—' }}</span>
          <div v-if="row.memberMobile" class="text-12px text-gray-500">{{ row.memberMobile }}</div>
        </template>
      </el-table-column>
      <el-table-column label="设备型号" min-width="140" prop="deviceModel" />
      <el-table-column label="应付合计" width="110">
        <template #default="{ row }">¥{{ ((row.payableAmount ?? 0) / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="支付状态" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="row.payStatus === 20 ? 'success' : 'info'">{{ row.payStatusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="退款状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="refundTagType(row.refundStatus)">{{ row.refundStatusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="完成维修" width="130">
        <template #default="{ row }">
          <template v-if="row.completeTime">
            <el-tag size="small" type="success">已完成</el-tag>
            <div class="text-12px text-gray-500">{{ row.completeOperatorName || '—' }}</div>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canHandle && canComplete(row)"
            link
            type="success"
            @click="completeOrder(row)"
          >完成维修</el-button>
          <el-button
            v-if="canRefund && row.payStatus === 20 && row.refundStatus === 0"
            link
            type="danger"
            @click="openRefund(row)"
          >退款</el-button>
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

  <Dialog v-model="detailVisible" title="维修订单详情" width="620px">
    <template v-if="detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单编号">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="维修申请">{{ detail.repairRequestId }}</el-descriptions-item>
        <el-descriptions-item label="会员">{{ detail.memberNickname || '—' }}</el-descriptions-item>
        <el-descriptions-item label="会员手机号">{{ detail.memberMobile || '—' }}</el-descriptions-item>
        <el-descriptions-item label="设备型号" :span="2">{{ detail.deviceModel || '—' }}</el-descriptions-item>
        <el-descriptions-item label="维修费">¥{{ ((detail.repairFee ?? 0) / 100).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="寄回运费">¥{{ ((detail.returnFreight ?? 0) / 100).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="应付合计">¥{{ ((detail.payableAmount ?? 0) / 100).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="费用说明">{{ detail.feeRemark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="支付状态">{{ detail.payStatusName }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ detail.payTime ? formatDate(detail.payTime) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="退款状态">{{ detail.refundStatusName }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">
          {{ detail.refundPrice != null ? `¥${(detail.refundPrice / 100).toFixed(2)}` : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="退款原因" :span="2">{{ detail.refundReason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="退款时间" :span="2">
          {{ detail.refundTime ? formatDate(detail.refundTime) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="完成维修">
          {{ detail.completeTime ? '已完成' : '未完成' }}
        </el-descriptions-item>
        <el-descriptions-item label="完成人 / 时间">
          {{ detail.completeTime
            ? `${detail.completeOperatorName || '—'} / ${formatDate(detail.completeTime)}`
            : '—' }}
        </el-descriptions-item>
      </el-descriptions>
    </template>
  </Dialog>

  <Dialog v-model="refundVisible" title="维修订单退款" width="480px">
    <el-form label-width="90px">
      <el-form-item label="订单编号">{{ refunding?.id }}</el-form-item>
      <el-form-item label="退款金额">
        <b class="text-red-500">¥{{ (((refunding?.payableAmount ?? 0)) / 100).toFixed(2) }}</b>
      </el-form-item>
      <el-form-item label="退款方式">
        {{ isOfflineRefund ? '线下人工退款（直接标记已退款）' : '在线支付原路退回' }}
      </el-form-item>
      <el-form-item label="退款原因" required>
        <el-input
          v-model="refundReason"
          :rows="3"
          maxlength="200"
          placeholder="请填写退款原因（必填）"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="refundVisible = false">取消</el-button>
      <el-button :loading="refundingSaving" type="danger" @click="submitRefund">确认退款</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import { ElMessageBox } from 'element-plus'

defineOptions({ name: 'TradeCustomerServiceRepairOrder' })

const message = useMessage()
const canRefund = computed(() => checkPermi(['trade:customer-service:refund']))
const canHandle = computed(() => checkPermi(['trade:customer-service:handle']))

const canComplete = (row: Api.CustomerServiceRepairOrderVO) =>
  !row.completeTime &&
  row.refundStatus !== 20 &&
  (row.payStatus === 20 || row.payStatus === 30) &&
  (row.repairStatus === 30 || row.repairStatus === 40 || row.repairStatus === 50)

const completeOrder = async (row: Api.CustomerServiceRepairOrderVO) => {
  await ElMessageBox.confirm(
    `确认「${row.deviceModel || '维修订单 #' + row.id}」已完成维修？完成后会记录当前操作人。`,
    '完成维修',
    { type: 'warning' }
  )
  await Api.completeCustomerServiceRepairOrder(row.id)
  message.success('已标记完成维修')
  await getList()
}

const PAY_STATUS_OPTIONS = [
  { label: '待支付', value: 0 },
  { label: '待确认收款', value: 10 },
  { label: '已支付', value: 20 },
  { label: '无需支付', value: 30 },
  { label: '已取消', value: 40 }
]
const REFUND_STATUS_OPTIONS = [
  { label: '未退款', value: 0 },
  { label: '退款中', value: 10 },
  { label: '已退款', value: 20 },
  { label: '退款失败', value: 30 }
]

const loading = ref(false)
const list = ref<Api.CustomerServiceRepairOrderVO[]>([])
const total = ref(0)
const dateRange = ref<string[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  id: undefined as number | undefined,
  payStatus: undefined as number | undefined,
  refundStatus: undefined as number | undefined,
  createTime: undefined as string[] | undefined
})

const getList = async () => {
  loading.value = true
  try {
    queryParams.createTime = dateRange.value.length ? dateRange.value : undefined
    const data = await Api.getCustomerServiceRepairOrderPage(queryParams)
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
  dateRange.value = []
  queryParams.id = undefined
  queryParams.payStatus = undefined
  queryParams.refundStatus = undefined
  handleQuery()
}

const refundTagType = (status: number): 'success' | 'warning' | 'danger' | 'info' => {
  if (status === 20) return 'success'
  if (status === 10) return 'warning'
  if (status === 30) return 'danger'
  return 'info'
}

const detailVisible = ref(false)
const detail = ref<Api.CustomerServiceRepairOrderVO>()

const openDetail = (row: Api.CustomerServiceRepairOrderVO) => {
  detail.value = row
  detailVisible.value = true
}

const refundVisible = ref(false)
const refunding = ref<Api.CustomerServiceRepairOrderVO>()
const refundReason = ref('')
const refundingSaving = ref(false)
const isOfflineRefund = computed(() =>
  refunding.value != null &&
  (refunding.value.payChannelCode === 'offline' || refunding.value.offlineConfirmTime != null)
)

const openRefund = (row: Api.CustomerServiceRepairOrderVO) => {
  refunding.value = row
  refundReason.value = ''
  refundVisible.value = true
}

const submitRefund = async () => {
  if (!refunding.value) return
  if (!refundReason.value.trim()) {
    message.warning('请填写退款原因')
    return
  }
  refundingSaving.value = true
  try {
    await Api.refundCustomerServiceRepairOrder({
      id: refunding.value.id,
      reason: refundReason.value.trim()
    })
    message.success(isOfflineRefund.value ? '已标记退款' : '退款申请已提交，到账后状态会自动更新')
    refundVisible.value = false
    await getList()
  } finally {
    refundingSaving.value = false
  }
}

onMounted(getList)
</script>
