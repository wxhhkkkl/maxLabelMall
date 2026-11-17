<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listAddress } from '@/api/address'
import { bindSocialUser } from '@/api/social'
import {
  chooseRepairOrderOffline,
  createCustomerServiceRepair,
  createCustomerServiceTicket,
  getCustomerServiceRepair,
  getCustomerServiceRepairPage,
  getCustomerServiceTicketPage,
  markRepairShipped,
  payRepairOrderOnline,
  type CustomerServiceOfflinePayInfo,
  type CustomerServiceRepair,
  type CustomerServiceTicket,
} from '@/api/customerService'
import AccountSidebar from '@/components/AccountSidebar.vue'
import CustomerServiceImageUploader from '@/components/CustomerServiceImageUploader.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import MemberPayDialog from '@/components/MemberPayDialog.vue'
import MlModal from '@/components/base/MlModal.vue'
import { useToasts } from '@/components/base/useToasts'
import { useUserStore } from '@/store/user'
import type { Address } from '@/types'
import { formatDateTime } from '@/utils/time'
import { clearWxPayPending, readWxPayPending } from '@/utils/weixin'

/**
 * 客户服务（021 / US70 + US71）
 *
 * 投诉咨询：提交 → 我的记录 → 客服处理说明可见。
 * 在线维修：一台设备一条，提交后可在个人中心跟踪状态。
 */
const userStore = useUserStore()
const toast = useToasts()
const route = useRoute()
const router = useRouter()

const tabFromQuery = (): 'ticket' | 'repair' => route.query.tab === 'repair' ? 'repair' : 'ticket'
const activeTab = ref<'ticket' | 'repair'>(tabFromQuery())

/** 页签与侧栏入口（?tab=ticket / ?tab=repair）保持同步 */
function setTab(tab: 'ticket' | 'repair') {
  activeTab.value = tab
  router.replace({ path: route.path, query: { ...route.query, tab } })
}
watch(() => route.query.tab, () => {
  activeTab.value = tabFromQuery()
})

// ==================== 投诉咨询 ====================
const tickets = ref<CustomerServiceTicket[]>([])
const ticketTotal = ref(0)
const ticketLoading = ref(true)
const ticketError = ref(false)
const ticketSubmitting = ref(false)
const ticketFormOpen = ref(false)
const ticketForm = reactive({
  type: 1,
  content: '',
  contactMobile: userStore.member?.mobile ?? '',
  images: [] as string[],
})

async function loadTickets() {
  ticketLoading.value = true
  ticketError.value = false
  try {
    const data = await getCustomerServiceTicketPage({ pageNo: 1, pageSize: 20 })
    tickets.value = data.list
    ticketTotal.value = data.total
  } catch {
    ticketError.value = true
    tickets.value = []
    ticketTotal.value = 0
  } finally {
    ticketLoading.value = false
  }
}

async function submitTicket() {
  if (ticketSubmitting.value) return
  if (!ticketForm.content.trim()) {
    toast.error('请填写投诉或咨询内容')
    return
  }
  if (!/^1\d{10}$/.test(ticketForm.contactMobile)) {
    toast.error('请填写 11 位联系手机号')
    return
  }
  ticketSubmitting.value = true
  try {
    await createCustomerServiceTicket({
      type: ticketForm.type,
      content: ticketForm.content.trim(),
      contactMobile: ticketForm.contactMobile,
      images: ticketForm.images.length ? [...ticketForm.images] : undefined,
    })
    toast.success('已提交，客服会尽快处理')
    ticketForm.content = ''
    ticketForm.images = []
    ticketFormOpen.value = false
    await loadTickets()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '提交失败，请稍后重试')
  } finally {
    ticketSubmitting.value = false
  }
}

// ==================== 在线维修 ====================
const repairs = ref<CustomerServiceRepair[]>([])
const repairTotal = ref(0)
const repairLoading = ref(true)
const repairError = ref(false)
const repairSubmitting = ref(false)
const repairFormOpen = ref(false)
const addresses = ref<Address[]>([])
const selectedAddressId = ref<number>()
const repairDetails = ref<Record<number, CustomerServiceRepair>>({})
const payDialogRef = ref()
const offlineInfos = ref<Record<number, CustomerServiceOfflinePayInfo>>({})
const shipForms = reactive<Record<number, { company: string; no: string; saving: boolean }>>({})

