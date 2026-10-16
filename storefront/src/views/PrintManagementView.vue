<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import MallIcon from '@/components/MallIcon.vue'
import { printManagementFlow, printManagementRoles, printManagementScreens } from '@/data/printManagement'

const activeIndex = ref(0)
const compactScreen = useMediaQuery('(max-width: 750px)')
const activeScreen = computed(() => printManagementScreens[activeIndex.value]!)
const screenshotDialog = ref<HTMLDialogElement>()
const featureHighlights = [
  { icon: 'software', caption: '码量与状态，一眼清楚', benefit: '看清每一批码的当前去向' },
  { icon: 'sheet', caption: '统一版式，保留版本', benefit: '已有分配不会被新模板改动' },
  { icon: 'printer', caption: '产品、模板、数量联动', benefit: '让现场操作更专注于打印' },
  { icon: 'order', caption: '填写原因，原工位执行', benefit: '把重复出纸纳入受控流程' },
  { icon: 'rfid', caption: '一个码，查清完整经过', benefit: '出现问题时，有记录可核对' },
]
const activeHighlight = computed(() => featureHighlights[activeIndex.value]!)
function stepScreen(delta: number) { changeScreen((activeIndex.value + delta + printManagementScreens.length) % printManagementScreens.length) }
function changeScreen(index: number) { activeIndex.value = index }
function moveTab(event: KeyboardEvent, index: number) {
  let next: number
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (index + 1) % printManagementScreens.length
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = (index + printManagementScreens.length - 1) % printManagementScreens.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = printManagementScreens.length - 1
  else return
  event.preventDefault()
  changeScreen(next)
  document.getElementById(`print-tab-${next}`)?.focus()
}
</script>

