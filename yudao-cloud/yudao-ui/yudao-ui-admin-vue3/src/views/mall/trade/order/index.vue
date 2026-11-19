<template>
  <doc-alert title="【交易】交易订单" url="https://doc.iocoder.cn/mall/trade-order/" />
  <doc-alert title="【交易】购物车" url="https://doc.iocoder.cn/mall/trade-cart/" />

  <!-- 搜索 -->
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="订单状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-280px" clearable placeholder="全部">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.TRADE_ORDER_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="支付方式" prop="payChannelCode">
        <el-select
          v-model="queryParams.payChannelCode"
          class="!w-280px"
          clearable
          placeholder="全部"
        >
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PAY_CHANNEL_CODE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="定制订单" prop="customization">
        <el-select v-model="queryParams.customization" class="!w-280px" clearable placeholder="全部">
          <el-option label="仅看定制单" :value="true" />
          <el-option label="仅看普通单" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item label="下单方式" prop="agentCreated">
        <el-select v-model="queryParams.agentCreated" class="!w-280px" clearable placeholder="全部">
          <el-option label="仅代客下单" :value="true" />
          <el-option label="仅客户自助" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-280px"
          end-placeholder="自定义时间"
          start-placeholder="自定义时间"
          type="daterange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item label="订单来源" prop="terminal">
        <el-select v-model="queryParams.terminal" class="!w-280px" clearable placeholder="全部">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.TERMINAL)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="配送方式" prop="deliveryType">
        <el-select v-model="queryParams.deliveryType" class="!w-280px" clearable placeholder="全部">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.TRADE_DELIVERY_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="queryParams.deliveryType === DeliveryTypeEnum.EXPRESS.type"
        label="快递公司"
        prop="logisticsId"
      >
        <el-select v-model="queryParams.logisticsId" class="!w-280px" clearable placeholder="全部">
          <el-option
            v-for="item in deliveryExpressList"
            :key="item.id"
            :label="item.name"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="queryParams.deliveryType === DeliveryTypeEnum.PICK_UP.type"
        label="自提门店"
        prop="pickUpStoreId"
      >
        <el-select
          v-model="queryParams.pickUpStoreId"
          class="!w-280px"
          clearable
          multiple
          placeholder="全部"
        >
          <el-option
            v-for="item in pickUpStoreList"
            :key="item.id"
            :label="item.name"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="queryParams.deliveryType === DeliveryTypeEnum.PICK_UP.type"
        label="核销码"
        prop="pickUpVerifyCode"
      >
        <el-input
          v-model="queryParams.pickUpVerifyCode"
          class="!w-280px"
          clearable
          placeholder="请输入自提核销码"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="聚合搜索">
        <el-input
          v-show="true"
          v-model="queryParams[queryType.queryParam]"
          :type="queryType.queryParam === 'userId' ? 'number' : 'text'"
          class="!w-280px"
          clearable
          :disabled="!queryType.queryParam"
          :placeholder="queryType.queryParam ? '请输入' : '请先选择搜索类型'"
        >
          <template #prepend>
            <el-select
              v-model="queryType.queryParam"
              class="!w-110px"
              clearable
              placeholder="全部"
              @change="inputChangeSelect"
            >
              <el-option
                v-for="dict in dynamicSearchList"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery">
          <Icon class="mr-5px" icon="ep:search" />
          搜索
        </el-button>
        <el-button @click="resetQuery">
          <Icon class="mr-5px" icon="ep:refresh" />
          重置
        </el-button>
        <el-button
          v-hasPermi="['trade:order:agent-create']"
          type="success"
          @click="openAgentCreate"
        >
          <Icon class="mr-5px" icon="ep:plus" />
          代客下单
        </el-button>
        <!--
          导出（2026-11-10）：点开弹窗选时间范围（含近 1 周 / 近 1 个月 / 近半年快捷项）。
          口径：**只按下单时间范围**导出，不受列表当前筛选影响。
        -->
        <el-button v-hasPermi="['trade:order:query']" plain type="primary" @click="openExport">
          <Icon class="mr-5px" icon="ep:download" />
          导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!--
    状态标签页（2026-10-23）。
    默认停在「待发货/已发货」—— 运营每天主要盯的就是这两档，进页面不用再选一次。
  -->
  <ContentWrap>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane v-for="tab in STATUS_TABS" :key="tab.key" :label="tab.label" :name="tab.key" />
    </el-tabs>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <!-- 添加 row-key="id" 解决列数据中的 table#header 数据不刷新的问题  -->
    <el-table v-loading="loading" :data="list" row-key="id">
      <OrderTableColumn :list="list" :pick-up-store-list="pickUpStoreList">
        <template #default="{ row }">
          <div class="order-actions">
            <el-button
              v-hasPermi="['trade:order:query']"
              link
              type="primary"
              @click="openDetail(row.id)"
            >
              详情
            </el-button>
            <!--
              操作全部平铺（2026-11-18 所有者要求）：原来收在「更多」下拉里，运营每单要多点一次；
              现在按条件直接列出，并新增「开单」（ERP）与「调整价格」。
            -->
            <el-button
              v-if="
                row.deliveryType !== DeliveryTypeEnum.PICK_UP.type &&
                row.status === TradeOrderStatusEnum.UNDELIVERED.status
              "
              v-hasPermi="['trade:order:update']"
              link
              type="primary"
              @click="openDelivery(row)"
            >
              发货
            </el-button>
            <el-button
              v-if="canConfirmOfflinePaid(row)"
              link
              type="primary"
              @click="openOfflineConfirm(row)"
            >
              确认收款
            </el-button>
            <el-button
              v-if="canIssueOrderInvoice(row)"
              link
              type="primary"
              @click="openInvoiceIssue(row)"
            >
              {{ orderInvoiceAction(row.invoiceStatus) }}
            </el-button>
            <!-- ERP 开单（2026-11-18）：已开单的订单不再显示 -->
            <el-button
              v-if="!row.erpCreated"
              v-hasPermi="['trade:order:update']"
              link
              type="primary"
              @click="markErpCreated(row)"
            >
              开单
            </el-button>
            <!-- 调整价格（2026-11-18）：与详情页一致，只对未支付订单开放 -->
            <el-button
              v-if="row.status === TradeOrderStatusEnum.UNPAID.status"
              v-hasPermi="['trade:order:update']"
              link
              type="primary"
              @click="openUpdatePrice(row)"
            >
              调整价格
            </el-button>
            <el-button
              v-if="row.status === TradeOrderStatusEnum.UNPAID.status"
              v-hasPermi="['trade:order:update']"
              link
              type="danger"
              @click="cancelOrder(row)"
            >
              取消订单
            </el-button>
            <el-button
              v-hasPermi="['trade:order:update']"
              link
              type="primary"
              @click="openRemark(row)"
            >
              备注
            </el-button>
          </div>
        </template>
      </OrderTableColumn>
    </el-table>
    <!-- 分页 -->
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 各种操作的弹窗 -->
  <OrderDeliveryForm ref="deliveryFormRef" @success="getList" />
  <OrderUpdateRemarkForm ref="updateRemarkForm" @success="getList" />
  <OrderUpdatePriceForm ref="updatePriceFormRef" @success="getList" />
  <OrderOfflineConfirmForm ref="offlineConfirmFormRef" @success="getList" />
  <InvoiceIssueForm ref="invoiceIssueFormRef" @success="getList" />
  <OrderExportForm ref="exportFormRef" />
