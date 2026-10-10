<script setup lang="ts">
import SupportContactInfo from '@/components/SupportContactInfo.vue'
import '@/styles/support.css'
</script>

<template>
  <div class="support-page support-contact-page">
    <div class="support-container">
      <nav
        class="support-breadcrumbs"
        aria-label="面包屑"
      >
        <RouterLink to="/support">
          服务与支持
        </RouterLink>
        <span aria-hidden="true">/</span><span>联系支持</span>
      </nav>
      <header class="support-contact-heading">
        <p class="support-eyebrow">
          联系支持
        </p>
        <h1>沟通，从这里开始</h1>
        <p>设备使用、打印问题与耗材选择，都可以与我们沟通。</p>
      </header>
      <SupportContactInfo />
      <RouterLink
        class="support-text-link support-contact-back"
        to="/support"
      >
        ← 返回帮助中心
      </RouterLink>
    </div>
  </div>
</template>