<template>
  <div class="print-management-page">
    <section
      class="pm-hero"
      aria-labelledby="pm-title"
    >
      <div class="pm-container pm-hero-inner">
        <div class="pm-hero-copy">
          <p class="pm-eyebrow">
            企业标签打印管理系统
          </p><h1 id="pm-title">
            总部统一管理<br>工位有序打印
          </h1><p class="pm-hero-description">
            从码导入、模板分配到现场打印与记录追溯，<br>把分散的打印工作，连接成清楚的管理流程。
          </p><div class="pm-hero-actions">
            <a
              class="pm-button"
              href="#system-preview"
            >查看系统界面 <MallIcon name="chevron" /></a><RouterLink
              class="pm-text-link"
              to="/support/contact"
            >
              咨询应用方案 →
            </RouterLink>
          </div><span class="pm-hero-caption">网页管理后台 ＋ Windows 工位打印客户端</span>
        </div>
        <figure class="pm-hero-figure">
          <div class="pm-window-bar">
            <span /><span /><span /><small>打印管理系统 · 管理后台</small>
          </div><img
            src="/assets/print-management/admin-dashboard.webp"
            alt="打印管理系统码量看板，展示当前码状态分布的演示数据"
            fetchpriority="high"
          ><figcaption>系统界面 · 示例数据</figcaption>
        </figure>
      </div>
    </section>

    <section
      class="pm-container pm-roles"
      aria-labelledby="roles-title"
    >
      <div class="pm-section-intro">
        <p class="pm-eyebrow">
          清楚分工，协同管理
        </p><h2 id="roles-title">
          同一套系统，各司其职
        </h2><p>总部管规则，分公司管协作，工位管执行。</p>
      </div><div class="pm-role-grid">
        <article
          v-for="role in printManagementRoles"
          :key="role.title"
        >
          <MallIcon :name="role.icon" /><div><span class="pm-role-tag">{{ role.tag }}</span><h3>{{ role.title }}</h3><p>{{ role.text }}</p></div>
        </article>
      </div>
    </section>

    <section
      id="system-preview"
      class="pm-preview-section"
      aria-labelledby="preview-title"
    >
      <div class="pm-container">
        <div class="pm-section-intro">
          <p class="pm-eyebrow">
            从管理到执行，看见实际功能
          </p><h2 id="preview-title">
            让打印过程有章可循
          </h2><p>选择一个功能，查看核心特点与实际界面。</p>
        </div>
        <div class="pm-feature-layout">
          <div
            class="pm-tabs"
            role="tablist"
            aria-label="系统功能界面"
            :aria-orientation="compactScreen ? 'horizontal' : 'vertical'"
          >
            <button
              v-for="(screen, index) in printManagementScreens"
              :id="`print-tab-${index}`"
              :key="screen.id"
              type="button"
              role="tab"
              :aria-label="screen.label"
              :aria-selected="activeIndex === index"
              aria-controls="print-preview-panel"
              :tabindex="activeIndex === index ? 0 : -1"
              @click="changeScreen(index)"
              @keydown="moveTab($event, index)"
            >
              <span class="pm-feature-tab-top"><MallIcon :name="featureHighlights[index]!.icon" /><strong>{{ screen.label }}</strong></span>
              <span class="pm-feature-tab-caption">{{ featureHighlights[index]!.caption }}</span>
              <MallIcon
                class="pm-feature-tab-arrow"
                name="chevron"
              />
            </button>
          </div>
          <div class="pm-feature-detail">
            <div class="pm-preview-toolbar">
              <p aria-live="polite">
                <span>0{{ activeIndex + 1 }} / 0{{ printManagementScreens.length }}</span>当前功能：<strong>{{ activeScreen.label }}</strong>
              </p><div>
                <button
                  type="button"
                  aria-label="上一个功能"
                  @click="stepScreen(-1)"
                >
                  ← 上一个
                </button><button
                  type="button"
                  aria-label="下一个功能"
                  @click="stepScreen(1)"
                >
                  下一个 →
                </button>
              </div>
            </div>
            <div
              id="print-preview-panel"
              class="pm-preview-panel"
              role="tabpanel"
              :aria-labelledby="`print-tab-${activeIndex}`"
            >
              <div class="pm-preview-copy">
                <div class="pm-feature-benefit">
                  <span>核心特点</span><strong>{{ activeHighlight.benefit }}</strong>
                </div>
                <p class="pm-eyebrow">
                  {{ activeScreen.eyebrow }}
                </p><h3>{{ activeScreen.title }}</h3><p>{{ activeScreen.text }}</p><ul>
                  <li
                    v-for="point in activeScreen.points"
                    :key="point"
                  >
                    {{ point }}
                  </li>
                </ul>
              </div><button
                class="pm-screenshot-button"
                type="button"
                aria-label="放大查看当前系统界面"
                @click="screenshotDialog?.showModal()"
              >
                <img
                  :src="`/assets/print-management/${activeScreen.image}.webp`"
                  :alt="activeScreen.alt"
                  loading="lazy"
                ><span>点击放大查看 <span aria-hidden="true">↗</span></span>
              </button>
            </div><p class="pm-preview-note">
              截图来自当前开发版本，账号、码值与数量均为演示示例；具体部署与设备适配需结合实际环境确认。
            </p>
          </div>
        </div>
      </div>
    </section>

    <section
      class="pm-container pm-flow"
      aria-labelledby="flow-title"
    >
      <div class="pm-section-intro">
        <p class="pm-eyebrow">
          连接每一个关键环节
        </p><h2 id="flow-title">
          从一批码，到每一张标签
        </h2><p>管理端完成准备与分配，工位端执行打印，记录留在系统中。</p>
      </div><ol class="pm-flow-grid">
        <li
          v-for="(step, index) in printManagementFlow"
          :key="step.title"
        >
          <span>0{{ index + 1 }}</span><h3>{{ step.title }}</h3><p>{{ step.text }}</p>
        </li>
      </ol>
    </section>

    <section
      class="pm-control-section"
      aria-labelledby="control-title"
    >
      <div class="pm-container pm-controls">
        <div>
          <p class="pm-eyebrow">
            关键规则，落实在操作中
          </p><h2 id="control-title">
            打印有边界<br>处理有依据
          </h2><p class="pm-control-description">
            围绕公司、工位、模板和码状态建立约束，<br>让日常执行与异常处理都有明确的路径。
          </p><RouterLink
            class="pm-text-link"
            to="/support/contact"
          >
            沟通你的管理需求 →
          </RouterLink>
        </div><div class="pm-control-list">
          <article><span>01</span><div><h3>公司数据按权限隔离</h3><p>分公司管理员在本公司范围内管理工位、员工与记录；总部进行跨公司的统一管理。</p></div></article><article><span>02</span><div><h3>模板版本跟随分配单</h3><p>修改默认模板影响后续新分配，已有分配仍使用原版本，避免正在执行的标签随意变版。</p></div></article><article><span>03</span><div><h3>首次打印与补打分开处理</h3><p>已打印码通过有原因、有任务的补打流程再次出纸，保留原首次打印日期与流水号。</p></div></article><article><span>04</span><div><h3>结果记录与现场核对结合</h3><p>系统提供打印明细、台账与日志；现场人员仍需核对实际出纸数量、版式与标签内容。</p></div></article>
        </div>
      </div>
    </section>

    <section class="pm-container pm-contact">
      <div>
        <p class="pm-eyebrow">
          让系统贴合你的打印现场
        </p><h2>聊聊你的组织与打印流程</h2><p>提供公司与工位分布、标签样式和打印机型号，一起梳理部署与使用方式。</p>
      </div><RouterLink
        class="pm-button"
        to="/support/contact"
      >
        联系张经理 <MallIcon name="chevron" />
      </RouterLink>
    </section>

    <dialog
      ref="screenshotDialog"
      class="pm-image-dialog"
      aria-label="系统界面大图"
      @click="($event.target === screenshotDialog) && screenshotDialog?.close()"
    >
      <div class="pm-dialog-heading">
        <strong>{{ activeScreen.label }}</strong><button
          type="button"
          aria-label="关闭系统界面大图"
          @click="screenshotDialog?.close()"
        >
          关闭 ×
        </button>
      </div><img
        :src="`/assets/print-management/${activeScreen.image}.webp`"
        :alt="activeScreen.alt"
      ><p>系统演示界面 · 示例数据</p>
    </dialog>
  </div>
