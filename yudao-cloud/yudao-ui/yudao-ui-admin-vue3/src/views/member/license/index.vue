<template>
  <div>
    <LicenseSummaryCards :summary="summary" class="mb-16px" @drill="handleDrill" />

    <!-- 赠送是独立入口：不要求对象已有授权（Q162），所以不能只放在明细行里 -->
    <div v-if="canGift" class="mb-12px flex justify-end">
      <el-button type="primary" @click="openGiftPicker">
        <Icon class="mr-5px" icon="ep:present" />赠送授权
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="客户授权" name="customer">
        <CustomerTable ref="customerTableRef" />
      </el-tab-pane>
      <el-tab-pane label="赠送记录" name="record">
        <GrantRecordTable ref="grantRecordTableRef" @success="handleGrantSuccess" />
      </el-tab-pane>
    </el-tabs>

    <GiftTargetPicker ref="giftTargetPickerRef" @select="handleGiftTargetSelected" />
    <GrantForm ref="grantFormRef" @success="handleGrantSuccess" />
  </div>
</template>

<script lang="ts" setup>
import * as LicenseApi from '@/api/member/license'
import { checkPermi } from '@/utils/permission'
import LicenseSummaryCards from './components/LicenseSummaryCards.vue'
import CustomerTable from './components/CustomerTable.vue'
import GrantRecordTable from './components/GrantRecordTable.vue'
import GrantForm from './components/GrantForm.vue'
import GiftTargetPicker from './components/GiftTargetPicker.vue'

defineOptions({ name: 'MemberLicense' })

const activeTab = ref('customer')
const summary = ref<LicenseApi.LicenseSummaryVO>()
const customerTableRef = ref()
const grantRecordTableRef = ref()
const giftTargetPickerRef = ref()
const grantFormRef = ref()

/** 赠送是会改变客户商业权益的动作，与"看"分开授权（Q29 精神 / FR-539） */
const canGift = computed(() => checkPermi(['member:license:gift']))

const loadSummary = async () => {
  summary.value = await LicenseApi.getLicenseSummary()
}

const handleTabChange = () => {
  // 切换页签时刷新对应数据（赠送后两边都要变）
  loadSummary()
  if (activeTab.value === 'record') {
    grantRecordTableRef.value?.getList()
  }
}

/** 数字下钻：切到明细页签并带上客户类型筛选（FR-543） */
const handleDrill = (filter: { targetType?: number; rightsType?: number }) => {
  activeTab.value = 'customer'
  const table = customerTableRef.value
  if (!table) return
  // T092：席位会员的下钻只列席位会员，不能退化成"全部个人"
  table.queryParams.targetType = filter.targetType
  table.queryParams.rightsType = filter.rightsType
  table.queryParams.pageNo = 1
  table.getList()
}

/** 独立入口：先从全部个人 / 企业中选对象，再走同一个赠送弹窗 */
const openGiftPicker = () => {
  giftTargetPickerRef.value?.open()
}

const handleGiftTargetSelected = (row: LicenseApi.LicenseGiftTargetVO) => {
  grantFormRef.value?.open(row)
}

const handleGrantSuccess = () => {
  loadSummary()
  customerTableRef.value?.getList()
  grantRecordTableRef.value?.getList()
}

onMounted(loadSummary)
</script>
