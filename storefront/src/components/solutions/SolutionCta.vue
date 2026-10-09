<script setup lang="ts">
defineProps<{ image?: string }>()
</script>

<template>
  <section class="solution-cta-section">
    <img
      v-if="image"
      class="solution-cta-photo"
      :src="image"
      alt=""
      aria-hidden="true"
      loading="lazy"
    >
    <div class="solution-container solution-cta">
      <div>
        <h2>把您的场景，变成可执行的标签方案</h2>
        <p>从实际流程出发，一起梳理适合现场的标签需求。</p>
      </div>
      <RouterLink
        class="cta-btn"
        to="/contact"
      >
        咨询行业方案 <span aria-hidden="true">→</span>
      </RouterLink>
    </div>
  </section>
</template>
