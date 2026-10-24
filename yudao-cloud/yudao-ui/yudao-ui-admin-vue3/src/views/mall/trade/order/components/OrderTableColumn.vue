<template>
  <el-table-column class-name="order-table-col">
    <!--
      ⚠️ 表头与每笔订单的内容**共用同一个 grid 模板**（`.order-grid`，见下方样式）。
      早先这里是「外层表头 + 每个订单一张内层 el-table」，表头宽度靠挂载后实测内层表的
      像素宽再回填 —— 容器一变（窗口缩放、侧栏折叠）就错位，2026-10-23 预览时肉眼可见。
      现在两边都由同一个 fr 模板算宽度，天然对齐，也不再需要那套测量代码。
    -->
    <template #header>
      <div class="order-grid order-grid-head">
        <div>产品</div>
        <div>数量</div>
        <div>金额</div>
        <div>下单时间</div>
        <div>会员</div>
        <div>发票</div>
        <div>收件人信息</div>
        <div>状态</div>
        <div>操作</div>
      </div>
    </template>
    <template #default="scope">
      <div class="order-block">
        <!--
          订单信息条（按旧后台的版式）：左边「订单编号 + 完成时间」，右边「快递编号 + 查看物流」。
          原来那条亮黄色（#f1f706）把来源/支付方式/支付时间/订单类型全塞在一行里，挤且刺眼；
          现在只留运营扫单时真正会看的几项，其余去详情页看。
        -->
        <div class="order-head-bar">
          <div class="order-head-left">
            <span>订单编号：{{ scope.row.no }}</span>
            <span v-if="scope.row.finishTime">
              完成时间：{{ formatDate(scope.row.finishTime) }}
            </span>
            <span v-else>下单时间：{{ formatDate(scope.row.createTime) }}</span>
          </div>
          <div v-if="scope.row.logisticsNo" class="order-head-right">
            <span>快递编号：{{ scope.row.logisticsNo }}</span>
            <!-- 物流轨迹在订单详情里（唯一一处实现），这里只负责把人送过去 -->
            <el-link
              type="primary"
              :underline="false"
              class="order-head-link"
              @click="openDetail(scope.row.id)"
            >
              查看物流
            </el-link>
          </div>
        </div>

        <div class="order-grid order-grid-body">
          <!-- 每个订单项一行：产品 / 数量 -->
          <template v-for="(item, index) in scope.row.items" :key="item.id">
            <div class="order-cell is-left" :style="{ gridColumn: '1', gridRow: itemRow(index) }">
              <div class="order-product">
                <div class="order-product-pic">
                  <el-image :src="item.picUrl" fit="cover" class="h-[44px] w-[44px]">
                    <template #error>
                      <div class="flex h-full w-full items-center justify-center bg-gray-100">
                        <Icon icon="ep:picture" :size="16" color="#c0c4cc" />
                      </div>
                    </template>
                  </el-image>
                </div>
                <div class="order-product-info">
                  <ElTooltip :content="item.spuName" placement="top">
                    <span class="order-product-name">{{ item.spuName }}</span>
                  </ElTooltip>
                  <div v-if="item.properties?.length" class="order-product-spec">
                    <el-tag
                      v-for="property in item.properties"
                      :key="property.propertyId"
                      size="small"
                      class="mr-[4px]"
                    >
                      {{ property.propertyName }}: {{ property.valueName }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </div>
            <div class="order-cell" :style="{ gridColumn: '2', gridRow: itemRow(index) }">
              {{ item.count }}
            </div>
          </template>

          <!-- 订单级信息：跨所有订单项行 -->
          <div
            class="order-cell"
            :style="{ gridColumn: '3', gridRow: spanRow(rowSpan(scope.row)) }"
          >
            <div class="order-amount">{{ floatToFixed2(scope.row.payPrice!) }}</div>
            <div class="order-amount-origin">{{ floatToFixed2(scope.row.totalPrice!) }}元</div>
          </div>
          <div
            class="order-cell"
            :style="{ gridColumn: '4', gridRow: spanRow(rowSpan(scope.row)) }"
          >
            {{ formatDate(scope.row.createTime) }}
          </div>
          <div
            class="order-cell"
            :style="{ gridColumn: '5', gridRow: spanRow(rowSpan(scope.row)) }"
          >
            <div>{{ scope.row.user?.nickname || '—' }}</div>
            <div class="order-sub-text">(ID:{{ scope.row.userId }})</div>
          </div>
          <!-- 是否需要发票（2026-10-23 替换原来的「推荐人」列） -->
          <div
            class="order-cell"
            :style="{ gridColumn: '6', gridRow: spanRow(rowSpan(scope.row)) }"
          >
            <el-tag v-if="scope.row.invoiceStatus === INVOICE_STATUS.WAITING" type="warning">
              待开票
            </el-tag>
            <el-tag v-else-if="scope.row.invoiceStatus === INVOICE_STATUS.ISSUED" type="success">
              已开票
            </el-tag>
            <el-tag v-else-if="scope.row.invoiceStatus === INVOICE_STATUS.CANCELED" type="info">
              已作废
            </el-tag>
            <span v-else class="order-sub-text">否</span>
          </div>
          <div
            class="order-cell"
            :style="{ gridColumn: '7', gridRow: spanRow(rowSpan(scope.row)) }"
          >
            <!-- 快递发货：收件人 -->
            <template v-if="scope.row.deliveryType === DeliveryTypeEnum.EXPRESS.type">
              <div>{{ scope.row.receiverName }}</div>
              <div class="order-sub-text">{{ scope.row.receiverMobile }}</div>
            </template>
            <!-- 虚拟商品（软件等）：没有收货人，只显示买家（007） -->
            <template v-else-if="scope.row.deliveryType === DeliveryTypeEnum.VIRTUAL.type">
              <div>买家：{{ scope.row.user?.nickname || '—' }}</div>
            </template>
            <!-- 自提 -->
            <template v-else>
              <div>
                {{ pickUpStoreList.find((p) => p.id === scope.row.pickUpStoreId)?.name }}
              </div>
              <div class="order-sub-text">
                {{ pickUpStoreList.find((p) => p.id === scope.row.pickUpStoreId)?.phone }}
              </div>
            </template>
          </div>
          <div
            class="order-cell"
            :style="{ gridColumn: '8', gridRow: spanRow(rowSpan(scope.row)) }"
          >
            <!--
              对公转账「待收款」（008 / FR-200 的运营侧）：状态值仍是「未支付」，
              但运营要一眼看出"这单在等打款"，不能与普通待支付混在一起 ——
              ⚠️ 判定由服务端给（`offlineWaiting`），前端**不看** `payChannelCode`
              （对公单确认收款前那一列是空的）。
            -->
            <el-tag v-if="scope.row.offlineWaiting" type="warning">待收款</el-tag>
            <dict-tag v-else :type="DICT_TYPE.TRADE_ORDER_STATUS" :value="scope.row.status" />
            <!-- 售后状态原来独占一列；收成一列后挂在状态下面，免得"退款中"在列表上看不见 -->
            <div v-if="hasAfterSale(scope.row)" class="order-after-sale">
              <dict-tag
                :type="DICT_TYPE.TRADE_ORDER_ITEM_AFTER_SALE_STATUS"
                :value="scope.row.afterSaleStatus"
              />
            </div>
          </div>
          <div
            class="order-cell"
            :style="{ gridColumn: '9', gridRow: spanRow(rowSpan(scope.row)) }"
          >
            <slot :row="scope.row"></slot>
          </div>
        </div>
      </div>
    </template>
  </el-table-column>
</template>
<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { DeliveryTypeEnum } from '@/utils/constants'
import { INVOICE_STATUS } from '@/utils/invoice'
import { formatDate } from '@/utils/formatTime'
import { floatToFixed2 } from '@/utils'
import { OrderVO } from '@/api/mall/trade/order'
import type { DeliveryPickUpStoreVO } from '@/api/mall/trade/delivery/pickUpStore'

defineOptions({ name: 'OrderTableColumn' })

defineProps<{
  /** 传而不用：列表页按原签名传入，留作以后「整单级」统计用 */
  list: OrderVO[]
  pickUpStoreList: DeliveryPickUpStoreVO[]
}>()

/** 订单级格子要跨几行（= 这笔订单有几件商品） */
const spanRow = (span: number) => `1 / span ${span}`

/** 第 index 个订单项所在的行（从 1 起） */
const itemRow = (index: string | number) => String(Number(index) + 1)

const rowSpan = (row: OrderVO) => Math.max(row.items?.length ?? 0, 1)

/** 有没有售后（`0` = 无售后，见字典 TRADE_ORDER_ITEM_AFTER_SALE_STATUS） */
const hasAfterSale = (row: OrderVO) => !!row.afterSaleStatus

const { push } = useRouter()

/** 查看物流：物流轨迹在订单详情页（全站唯一一处实现），这里只负责把人送过去 */
const openDetail = (id?: number | null) => {
  if (id) push({ name: 'TradeOrderDetail', params: { id } })
}
</script>
<style lang="scss" scoped>
/*
 * 表头与每笔订单的内容共用这套列宽。
 * ⚠️ 只有这一处定义 —— 表头和数据都读它，不存在"两边各写一遍、改了一边"的可能。
 */
.order-grid {
  display: grid;
  grid-template-columns: 24fr 6fr 8fr 12fr 10fr 9fr 12fr 9fr 10fr;
}
.order-grid-head {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  text-align: center;
}
/* 「产品」列是左对齐的长商品名，居中的表头会显得漂 */
.order-grid-head > div:first-child {
  text-align: left;
}

/*
 * 订单信息条：中性灰底，替代原来的亮黄色。
 * 左「订单编号 + 完成时间」，右「快递编号 + 查看物流」。
 */
.order-head-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 14px;
  background-color: #f5f7fa;
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
}
.order-head-left,
.order-head-right {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}
.order-head-left span,
.order-head-right span {
  overflow: hidden;
  text-overflow: ellipsis;
}
.order-head-link {
  font-size: 12px;
}

