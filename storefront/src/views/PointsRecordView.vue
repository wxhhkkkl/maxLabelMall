<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { pagePointRecords } from '@/api/point'
import AccountSidebar from '@/components/AccountSidebar.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import Pagination from '@/components/Pagination.vue'
import type { MemberPointRecord } from '@/types'
import { formatDateTime } from '@/utils/time'

/**
 * 积分明细（FR-011d）。
 *
 * ⚠️ 本页只做**变动记录**：积分**余额/经验/等级**在个人中心的账号资料处展示
 * （数据直接来自 `/member/user/get`，不需要额外请求）。
 *
 * ⚠️ **租户 162 现在必然是空的**：`member_point_record` 零记录、`member_level` 零配置、
 * 93 个会员积分全为 0。空状态是**预期结果**，不是缺陷 —— 页面必须如实显示「暂无」，
 * 不得为好看编造数据，也不得因缺数据而报错。
 */
const PAGE_SIZE = 10

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
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <RouterLink to="/account">个人中心</RouterLink> /
    <span>积分明细</span>
  </div>

  <div class="acct-layout">
    <AccountSidebar />

    <div class="acct-main">
      <h1 class="acct-title">积分明细</h1>

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
        desc="参与活动或下单后，积分变动会记录在这里"
      />

      <template v-else>
        <div class="ml-card">
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
</template>

<style scoped>
.pt-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
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
  color: var(--ml-text);
}
.pt-desc,
.pt-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ml-text-ph);
}
.pt-delta {
  flex: none;
  font-size: 16px;
  color: var(--ml-primary);
}
.pt-delta.is-minus {
  color: var(--ml-text-sub);
}
</style>
