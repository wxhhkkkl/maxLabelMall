<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { pagePointRecords } from '@/api/point'
import AccountSidebar from '@/components/AccountSidebar.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import Pagination from '@/components/Pagination.vue'
import { useUserStore } from '@/store/user'
import type { MemberPointRecord } from '@/types'
import { formatDateTime } from '@/utils/time'

/**
 * 积分明细（FR-011d）。
 *
 * 顶部概览展示会员积分余额、经验与等级，下面展示积分变动记录。
 * 概览读取共享会员 store（来自 `/member/user/get`），不重复请求。
 *
 * ⚠️ **租户 162 现在必然是空的**：`member_point_record` 零记录、`member_level` 零配置、
 * 93 个会员积分全为 0。空状态是**预期结果**，不是缺陷 —— 页面必须如实显示「暂无」，
 * 不得为好看编造数据，也不得因缺数据而报错。
 */
const PAGE_SIZE = 10
const userStore = useUserStore()

const list = ref<MemberPointRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const error = ref(false)

/** 变动值的展示：**正数带 `+`**，否则看不出是加是减 */
function formatPoint(delta: number): string {
  return delta > 0 ? `+${delta}` : String(delta)
}

async function load() {
  loading.value = true
  error.value = false
  try {
    const res = await pagePointRecords({ pageNo: pageNo.value, pageSize: PAGE_SIZE })
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

function onPage(n: number) {
  pageNo.value = n
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="account-section-page">
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <RouterLink to="/account">个人中心</RouterLink> /
    <span>积分与等级</span>
  </div>

  <div class="account-wrap">
    <AccountSidebar />

    <div class="account-main account-section-main">
      <div class="account-page-heading">
        <div><h1>积分与等级</h1><p>账户权益与每一笔积分变动，清晰可查。</p></div>
      </div>
      <div class="account-content-card pt-overview" aria-label="积分与等级概览">
        <div class="pt-stat"><span>当前积分</span><b>{{ userStore.member ? (userStore.member.point ?? 0) : '—' }}</b><small>账户积分余额</small></div>
        <div class="pt-stat"><span>经验值</span><b>{{ userStore.member ? (userStore.member.experience ?? 0) : '—' }}</b><small>当前累计经验</small></div>
        <div class="pt-stat pt-level"><span>会员等级</span><b>{{ userStore.member ? (userStore.member.level?.name || '暂无等级') : '—' }}</b><small>{{ userStore.member?.level?.name ? '当前账户等级' : '已设置的等级将在这里展示' }}</small></div>
      </div>

      <div class="account-content-card pt-records">
      <div class="account-section-heading"><h2>积分明细</h2><span v-if="!loading && !error">共 {{ total }} 条</span></div>

      <LoadingState v-if="loading" />

      <EmptyState
        v-else-if="error"
        mode="error"
        title="积分明细加载失败"
        desc="网络或服务暂时不可用"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!list.length"
        title="暂无积分记录"
        desc="积分发生变化后，可在这里查看增减明细。"
      />

      <template v-else>
        <div class="pt-record-list">
          <div v-for="it in list" :key="it.id" class="pt-row">
            <div class="pt-info">
              <div class="pt-title">{{ it.title }}</div>
              <div v-if="it.description" class="pt-desc">{{ it.description }}</div>
              <div class="pt-time">{{ formatDateTime(it.createTime) }}</div>
            </div>
            <div class="pt-delta" :class="{ 'is-minus': it.point < 0 }">
              {{ formatPoint(it.point) }}
            </div>
          </div>
        </div>

        <Pagination :page-no="pageNo" :page-size="PAGE_SIZE" :total="total" @update:page-no="onPage" />
      </template>
      </div>
    </div>
  </div>
  </div>
</template>

<style scoped>
.pt-overview { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); background: linear-gradient(110deg, #FFFFFF, #F0F4FB); }
.pt-stat { padding: 4px 28px; border-left: 1px solid var(--ml-border); min-width: 0; }
.pt-stat:first-child { padding-left: 0; border-left: 0; }
.pt-stat span { display: block; font-size: 12px; color: var(--ml-text-sub); }
.pt-stat b { display: block; color: #0C2148; font-size: 30px; font-weight: 600; line-height: 1.5; margin-top: 10px; overflow-wrap: anywhere; }
.pt-level b { font-size: 21px; line-height: 2.14; }
.pt-stat small { display: block; color: var(--ml-text-ph); font-size: 11px; margin-top: 6px; line-height: 1.7; }
.pt-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 0;
  border-bottom: 1px solid var(--ml-border);
}
.pt-row:last-child {
  border-bottom: 0;
}
.pt-info {
  min-width: 0;
}
.pt-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.7;
  overflow-wrap: anywhere;
  color: var(--ml-text);
}
.pt-desc,
.pt-time {
  margin-top: 8px;
  font-size: 12px;
  color: var(--ml-text-sub);
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.pt-delta {
  flex: none;
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--ml-primary);
}
.pt-delta.is-minus {
  color: var(--ml-text-sub);
}
@media (max-width: 768px) {
  .pt-stat { padding-inline: 18px; }
}
@media (max-width: 480px) {
  .pt-overview { gap: 12px; }
  .pt-stat { padding: 0 0 0 12px; }
  .pt-stat b { font-size: 24px; }
  .pt-level b { font-size: 16px; line-height: 2.25; }
  .pt-stat small { font-size: 10px; }
  .pt-row { gap: 16px; padding-block: 20px; }
}
</style>
