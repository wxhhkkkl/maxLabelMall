<script setup lang="ts">
const values = [
  { number: '01', title: '创新', subtitle: '持续探索，向前一步', text: '投入技术研发，积极引入新的技术理念与方法，在探索中提升解决问题的能力。' },
  { number: '02', title: '专业', subtitle: '以技术能力回应需求', text: '重视团队的专业积累与人才培养，以扎实的技术和细致的执行，推进每一个项目。' },
  { number: '03', title: '诚信', subtitle: '认真对待每一份信任', text: '以客户为中心，重视沟通与长期合作，从前期规划到维护升级，持续跟进客户需求。' },
]
</script>

<template>
  <div class="company-page">
    <section
      class="company-hero"
      aria-labelledby="company-title"
    >
      <div class="company-container company-hero-inner">
        <div class="company-hero-copy">
          <p class="company-eyebrow">
            关于我们 · ABOUT US
          </p>
          <h1 id="company-title">
            北京御旺宸发<br>科技有限公司
          </h1>
          <p class="company-hero-subtitle">
            以创新为动力，以专业为根基，<br>为客户创造长期价值。
          </p>
          <a
            href="#company-introduction"
            class="company-discover"
          >了解我们 <span aria-hidden="true">↓</span></a>
        </div>
        <div
          class="company-hero-art"
          aria-hidden="true"
        >
          <div class="company-orbit company-orbit-one" /><div class="company-orbit company-orbit-two" />
          <span class="company-art-caption">TECHNOLOGY &amp; VALUE</span>
          <div class="company-art-title">
            科技<span>连接价值</span>
          </div>
          <div class="company-art-values">
            <span>创新</span><span>专业</span><span>诚信</span>
          </div>
        </div>
      </div>
    </section>

    <section
      id="company-introduction"
      class="company-container company-introduction"
      aria-labelledby="introduction-title"
    >
      <div class="company-section-label">
        <p class="company-eyebrow">
          OUR COMPANY
        </p><h2 id="introduction-title">
          以科技服务<br>创造长期价值
        </h2><span class="company-label-rule" />
      </div>
      <div class="company-introduction-copy">
        <p class="company-lead">
          北京御旺宸发科技有限公司致力于为客户提供高品质的科技服务与创新解决方案。
        </p>
        <p>公司以客户需求为出发点，业务涵盖软件开发、网站建设和移动应用开发。凭借团队的专业经验与技术积累，将不同业务场景的需求转化为具体的产品与系统，帮助客户提升运营效率和管理水平。</p>
        <p>在软件开发方面，围绕企业资源规划（ERP）系统及行业专用软件提供定制开发；在网站建设方面，将创意设计与技术实现结合，注重品牌表达与用户体验；在移动应用开发方面，关注多平台应用的功能与使用体验，满足不同用户群体的需求。</p>
        <p>从项目的前期规划到后期维护升级，我们重视每一个环节的沟通与落实，以个性化的服务方案和持续跟进，与客户建立长期、稳定的合作关系。</p>
        <div class="company-signature">
          <span>北京御旺宸发科技有限公司</span><small>创新 · 专业 · 诚信</small>
        </div>
      </div>
    </section>

    <section
      class="company-team-section"
      aria-labelledby="team-title"
    >
      <div class="company-container company-team">
        <div class="company-team-statement">
          <p class="company-eyebrow">
            OUR PEOPLE
          </p><h2 id="team-title">
            让人才与技术<br>共同成长
          </h2><p>经验的积累、持续的学习与团队的协作，<br>构成我们不断向前的力量。</p>
        </div>
        <div class="company-team-details">
          <article>
            <span
              class="company-detail-marker"
              aria-hidden="true"
            /><div><h3>持续投入技术研发</h3><p>积极引入和探索新的技术理念与方法，持续积累研发经验，提升技术实力与创新能力。</p></div>
          </article>
          <article>
            <span
              class="company-detail-marker"
              aria-hidden="true"
            /><div><h3>重视人才培养</h3><p>为员工提供学习机会与发展空间，鼓励专业成长，让个人的进步成为团队共同的积累。</p></div>
          </article>
          <article>
            <span
              class="company-detail-marker"
              aria-hidden="true"
            /><div><h3>凝聚团队创造力</h3><p>汇聚不同专业背景的技术人才，在交流与协作中形成富有创造力和凝聚力的团队。</p></div>
          </article>
        </div>
      </div>
    </section>

    <section
      class="company-container company-values-section"
      aria-labelledby="values-title"
    >
      <div class="company-values-heading">
        <p class="company-eyebrow">
          OUR VALUES
        </p><h2 id="values-title">
          以客户为中心<br>以创新、专业、诚信为准则
        </h2><p>理解需求，认真交付，珍视每一次合作。</p>
      </div>
      <div class="company-values-grid">
        <article
          v-for="value in values"
          :key="value.number"
          class="company-value"
        >
          <span class="company-value-number">{{ value.number }}</span><h3>{{ value.title }}</h3><h4>{{ value.subtitle }}</h4><p>{{ value.text }}</p>
        </article>
      </div>
    </section>

    <section
      class="company-future"
      aria-labelledby="future-title"
    >
      <div class="company-container company-future-inner">
        <div>
          <p class="company-eyebrow">
            LOOKING AHEAD
          </p><h2 id="future-title">
            立足当下，持续向前
          </h2>
        </div><p>面向未来，北京御旺宸发科技有限公司将继续秉承创新、专业、诚信的理念，不断提升技术实力，拓展业务领域，以高品质的科技服务为客户创造更大的价值，与合作伙伴共同成长。</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.company-page { background: #fff; color: #19334f; }
.company-container { width: min(1160px, calc(100% - 80px)); margin-inline: auto; }
.company-eyebrow { color: #6485a8; font-size: 12px; letter-spacing: 2px; margin: 0 0 22px; }
.company-page h2 { font-size: clamp(27px, 2.7vw, 36px); line-height: 1.55; font-weight: 500; margin: 0; }
.company-hero { background: linear-gradient(110deg, #f1f7fc, #e9f3fd 60%, #dbeafb); overflow: hidden; }
.company-hero-inner { display: grid; grid-template-columns: 1.2fr 1fr; min-height: 450px; gap: 50px; align-items: center; }
.company-hero-copy { padding-block: 66px; position: relative; z-index: 1; }
.company-hero h1 { font-size: clamp(34px, 3.5vw, 50px); line-height: 1.5; letter-spacing: 2px; font-weight: 600; margin: 0 0 22px; }
.company-hero-subtitle { font-size: 16px; color: #68809a; line-height: 1.9; margin: 0 0 26px; }
.company-discover { display: inline-flex; align-items: center; gap: 24px; font-size: 13px; color: #2e7cd6; padding: 8px 0; border-bottom: 1px solid #a9c6e4; }
.company-discover:hover { color: #165aa8; border-color: #2e7cd6; }
.company-hero-art { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 380px; isolation: isolate; }
.company-orbit { position: absolute; border: 1px solid #ffffffd9; border-radius: 50%; z-index: -1; }
.company-orbit-one { width: 430px; height: 430px; background: linear-gradient(145deg, #ffffff45, #c6dbf42b); box-shadow: 0 0 0 35px #ffffff20, 0 0 0 70px #ffffff12; }
.company-orbit-two { width: 290px; height: 290px; border-color: #b8d0eb88; transform: translate(50px, 30px); }
.company-art-caption { font-size: 10px; letter-spacing: 4px; color: #81a0c1; margin-bottom: 24px; }
.company-art-title { font-size: 58px; color: #5384b4; font-weight: 300; letter-spacing: 10px; line-height: 1.4; }
.company-art-title > span { display: block; font-size: 32px; letter-spacing: 6px; color: #6c94bd; margin-top: 4px; }
.company-art-values { display: flex; gap: 22px; margin-top: 30px; font-size: 12px; color: #7394b7; letter-spacing: 3px; }
.company-art-values span + span { border-left: 1px solid #abc6e0; padding-left: 22px; }
.company-introduction { display: grid; grid-template-columns: .8fr 1.4fr; gap: 90px; padding-block: 90px; scroll-margin-top: 110px; }
.company-label-rule { display: block; width: 48px; height: 2px; background: #2e7cd6; margin-top: 28px; }
.company-introduction-copy > p { font-size: 14px; color: #748399; line-height: 2.1; margin: 0 0 18px; }
.company-introduction-copy > .company-lead { font-size: 19px; font-weight: 500; color: #304d6b; line-height: 1.85; margin-bottom: 26px; }
.company-signature { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; border-top: 1px solid #e6edf5; padding-top: 22px; margin-top: 28px; font-size: 13px; color: #4b6581; }
.company-signature small { font-size: 11px; color: #98a8b9; letter-spacing: 1px; }
.company-team-section { background: #f6f9fc; }
.company-team { display: grid; grid-template-columns: 1fr 1.2fr; gap: 90px; padding-block: 76px; }
.company-team-statement > p:last-child { color: #8192a5; font-size: 14px; line-height: 1.9; margin: 24px 0 0; }
.company-team-details article { display: flex; gap: 22px; padding-block: 24px; border-bottom: 1px solid #e1eaf3; }
.company-team-details article:first-child { padding-top: 0; }
.company-team-details article:last-child { padding-bottom: 0; border: 0; }
.company-detail-marker { flex: none; width: 9px; height: 9px; border: 1px solid #5e92c5; transform: rotate(45deg); margin-top: 9px; }
.company-team-details h3 { font-weight: 500; font-size: 18px; margin: 0 0 10px; }
.company-team-details p { font-size: 13px; color: #7a8a9d; line-height: 1.95; margin: 0; }
.company-values-section { padding-block: 84px 90px; }
.company-values-heading { text-align: center; }
.company-values-heading > p:last-child { color: #8a9aab; font-size: 14px; margin: 20px 0 0; }
.company-values-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 52px; margin-top: 54px; }
.company-value { border-top: 1px solid #d8e4f0; padding-top: 24px; }
.company-value-number { font-size: 13px; color: #8eafcf; letter-spacing: 2px; }
.company-value h3 { font-size: 27px; color: #2c527b; font-weight: 500; margin: 20px 0 14px; }
.company-value h4 { font-size: 15px; font-weight: 500; margin: 0 0 12px; }
.company-value p { font-size: 13px; line-height: 1.95; color: #7a8a9d; margin: 0; }
.company-future { background: linear-gradient(110deg, #24476a, #1b3553); color: #fff; }
.company-future-inner { display: grid; grid-template-columns: 1fr 1.2fr; align-items: center; gap: 90px; padding-block: 60px; }
.company-future .company-eyebrow { color: #92b3d4; }
.company-future h2 { font-size: 30px; }
.company-future-inner > p { font-size: 14px; color: #c0d0e1; line-height: 2; margin: 0; }
.company-page a:focus-visible { outline: 2px solid #2e7cd6; outline-offset: 5px; }
@media(max-width: 1000px) {
  .company-introduction, .company-team, .company-future-inner { gap: 44px; }
  .company-hero-inner { gap: 20px; }
  .company-art-title { font-size: 48px; }
  .company-art-title > span { font-size: 27px; }
  .company-values-grid { gap: 28px; }
}
@media(max-width: 700px) {
  .company-container { width: calc(100% - 40px); }
  .company-hero-inner { min-height: 0; grid-template-columns: 1fr; gap: 0; }
  .company-hero-copy { padding-block: 48px 20px; }
  .company-hero h1 { font-size: 32px; letter-spacing: 1px; }
  .company-hero-subtitle { font-size: 14px; }
  .company-hero-art { min-height: 240px; padding-bottom: 24px; }
  .company-orbit-one { width: 290px; height: 290px; }
  .company-orbit-two { width: 210px; height: 210px; transform: translate(25px, 5px); }
  .company-art-title { font-size: 40px; }
  .company-art-title > span { font-size: 24px; }
  .company-art-caption { margin-bottom: 12px; font-size: 9px; }
  .company-art-values { margin-top: 18px; }
  .company-introduction, .company-team, .company-future-inner { grid-template-columns: 1fr; gap: 30px; padding-block: 50px; }
  .company-introduction-copy > .company-lead { font-size: 18px; }
  .company-introduction-copy > p { font-size: 14px; }
  .company-values-section { padding-block: 54px; }
  .company-values-heading { text-align: left; }
  .company-values-grid { grid-template-columns: 1fr; gap: 30px; margin-top: 30px; }
  .company-value h3 { margin: 12px 0; font-size: 25px; }
  .company-future-inner { gap: 22px; }
}
</style>
