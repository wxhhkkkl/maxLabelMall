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
      <!-- 撤回（Q164）：记录不删，撤回后在这里留痕 -->
      <el-table-column label="状态" width="170">
        <template #default="{ row }">
          <el-tag v-if="row.revokeTime" type="danger">已撤回</el-tag>
          <el-tag v-else type="success">已生效</el-tag>
          <div v-if="row.revokeTime" class="text-12px text-gray-500">
            {{ formatDate(row.revokeTime) }} {{ row.revokeOperatorName || '' }}
          </div>
          <div v-if="row.revokeTime" class="text-12px text-gray-500">
            理由：{{ row.revokeReason }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.revokeTime"
            v-hasPermi="['member:license:gift']"
            link
            type="danger"
            @click="openRevoke(row)"
          >
            撤回
          </el-button>
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

  <!-- 撤回必须填理由：权益收回去，留痕不能少 -->
  <Dialog v-model="revokeVisible" title="撤回赠送" width="520px">
    <el-form label-width="90px">
      <el-form-item label="赠送内容">
        {{ revokeTarget ? describe(revokeTarget) : '—' }}
      </el-form-item>
      <el-form-item label="撤回理由" required>
        <el-input
          v-model="revokeReason"
          :rows="3"
          maxlength="255"
          placeholder="必填：为什么要撤回这笔赠送"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>
    <div class="text-12px text-gray-500">
      只能撤回该客户最近一笔赠送；客户之后又购买或赠送过的，请找当时操作人核对。
    </div>
    <template #footer>
      <el-button @click="revokeVisible = false">取消</el-button>
      <el-button
        :disabled="!revokeReason.trim()"
        :loading="revoking"
        type="primary"
        @click="submitRevoke"
      >
        确认撤回
      </el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as LicenseApi from '@/api/member/license'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'LicenseGrantRecordTable' })
const message = useMessage()
const emit = defineEmits(['success'])

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

// ==================== 撤回（Q164） ====================

const revokeVisible = ref(false)
const revoking = ref(false)
const revokeReason = ref('')
const revokeTarget = ref<LicenseApi.LicenseGrantVO>()

const describe = (row: LicenseApi.LicenseGrantVO) =>
  `${row.targetName || row.targetId}：` +
  (row.contentType === 1 ? `${row.months} 个月` : `${row.seats} 个席位`)

const openRevoke = (row: LicenseApi.LicenseGrantVO) => {
  revokeTarget.value = row
  revokeReason.value = ''
  revokeVisible.value = true
}

const submitRevoke = async () => {
  if (!revokeTarget.value || !revokeReason.value.trim() || revoking.value) {
    return
  }
  revoking.value = true
  try {
    await LicenseApi.revokeLicenseGrant({
      id: revokeTarget.value.id,
      reason: revokeReason.value.trim()
    })
    message.success('已撤回，权益已收回')
    revokeVisible.value = false
    // 由父组件统一刷新看板 / 明细 / 记录，避免同一份数据请求两遍
    emit('success')
  } finally {
    revoking.value = false
  }
}

defineExpose({ getList })
onMounted(getList)
</script>