function ensureShipForm(id: number) {
  if (!shipForms[id]) shipForms[id] = { company: '', no: '', saving: false }
}
const repairForm = reactive({
  deviceModel: '',
  faultDesc: '',
  contactMobile: userStore.member?.mobile ?? '',
  receiverName: '',
  receiverMobile: '',
  receiverArea: '',
  receiverDetail: '',
  images: [] as string[],
})

async function loadRepairs() {
  repairLoading.value = true
  repairError.value = false
  try {
    const data = await getCustomerServiceRepairPage({ pageNo: 1, pageSize: 20 })
    repairs.value = data.list
    repairTotal.value = data.total
    data.list.forEach((item) => ensureShipForm(item.id))
    // 每条记录默认展开详情：批量拉取，单条失败不影响列表
    const details = await Promise.all(
      data.list.map((item) => getCustomerServiceRepair(item.id).catch(() => null)),
    )
    const detailMap: Record<number, CustomerServiceRepair> = {}
    details.forEach((detail) => {
      if (detail) detailMap[detail.id] = detail
    })
    repairDetails.value = detailMap
  } catch {
    repairError.value = true
    repairs.value = []
    repairTotal.value = 0
  } finally {
    repairLoading.value = false
  }
}

async function loadAddresses() {
  try {
    addresses.value = await listAddress()
  } catch {
    addresses.value = []
  }
}

async function refreshRepairDetail(id: number) {
  try {
    const detail = await getCustomerServiceRepair(id)
    repairDetails.value = { ...repairDetails.value, [id]: detail }
  } catch {
    // 详情刷新失败保留列表原样，下一次加载会重试
  }
}

async function payInline(item: CustomerServiceRepair) {
  if (!item.repairOrderId) return
  try {
    const data = await payRepairOrderOnline(item.repairOrderId)
    payDialogRef.value?.open(data.payOrderId)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '发起支付失败')
  }
}

async function chooseOfflineInline(item: CustomerServiceRepair) {
  if (!item.repairOrderId) return
  try {
    const data = await chooseRepairOrderOffline(item.repairOrderId)
    offlineInfos.value = { ...offlineInfos.value, [item.repairOrderId]: data.offline }
    await refreshRepairDetail(item.id)
    toast.success('已选择对公转账，请按账户信息完成打款')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  }
}

function offlineInfoOf(itemId: number): CustomerServiceOfflinePayInfo | undefined {
  const orderId = repairDetails.value[itemId]?.repairOrderId
  return orderId != null ? offlineInfos.value[orderId] : undefined
}

async function onPaid() {
  await loadRepairs()
}

async function submitShip(item: CustomerServiceRepair) {
  const form = shipForms[item.id]
  if (!form || !form.no.trim()) {
    toast.error('请填写寄出运单号')
    return
  }
  form.saving = true
  try {
    await markRepairShipped({
      id: item.id,
      shipCompany: form.company.trim() || undefined,
      shipNo: form.no.trim(),
    })
    toast.success('已标记寄出')
    await refreshRepairDetail(item.id)
    await loadRepairs()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    form.saving = false
  }
}

function onAddressChange() {
  const address = addresses.value.find((item) => item.id === selectedAddressId.value)
  if (!address) return
  repairForm.receiverName = address.name
  repairForm.receiverMobile = address.mobile
  repairForm.receiverArea = address.areaName
  repairForm.receiverDetail = address.detailAddress
}