</template>

<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import OrderDeliveryForm from '@/views/mall/trade/order/form/OrderDeliveryForm.vue'
import OrderOfflineConfirmForm from '@/views/mall/trade/order/form/OrderOfflineConfirmForm.vue'
import OrderUpdateRemarkForm from '@/views/mall/trade/order/form/OrderUpdateRemarkForm.vue'
import OrderUpdatePriceForm from '@/views/mall/trade/order/form/OrderUpdatePriceForm.vue'
import OrderExportForm from '@/views/mall/trade/order/form/OrderExportForm.vue'
import InvoiceIssueForm from '@/views/mall/trade/invoice/InvoiceIssueForm.vue'
import * as TradeOrderApi from '@/api/mall/trade/order'
import * as InvoiceApi from '@/api/mall/trade/invoice/index'
import { orderInvoiceAction } from '@/utils/invoice'
import * as PickUpStoreApi from '@/api/mall/trade/delivery/pickUpStore'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as DeliveryExpressApi from '@/api/mall/trade/delivery/express'
import { DeliveryTypeEnum, TradeOrderStatusEnum } from '@/utils/constants'
import { checkPermi } from '@/utils/permission'
import { OrderTableColumn } from './components'

defineOptions({ name: 'TradeOrder' })

const message = useMessage() // 消息弹窗
const { currentRoute, push } = useRouter() // 路由跳转
const loading = ref(true) // 列表的加载中
const total = ref(2) // 列表的总页数
const list = ref<TradeOrderApi.OrderVO[]>([]) // 列表的数据
const queryFormRef = ref<FormInstance>() // 搜索的表单

