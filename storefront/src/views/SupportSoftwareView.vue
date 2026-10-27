<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listSoftwareByCategory, listSoftwareCategories, type Software, type SoftwareCategory } from '@/api/software'

const router = useRouter()
const categories = ref<SoftwareCategory[]>([])
const activeCategory = ref<number | null>(null)
const softwares = ref<Software[]>([])
const loading = ref(true)
const error = ref('')

const loadSoftwares = async (categoryId: number) => {
  activeCategory.value = categoryId
  softwares.value = await listSoftwareByCategory(categoryId)
}

const selectCategory = async (category: SoftwareCategory) => {
  error.value = ''
  try {
    await loadSoftwares(category.id)
  } catch {
    error.value = '加载失败，请稍后重试'
  }
}

onMounted(async () => {
  try {
    categories.value = await listSoftwareCategories()
    if (categories.value.length > 0) {
      await loadSoftwares(categories.value[0]!.id)
    }
  } catch {
    error.value = '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
})

const openDetail = (item: Software) => router.push(`/support/software/${item.id}`)
</script>

<template>
  <div class="ml-page">
    <section class="ml-card">
      <h1 class="ml-card-title">软件与驱动下载</h1>
      <p v-if="error" class="text-red-600">{{ error }}</p>
      <p v-if="!loading && categories.length === 0">暂无可用下载</p>

      <div v-if="categories.length > 0" class="category-tabs" data-testid="software-categories">
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          :class="['category-tab', { active: category.id === activeCategory }]"
          @click="selectCategory(category)"
        >
          {{ category.name }}
        </button>
      </div>

      <ul v-if="softwares.length > 0" class="software-list" data-testid="software-list">
        <li v-for="item in softwares" :key="item.id" class="software-item">
          <div>
            <button type="button" class="software-title" @click="openDetail(item)">
              {{ item.title }}
            </button>
            <p v-if="item.introduction" class="software-intro">{{ item.introduction }}</p>
            <p v-if="item.latestVersion" class="software-meta">
              最新版本 {{ item.latestVersion }}
              <span v-if="item.platforms?.length"> · {{ item.platforms.join(' / ') }}</span>
            </p>
          </div>
          <button type="button" class="ml-btn" @click="openDetail(item)">查看版本</button>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}
.category-tab {
  padding: 6px 14px;
  border: 1px solid var(--ml-border, #e5e7eb);
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
}
.category-tab.active {
  border-color: var(--ml-primary, #2e6e93);
  color: var(--ml-primary, #2e6e93);
}
.software-list {
  display: grid;
  gap: 12px;
  list-style: none;
  padding: 0;
  margin: 0;
}
.software-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--ml-border, #eee);
  border-radius: 8px;
}
.software-title {
  background: none;
  border: none;
  padding: 0;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}
.software-intro {
  margin: 6px 0;
  color: #6b7280;
}
.software-meta {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
}
</style>
