<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { cancelOrder, getOrderDetail } from '@/api/order'
import { getPayOrder, listEnabledChannelCodes, submitPay } from '@/api/pay'
import { bindSocialUser, getSocialAuthRedirectUrl, getSocialUser } from '@/api/social'
import AccountSidebar from '@/components/AccountSidebar.vue'
import CommentCreateDialog from '@/components/CommentCreateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import InvoiceApplyDialog from '@/components/InvoiceApplyDialog.vue'
import LoadingState from '@/components/LoadingState.vue'
import MlAmountRow from '@/components/base/MlAmountRow.vue'
import MlModal from '@/components/base/MlModal.vue'
import MlPill from '@/components/base/MlPill.vue'
import QrPayDialog from '@/components/QrPayDialog.vue'
import RefundApplyDialog from '@/components/RefundApplyDialog.vue'
import type { OrderDetail } from '@/types'
import { InvoiceStatus, OrderStatus, PayOrderStatus } from '@/types'
import { cancelAfterSale } from '@/api/afterSale'
import { afterSaleItemStatusText, canApplyRefund, canCancelAfterSale } from '@/utils/afterSale'
import { invoiceStatusName } from '@/utils/invoice'
import { formatYuan, reconcile } from '@/utils/money'
import { OFFLINE_WAITING_TEXT, canCommentItem, orderStatusText } from '@/utils/orderStatus'
import { buildChannelOptions, type ChannelOption } from '@/utils/payChannel'
import { formatDateTime, toMillis } from '@/utils/time'
import {
  clearWxPayPending,
  invokeWxPay,
  isWechatBrowser,
  markWxPayPending,
  parseWxJsapiParams,
  readWxPayPending,
  redirectTo,
} from '@/utils/weixin'

/**
 * 订单详情 —— 按 design-new-pages.md §3.4 的版式实现（无设计稿）。
 *
 * 五条约束：
 *
 * 1. **状态一律来自后端**（FR-039）。支付完成后后端把订单推进到 `10 待发货`，
 *    前端不得自行把它标成「已支付」，也不得按 `payTime` 是否为空反推状态。
 *
 * 2. **用户侧没有「确认收货」**（FR-041b）。发货由运营在后台操作，收货时限到期后
 *    后端自动完成订单 —— 这里不提供任何推进状态的控件。
 *
 * 3. **待支付才展示支付截止时间**（FR-041）：已经付款或已取消的订单，
 *    「支付截止」没有意义。
 *
 * 4. 五种状态**共用同一版式**，只有状态区与操作区不同（§3.4）。
 *
 * 5. **支付渠道由后端下发**（FR-038，替代已删除的 SC-010），用户选哪个就提交哪个。
 *    微信公众号 JSAPI（`wx_pub`）比别的渠道多两步：先拿到该会员的 openid
 *    （没有就整页跳授权、回跳后接着付），再用后端给的 `displayContent` 唤起微信
 *    收银台 —— 详见 `payByWechatJsapi`。**收银台说「ok」也不代表已付款**：状态
 *    始终等后端回调推进（这就是约束 1）。
 */

const props = defineProps<{ id: number | string }>()

const route = useRoute()
const router = useRouter()

const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const error = ref(false)
const message = ref('')
const cancelOpen = ref(false)
const paying = ref(false)

/**
 * 正在申请退款的那个订单项（null = 弹层没开）。
 * 售后是**按订单项**申请的 —— 一笔订单里每个商品各是一条售后单。
 */
const refundItem = ref<OrderDetail['items'][number] | null>(null)
const refundOpen = ref(false)

/** 正在撤销申请的那个订单项（撤销要按**售后单编号**，见 `canCancelAfterSale`） */
const cancelRefundItem = ref<OrderDetail['items'][number] | null>(null)
const cancelRefundOpen = ref(false)
const cancelRefunding = ref(false)

/** 正在评价的那个订单项 —— 后端是按**订单项**提交评价的 */
const commentItem = ref<OrderDetail['items'][number] | null>(null)
const commentOpen = ref(false)

/**
 * 二维码弹层（电脑端微信扫码支付）。
 *
 * `qrContent` 是后端给的**裸 `code_url`**，不是图片地址也不是跳转地址 ——
 * 渲染与轮询都在 `QrPayDialog` 里，这里只负责开和关。
 */
const qrOpen = ref(false)
const qrContent = ref('')

/**
 * 弹层要看倒计时，所以把 `payExpireTime`（类型是 `number | string`）归一成毫秒数。
 * 认不出就是 `undefined` —— 弹层会当作「没有截止时间」，只靠轮询发现失效。
 */
const qrExpireAt = computed(() => toMillis(order.value?.payExpireTime))

/**
 * 微信公众号的 JSAPI 渠道码。这里**只用来分派「怎么把收银台唤起来」**，
 * 不代表前端判断渠道可用性 —— 用不用得上仍由后端的启用列表说了算（FR-038）。
 */
const WX_PUB_CHANNEL = 'wx_pub'

/** 后端的「跳转型」展示方式 —— `displayContent` 是收银台地址，必须真的跳过去 */
const PAY_DISPLAY_MODE_URL = 'url'

/** 后端的「二维码」展示方式 —— `displayContent` 是裸的 `code_url`，由 `QrPayDialog` 渲染 */
const PAY_DISPLAY_MODE_QR = 'qr_code'

/** 前端还没实现、但**不能装作没发生**的展示方式（表单提交、二维码图片链接） */
const UNSUPPORTED_DISPLAY_MODES: string[] = ['qr_code_url', 'form']

/**
 * 付款要用的公众号 openid。**只放内存不落盘**：
 * 一是后端 `/member/social-user/get` 已经能查到绑定关系（下次付款自动取到，不必重复授权），
 * 二是这个 openid 的绑定可能被同一手机的另一个账号「抢走」，落盘会留下陈旧值。
 */
