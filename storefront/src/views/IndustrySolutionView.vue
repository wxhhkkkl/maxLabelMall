<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import SolutionCta from '@/components/solutions/SolutionCta.vue'
import SolutionLabel from '@/components/solutions/SolutionLabel.vue'
import SolutionWorkflow from '@/components/solutions/SolutionWorkflow.vue'
import SolutionStepArtwork from '@/components/solutions/SolutionStepArtwork.vue'
import { findIndustry, industrySolutions, industryArtwork } from '@/data/industrySolutions'

const route = useRoute()
const industry = computed(() => findIndustry(String(route.params.industry ?? '')))
const relatedIndustries = computed(() =>
  industrySolutions.filter((item) => item.id !== industry.value?.id && item.group === industry.value?.group).slice(0, 3),
)
</script>

<template>
  <div class="solutions-page solution-detail-page">
    <template v-if="industry">
      <nav
        class="solution-container solution-breadcrumbs"
        aria-label="面包屑"
      >
        <RouterLink to="/solutions">
          行业方案
        </RouterLink><span aria-hidden="true">/</span><span aria-current="page">{{ industry.name }}</span>
      </nav>
      <section class="solution-hero solution-detail-hero solution-banner">
        <picture>
          <source
            media="(max-width: 768px)"
            :srcset="industry.image"
          >
          <img
            class="solution-banner-photo"
            :src="industryArtwork(industry, 'banner')"
            :alt="industry.name + '标签应用场景'"
            fetchpriority="high"
          >
        </picture>
        <div class="solution-container solution-hero-layout">
          <div class="solution-hero-copy">
            <span class="solution-eyebrow">{{ industry.name }}标签解决方案</span>
            <h1 class="solution-detail-title">
              {{ industry.headline }}
            </h1>
            <p>{{ industry.intro }}</p>
            <div class="solution-actions">
              <RouterLink
                class="btn-primary"
                to="/contact"
              >
                咨询行业方案 <span aria-hidden="true">↗</span>
              </RouterLink>
              <RouterLink
                class="solution-secondary-link"
                to="/solutions"
              >
                查看全部行业
              </RouterLink>
            </div>
          </div>
          <div class="solution-banner-tags">
            <div class="solution-media-caption">
              <span
                v-for="tag in industry.tags"
                :key="tag"
              >{{ tag }}</span>
            </div>
          </div>
        </div>
      </section>
      <section class="solution-container solution-section solution-challenges-section">
        <div class="solution-section-heading">
          <span class="solution-eyebrow">理解现场，才能解决问题</span><h2>{{ industry.name }}的常见挑战</h2>
        </div>
        <div class="solution-pain-grid">
          <article
            v-for="([title, description], index) in industry.pains"
            :key="title"
          >
            <SolutionStepArtwork :kind="['document', 'clock', 'tag'][index]" />
            <span class="solution-pain-number">{{ String(index + 1).padStart(2, '0') }}</span>
            <h3>{{ title }}</h3><p>{{ description }}</p>
            <img
              v-if="industry.id === 'food'"
              class="solution-pain-photo"
              :src="industryArtwork(industry, 'pain', index)"
              :alt="title + '场景示意'"
              loading="lazy"
            >
          </article>
        </div>
      </section>
      <SolutionWorkflow :industry="industry" />
      <section class="solution-container solution-section">
        <div class="solution-section-heading">
          <span class="solution-eyebrow">不同环节，不同表达</span><h2>标签，落在真实场景里</h2><p>版式与字段示例，可根据实际商品、设备和材料调整。</p>
        </div>
        <div class="solution-detail-samples">
          <SolutionLabel
            v-for="(sample, index) in industry.samples"
            :key="sample.name"
            :sample="sample"
            :image="industryArtwork(industry, 'label', index)"
          />
        </div>
      </section>
      <section class="solution-container solution-highlights">
        <div class="solution-section-heading">
          <span class="solution-eyebrow">值得多想一步的细节</span><h2>贴合现场的方案思路</h2>
        </div>
        <div class="solution-highlight-grid">
          <article
            v-for="([title, description], index) in industry.highlights"
            :key="title"
          >
            <SolutionStepArtwork :kind="index === 0 ? 'link' : 'check'" />
            <span class="solution-step-number">{{ String(index + 1).padStart(2, '0') }}</span><h3>{{ title }}</h3><p>{{ description }}</p>
          </article>
        </div>
      </section>
      <SolutionCta :image="industryArtwork(industry, 'banner')" />
      <section
        v-if="relatedIndustries.length"
        class="solution-container solution-related"
      >
        <h2>探索相近行业</h2>
        <div class="solution-related-grid">
          <RouterLink
            v-for="item in relatedIndustries"
            :key="item.id"
            :to="`/solutions/${item.id}`"
          >
            <img
              :src="item.image"
              :alt="item.name + '应用场景'"
              loading="lazy"
            >
            <span>{{ item.name }}</span> <span aria-hidden="true">→</span>
          </RouterLink>
        </div>
      </section>
    </template>
    <section
      v-else
      class="solution-container solution-section"
    >
      <EmptyState
        title="未找到这个行业方案"
        desc="请返回行业总览，选择适合您的场景。"
      />
      <RouterLink
        class="solution-text-link"
        to="/solutions"
      >
        返回行业方案 <span aria-hidden="true">→</span>
      </RouterLink>
    </section>
  </div>
</template>
