<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { pageComments } from '@/api/comment'
import CommentList from '@/components/CommentList.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import Pagination from '@/components/Pagination.vue'
import type { ProductComment } from '@/types'

/**
 * 某个商品的**全部评价**（FR-071 / FR-072）。
 *
 * 详情页只放几条摘要，这里才是完整列表 —— 所以两处共用 `CommentList`，差别在参数：
 * 本页 `collapsible=false`（要看得到完整内容）、也不给"查看全部"入口（已经在这里了）。
 *
 * ⚠️ **本页不拉商品信息**：只为了在标题里显示商品名而多发一次请求不划算，
 * 面包屑上的「商品」链回详情页就够了。
 */
const props = defineProps<{ id: number | string }>()

const PAGE_SIZE = 10

const list = ref<ProductComment[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const error = ref(false)

async function load() {
  loading.value = true
  error.value = false
  try {
    const res = await pageComments({ spuId: Number(props.id), pageNo: pageNo.value, pageSize: PAGE_SIZE })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } catch {
    error.value = true
    list.value = []
  } finally {
    loading.value = false
  }
}

function changePage(n: number) {
  pageNo.value = n
  void load()
}

onMounted(load)
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink> / <RouterLink to="/mall">商城</RouterLink> /
    <RouterLink :to="`/product/${props.id}`">商品</RouterLink> / <span>全部评价</span>
  </div>

  <div class="pcm-page">
    <h1 class="pcm-title">全部评价</h1>

    <LoadingState v-if="loading" :count="2" />

    <EmptyState
      v-else-if="error"
      mode="error"
      title="评价加载失败"
      desc="网络或服务暂时不可用"
      action-text="重新加载"
      @action="load"
    />

    <template v-else>
      <CommentList :comments="list" :collapsible="false" />
      <Pagination
        v-if="total"
        :page-no="pageNo"
        :page-size="PAGE_SIZE"
        :total="total"
        @update:page-no="changePage"
      />
    </template>
  </div>
</template>

<style scoped>
.pcm-title {
  margin: 0 0 16px;
  font-size: 22px;
  font-weight: 700;
  color: var(--ml-text);
}
</style>