const wxOpenid = ref('')

/**
 * 可选支付渠道。**可用性来自后端**（`/pay/channel/get-enable-code-list`），
 * 前端只做「编码 → 中文名」的展示映射，不自己判断哪个渠道能用。
 */
const channelOptions = ref<ChannelOption[]>([])
const selectedChannel = ref('')

/**
 * 是不是**还没确认收款的对公转账单**（008 / FR-200）。
 *
 * ⚠️ 判定**只认后端的 `offlinePay.waiting`** —— 它的定义是"是对公单 **且** 订单还没付"，
 * 与下面 `needsNoPay` 的条件（未支付 + 没有支付单）**正好重合**，这是本页最容易出错的地方（R47）。
 */
const offlineWaiting = computed(() => order.value?.offlinePay?.waiting === true)

const statusText = computed(() =>
  order.value
    ? offlineWaiting.value
      ? OFFLINE_WAITING_TEXT
      : orderStatusText(order.value.status)
    : '',
)
const isUnpaid = computed(() => order.value?.status === OrderStatus.UNPAID)

/**
 * 补开票弹层（US20 / FR-163）。
 *
 * 入口条件：**已支付 + 未取消 + 尚无申请** —— 三条缺一不可，
 * 与后端 `createInvoiceByMember` 的校验一一对应（后端仍是权威，这里只是不显示"点了必被拒"的按钮）。
 */
const invoiceApplyOpen = ref(false)
const canApplyInvoice = computed(() => {
  const o = order.value
  if (!o || o.invoice) return false
  // 与后端 `createInvoiceByMember` 的四条校验一一对应（后端仍是权威，这里只是不显示"点了必被拒"的按钮）：
  // 未支付 / 已取消 / 实付 0 元，三种情况都不给入口
  return o.status !== OrderStatus.UNPAID
    && o.status !== OrderStatus.CANCELED
    && o.payPrice > 0
})

/** 补开成功：**重新拉一次详情**，而不是本地把状态改掉 —— 状态一律以后端为准（FR-039） */
async function onInvoiceApplied() {
  await load()
}

/** 发票状态的说明文案（FR-176：每种状态都要如实说清现在能做什么） */
const invoiceStatusHint = computed(() => {
  const o = order.value
  const status = o?.invoice?.status
  if (status === InvoiceStatus.WAITING) {
    // 申请已提交但订单还没付款 —— 这时说"开具中"是骗人的（后台不会给未支付的订单开票）
    return o?.status === OrderStatus.UNPAID
      ? '订单付款后开具，开出后可在这里下载。'
      : '发票正在开具中，开出后可在这里下载。'
  }
  if (status === InvoiceStatus.ISSUED) return '发票已开出。'
  if (status === InvoiceStatus.CANCELED) return '订单已退款或取消，该开票申请已作废。'
  return ''
})
/**
 * 全额抵扣（`payPrice === 0`）的订单后端**不创建支付单**，`payOrderId` 为 null ——
 * 此时没有任何 id 可以提交支付，所以不给支付入口（FR-037）。
 *
 * ⚠️ **必须排除对公待收款单**（R47）：对公单**也没有在线支付单**，条件完全重合，
 * 不排除就会把"请打款"显示成"**无需支付**"（FR-201/202）。
 */
const needsNoPay = computed(() => isUnpaid.value && !order.value?.payOrderId && !offlineWaiting.value)

/** 能不能提交在线支付：要**有支付单**，且不是对公待收款单（对公单没有任何在线支付入口，FR-201） */
const canPayOnline = computed(
  () => isUnpaid.value && !!order.value?.payOrderId && !offlineWaiting.value,
)
// 详情的金额字段是**平铺**在返回对象上的，结构上即 OrderPrice
const amountConsistent = computed(() => (order.value ? reconcile(order.value) : true))
/** 券名后端未返回（`AppTradeOrderDetailRespVO` 只有 couponId/couponPrice） */
const usedCouponId = computed(() => order.value?.couponId ?? null)

/** 各状态下的一句话说明 —— 只说这一状态**确实意味着**的事，不做物流承诺 */
const STATUS_HINT: Record<number, string> = {
  [OrderStatus.UNPAID]: '请尽快完成支付，超时未支付订单将自动取消。',
  [OrderStatus.UNDELIVERED]: '已付款，商家正在准备发货。',
  [OrderStatus.DELIVERED]: '商品已发出，请留意物流信息。',
  [OrderStatus.COMPLETED]: '订单已完成，感谢购买。',
  [OrderStatus.CANCELED]: '订单已取消，商品库存与所用优惠券已释放。',
}

/**
 * 状态下面那句说明。⚠️ 对公待收款单**不能沿用**「请尽快完成支付，**超时未支付订单将自动取消**」
 * —— 它没有在线支付入口（FR-201），也**不会被超时自动取消**（FR-212），两句都是错的。
 */
const statusHint = computed(() => {
  if (offlineWaiting.value) return '请按下方收款账户完成转账，我们确认到账后即安排发货。'
  return order.value ? (STATUS_HINT[order.value.status] ?? '') : ''
})

/**
 * `sync = true` 让后端**主动同步支付渠道**再返回 —— 支付后确认状态就靠它
 * （`AppTradeOrderController.getOrderDetail` 的 `sync` 分支会调
 * `syncOrderPayStatusQuietly`）。
 */
/**
 * 拉该订单可用的支付渠道。
 *
 * 走两步：支付单里带 `appId` → 查这个应用启用哪些渠道码。
 * **刻意不把应用编号写死在前端** —— 那是租户相关的（线上 162 是 10），写死了换个租户就错。
 *
 * 只在「待支付且有支付单」时才拉：已付款的订单没有支付可言，白拉一次请求。
 * 失败不抛 —— 渠道拉不到只该让支付入口降级，不该把整个订单详情页带崩。
 */
