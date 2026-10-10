<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { findSupportArticle, supportArticles, supportCategories } from '@/data/supportContent'
import '@/styles/support.css'
const route = useRoute()
const article = computed(() => findSupportArticle(String(route.params.slug ?? '')))
const related = computed(() => supportArticles.filter((item) => item.category === article.value?.category && item.slug !== article.value?.slug).slice(0, 3))
</script>
<template>
  <div class="support-page support-reading-page">
    <div class="support-container">
      <nav
        class="support-breadcrumbs"
        aria-label="面包屑"
      >
        <RouterLink to="/support">
          服务与支持
        </RouterLink><span aria-hidden="true">/</span><span>{{ article?.title ?? '未找到内容' }}</span>
      </nav>
      <template v-if="article">
        <header class="support-reading-header">
          <p class="support-eyebrow">
            {{ supportCategories.find((item) => item.id === article!.category)?.title }}
          </p><h1>{{ article.title }}</h1><p>{{ article.summary }}</p><span class="support-reading-scope">通用参考 · 具体操作以设备型号说明书为准</span>
        </header>
        <div class="support-reading-layout">
          <article class="support-article-body">
            <figure>
              <img
                :src="article.image"
                :alt="article.title + '场景示意'"
                width="1448"
                height="1086"
              ><figcaption>场景示意图，不代表具体型号的安装路线或结构。</figcaption>
            </figure>
            <section
              id="preparation"
              class="support-article-preparation"
            >
              <h2>开始之前</h2><ul>
                <li
                  v-for="item in article.preparation"
                  :key="item"
                >
                  {{ item }}
                </li>
              </ul>
            </section>
            <section
              v-for="(section, index) in article.sections"
              :id="'step-' + index"
              :key="section.title"
              class="support-article-section"
            >
              <p class="support-step-kicker">
                {{ String(index + 1).padStart(2, '0') }}
              </p><h2>{{ section.title }}</h2><p>{{ section.text }}</p>
            </section>
            <section
              id="verification"
              class="support-article-verification"
            >
              <h2>怎样确认结果</h2><ul>
                <li
                  v-for="item in article.checks"
                  :key="item"
                >
                  {{ item }}
                </li>
              </ul>
            </section>
            <section class="support-article-next">
              <h2>仍然没有解决？</h2><p>整理设备型号、连接方式、已尝试的操作与脱敏后的样张，便于进一步沟通。</p><RouterLink
                class="support-button"
                to="/support/contact"
              >
                联系支持 →
              </RouterLink>
            </section>
          </article>
          <aside class="support-reading-sidebar">
            <nav aria-label="文章目录">
              <p>本篇内容</p><a href="#preparation">开始之前</a><a
                v-for="(section, index) in article.sections"
                :key="section.title"
                :href="'#step-' + index"
              >{{ section.title }}</a><a href="#verification">怎样确认结果</a>
            </nav><div>
              <h3>继续阅读</h3><RouterLink
                v-for="item in related"
                :key="item.slug"
                :to="'/support/' + item.slug"
              >
                {{ item.title }} →
              </RouterLink><RouterLink to="/support#knowledge">
                返回帮助中心 →
              </RouterLink>
            </div>
          </aside>
        </div>
      </template>
      <div
        v-else
        class="support-not-found"
      >
        <h1>未找到这篇帮助内容</h1><p>链接可能有误，试试在帮助中心搜索相关问题。</p><RouterLink
          class="support-button"
          to="/support"
        >
          返回服务与支持
        </RouterLink>
      </div>
    </div>
  </div>
</template>