/* 数据区：格子即「单元格」，右侧与下方描线，视觉上仍是一张表 */
.order-grid-body {
  border-top: 1px solid #ebeef5;
}
.order-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px 8px;
  border-right: 1px solid #ebeef5;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
  color: #303133;
  text-align: center;
  overflow-wrap: anywhere;
}
.order-cell.is-left {
  align-items: flex-start;
  text-align: left;
}
/* 每行最后一格不描右边，避免与外层表格右边框叠成双线 */
.order-cell:nth-last-child(1) {
  border-right: 0;
}

/* 产品单元格：缩略图 + 名称/规格 */
.order-product {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.order-product-pic {
  flex: 0 0 auto;
  overflow: hidden;
  border-radius: 4px;
}
.order-product-info {
  min-width: 0;
}
.order-product-name {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: 13px;
  line-height: 1.5;
}
.order-product-spec {
  margin-top: 4px;
}

/* 金额：实付（红）＋ 原价（灰） */
.order-amount {
  font-size: 15px;
  font-weight: 600;
  color: #e54d42;
  line-height: 1.4;
}
.order-amount-origin {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

/* 售后状态收进「状态」列后的第二行 */
.order-after-sale {
  margin-top: 4px;
}

/* 会员 ID、手机号这类次要信息 */
.order-sub-text {
  font-size: 12px;
  color: #909399;
}
</style>

<!--
  紧凑化：一页尽量多看几条订单。

  ⚠️ 这段**不能加 scoped**，是刻意的。`.order-table-col` 来自 `el-table-column` 的
  `class-name`，它被打在**外层表格的 `<td>` 上**，而那个 `<td>` 是 el-table 自己渲染的、
  不属于本组件的模板，拿不到本组件的 scope id。加了 scoped 就会编译成
  `[data-v-x] .order-table-col ...`，要求有一个带本组件 scope id 的祖先 —— **永远匹配不到**。
  选择器已由 `.order-table-col` 限定，不会外溢到别的页面。
-->
<style lang="scss">
/*
 * 外层格子上下各 8px 的内边距是大头（每个订单都白占 16px）。
 * 用 !important 是因为要压过 EP 的 `.el-table td.el-table__cell`：
 * 两者特异性相同，靠加载顺序决定胜负，不稳。
 */
.order-table-col {
  padding: 3px 0 !important;
}

/* 外层格子里的 `.cell` 自带左右内边距，会让我们和表头各偏一点；统一压平 */
.order-table-col .cell {
  padding: 0 !important;
}
</style>
