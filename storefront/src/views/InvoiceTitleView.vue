<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { deleteInvoiceTitle, listInvoiceTitles, updateInvoiceTitle } from '@/api/invoice'
import AccountSidebar from '@/components/AccountSidebar.vue'
import EmptyState from '@/components/EmptyState.vue'
import InvoiceTitleDialog from '@/components/InvoiceTitleDialog.vue'
import LoadingState from '@/components/LoadingState.vue'
import { useToasts } from '@/components/base/useToasts'
import type { InvoiceTitle } from '@/types'

/**
 * 个人中心 · 发票抬头簿（US23 / FR-164）
 *
 * 形态与**收货地址簿**一致（列表 + 弹层增改 + 设为默认 + 删除）—— 两者都是
 * "客户维护的一组可复用资料"，用同一套交互客户不用学第二遍。
 *
 * ⚠️ 删 / 改抬头**不会影响任何历史订单**：订单里的开票信息是下单（或补开）那一刻的**快照**
 * （FR-166 / SC-061）。页面上的说明文字要把这一点讲清楚，否则客户不敢删。
 */
const list = ref<InvoiceTitle[]>([])
const loading = ref(true)
const error = ref(false)

const dialogOpen = ref(false)
const editing = ref<InvoiceTitle | null>(null)
const toast = useToasts()

async function load() {
  loading.value = true
  error.value = false
  try {
    list.value = await listInvoiceTitles()
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  dialogOpen.value = true
}

function openEdit(title: InvoiceTitle) {
  editing.value = title
  dialogOpen.value = true
}

async function onSubmitted() {
  toast.success('抬头已保存')
  await load()
}

async function onDelete(title: InvoiceTitle) {
  try {
    await deleteInvoiceTitle(title.id)
    toast.success('抬头已删除')
    await load()
  } catch (e) {
    toast.error((e as { message?: string })?.message || '删除失败，请稍后重试')
  }
}

async function onSetDefault(title: InvoiceTitle) {
  try {
    await updateInvoiceTitle(title.id, {
      titleName: title.titleName,
      taxNo: title.taxNo ?? '',
      titleAddress: title.titleAddress ?? '',
      titlePhone: title.titlePhone ?? '',
      bankName: title.bankName ?? '',
      defaultStatus: true,
    })
    toast.success('已设为默认')
    await load()
  } catch (e) {
    toast.error((e as { message?: string })?.message || '设置失败，请稍后重试')
  }
}

onMounted(load)
</script>

<template>
  <div class="account-section-page">
    <div class="crumbs">
      <RouterLink to="/">首页</RouterLink> / <RouterLink to="/account">个人中心</RouterLink> / <span>发票抬头</span>
    </div>

    <div class="account-wrap">
      <AccountSidebar />
      <div class="account-main account-section-main">
        <div class="account-page-heading">
          <div><h1>发票抬头</h1><p>存好常用抬头，下单开票时直接选，不用每次重填。</p></div>
          <button id="addInvoiceTitle" class="account-primary-button" type="button" @click="openCreate">新增抬头</button>
        </div>

        <div class="account-content-card">
          <div class="account-section-heading">
            <h2>抬头管理</h2><span v-if="!loading && !error">共 {{ list.length }} 条</span>
          </div>
          <LoadingState v-if="loading" :count="2" />

          <EmptyState
            v-else-if="error"
            mode="error"
            title="发票抬头加载失败"
            desc="网络或服务暂时不可用"
            action-text="重新加载"
            @action="load"
          />

          <template v-else>
            <div v-if="list.length" class="invoice-title-list">
              <div v-for="t in list" :key="t.id" class="ml-card invoice-title-card" :class="{ 'is-default': t.defaultStatus }">
                <div class="invoice-title-head">
                  <span class="invoice-title-name">{{ t.titleName }}</span>
                  <span v-if="t.defaultStatus" class="ml-pill is-done">默认</span>
                </div>
                <p v-if="t.taxNo" class="invoice-title-line">税号：{{ t.taxNo }}</p>
                <p v-if="t.titleAddress" class="invoice-title-line">地址：{{ t.titleAddress }}</p>
                <p v-if="t.titlePhone" class="invoice-title-line">电话：{{ t.titlePhone }}</p>
                <p v-if="t.bankName" class="invoice-title-line">开户行：{{ t.bankName }}</p>
                <div class="invoice-title-actions">
                  <button class="btn-cart" type="button" @click="openEdit(t)">编辑</button>
                  <button v-if="!t.defaultStatus" class="btn-cart" type="button" @click="onSetDefault(t)">设为默认</button>
                  <button class="btn-cart" type="button" @click="onDelete(t)">删除</button>
                </div>
              </div>
            </div>

            <EmptyState
              v-else
              icon="🧾"
              title="还没有发票抬头"
              desc="存一条常用抬头，下单开票时直接选"
              action-text="新增抬头"
              @action="openCreate"
            />
          </template>
        </div>

        <div class="account-content-card invoice-title-note">
          <h2>关于发票</h2>
          <p>开票信息在下单时提交，提交后不可修改；需要修改请在开票前联系客服。</p>
          <p>修改或删除这里的抬头，<strong>不会影响任何历史订单</strong>——那些订单里存的是当时填的那一份。</p>
        </div>
      </div>
    </div>

    <InvoiceTitleDialog
      :open="dialogOpen"
      :title="editing"
      @close="dialogOpen = false"
      @submitted="onSubmitted"
    />
  </div>
</template>

<style scoped>
.invoice-title-list { display: grid; gap: 14px; }
.invoice-title-card { display: grid; gap: 8px; padding: 18px 20px; }
.invoice-title-card.is-default { border-color: #2e7cd6; }
.invoice-title-head { display: flex; align-items: center; gap: 10px; }
.invoice-title-name { font-size: 15px; font-weight: 600; color: #16233f; }
.invoice-title-line { margin: 0; font-size: 13px; color: #5b6c8f; line-height: 1.8; }
.invoice-title-actions { display: flex; gap: 10px; margin-top: 6px; }
.invoice-title-note h2 { font-size: 15px; margin: 0 0 10px; }
.invoice-title-note p { margin: 0 0 8px; font-size: 13px; color: #5b6c8f; line-height: 1.8; }
</style>