/**
 * 顶部状态标签页。
 *
 * 「待发货/已发货」是一个标签对两个状态 —— 运营把这两档当成同一批活看（货在手上 or 已发出），
 * 所以后端新增了 `statuses` 多选筛选（与单选的 `status` 互斥，见 `onTabChange`）。
 */
interface StatusTab {
  key: string
  label: string
  /** 该标签对应的订单状态；`undefined` = 不按状态筛（全部订单） */
  statuses?: number[]
}
const STATUS_TABS: StatusTab[] = [
  { key: 'all', label: '全部订单' },
  { key: 'unpaid', label: '未付款', statuses: [TradeOrderStatusEnum.UNPAID.status] },
  {
    key: 'shipping',
    label: '待发货/已发货',
    statuses: [TradeOrderStatusEnum.UNDELIVERED.status, TradeOrderStatusEnum.DELIVERED.status]
  },
  { key: 'completed', label: '已完成', statuses: [TradeOrderStatusEnum.COMPLETED.status] },
  { key: 'canceled', label: '已取消', statuses: [TradeOrderStatusEnum.CANCELED.status] }
]
/**
 * 授权订单菜单（2026-11-18）：与「商品订单」共用本页面，靠菜单的 component_name 区分。
 *
 * 授权订单 = 个人会员订阅 + 企业授权（后端 `maxlabel_subscription` / `maxlabel_org_id`），
 * 全部是支付即完成的虚拟单 —— 默认标签必须是「全部订单」，否则进页面一片空白。
 */
const isLicenseMode = computed(() => currentRoute.value?.name === 'TradeOrderLicense')

/** 进页面的默认标签（2026-10-23 所有者要求；授权订单见上） */
const DEFAULT_TAB = isLicenseMode.value ? 'all' : 'shipping'
const activeTab = ref<string>(DEFAULT_TAB)

/** 某个标签对应的状态集 */
const tabStatuses = (key: string) => STATUS_TABS.find((tab) => tab.key === key)?.statuses

// 表单搜索
const queryParams = ref({
  pageNo: 1, // 页数
  pageSize: 10, // 每页显示数量
  status: undefined, // 订单状态（下拉单选）
  statuses: tabStatuses(DEFAULT_TAB), // 订单状态（标签页多选）
  payChannelCode: undefined, // 支付方式
  customization: undefined, // 是否定制订单（014）
  agentCreated: undefined, // 是否代客下单（017）
  receiverName: undefined, // 收货人姓名（聚合搜索）
  receiverMobile: undefined, // 收货人手机号（聚合搜索）
  createTime: undefined, // 创建时间
  terminal: undefined, // 订单来源
  license: isLicenseMode.value, // 授权订单菜单 = true；商品订单菜单 = false
  deliveryType: undefined, // 配送方式
  logisticsId: undefined, // 快递公司
  pickUpStoreId: undefined, // 自提门店
  pickUpVerifyCode: undefined // 自提核销码
})
const queryType = reactive({ queryParam: '' }) // 订单搜索类型 queryParam

// 订单聚合搜索 select 类型配置（动态搜索）
const dynamicSearchList = ref([
  { value: 'no', label: '订单号' },
  { value: 'userId', label: '用户UID' },
  { value: 'userNickname', label: '用户昵称' },
  { value: 'userMobile', label: '用户电话' },
  { value: 'receiverName', label: '收货人姓名' },
  { value: 'receiverMobile', label: '收货人电话' }
])
/**
 * 聚合搜索切换查询对象时触发
 * @param val
 */
