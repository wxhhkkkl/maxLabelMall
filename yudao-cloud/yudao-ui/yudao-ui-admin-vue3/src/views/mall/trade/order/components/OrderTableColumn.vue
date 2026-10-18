<template>
  <el-table-column class-name="order-table-col">
    <template #header>
      <div class="flex items-center" style="width: 100%">
        <div :style="{ width: orderTableHeadWidthList[0] + 'px' }" class="flex justify-center">
          商品信息
        </div>
        <div :style="{ width: orderTableHeadWidthList[1] + 'px' }" class="flex justify-center">
          单价(元)/数量
        </div>
        <div :style="{ width: orderTableHeadWidthList[2] + 'px' }" class="flex justify-center">
          售后状态
        </div>
        <div :style="{ width: orderTableHeadWidthList[3] + 'px' }" class="flex justify-center">
          实付金额(元)
        </div>
        <div :style="{ width: orderTableHeadWidthList[4] + 'px' }" class="flex justify-center">
          买家/收货人
        </div>
        <div :style="{ width: orderTableHeadWidthList[5] + 'px' }" class="flex justify-center">
          配送方式
        </div>
        <div :style="{ width: orderTableHeadWidthList[6] + 'px' }" class="flex justify-center">
          订单状态
        </div>
        <div :style="{ width: orderTableHeadWidthList[7] + 'px' }" class="flex justify-center">
          操作
        </div>
      </div>
    </template>
    <template #default="scope">
      <el-table
        :ref="setOrderTableRef"
        :border="true"
        :data="scope.row.items"
        :header-cell-style="headerStyle"
        :span-method="spanMethod"
        style="width: 100%"
      >
        <el-table-column min-width="300" prop="spuName">
          <template #header>
            <div
              class="h-[28px] flex items-center -mx-[10px] px-[20px]"
              style="background-color: #f1f706"
            >
              <span class="mr-20px">订单号：{{ scope.row.no }} </span>
              <span class="mr-20px">下单时间：{{ formatDate(scope.row.createTime) }}</span>
              <span>订单来源：</span>
              <dict-tag :type="DICT_TYPE.TERMINAL" :value="scope.row.terminal" class="mr-20px" />
              <span>支付方式：</span>
              <dict-tag
                v-if="scope.row.payChannelCode"
                :type="DICT_TYPE.PAY_CHANNEL_CODE"
                :value="scope.row.payChannelCode"
                class="mr-20px"
              />
              <span v-else class="mr-20px">未支付</span>
              <span v-if="scope.row.payTime" class="mr-20px">
                支付时间：{{ formatDate(scope.row.payTime) }}
              </span>
              <span>订单类型：</span>
              <dict-tag :type="DICT_TYPE.TRADE_ORDER_TYPE" :value="scope.row.type" />
            </div>
          </template>
          <template #default="{ row }">
            <div class="flex flex-wrap items-center">
              <div class="mr-[8px] flex items-start">
                <ElTooltip :content="row.spuName" placement="top">
                  <span class="overflow-ellipsis max-h-[38px] overflow-hidden leading-[19px]">
                    {{ row.spuName }}
                  </span>
                </ElTooltip>
              </div>
              <el-tag
                v-for="property in row.properties"
                :key="property.propertyId"
                size="small"
                class="mb-[2px] mr-[6px]"
              >
                {{ property.propertyName }}: {{ property.valueName }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品原价*数量" prop="price" width="150">
          <template #default="{ row }">
            {{ floatToFixed2(row.price) }} 元 / {{ row.count }}
          </template>
        </el-table-column>
        <el-table-column label="售后状态" prop="afterSaleStatus" width="120">
          <template #default="{ row }">
            <dict-tag
              :type="DICT_TYPE.TRADE_ORDER_ITEM_AFTER_SALE_STATUS"
              :value="row.afterSaleStatus"
            />
          </template>
        </el-table-column>
        <el-table-column align="center" label="实际支付" min-width="120" prop="payPrice">
          <template #default>
            {{ floatToFixed2(scope.row.payPrice) + '元' }}
          </template>
        </el-table-column>
        <el-table-column label="买家/收货人" min-width="160">
          <template #default>
            <!-- 快递发货  -->
            <div
              v-if="scope.row.deliveryType === DeliveryTypeEnum.EXPRESS.type"
              class="flex flex-col"
            >
              <span>买家：{{ scope.row.user?.nickname }}</span>
              <span>
                收货人：{{ scope.row.receiverName }} {{ scope.row.receiverMobile }}
                {{ scope.row.receiverAreaName }} {{ scope.row.receiverDetailAddress }}
              </span>
            </div>
            <!-- 虚拟商品（软件等）：没有收货人，只显示买家（007） -->
            <div
              v-if="scope.row.deliveryType === DeliveryTypeEnum.VIRTUAL.type"
              class="flex flex-col"
            >
              <span>买家：{{ scope.row.user?.nickname }}</span>
            </div>
            <!-- 自提  -->
            <div
              v-if="scope.row.deliveryType === DeliveryTypeEnum.PICK_UP.type"
              class="flex flex-col"
            >
              <span>
                门店名称：
                {{ pickUpStoreList.find((p) => p.id === scope.row.pickUpStoreId)?.name }}
              </span>
              <span>
                门店手机：
                {{ pickUpStoreList.find((p) => p.id === scope.row.pickUpStoreId)?.phone }}
              </span>
              <span>
                自提门店:
                {{ pickUpStoreList.find((p) => p.id === scope.row.pickUpStoreId)?.detailAddress }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column align="center" label="配送方式" width="120">
          <template #default>
            <dict-tag :type="DICT_TYPE.TRADE_DELIVERY_TYPE" :value="scope.row.deliveryType" />
          </template>
        </el-table-column>
        <el-table-column align="center" label="订单状态" width="120">
          <template #default>
            <!--
              对公转账「待收款」（008 / FR-200 的运营侧）：状态值仍是「未支付」，
              但运营要一眼看出"这单在等打款"，不能与普通待支付混在一起 ——
              ⚠️ 判定由服务端给（`offlineWaiting`），前端**不看** `payChannelCode`
              （对公单确认收款前那一列是空的）。
            -->
            <el-tag v-if="scope.row.offlineWaiting" type="warning">待收款</el-tag>
            <dict-tag v-else :type="DICT_TYPE.TRADE_ORDER_STATUS" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column align="center" fixed="right" label="操作" width="160">
          <template #default>
            <slot :row="scope.row"></slot>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </el-table-column>
</template>
<script lang="ts" setup>
import { nextTick, onMounted, onUnmounted, watch } from 'vue'
import { DICT_TYPE } from '@/utils/dict'
import { DeliveryTypeEnum } from '@/utils/constants'
import { formatDate } from '@/utils/formatTime'
import { floatToFixed2 } from '@/utils'
import * as TradeOrderApi from '@/api/mall/trade/order'
import { OrderVO } from '@/api/mall/trade/order'
import type { TableColumnCtx, TableInstance } from 'element-plus'
import type { DeliveryPickUpStoreVO } from '@/api/mall/trade/delivery/pickUpStore'

defineOptions({ name: 'OrderTableColumn' })

const props = defineProps<{
  list: OrderVO[]
  pickUpStoreList: DeliveryPickUpStoreVO[]
}>()

const headerStyle = ({ row, columnIndex }: any) => {
  // 表头第一行第一列占 8
  if (columnIndex === 0) {
    row[columnIndex].colSpan = 8
  } else {
    // 其余的不要
    row[columnIndex].colSpan = 0
    return {
      display: 'none'
    }
  }
  return {}
}

interface SpanMethodProps {
  row: TradeOrderApi.OrderItemRespVO
  column: TableColumnCtx<TradeOrderApi.OrderItemRespVO>
  rowIndex: number
  columnIndex: number
}

type spanMethodResp = number[] | { rowspan: number; colspan: number } | undefined
const spanMethod = ({ row, rowIndex, columnIndex }: SpanMethodProps): spanMethodResp => {
  const len = props.list.find(
    (order) => order.items?.findIndex((item) => item.id === row.id) !== -1
  )?.items?.length
  // 要合并的列，从零开始
  const colIndex = [3, 4, 5, 6, 7]
  if (colIndex.includes(columnIndex)) {
    // 除了第一行其余的不要
    if (rowIndex !== 0) {
      return {
        rowspan: 0,
        colspan: 0
      }
    }
    // 动态合并行
    return {
      rowspan: len!,
      colspan: 1
    }
  }
}

const orderTableHeadWidthList = ref([300, 150, 120, 120, 160, 120, 120, 160]) // 头部 col 宽度初始化
let isFirstTable = false // 标记是否已处理第一个表格
let firstTableInstance: TableInstance | null = null

/** 解决 ref 在 v-for 中的获取问题*/
const setOrderTableRef = async (el: TableInstance) => {
  if (!el) return
  // 只处理第一个表格实例
  if (!isFirstTable) {
    isFirstTable = true
    firstTableInstance = el
    // 使用 nextTick 确保 DOM 已完全渲染
    await nextTick()
    tableHeadWidthAuto(el)
  }
}

/** 头部宽度自适应 */
const tableHeadWidthAuto = (el: TableInstance) => {
  if (!el) return
  const columns = el.store.states.columns.value
  if (columns.length === 0) {
    return
  }
  columns.forEach((col: TableColumnCtx<TableInstance>, index: number) => {
    if (col.realWidth) {
      orderTableHeadWidthList.value[index] = col.realWidth
    }
  })
}

/** 监听窗口大小变化，重新计算表头宽度 */
const handleResize = async () => {
  if (firstTableInstance) {
    await nextTick()
    tableHeadWidthAuto(firstTableInstance!)
  }
}

/** 监听列表数据变化，重新计算表头宽度 */
watch(
  () => props.list,
  async () => {
    // 数据变化后，等待 DOM 更新完成再重新计算宽度
    await nextTick()
    if (firstTableInstance) {
      // 延迟一小段时间，确保表格已完全渲染
      await new Promise((resolve) => setTimeout(resolve, 100))
      tableHeadWidthAuto(firstTableInstance!)
    }
  },
  { deep: true }
)

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>
<style lang="scss" scoped>
:deep(.order-table-col > .cell) {
  padding: 0;
}
</style>

<!--
  紧凑化：一页尽量多看几条订单。

  ⚠️ 这段**不能加 scoped**，是刻意的。`.order-table-col` 来自 `el-table-column` 的
  `class-name`，它被打在**外层表格的 `<td>` 上**，而那个 `<td>` 是 el-table 自己渲染的、
  不属于本组件的模板，拿不到本组件的 scope id。加了 scoped 就会编译成
  `[data-v-x] .order-table-col ...`，要求有一个带本组件 scope id 的祖先 —— **永远匹配不到**
  （上面那条 `:deep(.order-table-col > .cell)` 就是这种情况，是死代码）。
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

/* 内层表格的行同样压一遍（8px → 3px） */
.order-table-col .el-table tr > .el-table__cell {
  padding: 3px 0;
}

/* 内层表头那一格整格就是「订单号 / 下单时间」信息条，上下内边距全去掉 */
.order-table-col .el-table__header-wrapper tr > .el-table__cell {
  padding: 0;
}
</style>
