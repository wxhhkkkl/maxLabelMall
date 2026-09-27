<script setup lang="ts">
/**
 * 服务支持页 —— 按设计稿 `www/support.html` 还原。
 *
 * 页脚与联系方式由 `DefaultLayout` / `SiteFooter` 经 `PendingText` 渲染（FR-054）。
 *
 * ⚠️ **待处理（2026-09-27 发现，见 tasks.md 的遗留问题）**：
 * 下面这组 FAQ 的**内容与设计稿不同**（设计稿是「蓝牙连不上 / 内容偏移 / 专业版文档 /
 * 支持哪些打印机 / 企业批量折扣」，这里是 Excel 导入 / 条码类型 / 软件收费），
 * 而且含**未经业务方确认的报价与能力声明**（如「基础功能免费使用；专业版按年订阅」）。
 * 这两点分别触碰 FR-046（文案基线不得擅改）与 FR-056（营销承诺须先确认后保留 /
 * 未确认前按占位处理）。**尚未修**，因为"保留我们的文案"与"恢复设计稿文案"都说得通，
 * 需项目所有者定夺后再动。
 */
const faqs = [
  { q: '打印机连不上怎么办？', a: '先确认 USB / 蓝牙已连接，并在系统里选中正确的打印机型号。' },
  { q: '标签纸打印出来偏移？', a: '在软件里执行一次「标签学习 / 校准」，并确认纸张尺寸设置与实际一致。' },
  { q: '如何导入 Excel 批量打印？', a: '在软件中打开「数据源」，导入 Excel 后把字段拖到标签对应位置即可。' },
  { q: '支持哪些条码类型？', a: '支持 Code128、Code39、EAN-13、QR Code、DataMatrix 等主流条码与二维码。' },
  { q: '软件是收费的吗？', a: '基础功能免费使用；专业版按年订阅，企业版支持私有化部署与定制对接。' },
]
</script>

<template>
  <section class="sup-hero">
    <h1>服务与支持</h1>
    <p class="sw-hero-sub">驱动下载、常见问题与使用教程，帮你快速解决打印问题</p>
  </section>

  <section class="support-home">
    <div class="s-grid">
      <RouterLink class="entry-card" to="/contact">
        <h3>驱动与软件下载</h3>
        <p>全型号驱动 · 快速指南<br />持续更新一键获取</p>
        <span class="entry-link">前往下载 →</span>
      </RouterLink>
      <div class="entry-card">
        <h3>7×12 在线客服</h3>
        <p>工作日 9:00-21:00<br />随时响应打印问题</p>
        <RouterLink class="entry-link" to="/contact">联系客服 →</RouterLink>
      </div>
      <div class="entry-card">
        <h3>视频教程学院</h3>
        <p>从入门到进阶<br />手把手教你玩转标签</p>
        <RouterLink class="entry-link" to="/templates">浏览模板 →</RouterLink>
      </div>
      <div class="entry-card">
        <h3>整机一年质保</h3>
        <p>打印机整机质保一年<br />耗材支持无理由退换</p>
        <RouterLink class="entry-link" to="/contact">售后入口 →</RouterLink>
      </div>
    </div>
  </section>

  <section class="features">
    <div>
      <h2 class="section-title" style="text-align: center">常见问题</h2>
      <p class="section-desc">打印中最常遇到的几个问题</p>
    </div>
    <div class="f-grid">
      <details v-for="f in faqs" :key="f.q" class="faq-card">
        <summary class="faq-q">{{ f.q }}</summary>
        <p class="faq-a">{{ f.a }}</p>
      </details>
    </div>
  </section>

  <section class="final-cta">
    <h2 class="cta-title">还需要更多帮助？</h2>
    <p class="cta-sub">客服热线与售后邮箱见联系我们页面</p>
    <RouterLink class="cta-btn" to="/contact">联系我们</RouterLink>
  </section>
</template>