const inputChangeSelect = (val: string) => {
  dynamicSearchList.value
    .filter((item) => item.value !== val)
    ?.forEach((item1) => {
      // 清除集合搜索无用属性
      if (queryParams.value.hasOwnProperty(item1.value)) {
        delete queryParams.value[item1.value]
      }
    })
}

/**
 * 切换状态标签。
 *
 * 标签是「快筛」：会清掉下拉里的单一状态，否则会出现"标签高亮着待发货、实际按已完成筛"
 * 这种自相矛盾的界面。反过来用下拉选了状态时，标签退回「全部订单」（见下面的 watch）。
 */
const onTabChange = (key: string) => {
  queryParams.value.status = undefined
  queryParams.value.statuses = tabStatuses(key)
  queryParams.value.pageNo = 1
  getList()
}

// 用下拉选了具体状态 → 不再属于任何一个标签
watch(
  () => queryParams.value.status,
  (status) => {
    if (status === undefined || status === null) return
    activeTab.value = 'all'
    queryParams.value.statuses = undefined
  }
)

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    // ⚠️ 聚合搜索**没选搜索类型**时，上面那个输入框的 v-model 键是空串（`queryParams['']`），
    // 序列化出来就是 `?pageNo=1&pageSize=10&=%E6%9D%8Esir` —— **空参数名会让 Tomcat
    // 直接拒掉整个查询串**，前端只看得到「系统异常」（2026-10-16 线上就是这个问题）。
    // 输入框已改成"没选类型就禁用"，这里再兜一道：万一键还在，发请求前一定清掉。
    delete queryParams.value['']
    const data = await TradeOrderApi.getOrderPage(unref(queryParams))
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = async () => {
  queryParams.value.pageNo = 1
  await getList()
}

/** 重置按钮操作 */
/**
 * 重置：只清搜索表单，**不动当前状态标签** ——
 * 运营正在看「待发货/已发货」，点重置是要清掉自己填的筛选条件，不是要跳回默认标签。
 */
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.value = {
    pageNo: 1, // 页数
    pageSize: 10, // 每页显示数量
    status: undefined, // 订单状态（下拉单选）
    statuses: tabStatuses(activeTab.value), // 订单状态（标签页多选）
    payChannelCode: undefined, // 支付方式
    customization: undefined, // 是否定制订单（014）
    agentCreated: undefined, // 是否代客下单（017）
    receiverName: undefined, // 收货人姓名（聚合搜索）
    receiverMobile: undefined, // 收货人手机号（聚合搜索）
    createTime: undefined, // 创建时间
    terminal: undefined, // 订单来源
    license: isLicenseMode.value, // 授权订单菜单 = true；商品订单菜单 = false
    deliveryType: undefined, // 配送方式
    logisticsId: undefined, // 快递公司
    pickUpStoreId: undefined, // 自提门店
    pickUpVerifyCode: undefined // 自提核销码
  }
  handleQuery()
}

/** 查看订单详情 */
const openDetail = (id: number) => {
  push({ name: 'TradeOrderDetail', params: { id } })
}

/** 代客下单（017） */
const openAgentCreate = () => {
  push({ name: 'TradeOrderAgentCreate' })
}

/** 导出（2026-11-10）：弹窗里选时间范围 */
const exportFormRef = ref()
const openExport = () => {
  exportFormRef.value?.open()
}

/** 操作分发 */
const deliveryFormRef = ref()
const updateRemarkForm = ref()
const updatePriceFormRef = ref() // 订单调价表单 Ref
const offlineConfirmFormRef = ref() // 确认收款弹层（008）
const invoiceIssueFormRef = ref() // 开票上传弹层（006）

/**
 * 能不能在这行上「开票」（006）
 *
 * 只对**客户已经提交过申请**的订单出现：待开票 → 开票，已开票 → 重新上传（Q31）。
 * 没申请（null）和已作废（退款 / 取消，Q30）都不出现。
 * ⚠️ 上传弹层自身的权限由 `trade:order-invoice:issue` 把关（接口也有 @PreAuthorize），
 * 这里先挡一道，避免把"点了必被拒"的入口给没有权限的人。
 */
const canIssueOrderInvoice = (row: TradeOrderApi.OrderVO) =>
  orderInvoiceAction(row.invoiceStatus) !== null && checkPermi(['trade:order-invoice:issue'])

