<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { findSupportArticle, searchSupportArticles, supportArticles, supportCategories, supportFaqs, supportIssueTabs } from '@/data/supportContent'
import '@/styles/support.css'
import SupportContactInfo from '@/components/SupportContactInfo.vue'

const route = useRoute()
const router = useRouter()
const query = computed(() => typeof route.query.q === 'string' ? route.query.q : '')
const input = ref(query.value)
watch(query, (value) => { input.value = value })
const category = computed(() => supportCategories.some((c) => c.id === route.query.category) ? String(route.query.category) : 'all')
const results = computed(() => searchSupportArticles(query.value, category.value))
const expanded = ref(false)
const visibleArticles = computed(() => query.value || category.value !== 'all' || expanded.value ? results.value : results.value.slice(0, 6))
const issueTab = ref('connection')
function moveIssueTab(event: KeyboardEvent) {
  const index = supportIssueTabs.findIndex((tab) => tab.id === issueTab.value)
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? supportIssueTabs.length - 1 : event.key === 'ArrowRight' ? (index + 1) % supportIssueTabs.length : event.key === 'ArrowLeft' ? (index + supportIssueTabs.length - 1) % supportIssueTabs.length : -1
  if (next < 0) return
  event.preventDefault()
  issueTab.value = supportIssueTabs[next]!.id
  document.getElementById('tab-' + issueTab.value)?.focus()
}
const issues = computed(() => supportIssueTabs.find((tab) => tab.id === issueTab.value)!.slugs.map((slug) => findSupportArticle(slug)!))
const guides = ['load-labels', 'paper-size', 'first-print'].map((slug) => findSupportArticle(slug)!)
const materials = supportArticles.filter((article) => article.category === 'materials')
const quickLinks = [
  { title: '设备与驱动', text: '先核对型号，再找适用资料', to: '#device-resources', icon: 'M6 9V3h12v6M6 17H3V9h18v8h-3M6 14h12v7H6v-7Z' },
  { title: '使用指南', text: '从基础安装到首次测试', to: '#basic-guides', icon: 'M6 2h9l4 4v16H6V2ZM14 2v5h5M9 11h7M9 15h7M9 19h4' },
  { title: '打印问题', text: '按现象查找排查方向', to: '#printing-issues', icon: 'm12 3 10 18H2L12 3ZM12 9v5M12 17v1' },
  { title: '联系支持', text: '电话与微信，直接联系', to: '#contact-support', icon: 'M3 4h18v13H9l-5 4v-4H3V4ZM7 10h1M11 10h1M15 10h1' },
]
async function filter(nextQuery = input.value, nextCategory = category.value) {
  input.value = nextQuery
  expanded.value = false
  await router.push({ path: '/support', query: { ...(nextQuery.trim() ? { q: nextQuery.trim() } : {}), ...(nextCategory !== 'all' ? { category: nextCategory } : {}), }, hash: '#knowledge' })
  await nextTick()
  document.getElementById('knowledge')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="support-page">
    <section
      class="support-hero"
      aria-labelledby="support-heading"
    >
      <img
        class="support-hero-image"
        src="/assets/support/support-hero.webp"
        alt="整洁工作台上的标签打印机与标签纸安装场景示意"
        width="1942"
        height="809"
        fetchpriority="high"
      >
      <div class="support-container">
        <div class="support-hero-copy">
          <p class="support-eyebrow">
            服务与支持
          </p>
          <h1 id="support-heading">
            让每一次打印<span>都有清楚的答案</span>
          </h1>
          <p>从设备设置到日常使用，找到你需要的帮助。</p>
          <form
            class="support-search"
            role="search"
            @submit.prevent="filter()"
          >
            <label
              class="support-sr-only"
              for="support-search"
            >搜索帮助内容</label>
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              aria-hidden="true"
            ><circle
              cx="10"
              cy="10"
              r="7"
            /><path d="m15 15 6 6" /></svg>
            <input
              id="support-search"
              v-model="input"
              type="search"
              placeholder="搜索问题、操作或耗材关键词"
              maxlength="100"
            >
            <button type="submit">
              搜索
            </button>
          </form>
          <div class="support-hot">
            <span>常见搜索：</span><button
              v-for="word in ['打印偏移', '设备连接', '纸张尺寸']"
              :key="word"
              type="button"
              @click="filter(word, 'all')"
            >
              {{ word }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <div class="support-container support-body">
      <nav
        class="support-quick"
        aria-label="帮助分类"
      >
        <RouterLink
          v-for="link in quickLinks"
          :key="link.title"
          :to="link.to.startsWith('#') ? '/support' + link.to : link.to"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.4"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path :d="link.icon" /></svg>
          <div><strong>{{ link.title }}</strong><span>{{ link.text }}</span></div>
        </RouterLink>
      </nav>

      <section
        id="printing-issues"
        class="support-section support-problems"
        aria-labelledby="issues-heading"
      >
        <div>
          <h2 id="issues-heading">
            先从你遇到的问题开始
          </h2><p class="support-description">
            选择问题类型，查看排查方向。
          </p>
          <div
            class="support-tabs"
            role="tablist"
            aria-label="打印问题分类"
            @keydown="moveIssueTab"
          >
            <button
              v-for="tab in supportIssueTabs"
              :id="'tab-' + tab.id"
              :key="tab.id"
              type="button"
              role="tab"
              :aria-selected="issueTab === tab.id"
              :tabindex="issueTab === tab.id ? 0 : -1"
              aria-controls="issue-panel"
              @click="issueTab = tab.id"
            >
              {{ tab.name }}
            </button>
          </div>
          <div
            id="issue-panel"
            class="support-issue-list"
            role="tabpanel"
            :aria-labelledby="'tab-' + issueTab"
          >
            <RouterLink
              v-for="(article, index) in issues"
              :key="article.slug"
              :to="'/support/' + article.slug"
            >
              <span class="support-issue-number">0{{ index + 1 }}</span><div><h3>{{ article.title }}</h3><p>{{ article.summary }}</p></div><span
                class="support-arrow"
                aria-hidden="true"
              >→</span>
            </RouterLink>
          </div>
        </div>
        <aside class="support-help-panel">
          <h3>不知道从哪里开始？</h3><p>准备设备型号、问题描述与打印样张，便于进一步沟通。</p><ul><li>设备完整型号</li><li>系统与连接方式</li><li>问题照片与已尝试操作</li></ul><RouterLink
            class="support-button"
            to="/support/contact"
          >
            联系支持 <span aria-hidden="true">→</span>
          </RouterLink>
        </aside>
      </section>

      <section
        id="basic-guides"
        class="support-section"
        aria-labelledby="guides-heading"
      >
        <div class="support-section-head">
          <div>
            <h2 id="guides-heading">
              把设备用好，从基础开始
            </h2><p class="support-description">
              先把安装、尺寸与测试这几件事理清。
            </p>
          </div><button
            class="support-text-link"
            type="button"
            @click="filter('', 'guides')"
          >
            查看全部使用指南 →
          </button>
        </div>
        <div class="support-guide-grid">
          <RouterLink
            v-for="article in guides"
            :key="article.slug"
            class="support-guide-card"
            :to="'/support/' + article.slug"
          >
            <img
              :src="article.image"
              :alt="article.title + '场景示意'"
              width="1448"
              height="1086"
              loading="lazy"
            ><div><h3>{{ article.title }}</h3><p>{{ article.summary }}</p><span class="support-text-link">阅读指南 →</span></div>
          </RouterLink>
        </div>
      </section>

      <section
        id="device-resources"
        class="support-resource-strip"
        aria-labelledby="resources-heading"
      >
        <div>
          <p class="support-eyebrow">
            设备与驱动资料
          </p><h2 id="resources-heading">
            认准型号，再安装
          </h2><p>准备完整型号、系统版本与连接方式，先确认资料是否适用。</p><small>具体型号的驱动与说明书正在整理中。</small>
        </div><div class="support-resource-actions">
          <RouterLink
            class="support-button"
            to="/support/driver-install"
          >
            查看驱动安装指南 →
          </RouterLink><RouterLink
            class="support-text-link"
            to="/support/contact"
          >
            联系技术支持 →
          </RouterLink>
        </div>
      </section>

      <section
        class="support-section support-materials"
        aria-labelledby="materials-heading"
      >
        <div>
          <p class="support-eyebrow">
            耗材选型
          </p><h2 id="materials-heading">
            选对耗材，打印更从容
          </h2><p class="support-description">
            从打印方式、纸卷规格到贴附环境，逐项核对。
          </p><img
            src="/assets/support/paper-size.webp"
            alt="标签纸卷与测量尺的选型场景示意"
            width="1448"
            height="1086"
            loading="lazy"
          >
        </div><div class="support-material-list">
          <RouterLink
            v-for="(article, index) in materials"
            :key="article.slug"
            :to="'/support/' + article.slug"
          >
            <span>0{{ index + 1 }}</span><div><h3>{{ article.title }}</h3><p>{{ article.summary }}</p></div><b aria-hidden="true">↗</b>
          </RouterLink>
        </div>
      </section>

      <section
        id="knowledge"
        class="support-section"
        aria-labelledby="knowledge-heading"
      >
        <div class="support-section-head">
          <div>
            <h2 id="knowledge-heading">
              帮助内容，一处查找
            </h2><p class="support-description">
              基础使用、问题排查与耗材选型。
            </p>
          </div><button
            v-if="query || category !== 'all'"
            type="button"
            class="support-text-link"
            @click="filter('', 'all')"
          >
            清除筛选 →
          </button>
        </div>
        <div class="support-filter">
          <button
            v-for="item in supportCategories"
            :key="item.id"
            type="button"
            :aria-pressed="category === item.id"
            @click="filter(query, item.id)"
          >
            {{ item.title }}
          </button>
        </div>
        <p
          class="support-result-count"
          role="status"
        >
          {{ query ? '“' + query + '” · ' : '' }}找到 {{ results.length }} 篇内容
        </p>
        <div
          v-if="results.length"
          class="support-article-list"
        >
          <RouterLink
            v-for="article in visibleArticles"
            :key="article.slug"
            :to="'/support/' + article.slug"
          >
            <span class="support-article-category">{{ supportCategories.find((item) => item.id === article.category)?.title }}</span><div><h3>{{ article.title }}</h3><p>{{ article.summary }}</p></div><span aria-hidden="true">→</span>
          </RouterLink>
        </div>
        <div
          v-else
          class="support-no-results"
        >
          <h3>暂未找到匹配的内容</h3><p>试试“偏移”“USB”或“碳带”，也可以整理问题后联系支持。</p><button
            class="support-button"
            type="button"
            @click="filter('', 'all')"
          >
            查看全部内容
          </button>
        </div>
        <button
          v-if="visibleArticles.length < results.length"
          type="button"
          class="support-show-more"
          @click="expanded = true"
        >
          展开全部 {{ results.length }} 篇内容 <span aria-hidden="true">↓</span>
        </button>
      </section>

      <section
        class="support-section"
        aria-labelledby="faq-heading"
      >
        <h2 id="faq-heading">
          你可能还想知道
        </h2><p class="support-description">
          先看简短回答，再按需阅读详细说明。
        </p><div class="support-faqs">
          <details
            v-for="(faq, index) in supportFaqs"
            :key="faq.q"
            :open="index === 0"
          >
            <summary>{{ faq.q }}<span aria-hidden="true">＋</span></summary><p>{{ faq.a }}</p><RouterLink :to="faq.slug ? '/support/' + faq.slug : faq.to!">
              {{ faq.slug ? '阅读详细说明' : '查看相关信息' }} →
            </RouterLink>
          </details>
        </div>
      </section>
      <section
        id="contact-support"
        class="support-section support-direct-contact"
        aria-labelledby="contact-heading"
      >
        <h2 id="contact-heading">
          问题还没有解决？直接联系我们
        </h2>
        <p class="support-description">
          设备使用、打印问题与耗材选择，欢迎通过电话或微信沟通。
        </p>
        <SupportContactInfo compact />
      </section>
    </div>
  </div>
</template>
