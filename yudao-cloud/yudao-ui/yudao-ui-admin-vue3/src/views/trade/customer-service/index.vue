<template>
  <TicketTable
    ref="ticketTableRef"
    :can-handle="canHandle"
    @view="openDetail"
    @handle="openHandle"
    @create="openTicketCreate"
  />

  <Dialog v-model="detailVisible" title="工单详情" width="640px">
    <template v-if="detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="会员">{{ detail.memberNickname || '—' }}</el-descriptions-item>
        <el-descriptions-item label="会员手机号">{{ detail.memberMobile || '—' }}</el-descriptions-item>
        <el-descriptions-item label="诉求类型">{{ detail.typeName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.statusName }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="联系手机号">{{ detail.contactMobile }}</el-descriptions-item>
        <el-descriptions-item label="提交时间" :span="2">{{ formatDate(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">
          <div class="whitespace-pre-wrap">{{ detail.content }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.images?.length" label="图片" :span="2">
          <el-image
            v-for="url in detail.images"
            :key="url"
            :src="url"
            :preview-src-list="detail.images"
            class="mr-8px h-72px w-72px rounded"
            fit="cover"
          />
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.status === 10" label="处理说明" :span="2">
          {{ detail.handleRemark }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.status === 10" label="处理人 / 时间" :span="2">
          {{ detail.handlerName || '—' }} / {{ formatDate(detail.handleTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </template>
  </Dialog>

  <TicketCreateDialog ref="ticketCreateDialogRef" @success="ticketTableRef?.getList()" />

  <Dialog v-model="handleVisible" title="处理工单" width="560px">
    <el-form label-width="90px">
      <el-form-item label="工单内容">
        <div class="whitespace-pre-wrap text-13px text-gray-600">{{ handling?.content }}</div>
      </el-form-item>
      <el-form-item label="处理说明" required>
        <el-input
          v-model="handleRemark"
          :rows="4"
          maxlength="500"
          placeholder="请填写给客户看的处理说明（必填）"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleVisible = false">取消</el-button>
      <el-button :loading="saving" type="primary" @click="submitHandle">确认处理</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import TicketTable from './components/TicketTable.vue'
import TicketCreateDialog from './components/TicketCreateDialog.vue'

defineOptions({ name: 'TradeCustomerServiceTicket' })

const message = useMessage()
const ticketTableRef = ref()
const ticketCreateDialogRef = ref()
const canHandle = computed(() => checkPermi(['trade:customer-service:handle']))

const detailVisible = ref(false)
const detail = ref<Api.CustomerServiceTicketVO>()

const handleVisible = ref(false)
const handling = ref<Api.CustomerServiceTicketVO>()
const handleRemark = ref('')
const saving = ref(false)

const openDetail = async (row: Api.CustomerServiceTicketVO) => {
  detail.value = await Api.getCustomerServiceTicket(row.id)
  detailVisible.value = true
}

const openTicketCreate = () => {
  ticketCreateDialogRef.value?.open()
}

const openHandle = (row: Api.CustomerServiceTicketVO) => {
  handling.value = row
  handleRemark.value = ''
  handleVisible.value = true
}

const submitHandle = async () => {
  if (!handling.value || !handleRemark.value.trim()) {
    message.warning('请填写处理说明')
    return
  }
  saving.value = true
  try {
    await Api.handleCustomerServiceTicket({ id: handling.value.id, handleRemark: handleRemark.value.trim() })
    message.success('已处理')
    handleVisible.value = false
    ticketTableRef.value?.getList()
  } finally {
    saving.value = false
  }
}
</script>
