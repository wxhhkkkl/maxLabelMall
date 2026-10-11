<script setup lang="ts">
import { ref, watch } from 'vue'
import { pageProducts } from '@/api/product'
import type { CategoryNode, ProductSpu } from '@/types'
import ProductCard from './ProductCard.vue'
import LoadingState from './LoadingState.vue'

const props = defineProps<{ categories: CategoryNode[] }>()
defineEmits<{ select: [id: number] }>()
const floors = ref<{ category: CategoryNode; products: ProductSpu[]; failed: boolean }[]>([])
const loading = ref(true)
let request = 0
async function load() {
  const current = ++request
  loading.value = true
  const results: typeof floors.value = []
  // 分区依次请求，避免首页同时向商品分页接口突发多个查询。
  for (const category of props.categories) {
    if (current !== request) return
    try {
      // 单个父分类由后端展开子分类，避免批量分类参数在旧部署上被忽略。
      const result = await pageProducts({ categoryId: category.id, pageNo: 1, pageSize: 4 })
      results.push({ category, products: result.list, failed: false })
    } catch {
      results.push({ category, products: [], failed: true })
    }
  }
  if (current !== request) return
  floors.value = results
  loading.value = false
}
watch(() => props.categories, load, { immediate: true })
</script>

<template>
  <LoadingState
    v-if="loading"
    class="mall-loading"
    variant="grid"
    :count="4"
  />
  <div
    v-else
    class="mall-departments"
  >
    <section
      v-for="(floor, index) in floors"
      :key="floor.category.id"
      class="mall-department"
    >
      <div class="mall-department-head">
        <div><span class="mall-floor-number">{{ String(index + 1).padStart(2, '0') }}</span><h2>{{ floor.category.name }}</h2></div>
        <button
          type="button"
          @click="$emit('select', floor.category.id)"
        >
          查看全部 <span aria-hidden="true">→</span>
        </button>
      </div>
      <div
        v-if="floor.category.children?.length"
        class="mall-department-links"
      >
        <button
          v-for="child in floor.category.children"
          :key="child.id"
          type="button"
          @click="$emit('select', child.id)"
        >
          {{ child.name }}
        </button>
      </div>
      <div
        v-if="floor.products.length"
        class="mall-grid mall-department-grid"
      >
        <ProductCard
          v-for="p in floor.products"
          :key="p.id"
          :spu="p"
          catalog
          :category-label="floor.category.name"
        />
      </div>
      <div
        v-else
        class="mall-department-empty"
      >
        <p>{{ floor.failed ? '该分类商品暂时加载失败' : '该分类商品正在准备中' }}</p>
        <button
          v-if="floor.failed"
          type="button"
          @click="load"
        >
          重新加载
        </button>
        <RouterLink
          v-else
          to="/support/contact"
        >
          咨询选型 →
        </RouterLink>
      </div>
    </section>
  </div>
</template>