async function submitRepair() {
  if (repairSubmitting.value) return
  if (!repairForm.deviceModel.trim()) {
    toast.error('请填写设备型号')
    return
  }
  if (!repairForm.faultDesc.trim()) {
    toast.error('请填写故障描述')
    return
  }
  if (!/^1\d{10}$/.test(repairForm.contactMobile)) {
    toast.error('请填写 11 位联系手机号')
    return
  }
  if (!repairForm.receiverName.trim() || !repairForm.receiverMobile.trim()
    || !repairForm.receiverArea.trim() || !repairForm.receiverDetail.trim()) {
    toast.error('请填写返修收件信息')
    return
  }
  repairSubmitting.value = true
  try {
    await createCustomerServiceRepair({
      deviceModel: repairForm.deviceModel.trim(),
      faultDesc: repairForm.faultDesc.trim(),
      contactMobile: repairForm.contactMobile,
      receiverName: repairForm.receiverName.trim(),
      receiverMobile: repairForm.receiverMobile.trim(),
      receiverArea: repairForm.receiverArea.trim(),
      receiverDetail: repairForm.receiverDetail.trim(),
      images: repairForm.images.length ? [...repairForm.images] : undefined,
    })
    toast.success('已提交维修申请，客服会电话联系你')
    repairForm.deviceModel = ''
    repairForm.faultDesc = ''
    repairForm.images = []
    repairFormOpen.value = false
    await loadRepairs()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '提交失败，请稍后重试')
  } finally {
    repairSubmitting.value = false
  }
}

/** 微信公众号授权回跳：绑定 openid 后，接回授权前正在支付的那笔维修单 */
async function handleWechatCallback() {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  const state = typeof route.query.state === 'string' ? route.query.state : ''
  if (!code || !state) return
  const pending = readWxPayPending()
  clearWxPayPending()
  await router.replace({ path: route.path })
  try {
    await bindSocialUser(code, state)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '微信授权失败，请重试')
    return
  }
  if (pending) {
    setTab('repair')
    payDialogRef.value?.open(pending)
  }
}

onMounted(async () => {
  await handleWechatCallback()
  if (userStore.isLogin && !userStore.member) {
    await userStore.loadMember()
  }
  const mobile = userStore.member?.mobile ?? ''
  if (!ticketForm.contactMobile) ticketForm.contactMobile = mobile
  if (!repairForm.contactMobile) repairForm.contactMobile = mobile
  await Promise.all([loadTickets(), loadRepairs(), loadAddresses()])
})
</script>

