<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listAllSoftware, type SoftwareDetail } from '@/api/software'

const items = ref<SoftwareDetail[]>([])
const loading = ref(true)
const error = ref('')
const versionCount = computed(() => items.value.reduce((sum, item) => sum + (item.versions?.length || 0), 0))
const formatSize = (size: number) => {
  if (!size) return '—'
  const mb = size / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(size / 1024).toFixed(0)} KB`
}
const platformLabel = (platform: string) => ({ windows: 'Windows', macos: 'macOS', linux: 'Linux', android: 'Android' }[platform.toLowerCase()] || platform)
async function load() {
  loading.value = true
  error.value = ''
  try { items.value = (await listAllSoftware()) ?? [] }
  catch { error.value = '加载失败，请稍后重试' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="driver-page">
    <div class="driver-container">
      <section class="driver-hero">
        <div class="driver-hero-copy"><span class="driver-eyebrow">DOWNLOAD CENTER</span><h1>准备就绪，<br>让打印顺利开始。</h1><p>选择适合设备型号与操作系统的版本，直接下载。</p><div class="driver-hero-actions"><a class="driver-primary" href="#downloads">查找下载 <span>↓</span></a></div></div>
        <div class="driver-hero-art">
          <svg viewBox="0 0 460 270" role="img" aria-label="电脑通过驱动连接标签打印机的示意图">
            <path d="M115 215h280M230 190v-26h75" stroke="#b4c0d8" stroke-width="2" stroke-dasharray="5 5" fill="none" />
            <rect x="40" y="47" width="215" height="142" rx="12" fill="#ffffff" stroke="#b4c0d8" stroke-width="2" /><rect x="50" y="57" width="195" height="119" rx="5" fill="#eaf1ff" />
            <path d="M20 189h255l-12 15H37z" fill="#e3e9f4" stroke="#b4c0d8" />
            <rect x="93" y="82" width="110" height="67" rx="8" fill="#ffffff" /><circle cx="116" cy="104" r="10" fill="#2e7cd6" /><path d="M112 104l3 3 6-7" stroke="#ffffff" fill="none" stroke-width="2" /><path d="M138 101h45m-45 9h30m-64 23h78" stroke="#b4c0d8" stroke-width="3" stroke-linecap="round" />
            <rect x="289" y="104" width="132" height="91" rx="12" fill="#e3e9f4" stroke="#8a97b5" stroke-width="2" /><path d="M304 104V82q0-10 10-10h80q10 0 10 10v22" fill="#ffffff" stroke="#8a97b5" stroke-width="2" /><rect x="302" y="136" width="107" height="9" rx="4" fill="#16233f" /><circle cx="399" cy="122" r="4" fill="#2e7cd6" />
            <path d="M319 140h72v91h-72z" fill="#ffffff" stroke="#b4c0d8" /><rect x="329" y="152" width="52" height="21" rx="4" fill="#eaf1ff" /><path d="M331 186v25m4-25v25m5-25v25m6-25v25m4-25v25m6-25v25m5-25v25m6-25v25m6-25v25m4-25v25" stroke="#16233f" stroke-width="2" />
            <circle cx="274" cy="57" r="22" fill="#2e7cd6" /><path d="M264 57l7 7 13-15" stroke="#ffffff" fill="none" stroke-width="3" stroke-linecap="round" />
          </svg><span>连接设备 · 安装驱动 · 测试打印</span>
        </div>
      </section>
          <section id="downloads" class="driver-download-section">
            <div class="driver-section-heading"><div><span class="driver-eyebrow">下载中心</span><h2>软件与驱动下载</h2><p>核对型号、系统和架构，选择对应版本直接下载。</p></div><span v-if="!loading && !error" class="driver-count">{{ versionCount }} 个可用下载</span></div>
            <div v-if="loading" class="driver-state" role="status"><span class="driver-loader" />正在获取可用版本…</div>
            <div v-else-if="error" class="driver-state" role="alert"><b>{{ error }}</b><p>请检查网络连接后重试。</p><button class="driver-primary" type="button" @click="load">重新加载</button></div>
            <div v-else-if="!items.length" class="driver-state"><b>暂无可用下载</b><p>当前没有已发布的安装包，请稍后再试。</p></div>
            <div v-else class="driver-download-list">
              <article v-for="item in items" :key="item.id" class="software-block" data-testid="software-block">
                <div class="software-head"><span class="driver-package-icon" aria-hidden="true">{{ item.type === 2 ? '▤' : '▦' }}</span><div><span class="driver-type">{{ item.type === 2 ? '设备驱动' : '应用软件' }}</span><h3 class="software-title">{{ item.title }}</h3><p v-if="item.introduction" class="software-intro">{{ item.introduction }}</p></div></div>
                <div v-if="item.versions?.length" class="driver-versions">
                  <div v-for="(version, index) in item.versions" :key="version.platform + version.arch + version.version + index" class="driver-version">
                    <div class="driver-version-system"><b>{{ platformLabel(version.platform) }}</b><span>{{ version.arch }}</span></div>
                    <div class="driver-version-detail"><span>版本 {{ version.version }}</span><span>{{ formatSize(version.fileSize) }}</span><span v-if="version.releaseTime">{{ version.releaseTime.slice(0, 10) }}</span></div>
                    <a :href="version.downloadUrl" rel="noopener noreferrer" target="_blank" class="download" :aria-label="`下载 ${item.title} ${version.platform} ${version.arch} ${version.version}`">下载 <span aria-hidden="true">↓</span></a>
                  </div>
                </div><p v-else class="driver-no-version">暂无可用版本</p>
              </article>
            </div>
          </section>
    </div>
  </div>
</template>

<style scoped>
.driver-page { padding-top: 32px; background: #f5f8ff; color: #16233f; padding-bottom: 64px; }
.driver-container { width: min(1200px, calc(100% - 64px)); margin-inline: auto; }
.driver-hero { margin-bottom: 36px; display: grid; grid-template-columns: 1.1fr 1fr; gap: 30px; align-items: center; padding: 44px 48px; background: #eaf1ff; border: 1px solid #e3e9f4; border-radius: 16px; }
.driver-eyebrow { color: #2e7cd6; font-size: 11px; letter-spacing: 1.5px; font-weight: 600; }
.driver-hero h1 { font-size: clamp(32px, 3.3vw, 46px); line-height: 1.3; letter-spacing: -1.5px; margin: 16px 0; }.driver-hero-copy > p { color: #5b6c8f; line-height: 1.9; font-size: 14px; margin: 0 0 26px; }
.driver-hero-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 24px; }.driver-primary { display: inline-flex; align-items: center; justify-content: center; gap: 28px; padding: 12px 22px; border: 0; border-radius: 7px; color: #ffffff; background: #2e7cd6; font-size: 13px; text-decoration: none; cursor: pointer; }
.driver-hero-art svg { display: block; width: 100%; }.driver-hero-art > span { display: block; text-align: center; color: #5b6c8f; font-size: 11px; letter-spacing: 2px; }.driver-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 22px; }.driver-section-heading h2 { font-size: 25px; line-height: 1.5; margin: 8px 0; letter-spacing: -.5px; }.driver-section-heading p { color: #5b6c8f; font-size: 13px; line-height: 1.8; margin: 0; }.driver-count { color: #8a97b5; font-size: 11px; white-space: nowrap; }
.driver-download-list { display: grid; gap: 18px; }.software-block { border: 1px solid #e3e9f4; border-radius: 12px; background: #ffffff; overflow: hidden; }.software-head { padding: 24px; display: flex; align-items: start; gap: 16px; }.driver-package-icon { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 48px; height: 48px; border-radius: 12px; background: #eaf1ff; color: #2e7cd6; font-size: 26px; }.driver-type { color: #8a97b5; font-size: 11px; }.software-title { font-size: 19px; margin: 4px 0 0; overflow-wrap: anywhere; }.software-intro { color: #5b6c8f; font-size: 12px; line-height: 1.8; margin: 8px 0 0; }
.driver-versions { padding: 0 24px; }.driver-version { display: grid; grid-template-columns: 125px 1fr auto; gap: 16px; align-items: center; padding: 18px 0; border-top: 1px solid #e3e9f4; }.driver-version-system { display: flex; flex-direction: column; gap: 6px; font-size: 13px; }.driver-version-system > span { font-size: 11px; color: #8a97b5; }.driver-version-detail { display: flex; gap: 10px 16px; flex-wrap: wrap; color: #5b6c8f; font-size: 12px; overflow-wrap: anywhere; }.download { display: inline-flex; align-items: center; gap: 20px; padding: 9px 16px; border-radius: 6px; border: 1px solid #e3e9f4; background: #f5f8ff; color: #2e7cd6; font-size: 12px; text-decoration: none; white-space: nowrap; }.download:hover { background: #2e7cd6; border-color: #2e7cd6; color: #ffffff; }.driver-no-version { padding: 0 24px 20px; color: #8a97b5; font-size: 13px; }
.driver-state { padding: 40px; border: 1px dashed #b4c0d8; border-radius: 12px; background: #ffffff; color: #5b6c8f; text-align: center; font-size: 14px; }.driver-state p { font-size: 13px; line-height: 1.8; }.driver-loader { display: inline-block; width: 12px; height: 12px; border: 2px solid #e3e9f4; border-top-color: #2e7cd6; border-radius: 50%; margin-right: 8px; animation: driver-spin 1s linear infinite; }@keyframes driver-spin { to { transform: rotate(360deg); } }
.driver-page :is(a, button, summary):focus-visible { outline: 3px solid #35a5ff; outline-offset: 4px; }.driver-page section[id] { scroll-margin-top: 100px; }
@media (max-width: 1100px) {.driver-hero { padding: 36px; }.driver-version { grid-template-columns: 100px 1fr auto; gap: 10px; } }
@media (max-width: 768px) { .driver-container { width: calc(100% - 40px); }.driver-hero { grid-template-columns: 1fr; padding: 28px; gap: 0; }.driver-hero-art { display: none; }.driver-hero h1 { font-size: 34px; }.driver-section-heading h2 { font-size: 23px; } }
@media (max-width: 480px) { .driver-container { width: calc(100% - 32px); }.driver-hero { padding: 24px; }.driver-hero-actions { gap: 18px; }.driver-section-heading { align-items: start; }.driver-section-heading h2 { font-size: 21px; }.driver-count { margin-top: 5px; }.software-head { padding: 20px; gap: 12px; }.driver-versions { padding: 0 20px; }.driver-version { grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }.driver-version-system { flex-direction: row; gap: 8px; align-items: center; }.driver-version-detail { grid-column: 1; }.download { grid-column: 2; grid-row: 1 / 3; } }
@media (prefers-reduced-motion: reduce) { .driver-loader { animation: none; } }
</style>
