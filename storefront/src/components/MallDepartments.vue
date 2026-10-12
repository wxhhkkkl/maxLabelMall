<script setup lang="ts">
import { ref, watch } from 'vue'
import { pageProducts } from '@/api/product'
import type { CategoryNode, ProductSpu } from '@/types'
import ProductCard from './ProductCard.vue'
import LoadingState from './LoadingState.vue'
import { mallPromotion } from '@/data/mallPromotions'

const props = defineProps<{ categories: CategoryNode[] }>()
defineEmits<{ select: [id: number] }>()
const floors = ref<{ category: CategoryNode; products: ProductSpu[]; failed: boolean; loading: boolean; promo: ReturnType<typeof mallPromotion> }[]>([])
let request = 0
async function load() {
  const current = ++request
  floors.value = props.categories.map(category => ({ category, products: [], failed: false, loading: true, promo: mallPromotion(category.name) }))
  // 分区依次请求，避免首页同时向商品分页接口突发多个查询。
  for (const [index, category] of props.categories.entries()) {
    if (current !== request) return
    try {
      // 单个父分类由后端展开子分类，避免批量分类参数在旧部署上被忽略。
      const result = await pageProducts({ categoryId: category.id, pageNo: 1, pageSize: 4 })
      if (current !== request) return
      floors.value[index] = { category, products: result.list, failed: false, loading: false, promo: mallPromotion(category.name) }
    } catch {
      if (current !== request) return
      floors.value[index] = { category, products: [], failed: true, loading: false, promo: mallPromotion(category.name) }
    }
  }
}
watch(() => props.categories, load, { immediate: true })
</script>

<template>
  <div class="mall-departments">
    <section
      v-for="(floor, index) in floors"
      :key="floor.category.id"
      class="mall-department"
    >
      <div class="mall-department-head">
        <div><span class="mall-floor-number">{{ index + 1 }}F</span><h2>{{ floor.category.name }}</h2></div>
        <nav
          class="mall-floor-tabs"
          :aria-label="`${floor.category.name}快捷分类`"
        >
          <button
            v-for="child in floor.category.children"
            :key="child.id"
            type="button"
            @click="$emit('select', child.id)"
          >
            {{ child.name }}
          </button>
        </nav>
        <button
          type="button"
          @click="$emit('select', floor.category.id)"
        >
          查看更多 <span aria-hidden="true">→</span>
        </button>
      </div>
      <div class="mall-floor-body">
        <button
          class="mall-floor-promo"
          :class="{ 'is-software': floor.promo.image === 'software' }"
          :style="{ '--promo-color': floor.promo.color }"
          type="button"
          :aria-label="`选购${floor.category.name}`"
          @click="$emit('select', floor.category.id)"
        >
          <img
            :src="`/assets/mall-floors/${floor.promo.image}-v1.webp`"
            :alt="floor.promo.alt"
            loading="lazy"
          >
          <span class="mall-floor-promo-copy"><strong>{{ floor.promo.title }}</strong><small>{{ floor.promo.subtitle }}</small><span class="mall-floor-promo-action">探索{{ floor.category.name }} <span aria-hidden="true">→</span></span></span>
          <span
            v-if="floor.promo.image === 'software'"
            class="mall-floor-promo-note"
          >软件界面示意 · 敬请期待</span>
        </button>
        <LoadingState
          v-if="floor.loading"
          class="mall-loading mall-department-grid"
          variant="grid"
          :count="4"
        />
        <div
          v-else-if="floor.products.length"
          class="mall-grid mall-department-grid"
        >
          <ProductCard
            v-for="p in floor.products"
            :key="p.id"
            :spu="p"
            catalog
            floor
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
      </div>
    </section>
  </div>
</template>
