<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'

import { getPayOrder, listEnabledChannelCodes, submitPay } from '@/api/pay'
import { getSocialAuthRedirectUrl, getSocialUser } from '@/api/social'
import { useToasts } from '@/components/base/useToasts'
import MlModal from '@/components/base/MlModal.vue'
import QrPayDialog from '@/components/QrPayDialog.vue'
import { PayOrderStatus } from '@/types'
import {
  invokeWxPay,
  isWechatBrowser,
  markWxPayPending,
  parseWxJsapiParams,
  redirectTo,
} from '@/utils/weixin'

/**
 * 会员支付弹层（021 / US73）
 *
 * 复用既有 `/pay/order/**` 能力；维修订单只负责拿到 payOrderId，渠道选择与展示方式沿用主订单。
 * `displayMode=url` 必须真的跳转，`qr_code` 交给 QrPayDialog —— 两者漏掉都表现为「点了没反应」。
 */
const emit = defineEmits<{ paid: [] }>()
const toast = useToasts()
const route = useRoute()

const WX_PUB_CHANNEL = 'wx_pub'
const wxOpenid = ref('')

const visible = ref(false)
const loading = ref(false)
const paying = ref(false)
const payOrderId = ref<number>()
const channels = ref<string[]>([])
const selectedChannel = ref('')
const qrOpen = ref(false)
const qrContent = ref('')
const appId = ref<number>()

const CHANNEL_LABELS: Record<string, string> = {
  wx_lite: '微信支付',
  wx_pub: '微信支付',
  wx_native: '微信扫码',
  alipay_pc: '支付宝',
  alipay_wap: '支付宝',
  alipay_qr: '支付宝扫码',
  mock: '模拟支付',
}

const labelOf = (code: string) => CHANNEL_LABELS[code] || code

async function open(id: number) {
  payOrderId.value = id
  visible.value = true
  loading.value = true
  channels.value = []
  selectedChannel.value = ''
  try {
    const payOrder = await getPayOrder(id)
    appId.value = payOrder.appId
    const codes = await listEnabledChannelCodes(payOrder.appId)
    channels.value = codes.filter((code) => code !== 'offline')
    selectedChannel.value = channels.value[0] ?? ''
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '支付渠道加载失败')
  } finally {
    loading.value = false
  }
}

async function resolveWxOpenid(): Promise<string> {
  if (wxOpenid.value) return wxOpenid.value
  try {
    wxOpenid.value = (await getSocialUser())?.openid ?? ''
  } catch {
    wxOpenid.value = ''
  }
  return wxOpenid.value
}

/** 微信公众号 JSAPI：没有 openid 先整页跳授权，回来由客户服务页接着打开本弹层 */
async function payByWechatJsapi() {
  if (!isWechatBrowser()) {
    toast.error('请在微信中打开本页后再使用微信支付')
    return
  }
  const openid = await resolveWxOpenid()
  if (!openid) {
    markWxPayPending(payOrderId.value!)
    redirectTo(await getSocialAuthRedirectUrl(`${window.location.origin}${route.path}`))
    return
  }
  const resp = await submitPay(payOrderId.value!, WX_PUB_CHANNEL, { openid })
  const outcome = await invokeWxPay(parseWxJsapiParams(resp.displayContent))
  if (outcome === 'cancel') {
    toast.warn('支付已取消')
    return
  }
  const paidOrder = await getPayOrder(payOrderId.value!, true)
  if (paidOrder.status === PayOrderStatus.SUCCESS) {
    visible.value = false
    emit('paid')
  } else {
    toast.warn('支付处理中，请稍后刷新查看')
  }
}

async function pay() {
  if (!payOrderId.value || !selectedChannel.value) {
    toast.error('请选择支付方式')
    return
  }
  if (selectedChannel.value === WX_PUB_CHANNEL) {
    paying.value = true
    try {
      await payByWechatJsapi()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '支付失败，请稍后重试')
    } finally {
      paying.value = false
    }
    return
  }
  paying.value = true
  try {
    const resp = await submitPay(payOrderId.value, selectedChannel.value, undefined, window.location.href)
    if (resp.displayMode === 'url') {
      window.location.href = resp.displayContent
      return
    }
    if (resp.displayMode === 'qr_code') {
      if (!resp.displayContent) {
        toast.error('二维码获取失败，请换一个渠道重试')
        return
      }
      qrContent.value = resp.displayContent
      qrOpen.value = true
      return
    }
    // 其余渠道（含 mock）：晚一点同步一次支付单，成功就通知页面刷新
    const paidOrder = await getPayOrder(payOrderId.value, true)
    if (paidOrder.status === PayOrderStatus.SUCCESS) {
      visible.value = false
      emit('paid')
    } else {
      toast.warn('支付处理中，请稍后刷新查看')
    }
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '支付失败，请稍后重试')
  } finally {
    paying.value = false
  }
}

function onQrPaid() {
  qrOpen.value = false
  visible.value = false
  emit('paid')
}

defineExpose({ open })
</script>

<template>
  <MlModal :open="visible" title="支付维修订单" @close="visible = false">
    <div v-if="loading" class="pay-loading">正在获取支付方式…</div>
    <template v-else>
      <p class="pay-hint">请选择支付方式：</p>
      <div class="pay-channels">
        <button
          v-for="code in channels"
          :key="code"
          type="button"
          class="pay-channel"
          :class="{ 'is-active': selectedChannel === code }"
          @click="selectedChannel = code"
        >{{ labelOf(code) }}</button>
      </div>
      <p v-if="!channels.length" class="pay-hint">当前没有可用的在线支付渠道</p>
    </template>
    <template #foot>
      <button class="btn-cart" type="button" @click="visible = false">取消</button>
      <button class="btn-primary" type="button" :disabled="paying || !channels.length" @click="pay">
        {{ paying ? '支付中…' : '立即支付' }}
      </button>
    </template>
  </MlModal>

  <QrPayDialog
    :open="qrOpen"
    :code-url="qrContent"
    :pay-order-id="payOrderId ?? 0"
    @close="qrOpen = false"
    @paid="onQrPaid"
  />
</template>

<style scoped>
.pay-loading { padding: 20px 0; color: #5b6c8f; font-size: 13px; }
.pay-hint { color: #5b6c8f; font-size: 13px; margin: 0 0 10px; }
.pay-channels { display: flex; flex-wrap: wrap; gap: 10px; }
.pay-channel { padding: 9px 18px; border: 1px solid #e3e9f4; border-radius: 8px; background: #fff; color: #5b6c8f; cursor: pointer; }
.pay-channel.is-active { border-color: #2e7cd6; color: #2e7cd6; background: #f5f8ff; }
</style>
