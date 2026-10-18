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
      <el-form-item label="订单类型" prop="type">
        <el-select v-model="queryParams.type" class="!w-280px" clearable placeholder="全部">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.TRADE_ORDER_TYPE)"
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
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <!-- 添加 row-key="id" 解决列数据中的 table#header 数据不刷新的问题  -->
    <el-table v-loading="loading" :data="list" row-key="id">
      <OrderTableColumn :list="list" :pick-up-store-list="pickUpStoreList">
        <template #default="{ row }">
          <div class="flex items-center justify-center">
            <el-button
              v-hasPermi="['trade:order:query']"
              link
              type="primary"
              @click="openDetail(row.id)"
            >
              <Icon icon="ep:notification" />
              详情
            </el-button>
            <el-dropdown
              v-hasPermi="['trade:order:update']"
              @command="(command) => handleCommand(command, row)"
            >
              <el-button link type="primary">
                <Icon icon="ep:d-arrow-right" />
                更多
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <!--
                    【未发货】时展示【发货】：快递要发货；**虚拟商品（软件等）也要**（007）——
                    客服线下交付完，运营点「发货 → 无需发货」把它推进到已发货。
                    自提不走这里（它有专门的核销入口）。
                  -->
                  <el-dropdown-item
                    v-if="
                      row.deliveryType !== DeliveryTypeEnum.PICK_UP.type &&
                      row.status === TradeOrderStatusEnum.UNDELIVERED.status
                    "
                    command="delivery"
                  >
                    <Icon icon="ep:takeaway-box" />
                    发货
                  </el-dropdown-item>
                  <!--
                    确认收款（008 / FR-203）：只对**待收款**的对公单出现。
                    ⚠️ 它挂在 `trade:order:update` 的下拉里，所以这里**再加一层权限判断** ——
                    没有 `trade:order:confirm-paid` 的人不该看到它（资金动作）。
                    详情页那颗按钮同样是独立权限点。
                  -->
                  <el-dropdown-item
                    v-if="canConfirmOfflinePaid(row)"
                    command="confirmOfflinePaid"
                  >
                    <Icon icon="ep:money" />
                    确认收款
                  </el-dropdown-item>
                  <!--
                    取消订单（008 / FR-213）：只能取消**未支付**订单；
                    已支付（含已确认收款）的要走**退款**，取消不改金额、账对不上。
                  -->
                  <el-dropdown-item
                    v-if="row.status === TradeOrderStatusEnum.UNPAID.status"
                    command="cancel"
                  >
                    <Icon icon="ep:circle-close" />
                    取消订单
                  </el-dropdown-item>
                  <el-dropdown-item command="remark">
                    <Icon icon="ep:chat-line-square" />
                    备注
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
  <OrderOfflineConfirmForm ref="offlineConfirmFormRef" @success="getList" />
</template>

<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import OrderDeliveryForm from '@/views/mall/trade/order/form/OrderDeliveryForm.vue'
import OrderOfflineConfirmForm from '@/views/mall/trade/order/form/OrderOfflineConfirmForm.vue'
import OrderUpdateRemarkForm from '@/views/mall/trade/order/form/OrderUpdateRemarkForm.vue'
import * as TradeOrderApi from '@/api/mall/trade/order'
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
// 表单搜索
const queryParams = ref({
  pageNo: 1, // 页数
  pageSize: 10, // 每页显示数量
  status: undefined, // 订单状态
  payChannelCode: undefined, // 支付方式
  createTime: undefined, // 创建时间
  terminal: undefined, // 订单来源
  type: undefined, // 订单类型
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
  { value: 'userMobile', label: '用户电话' }
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
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.value = {
    pageNo: 1, // 页数
    pageSize: 10, // 每页显示数量
    status: undefined, // 订单状态
    payChannelCode: undefined, // 支付方式
    createTime: undefined, // 创建时间
    terminal: undefined, // 订单来源
    type: undefined, // 订单类型
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

/** 操作分发 */
const deliveryFormRef = ref()
const updateRemarkForm = ref()
const offlineConfirmFormRef = ref() // 确认收款弹层（008）

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

const handleCommand = (command: string, row: TradeOrderApi.OrderVO) => {
  switch (command) {
    case 'remark':
      updateRemarkForm.value?.open(row)
      break
    case 'delivery':
      deliveryFormRef.value?.open(row)
      break
    case 'confirmOfflinePaid':
      offlineConfirmFormRef.value?.open(row)
      break
    case 'cancel':
      cancelOrder(row)
      break
  }
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
