<script setup lang="ts">
import { ref } from 'vue'
import { supportContact } from '@/data/placeholders'
defineProps<{ compact?: boolean }>()
const copied = ref(false)
const copyMessage = ref('')
async function copyWechat() {
  try {
    await navigator.clipboard.writeText(supportContact.wechat)
    copied.value = true
    copyMessage.value = '微信号已复制，可在微信中搜索添加。'
  } catch {
    copyMessage.value = '请长按或选中微信号复制，在微信中搜索添加。'
  }
}
</script>

<template>
  <div
    class="support-contact-cards"
    :class="{ 'support-contact-compact': compact }"
  >
    <div class="support-contact-person">
      <p class="support-contact-kicker">
        MaxLabel · 服务与支持
      </p>
      <div
        class="support-contact-avatar"
        aria-hidden="true"
      >
        <svg
          viewBox="0 0 48 48"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
        ><circle
          cx="24"
          cy="16"
          r="7"
        /><path d="M10 40v-3a14 14 0 0 1 28 0v3" /></svg>
      </div>
      <h3>{{ supportContact.name }}</h3>
      <p class="support-contact-person-note">
        聊聊你的需求，<br>一起找到合适的标签方案。
      </p>
      <span
        class="support-contact-person-line"
        aria-hidden="true"
      />
    </div>
    <div class="support-contact-methods">
      <div class="support-contact-method">
        <span
          class="support-contact-method-icon"
          aria-hidden="true"
        ><svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linejoin="round"
        ><path d="M5 3h4l2 5-3 2a16 16 0 0 0 6 6l2-3 5 2v4a2 2 0 0 1-2 2A18 18 0 0 1 3 5a2 2 0 0 1 2-2Z" /></svg></span>
        <div class="support-contact-method-body">
          <p class="support-contact-label">
            电话沟通
          </p>
          <span
            class="support-contact-phone"
          >{{ supportContact.phone }}</span>
          <p class="support-contact-hint">
            直接拨打，沟通更清楚
          </p>
        </div>
      </div>
      <div class="support-contact-method support-contact-wechat-row">
        <span
          class="support-contact-method-icon"
          aria-hidden="true"
        ><svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        ><path d="M21 11a9 9 0 0 1-9 9H8l-5 2 1-5a9 9 0 1 1 17-6Z" /><path d="M8 11h.01M12 11h.01M16 11h.01" /></svg></span>
        <div class="support-contact-method-body">
          <p class="support-contact-label">
            微信联系
          </p>
          <span class="support-contact-wechat">{{ supportContact.wechat }}</span>
          <p class="support-contact-hint">
            添加微信，方便发送样张与图片
          </p>
        </div>
        <button
          class="support-contact-copy"
          type="button"
          @click="copyWechat"
        >
          {{ copied ? '已复制' : '复制微信号' }}
        </button>
      </div>
      <p
        v-if="copyMessage"
        class="support-contact-copy-message"
        role="status"
      >
        {{ copyMessage }}
      </p>
      <div class="support-contact-topics">
        <span>我们可以聊</span><p>设备使用 <i>·</i> 打印问题 <i>·</i> 耗材选择</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.support-contact-cards { display: grid; grid-template-columns: 32% minmax(0, 1fr); margin-top: 32px; border: 1px solid #E3EAF5; border-radius: 22px; background: #FFFFFF; overflow: hidden; box-shadow: 0 16px 50px rgb(25 64 122 / 5%); }
.support-contact-person { position: relative; isolation: isolate; overflow: hidden; padding: 42px 36px; color: #FFFFFF; background: linear-gradient(145deg, #2E7CD6, #2464BC); }
.support-contact-person::after { content: ''; position: absolute; z-index: -1; width: 280px; height: 280px; border: 1px solid rgb(255 255 255 / 12%); border-radius: 50%; right: -170px; bottom: -90px; box-shadow: 0 0 0 40px rgb(255 255 255 / 3%), 0 0 0 80px rgb(255 255 255 / 3%); }
.support-contact-kicker { font-size: 12px; letter-spacing: .06em; opacity: .8; }
.support-contact-avatar { width: 64px; height: 64px; margin-top: 42px; padding: 12px; border: 1px solid rgb(255 255 255 / 35%); border-radius: 50%; background: rgb(255 255 255 / 8%); }
.support-contact-avatar svg { width: 100%; height: 100%; }
.support-contact-person h3 { color: #FFFFFF; font-size: 30px; font-weight: 600; margin: 18px 0 12px; }
.support-contact-person-note { font-size: 14px; color: #E2EEFF; }
.support-contact-person-line { display: block; width: 32px; height: 2px; background: rgb(255 255 255 / 55%); margin-top: 30px; }
.support-contact-methods { padding: 20px 40px 0; min-width: 0; }
.support-contact-method { display: flex; align-items: center; gap: 20px; padding: 28px 0; }
.support-contact-wechat-row { border-top: 1px solid #EBEFF6; }
.support-contact-method-icon { align-self: flex-start; display: grid; place-items: center; flex: 0 0 42px; width: 42px; height: 42px; border-radius: 12px; color: #2E7CD6; background: #F0F5FD; margin-top: 4px; }
.support-contact-method-icon svg { width: 22px; height: 22px; }
.support-contact-method-body { flex: 1; min-width: 0; }
.support-contact-label { font-size: 13px; color: #647596; margin-bottom: 7px; }
.support-contact-phone, .support-contact-wechat { display: inline-block; color: #0C2148; font-size: clamp(22px, 2.4vw, 32px); font-weight: 600; line-height: 1.4; letter-spacing: -.025em; overflow-wrap: anywhere; }
.support-contact-wechat { font-size: 25px; }
.support-contact-hint { font-size: 12px; color: #8996AE; margin-top: 8px; }
.support-contact-copy { white-space: nowrap; border: 1px solid #DAE5F5; border-radius: 7px; background: #FFFFFF; color: #2E7CD6; font-size: 12px !important; padding: 10px 14px; }
.support-contact-copy:hover { background: #F0F5FD; }
.support-contact-topics { display: flex; align-items: center; gap: 22px; padding: 21px 0; border-top: 1px solid #EBEFF6; color: #657594; font-size: 12px; }
.support-contact-topics > span { color: #95A1B7; white-space: nowrap; }
.support-contact-topics i { padding-inline: 10px; color: #AFB9CA; font-style: normal; }
.support-contact-copy-message { color: #2E7CD6; font-size: 12px; padding-bottom: 16px; }
.support-contact-compact { grid-template-columns: minmax(230px, 30%) minmax(0, 1fr); }
.support-contact-compact .support-contact-person { padding: 28px 32px; }
.support-contact-compact .support-contact-avatar { margin-top: 22px; width: 48px; height: 48px; padding: 9px; }
.support-contact-compact .support-contact-person h3 { font-size: 27px; margin-top: 12px; }
.support-contact-compact .support-contact-person-line, .support-contact-compact .support-contact-topics { display: none; }
.support-contact-compact .support-contact-methods { padding: 0 32px; }
.support-contact-compact .support-contact-method { padding: 24px 0; }
.support-contact-compact .support-contact-phone { font-size: 28px; }
@media (min-width: 769px) and (max-width: 1000px) {
  .support-contact-compact .support-contact-methods { padding-inline: 24px; }
  .support-contact-compact .support-contact-method { flex-wrap: wrap; gap: 12px; }
  .support-contact-compact .support-contact-method-body { flex-basis: calc(100% - 62px); }
  .support-contact-compact .support-contact-copy { margin-left: 62px; }
}
@media (max-width: 768px) {
  .support-contact-cards { grid-template-columns: 1fr; border-radius: 16px; }
  .support-contact-person { padding: 28px; }
  .support-contact-avatar { margin-top: 24px; width: 52px; height: 52px; }
  .support-contact-person h3 { font-size: 27px; }
  .support-contact-person-note br { display: none; }
  .support-contact-person-line { display: none; }
  .support-contact-methods { padding: 4px 24px 0; }
  .support-contact-method { flex-wrap: wrap; gap: 14px; padding: 25px 0; }
  .support-contact-method-icon { flex-basis: 36px; width: 36px; height: 36px; border-radius: 10px; }
  .support-contact-phone { font-size: 25px; }
  .support-contact-wechat { font-size: 24px; }
  .support-contact-copy { margin-left: 50px; }
  .support-contact-method-body { flex-basis: calc(100% - 50px); }
  .support-contact-topics { flex-direction: column; align-items: flex-start; gap: 6px; }
  .support-contact-topics i { padding-inline: 5px; }
  .support-contact-compact { grid-template-columns: 1fr; }
  .support-contact-compact .support-contact-person { display: grid; grid-template-columns: 44px 1fr; column-gap: 14px; padding: 24px; align-items: center; }
  .support-contact-compact .support-contact-kicker { grid-column: 1 / -1; margin-bottom: 18px; }
  .support-contact-compact .support-contact-avatar { width: 44px; height: 44px; margin: 0; grid-column: 1; grid-row: 2; }
  .support-contact-compact .support-contact-person h3 { grid-column: 2; grid-row: 2; margin: 0; font-size: 24px; }
  .support-contact-compact .support-contact-person-note { display: none; }
  .support-contact-compact .support-contact-methods { padding-inline: 24px; }
  .support-contact-compact .support-contact-method { gap: 12px; padding-block: 22px; }
  .support-contact-compact .support-contact-method-body { flex-basis: calc(100% - 48px); }
  .support-contact-compact .support-contact-phone { font-size: 24px; }
  .support-contact-compact .support-contact-copy { margin-left: 48px; }
  .support-contact-compact .support-contact-hint { display: none; }
}
</style>
