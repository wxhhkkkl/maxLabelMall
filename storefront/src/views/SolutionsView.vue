<script setup lang="ts">
import { computed, ref } from 'vue'
import EmptyState from '@/components/EmptyState.vue'
import SolutionCta from '@/components/solutions/SolutionCta.vue'
import SolutionLabel from '@/components/solutions/SolutionLabel.vue'
import SolutionStepArtwork from '@/components/solutions/SolutionStepArtwork.vue'
import { industrySolutions, solutionGroups, solutionMethod, industryArtwork } from '@/data/industrySolutions'

const query = ref('')
const selectedGroup = ref<(typeof solutionGroups)[number]>('全部行业')
const industriesSection = ref<HTMLElement | null>(null)
const filteredIndustries = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  return industrySolutions.filter((industry) =>
    (selectedGroup.value === '全部行业' || industry.group === selectedGroup.value)
    && (!keyword || [industry.name, industry.summary, ...industry.tags].join(' ').toLocaleLowerCase().includes(keyword)),
  )
})
const featuredSamples = [industrySolutions[0]!.samples[0]!, industrySolutions[2]!.samples[0]!, industrySolutions[4]!.samples[1]!]
function resetFilters() {
  query.value = ''
  selectedGroup.value = '全部行业'
}
function exploreIndustries() {
  industriesSection.value?.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
    block: 'start',
  })
}
</script>

<template>
  <div class="solutions-page">
    <section class="solution-hero solution-banner solution-overview-banner">
      <img
        class="solution-banner-photo"
        src="/assets/solutions/00-overview-v2.webp"
        alt="标签打印机、包装箱、服装吊牌与食品标签的应用场景"
        fetchpriority="high"
      >
      <div class="solution-container solution-hero-layout">
        <div class="solution-hero-copy">
          <span class="solution-eyebrow">行业标签解决方案</span>
          <h1>让标签，成为<br>业务的连接点</h1>
          <p>从商品识别到作业流转，<br class="solution-desktop-break">为不同场景设计清晰、实用的标签方案。</p>
          <div class="solution-actions">
            <button
              class="btn-primary"
              type="button"
              @click="exploreIndustries"
            >
              找到我的行业 <span aria-hidden="true">↓</span>
            </button>
            <RouterLink
              class="solution-secondary-link"
              to="/contact"
            >
              咨询定制方案 <span aria-hidden="true">↗</span>
            </RouterLink>
          </div>
        </div>
        <div class="solution-banner-tags">
          <div class="solution-media-caption">
            <span>商品身份</span><span>批次信息</span><span>流转记录</span>
          </div>
        </div>
      </div>
    </section>

    <section
      id="industries"
      ref="industriesSection"
      class="solution-section solution-container"
      aria-labelledby="industry-heading"
    >
      <div class="solution-section-heading">
        <span class="solution-eyebrow">从您的行业出发</span>
        <h2 id="industry-heading">
          找到适合您的标签方案
        </h2>
        <p>不同的作业场景，同样清晰的信息连接。</p>
      </div>
      <div class="solution-filters">
        <div
          class="solution-filter-groups"
          aria-label="行业分类"
        >
          <button
            v-for="group in solutionGroups"
            :key="group"
            type="button"
            :class="{ 'is-selected': selectedGroup === group }"
            :aria-pressed="selectedGroup === group"
            @click="selectedGroup = group"
          >
            {{ group }}
          </button>
        </div>
        <label class="solution-search">
          <span class="solution-sr-only">搜索行业或标签场景</span>
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
          ><circle
            cx="10.5"
            cy="10.5"
            r="6.5"
          /><path d="m16 16 4.5 4.5" /></svg>
          <input
            v-model="query"
            type="search"
            placeholder="搜索行业或标签场景"
          >
        </label>
      </div>
      <p
        class="solution-result-count"
        role="status"
      >
        共 {{ filteredIndustries.length }} 个行业方案
      </p>
      <div
        v-if="filteredIndustries.length"
        class="solution-industry-grid"
        :class="{ 'has-all-industries': filteredIndustries.length === 10 }"
      >
        <RouterLink
          v-for="industry in filteredIndustries"
          :key="industry.id"
          :to="`/solutions/${industry.id}`"
          class="solution-industry-card"
        >
          <div class="solution-card-photo">
            <img
              :src="industry.image"
              :alt="industry.name + '标签应用场景'"
              width="449"
              height="295"
              loading="lazy"
              decoding="async"
            >
          </div>
          <div class="solution-card-content">
            <span class="solution-card-category">{{ industry.group }}</span>
            <h3>{{ industry.name }}</h3>
            <p>{{ industry.summary }}</p>
            <div class="solution-card-tags">
              <span
                v-for="tag in industry.tags.slice(0, 2)"
                :key="tag"
              >{{ tag }}</span>
            </div>
            <span class="solution-card-link">查看方案 <span aria-hidden="true">→</span></span>
          </div>
        </RouterLink>
      </div>
      <EmptyState
        v-else
        title="暂未找到匹配的行业方案"
        desc="试试行业名称或标签类型，例如「库位」「吊牌」。"
        action-text="查看全部行业"
        @action="resetFilters"
      />
    </section>

    <section class="solution-method-section">
      <div class="solution-container solution-section">
        <div class="solution-section-heading">
          <span class="solution-eyebrow">从需求到现场</span>
          <h2>同一套方法，连接不同的行业</h2>
          <p>先理解流程，再决定标签应该如何表达。</p>
        </div>
        <ol class="solution-method-grid solution-illustrated-steps">
          <li
            v-for="([title, description], index) in solutionMethod"
            :key="title"
          >
            <SolutionStepArtwork :kind="['document', 'tag', 'printer', 'check'][index]" />
            <span class="solution-step-number">{{ String(index + 1).padStart(2, '0') }}</span>
            <h3>{{ title }}</h3><p>{{ description }}</p>
          </li>
        </ol>
      </div>
    </section>

    <section
      class="solution-container solution-section solution-showcase"
      aria-labelledby="sample-heading"
    >
      <div class="solution-section-heading">
        <span class="solution-eyebrow">让信息看得见</span>
        <h2 id="sample-heading">
          从一张标签，<br>看见完整方案
        </h2>
        <p>识别商品，突出关键变量，<br>连接每一次后续作业。</p>
        <RouterLink
          class="solution-text-link"
          to="/solutions/warehouse"
        >
          看看标签如何落地 <span aria-hidden="true">→</span>
        </RouterLink>
      </div>
      <div class="solution-sample-grid">
        <SolutionLabel
          v-for="(sample, index) in featuredSamples"
          :key="sample.name"
          :sample="sample"
          :image="industryArtwork(industrySolutions[[0, 2, 4][index]!]!, 'label', index === 2 ? 1 : 0)"
        />
      </div>
    </section>
    <SolutionCta image="/assets/solutions/00-overview.webp" />
  </div>
</template>
