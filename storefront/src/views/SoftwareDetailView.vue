<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getSoftwareDetail, type SoftwareDetail } from '@/api/software'

const route = useRoute()
const detail = ref<SoftwareDetail | null>(null)
const loading = ref(true)
const error = ref('')

const formatSize = (size: number) => {
  if (!size) return '—'
  const mb = size / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(size / 1024).toFixed(0)} KB`
}

onMounted(() => {
  // 用 then/catch 而非 async/await：保证失败处理器在同一个微任务里就挂上，
  // 否则调用方拿到的是"未处理的 rejection"。
  getSoftwareDetail(Number(route.params.id))
    .then((data) => {
      if (!data) {
        error.value = '该软件不存在或已下架'
        return
      }
      detail.value = data
    })
    .catch(() => {
      error.value = '该软件不存在或已下架'
    })
    .finally(() => {
      loading.value = false
    })
})
</script>

<template>
  <div class="ml-page">
    <section class="ml-card">
      <p v-if="error" class="text-red-600" data-testid="software-error">{{ error }}</p>
      <template v-else-if="detail">
        <h1 class="ml-card-title">{{ detail.title }}</h1>
        <p v-if="detail.introduction" class="intro">{{ detail.introduction }}</p>

        <table v-if="detail.versions?.length" class="version-table" data-testid="version-table">
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
            <tr v-for="version in detail.versions" :key="version.platform + version.arch">
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
        <p v-else>暂无可用版本</p>
      </template>
    </section>
  </div>
</template>

<style scoped>
.intro {
  color: #6b7280;
  margin: 8px 0 16px;
}
.version-table {
  width: 100%;
  border-collapse: collapse;
}
.version-table th,
.version-table td {
  text-align: left;
  padding: 10px 8px;
  border-bottom: 1px solid var(--ml-border, #eee);
}
.download {
  color: var(--ml-primary, #2e6e93);
}
</style>
