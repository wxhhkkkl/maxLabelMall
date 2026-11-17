<template>
  <RepairTable
    ref="repairTableRef"
    :can-handle="canHandle"
    @view="openRepairDetail"
    @contact="openContact"
    @order="openOrder"
    @create="openRepairCreate"
  />

  <Dialog v-model="repairDetailVisible" title="维修申请详情" width="680px">
    <template v-if="repairDetail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="会员">{{ repairDetail.memberNickname || '—' }}</el-descriptions-item>
        <el-descriptions-item label="会员手机号">{{ repairDetail.memberMobile || '—' }}</el-descriptions-item>
        <el-descriptions-item label="设备型号">{{ repairDetail.deviceModel }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ repairDetail.statusName }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ repairDetail.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="联系手机号">{{ repairDetail.contactMobile }}</el-descriptions-item>
        <el-descriptions-item label="提交时间" :span="2">{{ formatDate(repairDetail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="故障描述" :span="2">
          <div class="whitespace-pre-wrap">{{ repairDetail.faultDesc }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="返修收件信息" :span="2">
          {{ repairDetail.receiverName }} {{ repairDetail.receiverMobile }}<br />
          {{ repairDetail.receiverArea }} {{ repairDetail.receiverDetail }}
        </el-descriptions-item>
        <el-descriptions-item v-if="repairDetail.images?.length" label="图片" :span="2">
          <el-image
            v-for="url in repairDetail.images"
            :key="url"
            :src="url"
            :preview-src-list="repairDetail.images"
            class="mr-8px h-72px w-72px rounded"
            fit="cover"
          />
        </el-descriptions-item>
        <el-descriptions-item v-if="repairDetail.contactResult" label="联系结果" :span="2">
          {{ repairDetail.contactTime ? `${formatDate(repairDetail.contactTime)} · ` : '' }}{{ repairDetail.contactResult }}
        </el-descriptions-item>
        <el-descriptions-item v-if="repairDetail.shipNo" label="寄出信息" :span="2">
          {{ repairDetail.shipCompany }} {{ repairDetail.shipNo }}
        </el-descriptions-item>
      </el-descriptions>
      <RepairOrderPanel :repair="repairDetail" @refresh="refreshRepairDetail" />
    </template>
  </Dialog>

  <ContactDialog ref="contactDialogRef" @success="refreshRepairs" />
  <OrderCreateDialog ref="orderDialogRef" @success="refreshRepairs" />
  <RepairCreateDialog ref="repairCreateDialogRef" @success="refreshRepairs" />
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import RepairTable from '../components/RepairTable.vue'
import ContactDialog from '../components/ContactDialog.vue'
import OrderCreateDialog from '../components/OrderCreateDialog.vue'
import RepairOrderPanel from '../components/RepairOrderPanel.vue'
import RepairCreateDialog from '../components/RepairCreateDialog.vue'

defineOptions({ name: 'TradeCustomerServiceRepair' })

const repairTableRef = ref()
const contactDialogRef = ref()
const orderDialogRef = ref()
const repairCreateDialogRef = ref()
const canHandle = computed(() => checkPermi(['trade:customer-service:handle']))

const repairDetailVisible = ref(false)
const repairDetail = ref<Api.CustomerServiceRepairVO>()

const openRepairDetail = async (row: Api.CustomerServiceRepairVO) => {
  repairDetail.value = await Api.getCustomerServiceRepair(row.id)
  repairDetailVisible.value = true
}

const refreshRepairDetail = async () => {
  if (!repairDetail.value) return
  repairDetail.value = await Api.getCustomerServiceRepair(repairDetail.value.id)
  repairTableRef.value?.getList()
}

const openContact = (row: Api.CustomerServiceRepairVO) => {
  contactDialogRef.value?.open(row)
}

const openOrder = (row: Api.CustomerServiceRepairVO) => {
  orderDialogRef.value?.open(row)
}

const refreshRepairs = () => {
  repairTableRef.value?.getList()
}

const openRepairCreate = () => {
  repairCreateDialogRef.value?.open()
}
</script>
