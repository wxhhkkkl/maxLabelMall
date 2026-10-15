<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import type { Banner, CategoryNode } from '@/types'
import MallBanner from './MallBanner.vue'
import MallDepartments from './MallDepartments.vue'
import MallIcon from './MallIcon.vue'
import '@/styles/mall-home.css'

const props = defineProps<{ categories: CategoryNode[]; banners: Banner[] }>()
const emit = defineEmits<{ select: [id?: number] }>()
const openCategoryId = ref<number>()
const openCategory = computed(() => props.categories.find(category => category.id === openCategoryId.value && category.children?.length))
let closeTimer: ReturnType<typeof setTimeout> | undefined
function cancelClose() { clearTimeout(closeTimer) }
function showCategory(category: CategoryNode) {
  cancelClose()
  openCategoryId.value = category.children?.length ? category.id : undefined
}
function closeCategory() { cancelClose(); openCategoryId.value = undefined }
function scheduleClose() { cancelClose(); closeTimer = setTimeout(closeCategory, 300) }
function pickCategory(id?: number) { closeCategory(); emit('select', id) }
function closeOnFocusOut(event: FocusEvent) {
  if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) closeCategory()
}
onBeforeUnmount(cancelClose)
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
    <div
      class="mall-home-hero"
      @focusout="closeOnFocusOut"
      @keydown.esc.stop.prevent="closeCategory"
    >
      <nav
        class="mall-home-directory"
        aria-label="商品分类"
        :style="{ '--category-count': categories.length }"
        @mouseleave="scheduleClose"
      >
        <button
          class="cat-all mall-home-all"
          type="button"
          @click="emit('select')"
          @mouseenter="closeCategory"
          @focus="closeCategory"
        >
          <MallIcon name="menu" /> 全部商品分类
        </button>
        <div
          v-for="category in categories"
          :key="category.id"
          class="mall-directory-item"
          @mouseenter="showCategory(category)"
        >
          <button
            type="button"
            :class="{ 'is-open': openCategoryId === category.id }"
            :aria-expanded="category.children?.length ? openCategoryId === category.id : undefined"
            :aria-controls="category.children?.length ? 'mall-category-flyout' : undefined"
            @focus="showCategory(category)"
            @click="pickCategory(category.id)"
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
        </div>
      </nav>
      <div
        v-if="openCategory"
        id="mall-category-flyout"
        class="mall-category-flyout"
        :aria-label="`${openCategory.name}二级分类`"
        @mouseenter="cancelClose"
        @mouseleave="scheduleClose"
        @focusin="cancelClose"
      >
        <div class="mall-category-flyout-heading">
          <MallIcon :name="categoryIcon(openCategory.name)" /><h2>{{ openCategory.name }}</h2><span>按分类选购</span>
        </div>
        <div class="mall-category-flyout-links">
          <button
            v-for="child in openCategory.children"
            :key="child.id"
            type="button"
            @click="pickCategory(child.id)"
          >
            {{ child.name }}<MallIcon name="chevron" />
          </button>
        </div>
      </div>
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