<template>
  <div class="account-section-page">
    <div class="crumbs">
      <RouterLink to="/">首页</RouterLink> /
      <RouterLink to="/account">个人中心</RouterLink> / <span>客户服务</span>
    </div>

    <div class="account-wrap">
      <AccountSidebar />
      <div class="account-main account-section-main">
        <div class="account-page-heading">
          <div>
            <h1>客户服务</h1>
            <p>提交投诉咨询或在线维修，随时查看处理进度。</p>
          </div>
        </div>

        <!-- 投诉咨询 -->
        <template v-if="activeTab === 'ticket'">
          <div class="account-content-card">
            <div class="account-section-heading">
              <h2>投诉咨询</h2>
              <div class="cs-head-actions">
                <span v-if="!ticketLoading && !ticketError">共 {{ ticketTotal }} 条</span>
                <button id="ticketCreateOpen" class="account-primary-button" type="button" @click="ticketFormOpen = true">
                  提交投诉咨询
                </button>
              </div>
            </div>

            <LoadingState v-if="ticketLoading" :count="2" />
            <EmptyState
              v-else-if="ticketError"
              mode="error"
              title="记录加载失败"
              desc="网络或服务暂时不可用"
              action-text="重新加载"
              @action="loadTickets"
            />
            <EmptyState
              v-else-if="!tickets.length"
              icon="💬"
              title="还没有提交过投诉咨询"
              desc="点击右上角「提交投诉咨询」发起"
            />
            <div v-else class="cs-list">
              <article v-for="item in tickets" :key="item.id" class="ml-card cs-record">
                <div class="cs-record-head">
                  <span class="ml-pill">{{ item.typeName }}</span>
                  <span class="ml-pill" :class="item.status === 10 ? 'is-done' : ''">{{ item.statusName }}</span>
                  <time>{{ formatDateTime(item.createTime) }}</time>
                </div>
                <p class="cs-content">{{ item.content }}</p>
                <div v-if="item.images?.length" class="cs-record-images">
                  <img v-for="url in item.images" :key="url" :src="url" alt="" />
                </div>
                <div v-if="item.status === 10" class="cs-reply">
                  <b>客服回复</b>
                  <p>{{ item.handleRemark }}</p>
                  <small v-if="item.handleTime">{{ formatDateTime(item.handleTime) }}</small>
                </div>
              </article>
            </div>
          </div>
        </template>

        <!-- 在线维修 -->
        <template v-else>
          <div class="account-content-card">
            <div class="account-section-heading">
              <h2>在线维修</h2>
              <div class="cs-head-actions">
                <span v-if="!repairLoading && !repairError">共 {{ repairTotal }} 条</span>
                <button id="repairCreateOpen" class="account-primary-button" type="button" @click="repairFormOpen = true">
                  提交维修申请
                </button>
              </div>
            </div>

            <LoadingState v-if="repairLoading" :count="2" />
            <EmptyState
              v-else-if="repairError"
              mode="error"
              title="维修记录加载失败"
              desc="网络或服务暂时不可用"
              action-text="重新加载"
              @action="loadRepairs"
            />
            <EmptyState
              v-else-if="!repairs.length"
              icon="🔧"
              title="还没有提交过维修申请"
              desc="点击右上角「提交维修申请」发起"
            />
            <div v-else class="cs-list">
              <article v-for="item in repairs" :key="item.id" class="ml-card cs-record">
                <div class="cs-record-head">
                  <b class="cs-device">{{ item.deviceModel }}</b>
                  <span class="ml-pill" :class="item.status === 50 ? 'is-done' : ''">{{ item.statusName }}</span>
                  <span
                    v-if="item.repairOrderId"
                    class="ml-pill"
                    :class="item.refundStatus === 20 || item.completeTime ? 'is-done' : ''"
                  >
                    {{ item.refundStatus === 20 ? '已取消' : item.completeTime ? '已完成' : item.payStatusName }}
                  </span>
                  <time>{{ formatDateTime(item.createTime) }}</time>
                </div>
                <p class="cs-content">{{ item.faultDesc }}</p>
                <div v-if="item.contactResult" class="cs-reply">
                  <b>客服联系</b>
                  <p>{{ item.contactResult }}</p>
                </div>
                <div v-if="repairDetails[item.id]" class="cs-inline-detail">
                  <div class="cs-detail-grid">
                    <span>设备型号</span><b>{{ repairDetails[item.id].deviceModel }}</b>
                    <span>当前状态</span><b>{{ repairDetails[item.id].statusName }}</b>
                    <span>联系手机号</span><span>{{ repairDetails[item.id].contactMobile }}</span>
                    <span>返修收件</span>
                    <span>{{ repairDetails[item.id].receiverName }} {{ repairDetails[item.id].receiverMobile }} · {{ repairDetails[item.id].receiverArea }} {{ repairDetails[item.id].receiverDetail }}</span>
                    <span>故障描述</span><span class="cs-pre">{{ repairDetails[item.id].faultDesc }}</span>
                    <template v-if="repairDetails[item.id].contactResult">
                      <span>客服联系</span><span>{{ repairDetails[item.id].contactResult }}</span>
                    </template>
                    <template v-if="repairDetails[item.id].shipNo">
                      <span>寄出信息</span><span>{{ repairDetails[item.id].shipCompany }} {{ repairDetails[item.id].shipNo }}</span>
                    </template>
                  </div>

                  <div v-if="repairDetails[item.id].repairOrderId" class="cs-order-card">
                    <div class="cs-order-head">
                      <b>维修订单</b>
                      <span
                        class="ml-pill"
                        :class="repairDetails[item.id].refundStatus === 20
                          || repairDetails[item.id].completeTime
                          || repairDetails[item.id].payStatus === 20
                          || repairDetails[item.id].payStatus === 30 ? 'is-done' : ''"
                      >
                        {{ repairDetails[item.id].refundStatus === 20
                          ? '已取消'
                          : repairDetails[item.id].completeTime ? '已完成' : repairDetails[item.id].payStatusName }}
                      </span>
                    </div>
                    <div class="cs-order-row"><span>维修费</span><b>¥{{ ((repairDetails[item.id].repairFee ?? 0) / 100).toFixed(2) }}</b></div>
                    <div class="cs-order-row"><span>寄回运费</span><b>¥{{ ((repairDetails[item.id].returnFreight ?? 0) / 100).toFixed(2) }}</b></div>
                    <div class="cs-order-row cs-order-total"><span>应付合计</span><b>¥{{ ((repairDetails[item.id].payableAmount ?? 0) / 100).toFixed(2) }}</b></div>
                    <p v-if="repairDetails[item.id].feeRemark" class="cs-order-remark">{{ repairDetails[item.id].feeRemark }}</p>

                    <div v-if="repairDetails[item.id].payStatus === 0" class="cs-pay-actions">
                      <button class="btn-primary" type="button" @click="payInline(item)">在线支付</button>
                      <button class="btn-cart" type="button" @click="chooseOfflineInline(item)">对公转账</button>
                    </div>
                    <div v-else-if="repairDetails[item.id].payStatus === 10" class="cs-offline-info">
                      <p>待确认收款，请按以下信息完成转账：</p>
                      <p v-if="offlineInfoOf(item.id)">
                        {{ offlineInfoOf(item.id)?.accountName }} ·
                        {{ offlineInfoOf(item.id)?.accountNo }} ·
                        {{ offlineInfoOf(item.id)?.bankName }}
                      </p>
                      <p v-else>请按客服提供的账户完成打款，到账后客服会确认。</p>
                      <small v-if="offlineInfoOf(item.id)?.tip">
                        {{ offlineInfoOf(item.id)?.tip }}
                      </small>
                      <button class="cs-link" type="button" @click="chooseOfflineInline(item)">查看收款信息</button>
                    </div>
                  </div>

                  <div v-if="repairDetails[item.id].refundStatus !== 20" class="cs-address-card">
                    <b>回收邮寄地址</b>
                    <template v-if="repairDetails[item.id].returnAddress">
                      <p>
                        {{ repairDetails[item.id].returnAddress?.receiverName }} {{ repairDetails[item.id].returnAddress?.receiverMobile }}<br />
                        {{ repairDetails[item.id].returnAddress?.areaName }} {{ repairDetails[item.id].returnAddress?.detailAddress }}
                      </p>
                      <small v-if="repairDetails[item.id].returnAddress?.shipRemark">{{ repairDetails[item.id].returnAddress?.shipRemark }}</small>
                    </template>
                    <p v-else class="cs-missing">地址尚未配置，请联系客服获取邮寄地址。</p>
                  </div>

                  <div
                    v-if="repairDetails[item.id].refundStatus !== 20
                      && repairDetails[item.id].status === 20
                      && (repairDetails[item.id].payStatus === 20 || repairDetails[item.id].payStatus === 30)"
                    class="cs-ship-form"
                  >
                    <b>已支付，寄出后请回填运单号</b>
                    <div class="cs-ship-row">
                      <input v-model="shipForms[item.id].company" class="ml-input" maxlength="50" placeholder="快递公司（选填）" />
                      <input
                        :id="`repairShipNo-${item.id}`"
                        v-model="shipForms[item.id].no"
                        class="ml-input"
                        maxlength="64"
                        placeholder="运单号（必填）"
                      />
                      <button class="btn-primary" type="button" :disabled="shipForms[item.id].saving" @click="submitShip(item)">
                        {{ shipForms[item.id].saving ? '提交中…' : '标记已寄出' }}
                      </button>
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </div>
        </template>
      </div>
    </div>

    <MlModal :open="ticketFormOpen" title="提交投诉咨询" @close="ticketFormOpen = false">
      <form class="cs-form" @submit.prevent="submitTicket">
        <div class="cs-field">
          <span class="cs-label">诉求类型 <i>*</i></span>
          <div class="cs-type-row">
            <button
              type="button"
              class="cs-type"
              :class="{ 'is-active': ticketForm.type === 1 }"
              @click="ticketForm.type = 1"
            >投诉</button>
            <button
              type="button"
              class="cs-type"
              :class="{ 'is-active': ticketForm.type === 2 }"
              @click="ticketForm.type = 2"
            >咨询</button>
          </div>
        </div>
        <label class="cs-field">
          <span class="cs-label">内容 <i>*</i></span>
          <textarea
            v-model="ticketForm.content"
            class="ml-textarea"
            maxlength="1000"
            placeholder="请描述遇到的问题或想咨询的内容"
          />
        </label>
        <label class="cs-field">
          <span class="cs-label">联系手机号 <i>*</i></span>
          <input v-model="ticketForm.contactMobile" class="ml-input" maxlength="11" placeholder="11 位手机号" />
        </label>
        <div class="cs-field">
          <span class="cs-label">图片（选填，最多 5 张）</span>
          <CustomerServiceImageUploader v-model="ticketForm.images" />
        </div>
      </form>
      <template #foot>
        <button class="btn-cart" type="button" @click="ticketFormOpen = false">取消</button>
        <button id="ticketSubmit" class="account-primary-button" type="button" :disabled="ticketSubmitting" @click="submitTicket">
          {{ ticketSubmitting ? '提交中…' : '提交' }}
        </button>
      </template>
    </MlModal>

    <MlModal :open="repairFormOpen" title="提交维修申请" @close="repairFormOpen = false">
      <form class="cs-form" @submit.prevent="submitRepair">
        <label class="cs-field">
          <span class="cs-label">设备型号 <i>*</i></span>
          <input id="repairDeviceModel" v-model="repairForm.deviceModel" class="ml-input" maxlength="100" placeholder="如 Zebra ZT411" />
        </label>
        <label class="cs-field">
          <span class="cs-label">故障描述 <i>*</i></span>
          <textarea
            id="repairFaultDesc"
            v-model="repairForm.faultDesc"
            class="ml-textarea"
            maxlength="1000"
            placeholder="请描述故障现象、报错信息与已尝试的处理"
          />
        </label>
        <label class="cs-field">
          <span class="cs-label">联系手机号 <i>*</i></span>
          <input id="repairContactMobile" v-model="repairForm.contactMobile" class="ml-input" maxlength="11" placeholder="11 位手机号" />
        </label>
        <div class="cs-field">
          <span class="cs-label">返修收件信息 <i>*</i></span>
          <select v-if="addresses.length" v-model="selectedAddressId" class="ml-select" @change="onAddressChange">
            <option :value="undefined">从收货地址簿带入（可选）</option>
            <option v-for="address in addresses" :key="address.id" :value="address.id">
              {{ address.name }} {{ address.mobile }} {{ address.areaName }}{{ address.detailAddress }}
            </option>
          </select>
          <div class="cs-receiver-grid">
            <input id="repairReceiverName" v-model="repairForm.receiverName" class="ml-input" maxlength="50" placeholder="收件人" />
            <input id="repairReceiverMobile" v-model="repairForm.receiverMobile" class="ml-input" maxlength="20" placeholder="联系电话" />
            <input id="repairReceiverArea" v-model="repairForm.receiverArea" class="ml-input" maxlength="100" placeholder="省市区" />
            <input id="repairReceiverDetail" v-model="repairForm.receiverDetail" class="ml-input" maxlength="255" placeholder="详细地址" />
          </div>
        </div>
        <div class="cs-field">
          <span class="cs-label">图片（选填，最多 5 张）</span>
          <CustomerServiceImageUploader v-model="repairForm.images" />
        </div>
      </form>
      <template #foot>
        <button class="btn-cart" type="button" @click="repairFormOpen = false">取消</button>
        <button id="repairSubmit" class="account-primary-button" type="button" :disabled="repairSubmitting" @click="submitRepair">
          {{ repairSubmitting ? '提交中…' : '提交维修申请' }}
        </button>
      </template>
    </MlModal>

    <MemberPayDialog ref="payDialogRef" @paid="onPaid" />
  </div>
