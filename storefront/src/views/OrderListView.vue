<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { cancelOrder, pageOrders } from '@/api/order'
import AccountSidebar from '@/components/AccountSidebar.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import Pagination from '@/components/Pagination.vue'
import MlModal from '@/components/base/MlModal.vue'
import MlPill from '@/components/base/MlPill.vue'
import type { OrderPageItem } from '@/types'
import { OrderStatus } from '@/types'
import { formatYuan } from '@/utils/money'
import { orderStatusFilters } from '@/utils/orderStatus'
import { formatDateTime } from '@/utils/time'

/**
 * 我的订单 —— 按 design-new-pages.md §3.3 的版式实现（无设计稿）。
 *
 * 三条约束：
 *
 * 1. **状态文案与标签变体来自 `utils/orderStatus.ts`**，页面不自行命名（FR-041a）。
 *    「支付成功」对应的是 `10 待发货`，不是「已支付」。
 *
 * 2. **取消订单只对「待支付」开放**（FR-036），且取消后库存与所用券的释放是
 *    **后端的事** —— 前端只负责二次确认、调接口、重新拉列表。不自行把订单标成已取消。
 *
 * 3. 「全部」筛选**不传 `status`**：后端标了 `@InEnum`，传空串会被参数校验拒绝。
 */

const router = useRouter()

const list = ref<OrderPageItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const status = ref<number | ''>('')

const loading = ref(true)
const error = ref(false)
const message = ref('')

/** 待取消的订单；非空时二次确认弹层打开 */
const cancelTarget = ref<OrderPageItem | null>(null)

const filters = orderStatusFilters()

