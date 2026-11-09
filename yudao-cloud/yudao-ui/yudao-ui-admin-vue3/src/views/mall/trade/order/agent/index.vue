<template>
  <ContentWrap>
    <el-page-header content="代客下单" @back="goBack" />

    <!-- 1. 客户 -->
    <el-card class="mt-10px">
      <template #header>
        <div class="flex items-center justify-between">
          <span>1. 选择客户</span>
          <el-button type="primary" plain @click="customerSelectRef?.open()">选择客户</el-button>
        </div>
      </template>
      <el-descriptions v-if="customer" :column="3" border>
        <el-descriptions-item label="会员编号">{{ customer.id }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ customer.nickname || '—' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ customer.mobile || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else :image-size="60" description="请先选择目标客户" />
    </el-card>

    <!-- 2. 商品 -->
    <el-card class="mt-10px">
      <template #header>
        <div class="flex items-center justify-between">
          <span>2. 选择商品</span>
          <el-button type="primary" plain :disabled="!customer" @click="productSelectRef?.open()">
            添加商品
          </el-button>
        </div>
      </template>
      <el-table v-if="lines.length" :data="lines" row-key="skuId">
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="flex items-center gap-8px">
              <el-image :src="row.picUrl" class="h-40px w-40px" fit="cover" />
              <div>
                <div>{{ row.spuName }}</div>
                <div v-if="row.properties" class="text-xs text-gray-500">{{ row.properties }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="{ row }">{{ yuan(row.price) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.count"
              :min="1"
              :max="row.stock || 999999"
              size="small"
              @change="refreshSettlement"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeLine($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else :image-size="60" description="请至少添加一件商品" />
    </el-card>

    <!-- 3. 收货地址（仅实物单） -->
    <el-card v-if="settlement?.deliveryType === DELIVERY_TYPE_EXPRESS" class="mt-10px">
      <template #header><span>3. 收货地址（来自客户地址簿）</span></template>
      <AgentAddressSelect v-model="addressId" :user-id="customer?.id" />
    </el-card>

    <!-- 4. 价格与支付 -->
    <el-card v-if="settlement" v-loading="loadingSettlement" class="mt-10px">
      <template #header><span>4. 价格与支付</span></template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="商品原价">{{ yuan(settlement.price.totalPrice) }}</el-descriptions-item>
        <el-descriptions-item label="优惠">{{ yuan(settlement.price.discountPrice) }}</el-descriptions-item>
        <el-descriptions-item label="运费">{{ yuan(settlement.price.deliveryPrice) }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">
          <span class="text-lg font-bold text-red-500">{{ yuan(settlement.price.payPrice) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="配送方式">
          {{ settlement.deliveryType === DELIVERY_TYPE_VIRTUAL ? '虚拟商品（无需配送）' : '快递发货' }}
        </el-descriptions-item>
        <el-descriptions-item label="地址">
          {{ settlement.address ? `${settlement.address.name} ${settlement.address.mobile}` : '—' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="90px" class="mt-15px">
        <el-form-item label="支付方式" required>
          <el-radio-group v-model="paymentMode">
            <el-radio value="wx_native">微信扫码</el-radio>
            <el-radio value="alipay_pc">支付宝扫码</el-radio>
            <el-radio value="offline" :disabled="!settlement.offlinePayEnabled">
              对公转账{{ settlement.offlinePayEnabled ? '（待收款）' : '（未开启）' }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="买家留言">
          <el-input v-model="userRemark" :maxlength="200" show-word-limit type="textarea" />
        </el-form-item>
      </el-form>

      <div class="text-right">
        <el-button type="primary" :loading="submitting" @click="submit">创建订单</el-button>
      </div>
    </el-card>

    <AgentCustomerSelect ref="customerSelectRef" @select="onCustomerSelected" />
    <AgentProductSelect ref="productSelectRef" @select="addLine" />
  </ContentWrap>

  <!-- 支付二维码 / 链接 -->
  <Dialog v-model="payDialogVisible" :title="payTitle" width="380px" append-to-body>
    <div class="flex flex-col items-center">
      <Qrcode v-if="payDisplayMode === 'qr_code'" :text="payDisplayContent" :width="300" />
      <el-link v-else type="primary" :href="payDisplayContent" target="_blank">
        打开支付页面
      </el-link>
      <p class="mt-10px text-sm text-gray-500">客户完成支付后，订单会自动进入待发货</p>
      <el-button class="mt-10px" @click="stopPolling">关闭</el-button>
    </div>
  </Dialog>
</template>

<script lang="ts" setup>
import { Qrcode } from '@/components/Qrcode'
import * as TradeOrderApi from '@/api/mall/trade/order'
import AgentCustomerSelect from './AgentCustomerSelect.vue'
import AgentProductSelect from './AgentProductSelect.vue'
import AgentAddressSelect from './AgentAddressSelect.vue'
import {
  buildAgentCreateReq,
  DELIVERY_TYPE_EXPRESS,
  DELIVERY_TYPE_VIRTUAL,
  payPollingAction,
  validateAgentOrder,
  type AgentOrderLine,
  type AgentPaymentMode
} from './agentOrder'

defineOptions({ name: 'TradeOrderAgentCreate' })

const message = useMessage()
const router = useRouter()

const customerSelectRef = ref<InstanceType<typeof AgentCustomerSelect>>()
const productSelectRef = ref<InstanceType<typeof AgentProductSelect>>()

const customer = ref<{ id: number; nickname?: string; mobile?: string }>()
const lines = ref<AgentOrderLine[]>([])
const addressId = ref<number>()
const paymentMode = ref<AgentPaymentMode>()
const userRemark = ref('')
const settlement = ref<TradeOrderApi.AgentSettlementResp>()
const loadingSettlement = ref(false)
const submitting = ref(false)

const payDialogVisible = ref(false)
const payTitle = ref('扫码支付')
const payDisplayMode = ref('')
const payDisplayContent = ref('')
// 已创建但还没付成功的订单：页面内重试支付时复用，避免重复下单（Q130）
const createdOrderId = ref<number>()
const createdKey = ref('')
let pollTimer: ReturnType<typeof setInterval> | undefined

const currentKey = () =>
  JSON.stringify({
    userId: customer.value?.id,
    items: lines.value.map((item) => ({ skuId: item.skuId, count: item.count })),
    addressId: addressId.value,
    paymentMode: paymentMode.value
  })

const goBack = () => router.push({ name: 'TradeOrder' })

const yuan = (fen?: number) => `¥${((fen || 0) / 100).toFixed(2)}`

const onCustomerSelected = (user: { id: number; nickname?: string; mobile?: string }) => {
  customer.value = user
  lines.value = []
  addressId.value = undefined
  settlement.value = undefined
  paymentMode.value = undefined
  createdOrderId.value = undefined
  createdKey.value = ''
}

const addLine = (line: AgentOrderLine) => {
  const existing = lines.value.find((item) => item.skuId === line.skuId)
  if (existing) {
    existing.count += line.count
  } else {
    lines.value.push({ ...line })
  }
  refreshSettlement()
}

const removeLine = (index: number) => {
  lines.value.splice(index, 1)
  refreshSettlement()
}

const refreshSettlement = async () => {
  if (!customer.value || !lines.value.length) {
    settlement.value = undefined
    return
  }
  loadingSettlement.value = true
  try {
    settlement.value = await TradeOrderApi.agentSettlement({
      userId: customer.value.id,
      items: lines.value.map((item) => ({ skuId: item.skuId, count: item.count })),
      addressId: addressId.value
    })
  } catch (e) {
    settlement.value = undefined
    message.error((e as { message?: string })?.message || '结算失败，请检查商品或地址')
  } finally {
    loadingSettlement.value = false
  }
}

watch(addressId, () => refreshSettlement())

const submit = async () => {
  if (!customer.value) {
    return
  }
  const error = validateAgentOrder({
    userId: customer.value.id,
    items: lines.value,
    deliveryType: settlement.value?.deliveryType,
    addressId: addressId.value,
    paymentMode: paymentMode.value
  })
  if (error) {
    message.warning(error)
    return
  }
  submitting.value = true
  try {
    // 已创建过且参数没变：页面内重试支付，绝不重复下单（Q130）
    if (paymentMode.value !== 'offline' && createdOrderId.value
        && createdKey.value === currentKey()) {
      await payCreatedOrder(createdOrderId.value)
      return
    }
    const createResp = await TradeOrderApi.agentCreate(
      buildAgentCreateReq({
        userId: customer.value.id,
        items: lines.value,
        addressId: addressId.value,
        userRemark: userRemark.value,
        paymentMode: paymentMode.value!
      })
    )
    // 对公转账：订单已是待收款，直接去详情
    if (paymentMode.value === 'offline') {
      message.success('已生成待收款订单')
      goDetail(createResp.orderId)
      return
    }
    createdOrderId.value = createResp.orderId
    createdKey.value = currentKey()
    await payCreatedOrder(createResp.orderId)
  } catch (e) {
    message.error((e as { message?: string })?.message || '创建订单失败')
  } finally {
    submitting.value = false
  }
}

const payCreatedOrder = async (orderId: number) => {
  // 在线支付：服务端白名单只允许 wx_native / alipay_pc
  const payResp = await TradeOrderApi.agentPay({
    orderId,
    channelCode: paymentMode.value!
  })
  payDisplayMode.value = payResp.displayMode
  payDisplayContent.value = payResp.displayContent
  payTitle.value = paymentMode.value === 'wx_native' ? '微信扫码支付' : '支付宝扫码支付'
  payDialogVisible.value = true
  startPolling(orderId)
}

const goDetail = (orderId: number) => {
  stopPolling()
  router.push({ name: 'TradeOrderDetail', params: { id: orderId } })
}

const startPolling = (orderId: number) => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const status = await TradeOrderApi.getAgentPayStatus(orderId)
      const action = payPollingAction(status)
      if (action === 'paid') {
        message.success('支付成功')
        goDetail(orderId)
        return
      }
      if (action === 'closed') {
        stopPolling()
        payDialogVisible.value = false
        message.warning('订单已关闭，请重新下单')
      }
    } catch {
      // 轮询失败不打断页面，下一轮继续
    }
  }, 2000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

onUnmounted(stopPolling)
</script>
