<script setup lang="ts">
/**
 * 标签软件页 —— 按设计稿 `www/software.html` 还原，沿用其 class 名。
 *
 * ⚠️ 版本价格区（原「选择适合你的版本」的三档价格 ¥0 / ¥199/年 / ¥899/年，以及
 * 「200 次打印/月」「无限次打印 · 全部 2,000+ 模板」「私有化部署」等能力声明）
 * **按所有者要求暂时下线** —— 那些都是对外的报价与承诺，业务方尚未确认。
 * 原位置改为一句「马上上线，敬请期待」。数据仍在 `placeholders.ts` 的
 * `softwarePlans` 里，软件上线时取回来即可，视图这边不用重写。
 *
 * ⚠️ 下方 hero 里的「2,000+ 行业模板」是**写死在模板里的未经确认数字**，
 * 未走占位符（本次按所有者要求不动）—— 上线前仍需业务方确认，见 FR-055。
 */
</script>

<template>
  <section class="sw-hero">
    <div class="sw-hero-left">
      <h1>MaxLabel 标签编辑软件</h1>
      <p class="sw-hero-sub">
        2,000+ 行业模板一键套用，Excel 批量打印，
        Windows / macOS / iOS / Android 全平台云同步
      </p>
      <div class="hero-cta">
        <RouterLink class="btn-cyan" to="/templates">浏览模板中心</RouterLink>
        <RouterLink class="btn-ghost-white" to="/support">查看使用文档</RouterLink>
      </div>
    </div>
  </section>

  <section class="features">
    <div>
      <h2 class="section-title" style="text-align: center">软件能做什么</h2>
      <p class="section-desc">从模板到成稿，全程不超过 5 分钟</p>
    </div>
    <div class="f-grid">
      <div class="f-card">
        <h3>海量云端模板库</h3>
        <p>覆盖零售、物流、服装、食品、医药等行业的模板，选中即改，改完即打。</p>
      </div>
      <div class="f-card">
        <h3>Excel 批量打印引擎</h3>
        <p>导入 Excel / TXT 数据源，自动生成流水号与可变内容，一次打印整批标签。</p>
      </div>
      <div class="f-card">
        <h3>多端云同步</h3>
        <p>四端实时同步，换设备、重装系统，标签数据永不丢失。</p>
      </div>
      <div class="f-card">
        <h3>主流机型兼容</h3>
        <p>支持 TSPL / CPCL / ESC-POS 标准指令集，兼容市面主流桌面与便携打印机。</p>
      </div>
    </div>
  </section>

  <!-- 版本价格区暂时下线：三档价格与能力声明都是**对外的报价与承诺**，业务方尚未确认，
       与其展示了再改，不如等软件真正上线。数据仍在 placeholders.ts 的 softwarePlans 里。 -->
  <section class="features">
    <div>
      <h2 class="section-title" style="text-align: center">马上上线，敬请期待</h2>
    </div>
  </section>

  <section class="final-cta">
    <h2 class="cta-title">3 分钟上手标签设计</h2>
    <p class="cta-sub">免费注册 · 30 天专业版试用</p>
    <!-- 试用需先有账号；登录弹层由 ?login=1 打开（FR-010） -->
    <RouterLink class="cta-btn" to="/account?login=1">立即免费试用</RouterLink>
  </section>
</template>
