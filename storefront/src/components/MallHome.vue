<script setup lang="ts">
import type { Banner, CategoryNode } from '@/types'
import MallBanner from './MallBanner.vue'
import MallDepartments from './MallDepartments.vue'
import MallIcon from './MallIcon.vue'
import '@/styles/mall-home.css'

defineProps<{ categories: CategoryNode[]; banners: Banner[] }>()
const emit = defineEmits<{ select: [id?: number] }>()
const steps = [{ title: '选购商品', icon: 'cart' }, { title: '确认订单', icon: 'order' }, { title: '完成支付', icon: 'payment' }, { title: '等待收货', icon: 'truck' }]
function categoryIcon(name: string) {
  if (/RFID/i.test(name)) return 'rfid'
  if (/A4|平张/.test(name)) return 'sheet'
  if (/碳带/.test(name)) return 'ribbon'
  if (/打印/.test(name)) return 'printer'
  if (/扫描|采集/.test(name)) return 'scanner'
  if (/软件/.test(name)) return 'software'
  return 'labels'
}
</script>

<template>
  <div class="mall-container mall-home">
    <div class="mall-home-hero">
      <nav
        class="mall-home-directory"
        aria-label="商品分类"
        :style="{ '--category-count': categories.length }"
      >
        <button
          class="cat-all mall-home-all"
          type="button"
          @click="emit('select')"
        >
          <MallIcon name="menu" /> 全部商品分类
        </button>
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          @click="emit('select', category.id)"
        >
          <MallIcon
            class="mall-directory-icon"
            :name="categoryIcon(category.name)"
          />
          <strong>{{ category.name }}</strong><MallIcon
            class="mall-directory-chevron"
            name="chevron"
          />
        </button>
      </nav>
      <div class="mall-home-banner-area">
        <MallBanner
          v-if="banners.length"
          :banners="banners"
        />
        <div
          v-else
          class="mall-home-banner"
        >
          <img
            src="/assets/mall-banners/coated-labels-factory-v1.png"
            alt="多种规格的白色铜板不干胶标签卷与平张标签"
            fetchpriority="high"
          >
          <div class="mall-home-banner-copy">
            <span class="mall-banner-eyebrow">标签耗材 · 源头供应</span>
            <h2>铜板不干胶</h2><p>厂家直供，多规格支持定制</p><button
              type="button"
              @click="emit('select', categories.find(c => /卷筒|标签/.test(c.name))?.id)"
            >
              选购标签 <MallIcon name="chevron" />
            </button>
          </div>
        </div>
      </div>
      <ol
        class="mall-purchase-steps"
        aria-label="购物流程"
      >
        <li
          v-for="(step, index) in steps"
          :key="step.icon"
        >
          <MallIcon :name="step.icon" /><span>{{ index + 1 }}. {{ step.title }}</span><MallIcon
            v-if="index < steps.length - 1"
            class="mall-step-arrow"
            name="chevron"
          />
        </li>
      </ol>
    </div>
    <MallDepartments
      :categories="categories"
      @select="emit('select', $event)"
    />
    <div class="mall-home-consult">
      <MallIcon
        class="mall-consult-icon"
        name="support"
      />
      <div><h2>不确定怎么选？联系张经理协助选型</h2><p>提供设备型号与使用场景，帮你确认合适的标签与耗材。</p></div><RouterLink to="/support/contact">
        联系我们 <span aria-hidden="true">→</span>
      </RouterLink><div class="mall-home-contact">
        <span>电话：010-88613986</span><span>微信：q345845052</span>
      </div>
    </div>
  </div>
</template>
