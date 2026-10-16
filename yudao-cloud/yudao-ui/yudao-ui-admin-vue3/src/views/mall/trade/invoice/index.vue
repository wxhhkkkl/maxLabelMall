<template>
  <!-- 搜索 -->
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" label-width="68px">
      <el-form-item label="订单号" prop="orderNo">
        <el-input
          v-model="queryParams.orderNo"
          class="!w-260px"
          clearable
          placeholder="请输入订单号"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-200px" clearable placeholder="全部状态">
          <el-option
            v-for="option in INVOICE_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" />搜索
        </el-button>
        <el-button @click="resetQuery">
          <Icon icon="ep:refresh" class="mr-5px" />重置
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="订单号" prop="orderNo" min-width="170" />
      <el-table-column label="下单客户" prop="userNickname" min-width="110" />
      <el-table-column label="发票类型" prop="typeName" min-width="170" />
      <!-- 抬头类型（Q38）：个人抬头没有税号，财务不用靠"税号怎么空着"去猜 -->
      <el-table-column label="抬头类型" prop="titleKindName" min-width="100" />
      <el-table-column label="抬头" prop="titleName" min-width="170" show-overflow-tooltip />
      <!--
        开票金额是**申请时**的快照；订单实付 / 已退是**当前**的数。
        部分退款不会改前者（Q33），两个并排放才看得出差异。
      -->
      <el-table-column label="开票金额" min-width="110" align="right">
        <template #default="{ row }">{{ fenToYuan(row.amount) }} 元</template>
      </el-table-column>
      <el-table-column label="订单实付 / 已退" min-width="170" align="right">
        <template #default="{ row }">
          {{ fenToYuan(row.orderPayPrice) }} 元
          <el-tag v-if="hasRefund(row)" type="warning" size="small" class="ml-5px">
            已退 {{ fenToYuan(row.orderRefundPrice) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" min-width="170">
        <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="开票时间" min-width="170">
        <template #default="{ row }">{{ formatDate(row.issueTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="170">
        <template #default="{ row }">
          <el-button v-if="row.invoiceFileUrl" link type="primary" @click="openInvoiceFile(row)">
            查看发票
          </el-button>
          <!-- ⚠️ 权限点必须在 system_menu 里，否则这个按钮连超管都看不到（004 踩过） -->
          <el-button
            v-if="canIssue(row.status)"
            v-hasPermi="['trade:order-invoice:issue']"
            link
            type="primary"
            @click="openIssueForm(row)"
          >
            {{ row.status === INVOICE_STATUS.ISSUED ? '重新上传' : '开具发票' }}
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

  <!-- 开具 / 重新上传 -->
  <InvoiceIssueForm ref="issueFormRef" @success="getList" />
</template>

<script lang="ts" setup>
import * as InvoiceApi from '@/api/mall/trade/invoice/index'
import InvoiceIssueForm from './InvoiceIssueForm.vue'
import { formatDate } from '@/utils/formatTime'
import { INVOICE_STATUS, INVOICE_STATUS_OPTIONS, canIssue, hasRefund } from '@/utils/invoice'
import { fenToYuan } from '@/utils'

defineOptions({ name: 'TradeOrderInvoice' })

const loading = ref(true)
const total = ref(0)
const list = ref<InvoiceApi.TradeOrderInvoiceVO[]>([])
const queryFormRef = ref()
const issueFormRef = ref()

const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  orderNo: undefined as string | undefined,
  status: undefined as number | undefined
})

/** 状态标签配色：待开票要显眼（那是运营要干的活），已作废淡化 */
function statusTagType(status?: number) {
  if (status === INVOICE_STATUS.WAITING) return 'warning'
  if (status === INVOICE_STATUS.ISSUED) return 'success'
  return 'info'
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await InvoiceApi.getInvoicePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索 / 重置 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.orderNo = undefined
  queryParams.status = undefined
  handleQuery()
}

/** 开具 / 重新上传 */
const openIssueForm = (row: InvoiceApi.TradeOrderInvoiceVO) => {
  issueFormRef.value?.open(row)
}

/**
 * 查看已开出的发票
 *
 * ⚠️ 链接是**公网可读**的（既有对象存储的既有性质，spec 已记录为显式接受的风险），
 * 所以这里只是新开一个标签页，不做鉴权（本期不做）。
 */
const openInvoiceFile = (row: InvoiceApi.TradeOrderInvoiceVO) => {
  if (row.invoiceFileUrl) {
    window.open(row.invoiceFileUrl, '_blank', 'noopener')
  }
}

/** 初始化 */
onMounted(() => {
  getList()
})
</script>
