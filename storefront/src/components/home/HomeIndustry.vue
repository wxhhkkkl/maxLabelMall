<script setup lang="ts">
const industries = [
  { name: '涂料生产', description: '型号、色号与批次，随成品一起交付。', image: '/assets/software/paint.webp', alt: '蓝盖白色涂料桶上的产品标签，展示型号、色号和生产批次', to: '/solutions/manufacturing' },
  { name: '服装零售', description: '清晰呈现商品信息，让款号与尺码更好辨认。', image: '/assets/software/apparel.webp', alt: '深蓝色服装上的白色吊牌，展示款号、尺码和成分', to: '/solutions/apparel' },
  { name: '仓储物流', description: '让货品、库位与流转信息清楚对应。', image: '/assets/software/warehouse.webp', alt: '蓝色周转箱上的 A-01-03 库位标，展示库区、货架与层位', to: '/solutions/warehouse' },
]
</script>

<template>
  <section
    class="home-industry"
    aria-labelledby="home-industry-title"
  >
    <div class="home-industry-container">
      <div class="home-industry-head">
        <div>
          <h2 id="home-industry-title">
            行业标签方案
          </h2><p>不同业务场景，同样清楚有序。</p>
        </div>
        <RouterLink
          class="home-industry-all"
          to="/solutions"
        >
          探索全部行业方案 <span aria-hidden="true">→</span>
        </RouterLink>
      </div>
      <div class="home-industry-grid">
        <RouterLink
          v-for="item in industries"
          :key="item.name"
          class="home-industry-card"
          :to="item.to"
        >
          <div class="home-industry-photo">
            <img
              :src="item.image"
              :alt="item.alt"
              width="1448"
              height="1086"
              loading="lazy"
            >
          </div>
          <h3>{{ item.name }}</h3><p>{{ item.description }}</p><span class="home-industry-link">查看方案 <span aria-hidden="true">→</span></span>
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home-industry { padding: 76px 0 80px; background: linear-gradient(180deg, #F5F8FF, #FFFFFF); }
.home-industry-container { width: min(1200px, calc(100% - 96px)); margin-inline: auto; }
.home-industry-head { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 32px; }
.home-industry h2 { margin: 0; font-size: 34px; line-height: 1.45; font-weight: 750; letter-spacing: -.035em; color: #0C2148; }
.home-industry-head p { margin: 10px 0 0; color: #5B6C8F; font-size: 16px; line-height: 1.8; }
.home-industry-all { flex-shrink: 0; display: inline-flex; align-items: center; gap: 18px; color: #2E7CD6; font-size: 15px; font-weight: 600; }
.home-industry-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 28px; }
.home-industry-card { display: block; min-width: 0; border-radius: 12px; }
.home-industry-photo { overflow: hidden; border-radius: 12px; background: #E3E9F4; }
.home-industry-photo img { width: 100%; height: auto; aspect-ratio: 1.08; object-fit: cover; transition: transform .3s; }
.home-industry-card h3 { margin: 20px 0 8px; color: #0C2148; font-size: 22px; font-weight: 700; }
.home-industry-card p { margin: 0; color: #5B6C8F; font-size: 15px; line-height: 1.8; }
.home-industry-link { display: inline-flex; align-items: center; gap: 16px; margin-top: 14px; font-size: 15px; color: #2E7CD6; font-weight: 600; }
.home-industry-card:hover img { transform: scale(1.025); }
.home-industry-card:focus-visible, .home-industry-all:focus-visible { outline: 3px solid #35A5FF; outline-offset: 6px; }
@media (prefers-reduced-motion: reduce) { .home-industry-photo img { transition: none; } }
@media (max-width: 1100px) {
  .home-industry-container { width: calc(100% - 64px); }
  .home-industry-grid { gap: 24px; }
  .home-industry h2 { font-size: 30px; }
  .home-industry-card h3 { font-size: 20px; }
}
@media (max-width: 768px) {
  .home-industry { padding: 48px 0; }
  .home-industry-container { width: calc(100% - 40px); }
  .home-industry-head { flex-direction: column; align-items: flex-start; gap: 16px; margin-bottom: 26px; }
  .home-industry h2 { font-size: 28px; }
  .home-industry-head p { font-size: 15px; }
  .home-industry-grid { grid-template-columns: 1fr; gap: 32px; }
  .home-industry-photo img { aspect-ratio: 4 / 3; }
  .home-industry-card h3 { margin-top: 16px; }
  .home-industry-link { margin-top: 10px; }
}
@media (max-width: 480px) { .home-industry h2 { font-size: 25px; } }
</style>
