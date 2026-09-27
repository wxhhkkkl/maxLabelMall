<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { deleteAddress, listAddress, updateAddress } from '@/api/address'
import AccountSidebar from '@/components/AccountSidebar.vue'
import AddressFormDialog from '@/components/AddressFormDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useToasts } from '@/components/base/useToasts'
import type { Address } from '@/types'

/**
 * 收货地址管理 —— 版式见 design-new-pages.md §3.6（无设计稿）。
 *
 * 新增 / 编辑表单已抽到 `AddressFormDialog`（结算页也要能加地址，且**不能跳转**）。
 * 行政区划树由那个组件按需加载，这里只管列表。
 */
const list = ref<Address[]>([])
const loading = ref(true)
const error = ref(false)

/** 表单弹层。`editing` 为 null 表示新增 */
const formOpen = ref(false)
const editing = ref<Address | null>(null)

const toast = useToasts()

async function load() {
  loading.value = true
  error.value = false
  try {
    list.value = await listAddress()
  } catch {
    error.value = true
    list.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  formOpen.value = true
}

function openEdit(a: Address) {
  editing.value = a
  formOpen.value = true
}

async function onDelete(a: Address) {
  try {
    await deleteAddress(a.id)
    toast.success('地址已删除')
    await load()
  } catch {
    toast.error('删除失败，请重试')
  }
}

async function onSetDefault(a: Address) {
  try {
    await updateAddress(a.id, {
      name: a.name,
      mobile: a.mobile,
      areaId: a.areaId,
      detailAddress: a.detailAddress,
      defaultStatus: true,
    })
    await load()
  } catch {
    toast.error('设置失败，请重试')
  }
}

onMounted(load)
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink><span>/ 收货地址</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ml-page-title">收货地址</h1>
  </div>

  <div class="account-wrap">
    <AccountSidebar />
    <!-- 与个人中心同一套两列布局：侧栏常驻，点进子页不丢菜单 -->
    <div class="account-main">
      <LoadingState v-if="loading" :count="2" />

      <EmptyState
        v-else-if="error"
        mode="error"
        title="地址加载失败"
        desc="网络或服务暂时不可用"
        action-text="重新加载"
        @action="load"
      />

      <template v-else>
        <div v-if="list.length" class="addr-list">
          <div v-for="a in list" :key="a.id" class="ml-card addr-card">
            <div class="addr-head">
              <span class="addr-name">{{ a.name }}</span>
              <span class="addr-mobile">{{ a.mobile }}</span>
              <span v-if="a.defaultStatus" class="ml-pill is-done">默认</span>
            </div>
            <p class="addr-detail">{{ a.areaName }} {{ a.detailAddress }}</p>
            <div class="addr-actions">
              <button class="btn-cart" type="button" @click="openEdit(a)">编辑</button>
              <button v-if="!a.defaultStatus" class="btn-cart" type="button" @click="onSetDefault(a)">
                设为默认
              </button>
              <button class="btn-cart" type="button" @click="onDelete(a)">删除</button>
            </div>
          </div>
        </div>

        <EmptyState
          v-else
          icon="📍"
          title="还没有收货地址"
          desc="添加一个地址，下单时就能直接选"
          action-text="新增地址"
          @action="openCreate"
        />

        <button
          v-if="list.length"
          id="addAddress"
          class="btn-cyan addr-add"
          type="button"
          @click="openCreate"
        >
          新增地址
        </button>
      </template>

      <!-- 新增 / 编辑弹层（共用组件；结算页也用同一个） -->
      <AddressFormDialog
        :open="formOpen"
        :address="editing"
        @close="formOpen = false"
        @saved="load"
      />
    </div>
  </div>
</template>

<style scoped>
.addr-list {
  display: grid;
  gap: 12px;
}
.addr-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.addr-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--ml-text);
}
.addr-mobile {
  font-size: 14px;
  color: var(--ml-text-sub);
}
.addr-detail {
  font-size: 14px;
  color: var(--ml-text-sub);
  margin-bottom: 12px;
}
.addr-actions {
  display: flex;
  gap: 10px;
}
.addr-actions .btn-cart {
  padding: 4px 12px;
  font-size: 13px;
}
.addr-add {
  margin-top: 16px;
}
</style>
