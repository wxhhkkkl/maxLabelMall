<script setup lang="ts">
import { isPending, RENDERED, supportContact } from '@/data/placeholders'
import { footerNavigation } from '@/data/footerNavigation'

import PendingText from './base/PendingText.vue'

// The global footer uses the same confirmed contact information as support.
// Unreleased software resources are omitted until a usable destination exists.
</script>

<template>
  <footer class="footer">
    <div class="footer-main">
      <div class="brand-col">
        <RouterLink class="f-logo" to="/" aria-label="赋签 MaxLabel 首页">
          <img class="f-logo-img" src="/assets/logo.png" alt="赋签 MaxLabel" />
          <span class="f-cn">赋签</span>
          <span class="f-en">MaxLabel</span>
        </RouterLink>
        <p class="f-desc">
          一站式标签打印解决方案：软件、打印机与耗材，让每一枚标签精准呈现。
        </p>
        <p class="f-hotline">
          {{ supportContact.name }} · {{ supportContact.phone }}<br />
          微信：{{ supportContact.wechat }}
        </p>
      </div>

      <div v-for="group in footerNavigation" :key="group.title" class="f-col">
        <h4>{{ group.title }}</h4>
        <RouterLink v-for="link in group.links" :key="link.label" :to="link.to">{{ link.label }}</RouterLink>
      </div>
    </div>

    <div class="f-divider"></div>
    <div class="f-bottom">
      <span><PendingText :value="RENDERED.siteMeta.copyright" /></span>
      <div class="f-legal-links" aria-label="网站协议">
        <RouterLink to="/agreement/user">用户协议</RouterLink>
        <RouterLink to="/agreement/privacy">隐私政策</RouterLink>
        <span v-if="!isPending(RENDERED.siteMeta.icp)"><PendingText :value="RENDERED.siteMeta.icp" /></span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.f-logo { text-decoration: none; }
.f-legal-links { display: flex; flex-wrap: wrap; gap: 12px 24px; align-items: center; }
.f-legal-links a { color: #8FA0C4; }
.f-legal-links a:hover { color: #FFFFFF; }
.footer a:focus-visible { outline: 2px solid #35A5FF; outline-offset: 4px; border-radius: 3px; }
.f-hotline { overflow-wrap: anywhere; }
@media (max-width: 768px) {
  .f-legal-links { margin-top: 12px; }
}
</style>