</template>

<style scoped>
.cs-head-actions { display: flex; align-items: center; gap: 12px; }
.cs-head-actions span { color: #8a97b5; font-size: 12px; }
.cs-form { display: grid; gap: 18px; margin-bottom: 8px; }
.cs-field { display: grid; gap: 8px; }
.cs-label { color: #5b6c8f; font-size: 13px; }
.cs-label i { color: #ff5c22; font-style: normal; }
.cs-type-row { display: flex; gap: 10px; }
.cs-type { padding: 8px 24px; border: 1px solid #e3e9f4; border-radius: 8px; background: #fff; color: #5b6c8f; cursor: pointer; }
.cs-type.is-active { border-color: #2e7cd6; color: #2e7cd6; background: #f5f8ff; }
.cs-receiver-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.cs-actions { display: flex; justify-content: flex-end; }
.cs-list { display: grid; gap: 12px; }
.cs-record { padding: 16px 18px; border: 1px solid #e3e9f4; }
.cs-record-head { display: flex; align-items: center; gap: 8px; color: #8a97b5; font-size: 12px; }
.cs-record-head time { margin-left: auto; }
.cs-device { color: #16233f; font-size: 14px; }
.cs-content { margin: 10px 0 0; color: #16233f; line-height: 1.8; white-space: pre-wrap; }
.cs-record-images { display: flex; gap: 8px; margin-top: 10px; }
.cs-record-images img { width: 64px; height: 64px; object-fit: cover; border-radius: 6px; border: 1px solid #e3e9f4; }
.cs-reply { margin-top: 12px; padding: 10px 12px; border-radius: 8px; background: #f5f8ff; color: #5b6c8f; font-size: 13px; }
.cs-reply b { color: #2e7cd6; font-size: 12px; }
.cs-reply p { margin: 4px 0 0; line-height: 1.8; }
.cs-reply small { color: #8a97b5; }
.cs-inline-detail { margin-top: 12px; padding-top: 14px; border-top: 1px dashed #e3e9f4; }
.cs-link { border: 0; background: none; color: #2e7cd6; font-size: 13px; cursor: pointer; padding: 4px 0; }
.cs-link:hover { text-decoration: underline; }
.cs-detail-grid { display: grid; grid-template-columns: 90px 1fr; gap: 10px 14px; font-size: 13px; color: #5b6c8f; }
.cs-detail-grid b { color: #16233f; }
.cs-pre { white-space: pre-wrap; }
.cs-order-card { margin-top: 16px; padding: 14px 16px; border: 1px solid #e3e9f4; border-radius: 10px; background: #f9fbff; }
.cs-order-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; color: #16233f; }
.cs-order-row { display: flex; justify-content: space-between; font-size: 13px; color: #5b6c8f; padding: 3px 0; }
.cs-order-row b { color: #16233f; }
.cs-order-total { border-top: 1px solid #e3e9f4; margin-top: 6px; padding-top: 8px; }
.cs-order-total b { color: #ff5c22; font-size: 18px; }
.cs-order-remark { margin: 8px 0 0; font-size: 12px; color: #8a97b5; line-height: 1.7; }
.cs-pay-actions { display: flex; gap: 10px; margin-top: 12px; }
.cs-offline-info { margin-top: 10px; padding: 10px 12px; border-radius: 8px; background: #fff7e6; color: #8a5a00; font-size: 13px; line-height: 1.8; }
.cs-offline-info p { margin: 0; }
.cs-offline-info small { color: #a37a2c; }
.cs-address-card { margin-top: 14px; padding: 12px 14px; border: 1px dashed #b4c0d8; border-radius: 10px; color: #5b6c8f; font-size: 13px; }
.cs-address-card b { color: #16233f; }
.cs-address-card p { margin: 6px 0 0; line-height: 1.8; }
.cs-address-card small { color: #8a97b5; }
.cs-address-card .cs-missing { color: #ff5c22; }
.cs-ship-form { margin-top: 14px; padding: 12px 14px; border-radius: 10px; background: #f5f8ff; color: #16233f; font-size: 13px; }
.cs-ship-row { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.cs-ship-row .ml-input { flex: 1; min-width: 140px; }
@media (max-width: 768px) {
  .cs-type { padding: 8px 18px; }
  .cs-receiver-grid { grid-template-columns: 1fr; }
}
</style>
