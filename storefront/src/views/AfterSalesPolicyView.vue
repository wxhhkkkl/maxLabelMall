<script setup lang="ts">
import { supportContact } from '@/data/placeholders'

const sections = [
  { id: 'scope', title: '服务说明' },
  { id: 'delivery', title: '签收与检查' },
  { id: 'returns', title: '退换货申请' },
  { id: 'repair', title: '设备与技术支持' },
  { id: 'fees', title: '退款与费用' },
  { id: 'apply', title: '如何申请售后' },
]
</script>

<template>
  <div class="after-sales-page">
    <section class="policy-hero">
      <div class="policy-container">
        <p>服务与保障 · AFTER-SALES SERVICE</p><h1>售后政策</h1><span>了解售后申请方式，让每一次沟通更清楚。</span>
      </div>
    </section>
    <div class="policy-container policy-layout">
      <nav
        class="policy-nav"
        aria-label="售后政策目录"
      >
        <span>政策目录</span><a
          v-for="(section, index) in sections"
          :key="section.id"
          :href="`#${section.id}`"
        ><small>0{{ index + 1 }}</small>{{ section.title }}</a>
      </nav>
      <div class="policy-content">
        <section
          id="scope"
          aria-labelledby="scope-title"
        >
          <h2 id="scope-title">
            <span>01</span>服务说明
          </h2><p>本页介绍商城商品的售后申请与沟通方式。商品具体的退换货条件、质保期限及费用承担，需结合商品售后说明、订单约定及适用法律法规核实。</p><p>如对标签耗材、打印设备或软件服务有疑问，请提供订单及问题信息，联系售后确认适合的处理方式。</p>
        </section>
        <section
          id="delivery"
          aria-labelledby="delivery-title"
        >
          <h2 id="delivery-title">
            <span>02</span>签收与检查
          </h2><p>收到商品后，请核对商品名称、型号、规格、数量及随附配件，并检查外包装和商品外观。</p><p>如发现包装破损、漏发、错发或外观异常，建议拍照记录，保留包装、物流单及相关凭证，并及时联系售后说明情况。</p>
        </section>
        <section
          id="returns"
          aria-labelledby="returns-title"
        >
          <h2 id="returns-title">
            <span>03</span>退换货申请
          </h2><p>需要申请退换货时，请先说明订单号、商品信息、申请原因及当前商品状态。涉及质量问题的，请一并提供能够说明问题的照片、视频或打印样张。</p><p>定制标签、已拆封或使用的耗材、软件授权等商品或服务，请补充定制内容、使用情况或授权状态，以便核实具体售后条件。</p><div class="policy-note">
            寄回商品前，请先与售后确认处理方案、寄回地址和包装要求，避免寄错地址或缺少必要配件。
          </div>
        </section>
        <section
          id="repair"
          aria-labelledby="repair-title"
        >
          <h2 id="repair-title">
            <span>04</span>设备与技术支持
          </h2><p>打印设备出现问题时，请提供设备型号、故障现象、报错信息，以及正在使用的标签与碳带规格。可以附上打印效果、设备指示灯或操作过程的照片、视频。</p><p>需要维修的，请联系售后核实设备对应的质保范围和处理流程。软件使用问题，请说明软件名称、版本、系统环境及操作步骤。</p><RouterLink
            class="policy-inline-link"
            to="/support"
          >
            查看使用指南与打印问题排查 <span aria-hidden="true">→</span>
          </RouterLink>
        </section>
        <section
          id="fees"
          aria-labelledby="fees-title"
        >
          <h2 id="fees-title">
            <span>05</span>退款与费用
          </h2><p>涉及退款、维修或运费时，请在确认处理方案时一并核实退款金额、退款方式、相关费用及预计处理安排。</p><p>是否产生检测、维修或物流费用，以及费用由谁承担，应结合问题原因、商品售后说明、订单约定及适用法律法规确认。</p>
        </section>
        <section
          id="apply"
          aria-labelledby="apply-title"
        >
          <h2 id="apply-title">
            <span>06</span>如何申请售后
          </h2><ol class="policy-steps">
            <li><strong>准备订单与问题信息</strong><span>订单号、商品型号或规格、问题说明及必要的图片或视频。</span></li><li><strong>联系售后核实情况</strong><span>说明你的需求，沟通商品状态及相应的售后条件。</span></li><li><strong>确认方案并配合处理</strong><span>确认退换货、技术支持或维修安排；如需寄回，请保留物流凭证。</span></li>
          </ol><div class="policy-contact">
            <div><span>售后联系人</span><h3>{{ supportContact.name }}</h3><p>电话：{{ supportContact.phone }}</p><p>微信：{{ supportContact.wechat }}</p></div><RouterLink to="/support/contact">
              联系售后 <span aria-hidden="true">→</span>
            </RouterLink>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.after-sales-page { background: #fff; color: #203b59; }
.policy-container { width: min(1160px, calc(100% - 80px)); margin-inline: auto; }
.policy-hero { padding-block: 48px; background: linear-gradient(110deg, #f1f7fc, #e6f0fb); }
.policy-hero p { color: #7995b2; letter-spacing: 2px; font-size: 11px; margin: 0 0 16px; }
.policy-hero h1 { font-size: 36px; font-weight: 600; margin: 0 0 14px; }
.policy-hero > div > span { font-size: 14px; color: #788da5; }
.policy-layout { display: grid; grid-template-columns: 200px minmax(0,1fr); gap: 72px; align-items: start; padding-block: 56px 80px; }
.policy-nav { position: sticky; top: 104px; border: 1px solid #e4ecf5; border-radius: 6px; padding: 22px 20px; }
.policy-nav > span { display: block; font-size: 15px; font-weight: 500; margin-bottom: 18px; }
.policy-nav a { display: flex; align-items: center; gap: 12px; font-size: 13px; color: #7c8da3; padding-block: 11px; }
.policy-nav small { font-size: 10px; color: #a2b5c9; }
.policy-nav a:hover { color: #2e7cd6; }
.policy-content > section { scroll-margin-top: 104px; padding-bottom: 34px; margin-bottom: 32px; border-bottom: 1px solid #e8eef6; }
.policy-content > section:last-child { padding: 0; margin: 0; border: 0; }
.policy-content h2 { display: flex; align-items: center; gap: 14px; font-size: 21px; font-weight: 500; margin: 0 0 20px; }
.policy-content h2 > span { font-size: 12px; color: #8eaccb; font-weight: 400; }
.policy-content p { font-size: 14px; line-height: 2; color: #74859a; margin: 0 0 14px; }
.policy-note { border-left: 2px solid #95bde8; background: #f4f8fd; padding: 16px 18px; font-size: 13px; color: #617e9c; line-height: 1.9; margin-top: 20px; }
.policy-inline-link { color: #2e7cd6; font-size: 13px; display: inline-flex; gap: 16px; margin-top: 4px; }
.policy-steps { padding-left: 22px; margin: 0 0 28px; }
.policy-steps li { color: #7d9ab8; padding-left: 8px; margin-bottom: 20px; font-size: 13px; }
.policy-steps strong { display: block; color: #4e6b89; font-size: 14px; font-weight: 500; margin-bottom: 7px; }
.policy-steps li > span { display: block; font-size: 13px; line-height: 1.8; color: #8191a4; }
.policy-contact { display: flex; justify-content: space-between; align-items: center; gap: 24px; background: #eef5fd; padding: 26px; border-radius: 6px; }
.policy-contact > div > span { font-size: 11px; color: #829ab4; }
.policy-contact h3 { font-size: 20px; font-weight: 500; margin: 10px 0 14px; }
.policy-contact p { font-size: 13px; margin: 3px 0; }
.policy-contact > a { flex: none; color: #fff; background: #2e7cd6; border-radius: 4px; font-size: 13px; padding: 12px 18px; }
.policy-contact > a > span { margin-left: 16px; }
.after-sales-page a:focus-visible { outline: 2px solid #2e7cd6; outline-offset: 4px; }
@media(max-width: 900px) { .policy-layout { grid-template-columns: 170px minmax(0,1fr); gap: 32px; } }
@media(max-width: 650px) {
  .policy-container { width: calc(100% - 40px); }
  .policy-hero { padding-block: 36px; }
  .policy-hero h1 { font-size: 30px; }
  .policy-layout { display: block; padding-block: 28px 50px; }
  .policy-nav { position: static; display: flex; flex-wrap: wrap; gap: 4px 20px; padding: 12px 16px; margin-bottom: 36px; }
  .policy-nav > span { flex-basis: 100%; margin-bottom: 6px; }
  .policy-nav a { font-size: 12px; gap: 8px; padding-block: 8px; }
  .policy-content h2 { font-size: 20px; }
  .policy-contact { flex-wrap: wrap; padding: 22px; }
  .policy-contact > a { text-align: center; }
}
</style>
