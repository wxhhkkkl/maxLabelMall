<template>
  <section
    class="home-banner"
    aria-labelledby="home-banner-title"
  >
    <div class="home-banner-devices">
      <img
        class="home-banner-image"
        src="/assets/home/banner-software-v2.webp"
        alt="笔记本屏幕中的 MaxLabel 标签设计软件、桌面打印机、白色标签纸卷与黑色碳带"
        width="1942"
        height="809"
        fetchpriority="high"
      >
    </div>
    <div class="home-banner-container">
      <div class="home-banner-copy">
        <p class="home-banner-eyebrow">
          赋签 MaxLabel · 标签应用一站式方案
        </p>
        <h1 id="home-banner-title">
          设计、打印与耗材<span>一站解决</span>
        </h1>
        <p class="home-banner-description">
          从标签设计软件，到打印设备与标签耗材，为每一张标签提供连贯的解决方案。
        </p>
        <p class="home-banner-chain">
          <span>标签设计</span><i aria-hidden="true">→</i><span>设备打印</span><i aria-hidden="true">→</i><span>耗材配套</span>
        </p>
        <div class="home-banner-actions">
          <RouterLink
            class="home-banner-primary"
            to="/mall"
          >
            进入商城 <span aria-hidden="true">→</span>
          </RouterLink>
          <RouterLink
            class="home-banner-secondary"
            to="/software"
          >
            了解标签软件 <span aria-hidden="true">→</span>
          </RouterLink>
        </div>
        <p class="home-banner-software-note">
          MaxLabel 软件开发中 · 敬请期待
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home-banner { position: relative; isolation: isolate; background: linear-gradient(110deg, #F5F8FF, #EAF1FF); overflow: hidden; }
.home-banner-devices { position: absolute; right: 0; top: 50%; transform: translateY(-50%); width: 63%; }
.home-banner-image { width: 100%; height: auto; aspect-ratio: 1.65; object-fit: cover; object-position: right center; mask-image: linear-gradient(90deg, transparent, #000 3%), linear-gradient(180deg, transparent, #000 6%, #000 94%, transparent); mask-composite: intersect; }
.home-banner-container { display: flex; align-items: center; min-height: 570px; width: min(1200px, calc(100% - 96px)); margin-inline: auto; padding-block: 56px; }
.home-banner-copy { width: 34%; max-width: 440px; }
.home-banner-eyebrow { margin: 0 0 16px; font-size: 16px; font-weight: 600; color: #2E7CD6; }
.home-banner h1 { margin: 0; color: #0C2148; font-size: clamp(28px, 3vw, 44px); font-weight: 800; line-height: 1.3; letter-spacing: -.045em; }
.home-banner h1 span { display: block; color: #2E7CD6; }
.home-banner-description { margin: 18px 0 0; max-width: 440px; color: #5B6C8F; font-size: 15px; line-height: 1.9; }
.home-banner-chain { display: flex; align-items: center; gap: 16px; margin-top: 18px; color: #0C2148; font-size: 13px; font-weight: 500; }
.home-banner-chain i { font-style: normal; color: #2E7CD6; }
.home-banner-actions { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 24px; }
.home-banner-actions a { display: inline-flex; align-items: center; justify-content: center; gap: 20px; padding: 12px 22px; border: 1px solid #2E7CD6; border-radius: 10px; font-size: 14px; font-weight: 600; line-height: 1.5; transition: background .2s, box-shadow .2s; }
.home-banner-primary { background: #2E7CD6; color: #FFFFFF; }
.home-banner-secondary { background: rgb(255 255 255 / 65%); color: #2E7CD6; }
.home-banner-actions a:hover { box-shadow: 0 6px 20px rgb(46 124 214 / 15%); }
.home-banner-secondary:hover { background: #FFFFFF; }
.home-banner-actions a:focus-visible { outline: 3px solid #35A5FF; outline-offset: 4px; }
.home-banner-software-note { margin: 18px 0 0; font-size: 12px; color: #5B6C8F; line-height: 1.7; }
@media (max-width: 1100px) {
  .home-banner-container { width: calc(100% - 64px); min-height: 510px; padding-block: 40px; }
  .home-banner-copy { width: 34%; }
  .home-banner-eyebrow { font-size: 13px; }
  .home-banner-actions { gap: 10px; }
  .home-banner-actions a { padding: 11px 12px; gap: 10px; font-size: 13px; }
}
@media (max-width: 768px) {
  .home-banner { display: flex; flex-direction: column; }
  .home-banner-container { width: calc(100% - 40px); min-height: 0; padding: 36px 0 12px; order: -1; }
  .home-banner-copy { width: 100%; max-width: 560px; }
  .home-banner h1 { font-size: 42px; }
  .home-banner-description { max-width: 400px; }
  .home-banner-devices { position: relative; top: auto; right: auto; transform: none; width: 100%; }
  .home-banner-image { mask-image: linear-gradient(180deg, transparent, #000 6%); mask-composite: add; }
}
@media (max-width: 480px) {
  .home-banner h1 { font-size: 32px; }
  .home-banner-chain { gap: 12px; }
  .home-banner-actions a { gap: 14px; padding-inline: 18px; }
}
</style>