/** 打开开票上传弹层：先按订单号把申请取回来，**不离开列表页** */
const openInvoiceIssue = async (row: TradeOrderApi.OrderVO) => {
  const page = await InvoiceApi.getInvoicePage({ pageNo: 1, pageSize: 1, orderNo: row.no })
  const invoice = page.list?.find((item) => item.orderId === row.id)
  if (!invoice) {
    // 列表状态与申请不一致时（极少见）给个去处，别让人点了没反应
    message.warning('没有找到这笔订单的开票申请，请到「开票申请」页查看')
    return
  }
  invoiceIssueFormRef.value?.open(invoice)
}

/**
 * 能不能在这行上「确认收款」（008 / FR-203）
 *
 * 两个条件：**是待收款的对公单**（服务端给的 `offlineWaiting`）+ **有资金动作权限**。
 * ⚠️ 只看前端标识还不够 —— 接口那边也有 `@PreAuthorize`，两边都不能少。
 */
const canConfirmOfflinePaid = (row: TradeOrderApi.OrderVO) =>
  row.offlineWaiting && checkPermi(['trade:order:confirm-paid'])

/** 取消订单（008 / FR-213）。两次 try/catch 分开：弹层那次是"再想想"，接口那次是真出错 */
const cancelOrder = async (row: TradeOrderApi.OrderVO) => {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      '取消后商品库存与所用优惠券会释放，订单**不可恢复**。',
      `取消订单 ${row.no}`,
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '再想想',
        inputPlaceholder: '取消原因（选填，会记进商家备注）',
        type: 'warning'
      }
    )
    reason = value ?? ''
  } catch {
    return // 用户点了「再想想」
  }
  await TradeOrderApi.cancelOrder({ id: row.id!, reason: reason || undefined })
  message.success('订单已取消')
  await getList()
}

/** 操作按钮（2026-11-18 平铺后各自一个方法） */
const openRemark = (row: TradeOrderApi.OrderVO) => updateRemarkForm.value?.open(row)
const openDelivery = (row: TradeOrderApi.OrderVO) => deliveryFormRef.value?.open(row)
const openOfflineConfirm = (row: TradeOrderApi.OrderVO) => offlineConfirmFormRef.value?.open(row)
const openUpdatePrice = (row: TradeOrderApi.OrderVO) => updatePriceFormRef.value?.open(row)

/**
 * ERP 开单（2026-11-18）：列表上直接标记，必须填 ERP 单号；撤销仍在详情页。
 * 与详情页同一口径：独立于订单主状态，仅 `trade:order:update` 可操作。
 */
const markErpCreated = async (row: TradeOrderApi.OrderVO) => {
  let erpNo = ''
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入 ERP 系统里的单号',
      `标记订单 ${row.no} 已开单`,
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: 'ERP单号（必填）',
        inputValidator: (value: string) => (value && value.trim() ? true : '请输入 ERP 单号'),
        type: 'warning'
      }
    )
    erpNo = value.trim()
  } catch {
    return // 用户点了取消
  }
  await TradeOrderApi.updateOrderErp({ id: row.id!, erpCreated: true, erpNo })
  message.success('已标记为 ERP 已开单')
  await getList()
}

// 监听路由变化更新列表，解决订单保存/更新后，列表不刷新的问题。
watch(
  () => currentRoute.value,
  () => {
    getList()
  }
)

const pickUpStoreList = ref<PickUpStoreApi.DeliveryPickUpStoreVO[]>([]) // 自提门店精简列表
const deliveryExpressList = ref<DeliveryExpressApi.DeliveryExpressVO[]>([]) // 物流公司
/** 初始化 **/
onMounted(async () => {
  await getList()
  pickUpStoreList.value = await PickUpStoreApi.getSimpleDeliveryPickUpStoreList()
  deliveryExpressList.value = await DeliveryExpressApi.getSimpleDeliveryExpressList()
})
</script>

<style lang="scss" scoped>
/*
 * 操作按钮（2026-11-18）：平铺后要能排在一行里。
 * 只作用于本页，不改公共表格组件；Element Plus 默认给相邻按钮加 margin-left，
 * 换行时会错位，这里用 flex gap 统一间距。
 */
.order-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px 8px;

  :deep(.el-button) {
    padding: 0;
    font-size: 12px;
  }

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}
</style>