async function loadChannels() {
  const payOrderId = order.value?.payOrderId
  if (!isUnpaid.value || !payOrderId) {
    channelOptions.value = []
    return
  }
  try {
    const payOrder = await getPayOrder(payOrderId)
    const codes = await listEnabledChannelCodes(payOrder.appId)
    channelOptions.value = buildChannelOptions(codes)
    // 默认选中第一个可用的；一个都没有就留空（按钮置灰）
    selectedChannel.value = channelOptions.value.find((c) => c.enabled)?.code ?? ''
  } catch {
    channelOptions.value = []
    selectedChannel.value = ''
  }
}

async function load(sync = false) {
  loading.value = true
  error.value = false
  try {
    order.value = await getOrderDetail(Number(props.id), sync)
    await loadChannels()
  } catch {
    error.value = true
    order.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 取付款要用的 openid：先看内存，再问后端（会员可能早就绑过公众号了，
 * 那样第二次付款就不用再走一次整页授权）。
 */
async function resolveWxOpenid(): Promise<string> {
  if (wxOpenid.value) return wxOpenid.value
  try {
    wxOpenid.value = (await getSocialUser())?.openid ?? ''
  } catch {
    // 查不到就当没绑过，去走一次授权 —— 比在这里把支付卡死要好
    wxOpenid.value = ''
  }
  return wxOpenid.value
}

/**
 * 微信公众号 JSAPI 支付。
 *
 * 没有 openid **不发支付请求**：后端 `WxPubPayClient` 拿不到 openid 直接拒。
 * 这时先记下「正在付哪一笔」，再整页跳微信授权；回跳后由
 * {@link handleWechatCallback} 接着把这一笔付掉。
 */
async function payByWechatJsapi(payOrderId: number) {
  // 渠道选择器已按环境过滤过 wx_pub，这里是兜底（UA 判断也可能失灵）
  if (!isWechatBrowser()) {
    message.value = '请在微信中打开本页后再使用微信支付'
    return
  }
  const openid = await resolveWxOpenid()
  if (!openid) {
    markWxPayPending(payOrderId)
    // 回跳地址**不带 query**：带上会把 ?login=1、上一次的 code 之类一起塞进授权回调
    const redirectUri = `${window.location.origin}${route.path}`
    redirectTo(await getSocialAuthRedirectUrl(redirectUri))
    return
  }

  const resp = await submitPay(payOrderId, WX_PUB_CHANNEL, { openid })
  const outcome = await invokeWxPay(parseWxJsapiParams(resp.displayContent))
  if (outcome === 'cancel') {
    message.value = '支付已取消'
  }
  await load(true)
}

/**
 * 处理微信授权回跳（URL 上带 `?code=&state=`）。
 *
 * ⚠️ 读到之后**立刻**把这两个参数从地址栏抹掉。这条 URL 事实上是可重放的
 * （后端 `SocialUserServiceImpl.authSocialUser` 会先命中自己库里已存的
 * `(type, code, state)`，绕过 justauth 的 state 校验），刷新一次就会再绑一次 ——
 * 而绑定会把这个 openid 从它原先绑的会员身上解绑。
 */
async function handleWechatCallback() {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  const state = typeof route.query.state === 'string' ? route.query.state : ''
  if (!code || !state) return

  const pending = readWxPayPending()
  // 先消费掉标记、再清地址栏，最后才去绑 —— 中途失败都不会留下会重复触发的残留
  clearWxPayPending()
  await router.replace({ path: route.path })

  try {
    wxOpenid.value = await bindSocialUser(code, state)
  } catch (e) {
    // 例如公众号还没配好（后端报「社交授权失败，原因是…」）
    message.value = (e as { message?: string })?.message || '微信授权失败，请重试'
    return
  }

  // 授权前正在付的那一笔，接着付。标记是「读后即删」，且再校验一次订单仍是待支付，
  // 所以这个自动提交**最多发生一次**。
  if (pending && pending === order.value?.payOrderId && isUnpaid.value) {
    selectedChannel.value = WX_PUB_CHANNEL
    await onPay()
  }
}

/**
 * 提交支付，并按后端给的 `displayMode` **把这次支付接下去**。
 *
 * 这是后端与前端之间的一条契约，**漏掉一个分支的表现就是「点了没反应」**：
 *
 *   · `url`      —— 跳收银台（支付宝电脑网站支付）。`displayContent` 就是收银台地址，
 *                   **拿到地址却不跳 = 用户眼里什么都没发生**（2026-10-09 线上实况）。
 *   · `qr_code`  —— 电脑端微信扫码支付（`wx_native`）。`displayContent` 是裸的
 *                   `code_url`，交给 `QrPayDialog` 渲染二维码并盯着支付单。
 *   · `qr_code_url` / `form` —— 仍未实现（前者要取图片链接、后者要拼表单）。
 *                   **必须明说**，不能静默：静默就是同一类 bug。
 *   · `app`      —— 唤起 App/微信内的收银台，本项目由 `wx_pub` 那条分支自己处理。
 *   · 不设（null）—— 例如 `mock`：渠道自己受理了，重拉详情确认状态即可。
 */
async function submitAndContinue(payOrderId: number) {
  // returnUrl：跳转型渠道付完把浏览器送回本页（不传会停在渠道自己的页面上）
  const resp = await submitPay(
    payOrderId,
    selectedChannel.value,
    undefined,
    window.location.href,
  )
  if (resp.displayMode === PAY_DISPLAY_MODE_URL) {
    redirectTo(resp.displayContent)
    // 页面即将离开，不必（也不该）再去拉详情
    return
  }
  if (resp.displayMode === PAY_DISPLAY_MODE_QR) {
    // 提交即成功（极少数渠道会这样）就不必开弹层了
    if (resp.status === PayOrderStatus.SUCCESS) {
      await load(true)
      return
    }
    // 拿不到二维码内容要**明说** —— 开一个空白弹层就是静默降级
    if (!resp.displayContent) {
      message.value = '二维码获取失败，请换一个渠道重试'
      return
    }
    qrContent.value = resp.displayContent
    qrOpen.value = true
    // 状态交给弹层的轮询驱动，这里**不** load(true)：此刻后端必然还是待支付
    return
  }
  if (UNSUPPORTED_DISPLAY_MODES.includes(resp.displayMode)) {
    message.value = '该渠道需要扫码完成支付，当前页面暂不支持，请换一个渠道'
    return
  }
  // 其余（渠道已受理，如 mock 的提交即成功）→ 重拉详情确认状态（FR-039）
  await load(true)
}

/**
 * 立即支付。
 *
 * ⚠️ **提交成功后不把订单标成已支付**：交易订单要等后端回调
 * `/app-api/trade/order/update-paid` 才推进。所以这里重新拉一次详情
 * （`sync=true` 顺便让后端同步一次渠道状态），**页面显示的状态始终是后端给的**
 * —— 后端说还是待支付，就还是待支付（FR-039）。
 */
async function onPay() {
  const payOrderId = order.value?.payOrderId
  // 契约要求：payOrderId 为空时不得提交（全额抵扣的订单没有支付单）
  if (!payOrderId) return
  // 没有可用渠道时不提交 —— 免得发一个渠道码为空、必定被后端拒的请求
  if (!selectedChannel.value) return
  message.value = ''
  paying.value = true
  try {
    if (selectedChannel.value === WX_PUB_CHANNEL) {
      await payByWechatJsapi(payOrderId)
    } else {
      await submitAndContinue(payOrderId)
    }
  } catch (e) {
    // 支付放弃/中断/失败：订单仍是「待支付」，入口留着让用户再发起（FR-038）
    message.value = (e as { message?: string })?.message || '支付失败，请稍后重试'
  } finally {
    paying.value = false
  }
}

// ========== 二维码支付（电脑端微信扫码） ==========

/**
 * 扫码支付成功。
 *
 * **不在这里把订单标成已支付** —— 弹层说的「支付单成功」不等于交易订单已回调，
 * 所以重新拉一次详情，并以 `sync=true` 让后端同步一次渠道状态（FR-039）。
 */
async function onQrPaid() {
  qrOpen.value = false
  await load(true)
}

/** 二维码失效（本地倒计时归零，或后端把支付单关掉）。同样让状态以后端为准。 */
async function onQrExpired() {
  qrOpen.value = false
  await load(true)
}

/**
 * 用户主动关掉弹层。
 *
 * **刻意不重拉详情** —— `load()` 会连带 `loadChannels()`，把 `selectedChannel`
 * 重置成「第一个可用渠道」，等于在用户背后改掉他刚选的渠道。
 */
function onQrClosed() {
  qrOpen.value = false
}

// ========== 评价（按订单项） ==========

/** 该项现在能不能评价。判定要**同时**看订单状态与两个层级的 commentStatus —— 见 `canCommentItem` */
function canComment(it: OrderDetail['items'][number]): boolean {
  return order.value ? canCommentItem(order.value, it) : false
}

function openComment(it: OrderDetail['items'][number]) {
  message.value = ''
  commentItem.value = it
  commentOpen.value = true
}

/**
 * 评价提交成功。
 *
 * **重拉详情**（`sync=true`）：后端已把该订单项标成已评价，入口应当随之消失 ——
 * 状态一律以后端为准，不做本地乐观更新。文案由弹层自己给（"审核通过后展示"，FR-078）。
 */
async function onCommentSubmitted() {
  await load(true)
}

function onCommentClosed() {
  commentOpen.value = false
  commentItem.value = null
}

// ========== 申请退款（按订单项） ==========

/** 该项的售后状态文案；未售后返回空串（不渲染标签） */
function afterSaleText(it: OrderDetail['items'][number]): string {
  return afterSaleItemStatusText(it.afterSaleStatus)
}

/** 该项现在能不能申请退款（判定与口径说明见 `utils/afterSale.ts`） */
function canRefund(it: OrderDetail['items'][number]): boolean {
  return order.value ? canApplyRefund(it, order.value.status) : false
}

function openRefund(it: OrderDetail['items'][number]) {
  message.value = ''
  refundItem.value = it
  refundOpen.value = true
}

// ========== 撤销退款申请 ==========

/** 该商品能不能撤销申请（判定与口径说明见 `utils/afterSale.ts`） */
function canCancelRefund(it: OrderDetail['items'][number]): boolean {
  return canCancelAfterSale(it)
}

function openCancelRefund(it: OrderDetail['items'][number]) {
  message.value = ''
  cancelRefundItem.value = it
  cancelRefundOpen.value = true
}

/**
 * 确认撤销。与「取消订单」同一套写法：**先关弹层再发请求**，
 * 成功/失败都以后端为准（撤销成功后订单项回到「未售后」，两个入口会跟着变）。
 */
async function confirmCancelRefund() {
  const target = cancelRefundItem.value
  cancelRefundOpen.value = false
  cancelRefundItem.value = null
  // 没有售后单编号就发不出撤销请求（这里是兜底，界面上本就不给入口）
  if (!target?.afterSaleId) return
  message.value = ''
  cancelRefunding.value = true
  try {
    await cancelAfterSale(target.afterSaleId)
    await load()
  } catch (e) {
    // 例如后端说「售后单状态不允许取消」（商家已收货待退款）—— 原样透出
    message.value = (e as { message?: string })?.message || '撤销失败，请稍后重试'
  } finally {
    cancelRefunding.value = false
  }
}

function onRefundSubmitted() {
  message.value = '退款申请已提交，商家审核后处理'
}

/**
 * 弹层关闭时重拉一次详情。
 *
 * 两个作用：① 申请成功后把订单项的售后状态换成后端给的值（**不做本地乐观更新**）；
 * ② 万一申请失败是因为「已在别处申请过」这类竞态，重拉能把界面拉回后端真相。
 * 提交与关闭是两条事件，都走这里 → **只发一次请求**。
 */
async function onRefundClosed() {
  refundOpen.value = false
  refundItem.value = null
  await load()
}

async function confirmCancel() {
  cancelOpen.value = false
  const target = order.value
  if (!target) return
  message.value = ''
  try {
    await cancelOrder(target.id)
    // 状态由后端改，重新拉一次详情 —— 不在这里把本地对象标成「已取消」
    await load()
  } catch (e) {
    message.value = (e as { message?: string })?.message || '取消订单失败，请稍后重试'
  }
}

onMounted(async () => {
  await load()
  // 全额抵扣（payPrice=0）的订单没有支付单，也就没有「提交支付」这一步可做。
  // 按契约走另一条路：不给支付入口、提示「本单无需支付」，并同步一次状态，
  // 确认后端到底把它算成什么（不臆断它会自动完成）。
  if (needsNoPay.value) await load(true)
  // 微信授权回跳的那一次：用 code 换 openid，并接着把刚才那笔付掉。
  // 放在 `load()` 之后 —— 续跑前要先知道订单还是不是待支付。
  await handleWechatCallback()
})
</script>

<template>
  <div class="account-section-page">
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <RouterLink to="/order">我的订单</RouterLink> /
    <span>订单详情</span>
  </div>

  <div class="account-wrap">
    <AccountSidebar />
    <!-- 与个人中心同一套两列布局：侧栏常驻，点进子页不丢菜单 -->
    <div class="account-main account-section-main account-order-detail">
      <div class="account-page-heading">
        <div><h1>订单详情</h1><p>核对商品、收货信息与订单进度。</p></div>
        <RouterLink to="/order" class="account-heading-link">返回订单列表 <span aria-hidden="true">→</span></RouterLink>
      </div>
      <LoadingState v-if="loading" class="account-content-card" :count="3" />

      <EmptyState
        v-else-if="error"
        class="account-content-card"
        mode="error"
        title="订单加载失败"
        desc="网络或服务暂时不可用，请稍后重试"
        action-text="重新加载"
        @action="load"
      />

      <template v-else-if="order">
        <p v-if="message" class="od-msg">{{ message }}</p>

        <!-- 状态区：大号状态 + 一句话说明；待支付额外给支付截止时间 -->
        <div class="ml-card od-status">
          <div class="od-status-row">
            <span class="od-status-text">{{ statusText }}</span>
            <!-- 对公待收款单：底层仍是「未支付」，只是说辞不同（008 / FR-200） -->
            <MlPill
              :status="order.status"
              :text="offlineWaiting ? OFFLINE_WAITING_TEXT : undefined"
            />
            <!--
              待收款单**只有「取消订单」这一个动作**（没有任何在线支付入口，FR-201），
              所以把它放在状态行末尾 —— 否则下面会剩一个只装一颗按钮的空白操作条。
              打在款前反悔走的就是它（008 spec 的 Edge Case）。
            -->
            <button
              v-if="offlineWaiting"
              class="btn-cart cancel-order od-cancel-inline"
              type="button"
              @click="cancelOpen = true"
            >
              取消订单
            </button>
          </div>
          <p class="ml-hint">{{ statusHint }}</p>
          <!-- ⚠️ 对公待收款单**没有支付截止**：它不参与超时自动取消（FR-212），
               写了「逾期将自动取消」就是骗客户 -->
          <p v-if="isUnpaid && !offlineWaiting" class="od-deadline">
            支付截止 {{ formatDateTime(order.payExpireTime) }}，逾期订单将自动取消
          </p>
        </div>

        <!-- 操作区：只有「待支付」有用户侧动作（FR-041b 不提供确认收货）。
             排在收货信息**之前** —— 待支付时用户要办的是「怎么付」 -->
        <!-- ⚠️ 待收款单**不渲染这一条**：它没有在线支付入口，唯一的动作「取消订单」
             已经放在上面的状态行里了（见那里的说明） -->
        <div v-if="isUnpaid && !offlineWaiting" class="od-actions">
          <!-- 渠道选择：可用性由后端给，前端只渲染。未开通的渠道置灰占位，
               后台配好后不用改代码就会变成可选 -->
          <div v-if="canPayOnline && channelOptions.length" class="pay-channels">
            <button
              v-for="c in channelOptions"
              :key="c.code"
              :data-channel="c.code"
              class="pay-channel"
              :class="{ 'is-disabled': !c.enabled, 'is-active': c.code === selectedChannel }"
              type="button"
              :disabled="!c.enabled"
              @click="selectedChannel = c.code"
            >
              {{ c.label }}
              <span v-if="c.comingSoon" class="pay-channel-soon">即将上线</span>
            </button>
          </div>

          <!-- 全额抵扣的订单后端没有支付单，没有可提交的 id（FR-037）。
               ⚠️ 对公待收款单**不落到这里**（`needsNoPay` 已排除它）—— 它要显示的是
               收款账户，不是"无需支付"（R47）。 -->
          <span v-if="needsNoPay" class="od-nopay">本单无需支付</span>
          <button class="btn-cart cancel-order" type="button" @click="cancelOpen = true">
            取消订单
          </button>
          <!-- 对公待收款单**没有任何在线支付入口**（FR-201：防重复付款） -->
          <button
            v-if="canPayOnline"
            id="payOrder"
            class="btn-buy"
            type="button"
            :disabled="paying || !selectedChannel || qrOpen"
            @click="onPay"
          >
            {{ paying ? '支付中…' : '立即支付' }}
          </button>
        </div>

        <!--
          对公转账「待收款」区块（008 / FR-201）。**收款账户只在这里给** ——
          结算响应里没有（契约 §2：客户还没下单时看不到完整账号）。
          ⚠️ 它由 `offlinePay.waiting` 驱动，而不是 `payOrderId` 是否为空 ——
          后者对公单与"全额抵扣"完全一样，分不出来（R47）。
        -->
        <div v-if="offlineWaiting" id="offlinePay" class="ml-card">
          <div class="ml-card-title">待收款 · 请对公转账</div>
          <div class="dl-table">
            <div class="dl-row">
              <span class="dl-name">收款户名</span>
              <span class="dl-val">{{ order.offlinePay?.accountName || '—' }}</span>
            </div>
            <div class="dl-row">
              <span class="dl-name">收款账号</span>
              <span class="dl-val">{{ order.offlinePay?.accountNo || '—' }}</span>
            </div>
            <div class="dl-row">
              <span class="dl-name">开户行</span>
              <span class="dl-val">{{ order.offlinePay?.bankName || '—' }}</span>
            </div>
          </div>
          <p v-if="order.offlinePay?.tip" class="od-text">{{ order.offlinePay.tip }}</p>
          <p class="ml-hint">
            转账后我们核对到账即安排发货。确认到账前订单不会自动取消，有问题请
            <RouterLink to="/support/contact">联系我们</RouterLink>。
          </p>
        </div>

        <!--
          发票（006）。四态见 FR-176：未支付 / 已取消且没申请 → 整块不渲染；
          已支付未申请 → 给申请入口；待开票 → 只显示状态、**不给下载入口**；已开票 → 信息 + 下载。
        -->
        <div v-if="order.invoice" class="ml-card invoice-card">
          <div class="ml-card-title">发票</div>
          <p class="od-text">状态：{{ invoiceStatusName(order.invoice.status) }}</p>
          <!--
            ⚠️ **已作废（订单退款 / 取消）时不再展示任何发票内容**（2026-10-15 所有者口径：
            退款了票就要撤回）—— 只说明状态，抬头 / 金额 / 税号一律不露面。
          -->
          <template v-if="order.invoice.status !== InvoiceStatus.CANCELED">
            <p class="od-text">类型：{{ order.invoice.typeName }}</p>
            <!-- 抬头类型（Q38）：个人抬头没有税号，标出来客户就不会以为"怎么没开全" -->
            <p class="od-text">抬头类型：{{ order.invoice.titleKindName }}</p>
            <p class="od-text">抬头：{{ order.invoice.titleName }}</p>
            <p class="od-text">金额：{{ formatYuan(order.invoice.amount) }}</p>
          </template>
          <p class="ml-hint">{{ invoiceStatusHint }}</p>
          <!--
            已开票才有下载入口。**入口旁常驻求助路径**（R10 的缓解措施）——
            那个文件域没有 CORS 头，前端读不到请求结果，所以"当场告知文件失效"做不到，
            只能保证"不静默、有地方问"。别把这行当成冗余删掉。
          -->
          <template v-if="order.invoice.status === InvoiceStatus.ISSUED && order.invoice.fileUrl">
            <a
              id="downloadInvoice"
              class="btn-cart"
              :href="order.invoice.fileUrl"
              target="_blank"
              rel="noopener"
            >下载发票</a>
            <p class="ml-hint">
              文件打不开？可能是已失效，请<RouterLink to="/support/contact">联系我们</RouterLink>或致电 010-88613986。
            </p>
          </template>
        </div>
        <div v-else-if="canApplyInvoice" class="ml-card">
          <div class="ml-card-title">发票</div>
          <p class="od-text">这笔订单还没有申请开票。需要发票可以现在提交，开出后在这里下载。</p>
          <button id="applyInvoice" class="btn-cart" type="button" @click="invoiceApplyOpen = true">申请开票</button>
        </div>

        <!--
          收货信息 —— **虚拟商品单（软件等）没有收货信息**（007）：后台不给它存 receiver*，
          整块会是一片空白。所以按"有没有收货人"决定渲不渲染（自提单有联系人，照常显示）。
        -->
        <div v-if="order.receiverName" class="ml-card">
          <div class="ml-card-title">收货信息</div>
          <div class="addr-line">
            <b>{{ order.receiverName }}</b>
            <span>{{ order.receiverMobile }}</span>
          </div>
          <p class="od-text">
            {{ order.receiverAreaName }} {{ order.receiverDetailAddress }}
          </p>
        </div>

        <!--
          买家留言：**下单时客户自己填的**（`trade_order.user_remark`），
          与后台那个「商家备注」不是一回事 —— 所以这里只读展示，不给改（FR-182 同理）。
          没填时整块不渲染 —— 空标题比没有更让人犯嘀咕。
        -->
        <div v-if="(order.userRemark ?? '').trim()" class="ml-card">
          <div class="ml-card-title">买家留言</div>
          <p class="od-text remark-text">
            {{ order.userRemark }}
          </p>
        </div>

        <!-- 商品明细：名称 / 规格 / 单价 / 数量都是下单时的快照 -->
        <div class="ml-card">
          <div class="ml-card-title">商品明细（{{ order.items.length }} 项）</div>
          <div v-for="it in order.items" :key="it.id" class="od-item">
            <div class="od-thumb">
              <img v-if="it.picUrl" :src="it.picUrl" :alt="it.spuName" />
              <span v-else class="ph">图</span>
            </div>
            <div class="od-info">
              <div class="od-name">{{ it.spuName }}</div>
              <div class="od-spec">
                <template v-for="p in it.properties ?? []" :key="p.valueName">
                  {{ p.propertyName }}：{{ p.valueName }}
                </template>
              </div>
              <!-- 售后入口/状态。**放在 .od-info 内部** —— 外面那层是 4 列 grid，
                   多塞一个直接子元素会撑坏整行布局 -->
              <div class="od-sale-actions">
                <span v-if="afterSaleText(it)" class="od-aftersale">{{ afterSaleText(it) }}</span>
                <!-- 两者互斥：可撤销时项状态是「售后中」，可申请时是「未售后」 -->
                <button
                  v-if="canCancelRefund(it)"
                  class="od-cancel-refund"
                  type="button"
                  :data-item="it.id"
                  @click="openCancelRefund(it)"
                >
                  撤销申请
                </button>
                <button
                  v-else-if="canRefund(it)"
                  class="od-refund"
                  type="button"
                  :data-item="it.id"
                  @click="openRefund(it)"
                >
                  申请退款
                </button>
                <!--
                  评价入口（FR-073）。与上面两个互不相干 —— 售后是订单项的另一个维度。
                  判定见 `canCommentItem`：要**同时**看订单状态、订单级与订单项级的
                  commentStatus，只看一个就会给出"点了必然被拒"的按钮。
                -->
                <button
                  v-if="canComment(it)"
                  class="od-comment"
                  type="button"
                  :data-item="it.id"
                  @click="openComment(it)"
                >
                  评价
                </button>
              </div>
            </div>
            <div class="od-price">{{ formatYuan(it.price) }}</div>
            <div class="od-count">×{{ it.count }}</div>
          </div>
        </div>

        <!-- 金额构成：促销优惠与优惠券抵扣分列两行，无积分行（FR-026g） -->
        <div class="od-summary-grid">
        <div class="ml-card">
          <div class="ml-card-title">金额构成</div>
          <div class="ml-amount-card">
            <MlAmountRow label="商品小计" :fen="order.totalPrice" />
            <MlAmountRow label="促销优惠" :fen="-order.discountPrice" cut />
            <MlAmountRow label="运费" :fen="order.deliveryPrice" />
            <MlAmountRow label="优惠券抵扣" :fen="-order.couponPrice" cut />
            <MlAmountRow label="应付总额" :fen="order.payPrice" total />
          </div>
          <p v-if="usedCouponId" class="ml-hint od-coupon">
            本单使用了优惠券（券编号 {{ usedCouponId }}），抵扣
            {{ formatYuan(order.couponPrice) }}
          </p>
          <p v-if="!amountConsistent" class="od-msg">
            金额明细与应付总额不一致，请联系客服核对
          </p>
        </div>

        <!-- 订单信息 -->
        <div class="ml-card">
          <div class="ml-card-title">订单信息</div>
          <div class="dl-table">
            <div class="dl-row">
              <span class="dl-name">订单号</span><span class="dl-val">{{ order.no }}</span>
            </div>
            <div class="dl-row">
              <span class="dl-name">下单时间</span>
              <span class="dl-val">{{ formatDateTime(order.createTime) }}</span>
            </div>
            <div class="dl-row">
              <span class="dl-name">支付时间</span>
              <span class="dl-val">
                {{ order.payTime ? formatDateTime(order.payTime) : '尚未支付' }}
              </span>
            </div>
          </div>
        </div>
        </div>
      </template>
    </div>
  </div>

  <!-- 补开票弹层（US20）：`v-if` 兜住订单未加载时的渲染 -->
  <InvoiceApplyDialog v-if="order" :open="invoiceApplyOpen" :order-id="order.id" @close="invoiceApplyOpen = false" @submitted="onInvoiceApplied" />

  <MlModal :open="cancelOpen" title="取消订单" @close="cancelOpen = false">
    <p class="ml-hint">
      确认取消订单 {{ order?.no }}？取消后商品库存与所用优惠券会释放，订单不可恢复。
    </p>
    <template #foot>
      <button id="dismissCancel" class="btn-cart" type="button" @click="cancelOpen = false">
        再想想
      </button>
      <button id="confirmCancel" class="btn-buy" type="button" @click="confirmCancel">
        确认取消
      </button>
    </template>
  </MlModal>

  <!-- 撤销退款申请：先确认再发请求（照「取消订单」那套） -->
  <MlModal :open="cancelRefundOpen" title="撤销退款申请" @close="cancelRefundOpen = false">
    <p class="ml-hint">
      确认撤销「{{ cancelRefundItem?.spuName }}」的退款申请？撤销后该商品需重新申请，商家将不再受理这一条。
    </p>
    <template #foot>
      <button
        id="dismissCancelRefund"
        class="btn-cart"
        type="button"
        @click="cancelRefundOpen = false"
      >
        再想想
      </button>
      <button
        id="confirmCancelRefund"
        class="btn-buy"
        type="button"
        :disabled="cancelRefunding"
        @click="confirmCancelRefund"
      >
        {{ cancelRefunding ? '撤销中…' : '确认撤销' }}
      </button>
    </template>
  </MlModal>

  <!-- 申请退款（按单个商品）。提交后等商家审核，状态由后端给 -->
  <RefundApplyDialog
    :open="refundOpen"
    :item="refundItem"
    :order-status="order?.status ?? -1"
    @close="onRefundClosed"
    @submitted="onRefundSubmitted"
  />

  <!-- 写评价（按订单项）。提交成功后由弹层先告知"审核通过后展示"，再回这里重拉详情 -->
  <CommentCreateDialog
    :open="commentOpen"
    :item="commentItem"
    @close="onCommentClosed"
    @submitted="onCommentSubmitted"
  />

  <!-- 电脑端微信扫码支付（`wx_native`）。渲染二维码 + 盯支付单，都由弹层自己做 -->
  <QrPayDialog
    :open="qrOpen"
    :code-url="qrContent"
    :pay-order-id="order?.payOrderId ?? 0"
    :expire-at="qrExpireAt"
    :order-no="order?.no"
    @close="onQrClosed"
    @paid="onQrPaid"
    @expired="onQrExpired"
  />
  </div>
</template>

<style scoped>
.account-order-detail > .ml-card, .od-summary-grid > .ml-card { padding: 28px 32px; margin: 0; border-radius: 14px; min-width: 0; }
.account-order-detail .ml-card-title { margin-bottom: 20px; font-size: 16px; }
.od-summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; align-items: start; }
.account-order-detail .od-status { background: linear-gradient(110deg, #FFFFFF, #F0F4FB); }
.od-status .ml-hint { margin-top: 14px; line-height: 1.8; }
.account-order-detail > .empty-state { min-height: 320px; }
.od-msg {
  color: var(--ml-orange);
  font-size: 13px;
  margin: 12px 0;
}
.od-status-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
/* 状态行末尾的那颗「取消订单」（仅待收款单）：推到最右，与原来的操作条视觉一致 */
.od-cancel-inline {
  margin-left: auto;
  width: auto;
  font-size: 13px;
  padding: 10px 20px;
}
.od-status-text {
  font-size: 22px;
  font-weight: 700;
  color: var(--ml-text);
}
.od-deadline {
  margin-top: 8px;
  font-size: 13px;
  color: var(--ml-orange);
}
.addr-line {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.od-text {
  font-size: 14px;
  color: var(--ml-text-sub);
  margin-top: 10px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
/* 买家留言是两行的输入框，客户敲的换行要原样保留（长串撑破的问题 .od-text 已处理） */
.remark-text { white-space: pre-wrap; }
.od-item {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto auto;
  gap: 20px;
  align-items: center;
  padding: 20px 0;
  border-bottom: 1px solid var(--ml-border);
}
.od-item:last-child {
  border-bottom: 0;
}
.od-thumb {
  width: 64px;
  height: 64px;
  border-radius: var(--ml-radius-field);
  overflow: hidden;
  background: var(--ml-bg-card);
}
.od-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.od-name {
  font-size: 14px;
  color: var(--ml-text);
  font-weight: 600;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.od-info { min-width: 0; }
.od-thumb .ph { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--ml-text-ph); font-size: 12px; }
.od-spec {
  font-size: 12px;
  color: var(--ml-text-ph);
  margin-top: 4px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
/* 售后入口/状态 —— 放在 .od-info 里（外层是 4 列 grid，不能加直接子元素） */
.od-sale-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
}
.od-aftersale {
  font-size: 12px;
  color: var(--ml-primary);
}
.od-refund,
.od-cancel-refund {
  padding: 2px 10px;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-pill);
  background: var(--ml-bg-card);
  color: var(--ml-text-sub);
  font-size: 12px;
  cursor: pointer;
}
.od-refund:hover,
.od-cancel-refund:hover {
  border-color: var(--ml-primary);
  color: var(--ml-primary);
}
.od-price,
.od-count {
  font-size: 14px;
  color: var(--ml-text-sub);
  font-variant-numeric: tabular-nums;
}
.od-coupon {
  margin-top: 10px;
}
.dl-table .dl-row {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  padding: 14px 16px;
  gap: 16px;
}
.dl-val { overflow-wrap: anywhere; line-height: 1.8; font-size: 13px; }
.dl-name { font-size: 12px; }
.account-order-detail .dl-table { border-radius: 10px; }
.od-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 24px 32px;
  background: #FFFFFF;
  border: 1px solid var(--ml-border);
  border-radius: 14px;
}
.od-actions > .btn-cart, .od-actions > .btn-buy { width: auto; font-size: 13px; padding: 10px 20px; }
.od-actions > .btn-buy { border: 0; font-family: inherit; cursor: pointer; }
/* 渠道选择在窄屏下另起一行，避免把两个按钮挤变形 */
.pay-channels {
  display: flex;
  gap: 8px;
  margin-right: auto;
  flex-wrap: wrap;
}
.pay-channel {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid #e3e9f4;
  border-radius: 10px;
  background: #ffffff;
  font-size: 14px;
  color: #16233f;
  cursor: pointer;
}
.pay-channel.is-active {
  border-color: #2e7cd6;
  color: #2e7cd6;
  background: #f0f4fb;
}
.pay-channel.is-disabled {
  color: #b4c0d8;
  background: #f5f8ff;
  cursor: not-allowed;
}
/* 编码没有中文名时回落显示编码本身，字号小一点免得撑破按钮 */
.pay-channel-soon {
  font-size: 12px;
  color: #b4c0d8;
}
.od-nopay {
  font-size: 14px;
  color: var(--ml-text-sub);
}
@media (max-width: 768px) {
  .od-summary-grid { grid-template-columns: 1fr; gap: 16px; }
  .account-order-detail > .ml-card, .od-summary-grid > .ml-card { padding: 24px; }
  .pay-channels {
    width: 100%;
    margin-right: 0;
  }
}
@media (max-width: 768px) {
  .od-item {
    grid-template-columns: 56px minmax(0, 1fr) auto;
    gap: 10px 14px;
  }
  .od-thumb { grid-row: 1 / 3; width: 56px; height: 56px; }
  .od-info { grid-column: 2 / 4; }
  .od-price { grid-column: 2; }
  .od-count { grid-column: 3; }
}
@media (max-width: 480px) {
  .account-order-detail > .ml-card, .od-summary-grid > .ml-card, .od-actions { padding: 22px 20px; }
  .od-name, .od-text { font-size: 13px; }
  .dl-table .dl-row { grid-template-columns: 64px minmax(0, 1fr); padding: 12px; gap: 10px; }
  .pay-channel { font-size: 12px; padding: 8px 10px; }
}
</style>