</template>

<style scoped>
.print-management-page { background: #fff; color: #183552; }
.pm-container { width: min(1240px, calc(100% - 80px)); margin-inline: auto; }
.pm-eyebrow { color: #678cb4; font-size: 12px; letter-spacing: 1.5px; margin: 0 0 18px; }
.print-management-page h2 { font-size: clamp(27px, 2.5vw, 36px); font-weight: 600; line-height: 1.5; margin: 0 0 14px; }
.pm-hero { background: linear-gradient(120deg, #eef6fd, #e3eefb); overflow: hidden; }
.pm-hero-inner { display: grid; grid-template-columns: .9fr 1.35fr; gap: 44px; align-items: center; padding-block: 76px; }
.pm-hero h1 { font-size: clamp(34px, 3.25vw, 48px); font-weight: 600; letter-spacing: 1px; line-height: 1.5; margin: 0 0 22px; }
.pm-hero-description { color: #7087a0; font-size: 15px; line-height: 2; margin-bottom: 26px; }
.pm-hero-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 20px; }
.pm-button { display: inline-flex; align-items: center; gap: 22px; background: #2e7cd6; color: #fff; padding: 13px 20px; border-radius: 5px; font-size: 13px; white-space: nowrap; }
.pm-button:hover { background: #236bc0; }
.pm-button .mall-icon { width: 14px; height: 14px; }
.pm-text-link { color: #2e7cd6; font-size: 13px; }
.pm-hero-caption { display: block; margin-top: 25px; color: #93a7bd; font-size: 11px; }
.pm-hero-figure { margin: 0; background: #fff; border-radius: 8px; border: 1px solid #d3e2f2; overflow: hidden; box-shadow: 0 24px 50px #284d7a1a; transform: perspective(1300px) rotateY(-5deg); }
.pm-window-bar { display: flex; align-items: center; gap: 5px; padding: 12px 14px; border-bottom: 1px solid #e4ecf5; }
.pm-window-bar > span { width: 6px; height: 6px; border-radius: 50%; background: #c7d8eb; }
.pm-window-bar small { color: #8aa1bb; font-size: 10px; margin-left: 14px; }
.pm-hero-figure img { display: block; width: 100%; height: auto; }
.pm-hero-figure figcaption { font-size: 10px; color: #9baec1; padding: 10px 16px; border-top: 1px solid #edf2f8; }
.pm-section-intro { text-align: center; margin-bottom: 40px; }
.pm-section-intro > p:last-child { font-size: 14px; line-height: 1.9; color: #8395a9; margin: 0; }
.pm-roles { padding-block: 76px; }
.pm-role-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 38px; }
.pm-role-grid article { display: flex; gap: 20px; padding-top: 24px; border-top: 1px solid #dce7f3; }
.pm-role-grid .mall-icon { flex: none; width: 28px; height: 28px; color: #5a8cc0; margin-top: 5px; }
.pm-role-tag { color: #8da6c0; font-size: 11px; }
.pm-role-grid h3 { font-size: 19px; font-weight: 500; margin: 10px 0 12px; }
.pm-role-grid p { color: #7f91a5; font-size: 13px; line-height: 1.9; margin: 0; }
.pm-preview-section { background: #f5f8fc; padding-block: 44px; scroll-margin-top: 90px; }
.pm-preview-section .pm-section-intro { margin-bottom: 28px; }
.pm-preview-section .pm-section-intro .pm-eyebrow { margin-bottom: 10px; }
.pm-feature-layout { display: grid; grid-template-columns: 210px minmax(0,1fr); gap: 28px; align-items: start; }
.pm-feature-detail { min-width: 0; }
.pm-tabs { display: grid; grid-template-columns: 1fr; gap: 10px; margin: 0; }
.pm-tabs button { position: relative; display: flex; flex-direction: column; align-items: stretch; gap: 8px; background: #fff; border: 1px solid #dce6f2; border-radius: 6px; padding: 18px 30px 18px 16px; color: #345474; text-align: left; cursor: pointer; transition: background .18s, border-color .18s, box-shadow .18s; }
.pm-tabs button:hover { background: #edf5ff; border-color: #7cabde; box-shadow: 0 5px 14px #2c5d9410; }
.pm-tabs button[aria-selected="true"] { color: #fff; background: #2e7cd6; border-color: #2e7cd6; box-shadow: 0 7px 18px #2e7cd62b; }
.pm-feature-tab-top { display: flex; align-items: center; gap: 10px; }
.pm-feature-tab-top .mall-icon { width: 23px; height: 23px; flex: none; color: #6e99c7; }
.pm-feature-tab-top strong { font-size: 16px; font-weight: 600; }
.pm-feature-tab-caption { font-size: 12px; line-height: 1.7; color: #8b9eb4; }
.pm-feature-tab-arrow { position: absolute; right: 12px; top: 24px; width: 13px; height: 13px; color: #91acc7; }
.pm-tabs button[aria-selected="true"] .pm-feature-tab-arrow { color: white; }
.pm-feature-tab-action { display: flex; justify-content: space-between; align-items: center; font-size: 11px; color: #6c91b8; padding-top: 12px; margin-top: auto; border-top: 1px solid #e8eff7; }
.pm-tabs button[aria-selected="true"] .pm-feature-tab-top .mall-icon { color: #fff; }
.pm-tabs button[aria-selected="true"] .pm-feature-tab-caption { color: #e1efff; }
.pm-tabs button[aria-selected="true"] .pm-feature-tab-action { color: #fff; border-color: #ffffff38; }
.pm-feature-tab-action > span { font-size: 10px; }
.pm-preview-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 16px; }
.pm-preview-toolbar p { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 0; color: #8b9fb6; font-size: 12px; }
.pm-preview-toolbar p > span { color: #6994bf; margin-right: 12px; letter-spacing: 1px; }
.pm-preview-toolbar p > strong { color: #355675; font-size: 13px; font-weight: 500; }
.pm-preview-toolbar > div { display: flex; gap: 10px; flex: none; }
.pm-preview-toolbar button { padding: 8px 12px; background: #fff; border: 1px solid #d4e1ef; border-radius: 4px; color: #6484a6; font-size: 12px; cursor: pointer; }
.pm-preview-toolbar button:hover { color: #2e7cd6; border-color: #7eaadb; }
.pm-feature-benefit { border-left: 3px solid #2e7cd6; padding-left: 14px; margin-bottom: 18px; }
.pm-feature-benefit > span { display: block; font-size: 11px; color: #8ca7c0; margin-bottom: 10px; }
.pm-feature-benefit > strong { display: block; font-size: 21px; font-weight: 600; line-height: 1.55; color: #2c6eaf; }
.pm-preview-panel { display: grid; grid-template-columns: 245px minmax(0,1fr); gap: 24px; align-items: start; }
.pm-preview-copy .pm-eyebrow { display: none; }
.pm-preview-copy h3 { font-size: 16px; font-weight: 500; line-height: 1.6; margin: 0 0 12px; }
.pm-preview-copy > p:not(.pm-eyebrow) { font-size: 13px; color: #7c8ea5; line-height: 1.85; margin-bottom: 16px; }
.pm-preview-copy ul { list-style: none; padding: 0; margin: 0; }
.pm-preview-copy li { position: relative; padding-left: 16px; font-size: 12px; line-height: 1.9; color: #476789; margin-bottom: 12px; }
.pm-preview-copy li::before { content: ''; width: 4px; height: 4px; background: #6fa3d8; position: absolute; left: 0; top: 9px; border-radius: 50%; }
.pm-screenshot-button { display: block; min-width: 0; width: 100%; padding: 0; background: #fff; border: 1px solid #dce6f2; border-radius: 6px; overflow: hidden; cursor: zoom-in; box-shadow: 0 12px 24px #2449740a; }
.pm-screenshot-button img { display: block; width: 100%; height: 360px; object-fit: contain; background: #f7faff; }
.pm-screenshot-button > span { display: flex; justify-content: space-between; padding: 10px 15px; font-size: 11px; color: #7c97b4; border-top: 1px solid #edf2f8; }
.pm-preview-note { color: #98a8ba; font-size: 11px; line-height: 1.9; text-align: left; margin-top: 16px; margin-bottom: 0; }
.pm-flow { padding-block: 84px; }
.pm-flow-grid { list-style: none; display: grid; grid-template-columns: repeat(6,minmax(0,1fr)); gap: 24px; margin: 0; padding: 0; }
.pm-flow-grid li { border-top: 1px solid #dce6f1; padding-top: 22px; }
.pm-flow-grid li > span { font-size: 22px; color: #8cafce; font-weight: 300; }
.pm-flow-grid h3 { font-size: 15px; font-weight: 500; margin: 16px 0 10px; }
.pm-flow-grid p { font-size: 12px; line-height: 1.9; color: #8595a8; margin: 0; }
.pm-control-section { background: #f5f8fc; }
.pm-controls { display: grid; grid-template-columns: .9fr 1.2fr; gap: 100px; padding-block: 76px; }
.pm-control-description { color: #8296ad; font-size: 14px; line-height: 2; margin: 20px 0 26px; }
.pm-control-list article { display: flex; gap: 22px; padding-block: 24px; border-bottom: 1px solid #e0e9f3; }
.pm-control-list article:first-child { padding-top: 0; }
.pm-control-list article:last-child { padding-bottom: 0; border: 0; }
.pm-control-list article > span { font-size: 12px; color: #94afc9; margin-top: 4px; }
.pm-control-list h3 { margin: 0 0 10px; font-size: 17px; font-weight: 500; }
.pm-control-list p { margin: 0; color: #7e91a7; font-size: 13px; line-height: 1.95; }
.pm-contact { display: flex; align-items: center; justify-content: space-between; gap: 35px; padding-block: 64px; }
.pm-contact h2 { font-size: 30px; }
.pm-contact > div > p:last-child { color: #8799ad; font-size: 14px; line-height: 1.9; margin: 0; }
.pm-image-dialog { width: min(1500px, calc(100vw - 40px)); max-width: none; max-height: calc(100dvh - 40px); border: 0; border-radius: 8px; padding: 18px; color: #244160; }
.pm-image-dialog::backdrop { background: #102a48aa; }
.pm-dialog-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 14px; }
.pm-dialog-heading strong { font-size: 15px; font-weight: 500; }
.pm-dialog-heading button { border: 0; background: #eff5fc; color: #6082a6; padding: 8px 12px; border-radius: 4px; cursor: pointer; }
.pm-image-dialog > img { display: block; width: 100%; height: auto; }
.pm-image-dialog > p { font-size: 11px; color: #9aafc2; margin: 12px 0 0; }
.print-management-page a:focus-visible, .print-management-page button:focus-visible { outline: 2px solid #2e7cd6; outline-offset: 3px; }
@media(max-width: 1100px) { .pm-hero-inner { grid-template-columns: 1fr 1.15fr; gap: 25px; } .pm-role-grid { gap: 24px; } .pm-preview-panel { grid-template-columns: 240px minmax(0,1fr); gap: 24px; } .pm-controls { gap: 60px; } .pm-flow-grid { grid-template-columns: repeat(3,minmax(0,1fr)); gap: 30px; } }
@media(max-width: 750px) {
  .pm-container { width: calc(100% - 40px); }
  .pm-hero-inner { grid-template-columns: 1fr; gap: 32px; padding-block: 44px; }
  .pm-hero h1 { font-size: 34px; }
  .pm-hero-description { font-size: 14px; }
  .pm-hero-figure { transform: none; }
  .pm-roles, .pm-preview-section, .pm-flow { padding-block: 50px; }
  .pm-role-grid, .pm-preview-panel, .pm-controls { grid-template-columns: 1fr; }
  .pm-role-grid { gap: 24px; }
  .pm-section-intro { text-align: left; margin-bottom: 28px; }
  .pm-tabs { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; margin-bottom: 22px; }
  .pm-tabs button { padding: 15px 12px 12px; gap: 10px; }
  .pm-feature-tab-top { gap: 8px; }
  .pm-feature-tab-top strong { font-size: 15px; }
  .pm-feature-tab-top .mall-icon { width: 20px; height: 20px; }
  .pm-feature-tab-caption { font-size: 11px; }
  .pm-tabs button:last-child { grid-column: 1 / -1; }
  .pm-preview-toolbar { gap: 16px; flex-wrap: wrap; }
  .pm-preview-toolbar p > span { margin-right: 4px; }
  .pm-preview-copy h3 { font-size: 19px; }
  .pm-flow-grid { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 26px 22px; }
  .pm-controls { padding-block: 50px; gap: 34px; }
  .pm-contact { flex-direction: column; align-items: flex-start; padding-block: 48px; gap: 24px; }
  .pm-contact h2 { font-size: 27px; }
}
@media(min-width: 751px) and (max-width: 1100px) {
  .pm-feature-layout { grid-template-columns: 175px minmax(0,1fr); gap: 20px; }
  .pm-tabs button { padding: 16px 24px 16px 12px; }
  .pm-feature-tab-top { gap: 7px; }
  .pm-feature-tab-top strong { font-size: 14px; }
  .pm-feature-tab-top .mall-icon { width: 19px; height: 19px; }
  .pm-feature-tab-caption { font-size: 11px; }
  .pm-preview-panel { grid-template-columns: 1fr; }
  .pm-preview-copy ul { display: flex; flex-wrap: wrap; gap: 8px 20px; }
  .pm-preview-copy li { margin: 0; }
  .pm-screenshot-button img { height: 300px; }
}
@media(max-width: 750px) {
  .pm-feature-layout { display: block; }
  .pm-tabs { display: flex; overflow-x: auto; gap: 8px; padding-bottom: 10px; margin-bottom: 16px; }
  .pm-tabs button { flex: none; padding: 12px 14px; }
  .pm-feature-tab-caption, .pm-feature-tab-arrow { display: none; }
  .pm-preview-panel { grid-template-columns: 1fr; }
  .pm-screenshot-button img { height: auto; aspect-ratio: 3 / 2; }
}
@media(prefers-reduced-motion: reduce) { .pm-tabs button { transition: none; } }
</style>
