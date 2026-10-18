<template>
  <ContentWrap>
    <!-- 订单信息 -->
    <el-descriptions title="订单信息">
      <el-descriptions-item label="订单号: ">{{ formData.no }}</el-descriptions-item>
      <el-descriptions-item label="买家: ">{{ formData?.user?.nickname }}</el-descriptions-item>
      <el-descriptions-item label="订单类型: ">
        <dict-tag :type="DICT_TYPE.TRADE_ORDER_TYPE" :value="formData.type!" />
      </el-descriptions-item>
      <el-descriptions-item label="订单来源: ">
        <dict-tag :type="DICT_TYPE.TERMINAL" :value="formData.terminal!" />
      </el-descriptions-item>
      <el-descriptions-item label="买家留言: ">{{ formData.userRemark }}</el-descriptions-item>
      <el-descriptions-item label="商家备注: ">{{ formData.remark }}</el-descriptions-item>
      <el-descriptions-item label="支付单号: ">{{ formData.payOrderId }}</el-descriptions-item>
      <el-descriptions-item label="付款方式: ">
        <dict-tag :type="DICT_TYPE.PAY_CHANNEL_CODE" :value="formData.payChannelCode!" />
      </el-descriptions-item>
      <el-descriptions-item v-if="formData.brokerageUser" label="推广用户: ">
        {{ formData.brokerageUser?.nickname }}
      </el-descriptions-item>
    </el-descriptions>

    <!-- 订单状态 -->
    <el-descriptions :column="1" title="订单状态">
      <el-descriptions-item label="订单状态: ">
        <dict-tag :type="DICT_TYPE.TRADE_ORDER_STATUS" :value="formData.status!" />
      </el-descriptions-item>
      <!--
        确认对公转账收款（008 / FR-203）。**独立权限点、独立一行** ——
        它是**资金动作**，不是所有后台角色都该能做；塞进下面那行（trade:order:update）
        会让"只有 confirm-paid 权限"的人看不到它。
      -->
      <el-descriptions-item v-hasPermi="['trade:order:confirm-paid']" label-class-name="no-colon">
        <el-button v-if="formData.offlineWaiting" type="primary" @click="confirmOfflinePaid">
          确认收款
        </el-button>
      </el-descriptions-item>
      <el-descriptions-item v-hasPermi="['trade:order:update']" label-class-name="no-colon">
        <el-button
          v-if="formData.status! === TradeOrderStatusEnum.UNPAID.status"
          type="primary"
          @click="updatePrice"
        >
          调整价格
        </el-button>
        <el-button type="primary" @click="remark">备注</el-button>
        <!--
          取消订单（008 / FR-213）。**只能取消未支付订单** —— 已支付（含已确认收款）的
          必须走**退款**：取消不改金额，账对不上。后台此前根本没有这个入口，
          对公转账单又不参与超时自动取消（FR-212），弃单就是靠它清掉的。
        -->
        <el-button
          v-if="formData.status! === TradeOrderStatusEnum.UNPAID.status"
          type="danger"
          @click="cancelOrder"
        >
          取消订单
        </el-button>
        <!-- 待发货 -->
        <template v-if="formData.status! === TradeOrderStatusEnum.UNDELIVERED.status">
          <!--
            快递要发货；**虚拟商品（软件等）也要**（007）—— 客服线下交付完，
            运营点「发货 → 无需发货」把它推进到已发货。自提不走这里（另外的核销入口）。
          -->
          <el-button
            v-if="formData.deliveryType !== DeliveryTypeEnum.PICK_UP.type"
            type="primary"
            @click="delivery"
          >
            发货
          </el-button>
          <el-button
            v-if="formData.deliveryType === DeliveryTypeEnum.EXPRESS.type"
            type="primary"
            @click="updateAddress"
          >
            修改地址
          </el-button>
          <!-- 到店自提 -->
          <el-button
            v-if="formData.deliveryType === DeliveryTypeEnum.PICK_UP.type && showPickUp"
            type="primary"
            @click="handlePickUp"
          >
            核销
          </el-button>
        </template>
      </el-descriptions-item>
      <el-descriptions-item>
        <template #label><span style="color: red">提醒: </span></template>
        买家付款成功后，货款将直接进入您的商户号（微信、支付宝）<br />
        请及时关注你发出的包裹状态，确保可以配送至买家手中 <br />
        如果买家表示没收到货或货物有问题，请及时联系买家处理，友好协商
      </el-descriptions-item>
    </el-descriptions>

    <!--
      对公转账（008）。**待收款**时提示财务去核对到账；已确认时把痕迹摆出来（FR-205）——
      "凭哪笔流水、谁确认的、什么时候"，事后对账全靠这几行。
      ⚠️ `offlinePayment` 为 null 时整块不渲染（不是对公单）。
    -->
    <el-descriptions v-if="formData.offlinePayment" :column="2" title="对公转账">
      <el-descriptions-item label="收款状态: ">
        <el-tag v-if="formData.offlineWaiting" type="warning">待收款</el-tag>
        <el-tag v-else type="success">已确认收款</el-tag>
      </el-descriptions-item>
      <el-descriptions-item v-if="formData.offlinePayment.bankSerialNo" label="银行流水号: ">
        {{ formData.offlinePayment.bankSerialNo }}
      </el-descriptions-item>
      <el-descriptions-item v-if="formData.offlinePayment.payerName" label="打款方: ">
        {{ formData.offlinePayment.payerName }}
      </el-descriptions-item>
      <el-descriptions-item v-if="formData.offlinePayment.paidTime" label="到账时间: ">
        {{ formatDate(formData.offlinePayment.paidTime) }}
      </el-descriptions-item>
      <el-descriptions-item v-if="formData.offlinePayment.remark" label="备注: ">
        {{ formData.offlinePayment.remark }}
      </el-descriptions-item>
      <el-descriptions-item v-if="formData.offlinePayment.confirmTime" label="确认人 / 确认时间: ">
        {{ formData.offlinePayment.confirmUserId }} /
        {{ formatDate(formData.offlinePayment.confirmTime) }}
      </el-descriptions-item>
    </el-descriptions>

    <!-- 商品信息 -->
    <el-descriptions title="商品信息">
      <el-descriptions-item labelClassName="no-colon">
        <el-row :gutter="20">
          <el-col :span="15">
            <el-table :data="formData.items" border>
              <el-table-column label="商品" prop="spuName" width="auto">
                <template #default="{ row }">
                  {{ row.spuName }}
                  <el-tag v-for="property in row.properties" :key="property.propertyId">
                    {{ property.propertyName }}: {{ property.valueName }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="商品原价" prop="price" width="150">
                <template #default="{ row }">{{ fenToYuan(row.price) }}元</template>
              </el-table-column>
              <el-table-column label="数量" prop="count" width="100" />
              <el-table-column label="合计" prop="payPrice" width="150">
                <template #default="{ row }">{{ fenToYuan(row.payPrice) }}元</template>
              </el-table-column>
              <el-table-column label="售后状态" prop="afterSaleStatus" width="120">
                <template #default="{ row }">
                  <dict-tag
                    :type="DICT_TYPE.TRADE_ORDER_ITEM_AFTER_SALE_STATUS"
                    :value="row.afterSaleStatus"
                  />
                </template>
              </el-table-column>
            </el-table>
          </el-col>
          <el-col :span="10" />
        </el-row>
      </el-descriptions-item>
    </el-descriptions>
    <el-descriptions :column="4">
      <!-- 第一层 -->
      <el-descriptions-item label="商品总额: ">
        {{ fenToYuan(formData.totalPrice!) }} 元
      </el-descriptions-item>
      <el-descriptions-item label="运费金额: ">
        {{ fenToYuan(formData.deliveryPrice!) }} 元
      </el-descriptions-item>
      <el-descriptions-item label="订单调价: ">
        {{ fenToYuan(formData.adjustPrice!) }} 元
      </el-descriptions-item>
      <el-descriptions-item v-for="item in 1" :key="item" label-class-name="no-colon" />
      <!-- 第二层 -->
      <el-descriptions-item>
        <template #label><span style="color: red">优惠劵优惠: </span></template>
        {{ fenToYuan(formData.couponPrice!) }} 元
      </el-descriptions-item>
      <el-descriptions-item>
        <template #label><span style="color: red">VIP 优惠: </span></template>
        {{ fenToYuan(formData.vipPrice!) }} 元
      </el-descriptions-item>
      <el-descriptions-item>
        <template #label><span style="color: red">活动优惠: </span></template>
        {{ fenToYuan(formData.discountPrice!) }} 元
      </el-descriptions-item>
      <el-descriptions-item>
        <template #label><span style="color: red">积分抵扣: </span></template>
        {{ fenToYuan(formData.pointPrice!) }} 元
      </el-descriptions-item>
      <!-- 第三层 -->
      <el-descriptions-item v-for="item in 3" :key="item" label-class-name="no-colon" />
      <el-descriptions-item label="应付金额: ">
        {{ fenToYuan(formData.payPrice!) }} 元
      </el-descriptions-item>
    </el-descriptions>

    <!-- 物流信息 -->
    <el-descriptions :column="4" title="收货信息">
      <el-descriptions-item label="配送方式: ">
        <dict-tag :type="DICT_TYPE.TRADE_DELIVERY_TYPE" :value="formData.deliveryType!" />
      </el-descriptions-item>
      <!-- 虚拟商品单没有收货人（007），别摆两行空白让人以为数据丢了 -->
      <el-descriptions-item
        v-if="formData.deliveryType !== DeliveryTypeEnum.VIRTUAL.type"
        label="收货人: "
      >{{ formData.receiverName }}</el-descriptions-item>
      <el-descriptions-item
        v-if="formData.deliveryType !== DeliveryTypeEnum.VIRTUAL.type"
        label="联系电话: "
      >{{ formData.receiverMobile }}</el-descriptions-item>
      <!-- 快递配送 -->
      <div v-if="formData.deliveryType === DeliveryTypeEnum.EXPRESS.type">
        <el-descriptions-item v-if="formData.receiverDetailAddress" label="收货地址: ">
          {{ formData.receiverAreaName }} {{ formData.receiverDetailAddress }}
          <el-link
            v-clipboard:copy="formData.receiverAreaName + ' ' + formData.receiverDetailAddress"
            v-clipboard:success="clipboardSuccess"
            icon="ep:document-copy"
            type="primary"
          />
        </el-descriptions-item>
        <el-descriptions-item v-if="formData.logisticsId" label="物流公司: ">
          {{ deliveryExpressList.find((item) => item.id === formData.logisticsId)?.name }}
        </el-descriptions-item>
        <el-descriptions-item v-if="formData.logisticsId" label="运单号: ">
          {{ formData.logisticsNo }}
        </el-descriptions-item>
        <el-descriptions-item v-if="formData.deliveryTime" label="发货时间: ">
          {{ formatDate(formData.deliveryTime) }}
        </el-descriptions-item>
        <el-descriptions-item v-for="item in 2" :key="item" label-class-name="no-colon" />
        <el-descriptions-item v-if="expressTrackList.length > 0" label="物流详情: ">
          <el-timeline>
            <el-timeline-item
              v-for="(express, index) in expressTrackList"
              :key="index"
              :timestamp="formatDate(express.time)"
            >
              {{ express.content }}
            </el-timeline-item>
          </el-timeline>
        </el-descriptions-item>
      </div>
      <!-- 自提门店 -->
      <div v-if="formData.deliveryType === DeliveryTypeEnum.PICK_UP.type">
        <el-descriptions-item v-if="formData.pickUpStoreId" label="自提门店: ">
          {{ pickUpStore?.name }}
        </el-descriptions-item>
      </div>
    </el-descriptions>

    <!-- 订单日志 -->
    <el-descriptions title="订单操作日志">
      <el-descriptions-item labelClassName="no-colon">
        <el-timeline>
          <el-timeline-item
            v-for="(log, index) in formData.logs"
            :key="index"
            :timestamp="formatDate(log.createTime!)"
            placement="top"
          >
            <div class="el-timeline-right-content">
              {{ log.content }}
            </div>
            <template #dot>
              <span
                :style="{ backgroundColor: getUserTypeColor(log.userType!) }"
                class="dot-node-style"
              >
                {{ getDictLabel(DICT_TYPE.USER_TYPE, log.userType)[0] }}
              </span>
            </template>
          </el-timeline-item>
        </el-timeline>
      </el-descriptions-item>
    </el-descriptions>
  </ContentWrap>

  <!-- 各种操作的弹窗 -->
  <OrderDeliveryForm ref="deliveryFormRef" @success="getDetail" />
  <OrderOfflineConfirmForm ref="offlineConfirmFormRef" @success="getDetail" />
  <OrderUpdateRemarkForm ref="updateRemarkForm" @success="getDetail" />
  <OrderUpdateAddressForm ref="updateAddressFormRef" @success="getDetail" />
  <OrderUpdatePriceForm ref="updatePriceFormRef" @success="getDetail" />
</template>
<script lang="ts" setup>
import * as TradeOrderApi from '@/api/mall/trade/order'
import { fenToYuan } from '@/utils'
import { formatDate } from '@/utils/formatTime'
import { DICT_TYPE, getDictLabel, getDictObj } from '@/utils/dict'
import OrderUpdateRemarkForm from '@/views/mall/trade/order/form/OrderUpdateRemarkForm.vue'
import OrderDeliveryForm from '@/views/mall/trade/order/form/OrderDeliveryForm.vue'
import OrderOfflineConfirmForm from '@/views/mall/trade/order/form/OrderOfflineConfirmForm.vue'
import OrderUpdateAddressForm from '@/views/mall/trade/order/form/OrderUpdateAddressForm.vue'
import OrderUpdatePriceForm from '@/views/mall/trade/order/form/OrderUpdatePriceForm.vue'
import * as DeliveryExpressApi from '@/api/mall/trade/delivery/express'
import { useTagsViewStore } from '@/store/modules/tagsView'
import { DeliveryTypeEnum, TradeOrderStatusEnum } from '@/utils/constants'
import * as DeliveryPickUpStoreApi from '@/api/mall/trade/delivery/pickUpStore'
import { propTypes } from '@/utils/propTypes'

defineOptions({ name: 'TradeOrderDetail' })

const message = useMessage() // 消息弹窗

/** 获得 userType 颜色 */
const getUserTypeColor = (type: number) => {
  const dict = getDictObj(DICT_TYPE.USER_TYPE, type)
  switch (dict?.colorType) {
    case 'success':
      return '#67C23A'
    case 'info':
      return '#909399'
    case 'warning':
      return '#E6A23C'
    case 'danger':
      return '#F56C6C'
  }
  return '#409EFF'
}

// 订单详情
const formData = ref<TradeOrderApi.OrderVO>({
  items: [],
  logs: []
})

/** 各种操作 */
const updateRemarkForm = ref() // 订单备注表单 Ref
const remark = () => {
  updateRemarkForm.value?.open(formData.value)
}
const deliveryFormRef = ref() // 发货表单 Ref
const delivery = () => {
  deliveryFormRef.value?.open(formData.value)
}
const updateAddressFormRef = ref() // 收货地址表单 Ref
const updateAddress = () => {
  updateAddressFormRef.value?.open(formData.value)
}
const updatePriceFormRef = ref() // 订单调价表单 Ref
const updatePrice = () => {
  updatePriceFormRef.value?.open(formData.value)
}

/** 确认收款表单 Ref（008 / FR-203） */
const offlineConfirmFormRef = ref()
const confirmOfflinePaid = () => {
  offlineConfirmFormRef.value?.open(formData.value)
}

/**
 * 后台取消订单（008 / FR-213）
 *
 * ⚠️ 两次 try/catch **刻意分开**：弹层那次是"用户点了再想想"（正常操作，静默返回），
 * 接口那次是真出错（必须让 message 的全局报错冒出来）。合成一个 catch 会把接口失败也吞掉。
 */
const cancelOrder = async () => {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      '取消后商品库存与所用优惠券会释放，订单**不可恢复**。',
      '取消订单',
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
  await TradeOrderApi.cancelOrder({ id: formData.value.id!, reason: reason || undefined })
  message.success('订单已取消')
  await getDetail()
}

/** 核销 */
const handlePickUp = async () => {
  try {
    // 二次确认
    await message.confirm('确认核销订单吗？')
    // 提交
    await TradeOrderApi.pickUpOrder(formData.value.id!)
    message.success('核销成功')
    // 刷新列表
    await getDetail()
  } catch {}
}

/** 获得详情 */
const { params } = useRoute() // 查询参数
const props = defineProps({
  id: propTypes.number.def(undefined), // 订单ID
  showPickUp: propTypes.bool.def(true) // 显示核销按钮
})
const id = Number(params.id || props.id)
const getDetail = async () => {
  if (id) {
    const res = (await TradeOrderApi.getOrder(id)) as TradeOrderApi.OrderVO
    // 没有表单信息则关闭页面返回
    if (!res) {
      message.error('交易订单不存在')
      close()
    }
    formData.value = res
  }
}

/** 关闭 tag */
const { delView } = useTagsViewStore() // 视图操作
const { push, currentRoute } = useRouter() // 路由
const close = () => {
  delView(unref(currentRoute))
  push({ name: 'TradeOrder' })
}

/** 复制 */
const clipboardSuccess = () => {
  message.success('复制成功')
}

/** 初始化 **/
const deliveryExpressList = ref<DeliveryExpressApi.DeliveryExpressVO[]>([]) // 物流公司
const expressTrackList = ref<Array<{ time?: Date | string; content?: string }>>([]) // 物流详情
const pickUpStore = ref<DeliveryPickUpStoreApi.DeliveryPickUpStoreVO>() // 自提门店
onMounted(async () => {
  await getDetail()
  // 如果配送方式为快递，则查询物流公司
  if (formData.value.deliveryType === DeliveryTypeEnum.EXPRESS.type) {
    deliveryExpressList.value = await DeliveryExpressApi.getSimpleDeliveryExpressList()
    if (formData.value.logisticsId) {
      expressTrackList.value = await TradeOrderApi.getExpressTrackList(formData.value.id!)
    }
  } else if (formData.value.deliveryType === DeliveryTypeEnum.PICK_UP.type) {
    if (formData.value.pickUpStoreId) {
      pickUpStore.value = await DeliveryPickUpStoreApi.getDeliveryPickUpStore(
        formData.value.pickUpStoreId
      )
    }
  }
})
</script>
<style lang="scss" scoped>
:deep(.el-descriptions) {
  &:not(:nth-child(1)) {
    margin-top: 20px;
  }

  .el-descriptions__title {
    display: flex;
    align-items: center;

    &::before {
      display: inline-block;
      width: 3px;
      height: 20px;
      margin-right: 10px;
      background-color: #409eff;
      content: '';
    }
  }

  .el-descriptions-item__container {
    margin: 0 10px;

    .no-colon {
      margin: 0;

      &::after {
        content: '';
      }
    }
  }
}

// 时间线样式调整
:deep(.el-timeline) {
  margin: 10px 0 0 160px;

  .el-timeline-item__wrapper {
    position: relative;
    top: -20px;

    .el-timeline-item__timestamp {
      position: absolute !important;
      top: 10px;
      left: -150px;
    }
  }

  .el-timeline-right-content {
    display: flex;
    min-height: 30px;
    padding: 10px;
    background-color: var(--app-content-bg-color);
    border-radius: var(--el-card-border-radius);
    align-items: center;

    &::before {
      position: absolute;
      top: 10px;
      left: 13px; /* 将伪元素水平居中 */
      border-color: transparent var(--app-content-bg-color) transparent transparent; /* 尖角颜色，左侧朝向 */
      border-style: solid;
      border-width: 8px; /* 调整尖角大小 */
      content: ''; /* 必须设置 content 属性 */
    }
  }

  .dot-node-style {
    position: absolute;
    left: -5px;
    display: flex;
    width: 20px;
    height: 20px;
    font-size: 10px;
    color: #fff;
    border-radius: 50%;
    justify-content: center;
    align-items: center;
  }
}
</style>
