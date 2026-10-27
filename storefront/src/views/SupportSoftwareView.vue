<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listAllSoftware, type SoftwareDetail } from '@/api/software'

const items = ref<SoftwareDetail[]>([])
const loading = ref(true)
const error = ref('')

const formatSize = (size: number) => {
  if (!size) return '—'
  const mb = size / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(size / 1024).toFixed(0)} KB`
}

onMounted(() => {
  listAllSoftware()
    .then((data) => {
      items.value = data ?? []
    })
    .catch(() => {
      error.value = '加载失败，请稍后重试'
    })
    .finally(() => {
      loading.value = false
    })
})
</script>

<template>
  <div class="ml-page">
    <section class="ml-card">
      <h1 class="ml-card-title">软件与驱动下载</h1>
      <p v-if="error" class="text-red-600">{{ error }}</p>
      <p v-if="!loading && items.length === 0">暂无可用下载</p>

      <!-- 一屏平铺：每个软件一块，版本直接列在下面，点「下载」即走 OSS 直链 -->
      <div v-for="item in items" :key="item.id" class="software-block" data-testid="software-block">
        <div class="software-head">
          <h2 class="software-title">{{ item.title }}</h2>
          <p v-if="item.introduction" class="software-intro">{{ item.introduction }}</p>
        </div>
        <table v-if="item.versions?.length" class="version-table">
          <thead>
            <tr>
              <th>版本</th>
              <th>系统</th>
              <th>架构</th>
              <th>大小</th>
              <th>下载</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="version in item.versions" :key="version.platform + version.arch">
              <td>{{ version.version }}</td>
              <td>{{ version.platform }}</td>
              <td>{{ version.arch }}</td>
              <td>{{ formatSize(version.fileSize) }}</td>
              <td>
                <a :href="version.downloadUrl" rel="noopener noreferrer" target="_blank" class="download">
                  下载
                </a>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="software-intro">暂无可用版本</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.software-block {
  padding: 16px 0;
  border-bottom: 1px solid var(--ml-border, #eee);
}
.software-block:last-child {
  border-bottom: none;
}
.software-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}
.software-intro {
  margin: 6px 0 10px;
  color: #6b7280;
  font-size: 14px;
}
.version-table {
  width: 100%;
  border-collapse: collapse;
}
.version-table th,
.version-table td {
  text-align: left;
  padding: 8px;
  border-bottom: 1px solid var(--ml-border, #f2f2f2);
  font-size: 14px;
}
.download {
  color: var(--ml-primary, #2e6e93);
}
</style>
