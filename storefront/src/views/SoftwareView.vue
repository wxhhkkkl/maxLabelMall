<script setup lang="ts">
import CoatingLabel from '@/components/software/CoatingLabel.vue'
import SoftwarePreview from '@/components/software/SoftwarePreview.vue'
import { coatingExamples, softwareApplications, softwareExperiences } from '@/data/softwareIntroduction'

const featureProduct = { name: '水性工业涂料', color: 'RAL7035', batch: 'TL-001', swatch: '#B4C0D8' }
</script>

<template>
  <div class="software-page">
    <section
      class="software-hero"
      aria-labelledby="software-title"
    >
      <div class="software-container">
        <p class="software-eyebrow">
          MaxLabel 标签设计软件
        </p>
        <h1 id="software-title">
          让标签设计<span>极致简单</span>
        </h1>
        <p class="hero-description">
          从第一张标签开始，让设计、数据与打印更直观。
        </p>
        <SoftwarePreview
          class="hero-preview"
          announcement
        />
      </div>
    </section>
    <div class="software-container software-content">
      <section
        class="software-intro"
        aria-labelledby="intro-title"
      >
        <h2 id="intro-title">
          简单，从第一张标签开始
        </h2>
        <p class="section-description">
          少一些繁琐，多一些直观。
        </p>
        <div class="experience-list">
          <div
            v-for="item in softwareExperiences"
            :key="item.title"
            class="experience-item"
          >
            <div class="experience-icon">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              ><path :d="item.path" /></svg>
            </div>
            <div><h3>{{ item.title }}</h3><p>{{ item.description }}</p></div>
          </div>
        </div>
      </section>
      <section
        class="design-experience"
        aria-labelledby="design-title"
      >
        <div class="experience-media">
          <SoftwarePreview class="experience-editor" />
          <img
            class="experience-photo"
            src="/assets/software/paint.webp"
            alt="涂料生产企业的白色产品桶与蓝色桶盖"
            width="1448"
            height="1086"
            loading="lazy"
          >
          <CoatingLabel
            class="feature-label"
            :product="featureProduct"
            detailed
          />
          <span class="development-badge">界面预览 · 开发中</span>
        </div>
        <div class="design-copy">
          <p class="feature-eyebrow">
            设计体验
          </p>
          <h2 id="design-title">
            把想法，放到标签上
          </h2>
          <p class="section-description">
            正在打磨更直观的排版方式，让文字、条码与图形各就其位。
          </p>
          <ul><li>看得见的尺寸与位置</li><li>清楚的内容层级</li><li>可重复使用的标签版式</li></ul>
        </div>
      </section>
      <section
        class="software-data"
        aria-labelledby="data-title"
      >
        <h2 id="data-title">
          一份数据，多张不同的标签
        </h2>
        <p class="section-description">
          将产品、色号与批次对应起来，为批量打印做好准备。
        </p>
        <div class="data-demonstration">
          <div class="data-source">
            <div class="data-table-wrap">
              <table>
                <caption class="visually-hidden">
                  涂料企业产品数据示例，与右侧三张标签一一对应
                </caption>
                <thead>
                  <tr>
                    <th scope="col">
                      产品
                    </th><th scope="col">
                      色号
                    </th><th scope="col">
                      批次
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="product in coatingExamples"
                    :key="product.batch"
                  >
                    <td>{{ product.name }}</td><td>{{ product.color }}</td><td>{{ product.batch }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p class="example-note">
              数据绑定示意
            </p>
          </div>
          <div
            class="data-connector"
            aria-hidden="true"
          >
            <svg
              viewBox="0 0 70 150"
              fill="none"
            ><path
              d="M2 75h15q15 0 15-15V25q0-10 12-10h20M2 75h62M2 75h15q15 0 15 15v35q0 10 12 10h20"
              stroke="currentColor"
              stroke-width="1.5"
            /><path
              d="m59 11 6 4-6 4m0 52 6 4-6 4m0 52 6 4-6 4"
              stroke="currentColor"
              stroke-width="1.5"
            /><circle
              cx="2"
              cy="75"
              r="2"
              fill="currentColor"
            /></svg>
          </div>
          <div class="data-labels">
            <CoatingLabel
              v-for="product in coatingExamples"
              :key="product.batch"
              :product="product"
            />
          </div>
        </div>
      </section>
      <section
        class="software-applications"
        aria-labelledby="applications-title"
      >
        <h2 id="applications-title">
          小小标签，也值得认真设计
        </h2>
        <p class="section-description">
          从生产包装到商品陈列与仓储，让信息表达更清楚。
        </p>
        <div class="application-grid">
          <figure
            v-for="item in softwareApplications"
            :key="item.title"
            class="application-example"
          >
            <img
              :src="item.image"
              :alt="item.alt"
              width="1448"
              height="1086"
              loading="lazy"
            >
            <figcaption><h3>{{ item.title }}</h3><p>{{ item.description }}</p></figcaption>
          </figure>
        </div>
      </section>
      <section
        class="software-coming"
        aria-labelledby="coming-title"
      >
        <SoftwarePreview class="coming-preview" />
        <div class="coming-copy">
          <h2 id="coming-title">
            更简单的标签设计，正在到来
          </h2><p>MaxLabel 正在打磨中，感谢您的关注与期待。</p><span>敬请期待</span>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped src="@/styles/software-introduction.css"></style>