async function load() {
  loading.value = true
  error.value = false
  try {
    const res = await pageOrders({
      pageNo: pageNo.value,
      pageSize,
      // undefined 会被 axios 丢掉，正好满足「全部不传 status」
      status: status.value === '' ? undefined : status.value,
    })
    list.value = res.list
    total.value = res.total
  } catch {
    error.value = true
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function pickStatus(next: number | '') {
  if (status.value === next) return
  status.value = next
  pageNo.value = 1
  void load()
}

function onPage(n: number) {
  pageNo.value = n
  void load()
}

function askCancel(o: OrderPageItem) {
  message.value = ''
  cancelTarget.value = o
}

async function confirmCancel() {
  const target = cancelTarget.value
  if (!target) return
  cancelTarget.value = null
  try {
    await cancelOrder(target.id)
    // 状态由后端改，重新拉一次即可 —— 不在这里把本地对象标成「已取消」
    await load()
  } catch (e) {
    message.value = (e as { message?: string })?.message || '取消订单失败，请稍后重试'
  }
}

onMounted(load)
</script>

<template>
  <div class="account-section-page">
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <RouterLink to="/account">个人中心</RouterLink> / <span>我的订单</span>
  </div>

  <div class="account-wrap">
    <AccountSidebar />
    <!-- 与个人中心同一套两列布局：侧栏常驻，点进子页不丢菜单 -->
    <div class="account-main account-section-main">
      <div class="account-page-heading">
        <div><h1>我的订单</h1><p>查看采购记录，跟进订单与物流状态。</p></div>
        <RouterLink to="/mall" class="account-heading-link">前往商城 <span aria-hidden="true">↗</span></RouterLink>
      </div>
      <div class="account-content-card">
      <div class="cat-pills account-tabs" aria-label="订单状态">
        <button
          v-for="f in filters"
          :key="String(f.value)"
          class="tab"
          :class="{ active: status === f.value }"
          :aria-pressed="status === f.value"
          type="button"
          @click="pickStatus(f.value)"
        >
          {{ f.label }}
        </button>
      </div>

      <p v-if="message" class="ol-msg">{{ message }}</p>

      <LoadingState v-if="loading" :count="2" />

      <EmptyState
        v-else-if="error"
        mode="error"
        title="订单加载失败"
        desc="网络或服务暂时不可用，请稍后重试"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!list.length"
        icon="📦"
        title="还没有订单"
        desc="下单后可以在这里查看订单状态与物流进度"
        action-text="去商城逛逛"
        @action="router.push('/mall')"
      />

      <template v-else>
        <div class="account-order-list">
        <div v-for="o in list" :key="o.id" class="p-card order-card">
          <div class="oc-head">
            <span class="oc-no">订单号 {{ o.no }}</span>
            <span class="oc-time">{{ formatDateTime(o.createTime) }}</span>
            <MlPill :status="o.status" />
          </div>

          <div class="oc-items">
            <div v-for="it in o.items" :key="it.id" class="oc-item">
              <div class="oc-thumb">
                <img v-if="it.picUrl" :src="it.picUrl" :alt="it.spuName" />
                <span v-else class="ph">图</span>
              </div>
              <RouterLink class="oc-name" :to="`/order/${o.id}`">{{ it.spuName }}</RouterLink>
              <span class="oc-count">×{{ it.count }}</span>
              <span class="oc-price">{{ formatYuan(it.price) }}</span>
            </div>
          </div>

          <div class="oc-foot">
            <span class="oc-total-label">应付金额</span>
            <span class="oc-total">{{ formatYuan(o.payPrice) }}</span>
            <div class="oc-actions">
            <!--
              支付入口在订单详情页（T117）—— 全站只有一处实现支付调用，
              列表这里只把人送过去，不复制一份支付逻辑。
            -->
            <button
              v-if="o.status === OrderStatus.UNPAID"
              class="btn-cart go-pay"
              type="button"
              @click="router.push(`/order/${o.id}`)"
            >
              立即支付
            </button>
            <button
              v-if="o.status === OrderStatus.UNPAID"
              class="btn-cart cancel-order"
              type="button"
              @click="askCancel(o)"
            >
              取消订单
            </button>
            <RouterLink class="btn-cart" :to="`/order/${o.id}`">查看详情</RouterLink>
            </div>
          </div>
        </div>
        </div>

        <Pagination :page-no="pageNo" :page-size="pageSize" :total="total" @update:page-no="onPage" />
      </template>
      </div>
    </div>
  </div>

  <!-- 取消订单不可撤销，必须先二次确认（FR-036） -->
  <MlModal :open="cancelTarget !== null" title="取消订单" @close="cancelTarget = null">
    <p class="ml-hint">
      确认取消订单 {{ cancelTarget?.no }}？取消后商品库存与所用优惠券会释放，订单不可恢复。
    </p>
    <template #foot>
      <button id="dismissCancel" class="btn-cart" type="button" @click="cancelTarget = null">
        再想想
      </button>
      <button id="confirmCancel" class="btn-buy" type="button" @click="confirmCancel">
        确认取消
      </button>
    </template>
  </MlModal>
  </div>
</template>

<style scoped>
.ol-msg {
  color: var(--ml-orange);
  font-size: 13px;
  margin-bottom: 12px;
}
/* 订单卡：版式沿用 §3.3 说的 .p-card，但它是给商品卡设计的（悬停上浮、
   纵向堆叠、图片区 190px），这里按订单卡的层级重排，并去掉悬停动效。 */
.order-card {
  display: block;
  padding: 0;
  gap: 0;
  margin: 0;
  overflow: hidden;
  border-radius: 12px;
  box-shadow: none;
  cursor: default;
}
.order-card:hover {
  transform: none;
  box-shadow: none;
}
.oc-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  flex-wrap: wrap;
  background: #F5F8FF;
  border-bottom: 1px solid var(--ml-border);
}
.oc-no {
  font-size: 13px;
  font-weight: 600;
  color: var(--ml-text);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
  min-width: 0;
}
.oc-head .ml-pill { margin-left: auto; }
.oc-items { padding-inline: 20px; }
.oc-time {
  font-size: 13px;
  color: var(--ml-text-ph);
  flex: 1;
}
.oc-item {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto auto;
  gap: 20px;
  align-items: center;
  padding: 24px 0;
  border-bottom: 1px solid var(--ml-border);
}
.oc-item:last-child { border-bottom: 0; }
.oc-thumb {
  width: 64px;
  height: 64px;
  border-radius: var(--ml-radius-field);
  overflow: hidden;
  background: var(--ml-bg-card);
}
.oc-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.oc-name {
  font-size: 14px;
  color: var(--ml-text);
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.oc-thumb .ph { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--ml-text-ph); font-size: 12px; }
.oc-count,
.oc-price {
  font-size: 13px;
  color: var(--ml-text-sub);
  font-variant-numeric: tabular-nums;
}
.oc-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  flex-wrap: wrap;
  border-top: 1px solid var(--ml-border);
}
.oc-total-label {
  font-size: 13px;
  color: var(--ml-text-sub);
}
.oc-total {
  font-size: 18px;
  font-weight: 700;
  color: var(--ml-orange);
  font-variant-numeric: tabular-nums;
  margin-right: auto;
}
.oc-foot .btn-buy,
.oc-foot .btn-cart {
  padding: 8px 16px;
  font-size: 13px;
  width: auto;
}
.oc-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }
@media (max-width: 768px) {
  .oc-head {
    flex-wrap: wrap;
    gap: 8px;
  }
  .oc-item {
    grid-template-columns: 56px minmax(0, 1fr) auto;
    gap: 10px 14px;
  }
  .oc-thumb { grid-row: 1 / 3; width: 56px; height: 56px; }
  .oc-name { grid-column: 2 / 4; }
  .oc-count { grid-column: 2; }
  .oc-price { grid-column: 3; }
  .oc-foot {
    flex-wrap: wrap;
  }
}
@media (max-width: 480px) {
  .oc-head, .oc-foot { padding: 14px; gap: 10px; }
  .oc-no { flex: 1 1 100%; font-size: 12px; }
  .oc-time { font-size: 11px; }
  .oc-items { padding-inline: 14px; }
  .oc-name { font-size: 13px; }
  .oc-total { margin-right: 0; }
  .oc-total-label { flex: 1; }
  .oc-foot .btn-cart { font-size: 12px; padding: 8px 12px; }
  .oc-actions { flex-basis: 100%; }
}
</style>
