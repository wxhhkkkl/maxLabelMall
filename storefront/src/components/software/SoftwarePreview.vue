<script setup lang="ts">
withDefaults(defineProps<{ announcement?: boolean }>(), { announcement: false })
</script>
<template>
  <div class="software-preview">
    <div
      class="preview-workspace"
      aria-hidden="true"
    >
      <img
        src="/assets/software/editor-preview.webp"
        alt=""
        width="2019"
        height="1236"
      >
      <div class="preview-paper">
        <strong>水性工业涂料</strong><p>型号 WX-210</p><p>色号 RAL7035</p><p>批次 TL-001</p><div class="preview-bars" />
      </div>
    </div>
    <div
      v-if="announcement"
      class="preview-announcement"
    >
      <p>正在打磨，更好的标签体验</p><strong>敬请期待</strong><span>MaxLabel · 开发中</span>
    </div>
  </div>
</template>
<style scoped>
.software-preview { position: relative; overflow: hidden; background: #EAF1FF; border-radius: 20px; isolation: isolate; }
.preview-workspace { position: absolute; inset: 0; filter: blur(4px); transform: scale(1.018); pointer-events: none; user-select: none; }
.preview-workspace > img { width: 100%; height: 100%; object-fit: fill; opacity: .78; }
.preview-paper { position: absolute; left: 29%; top: 30%; width: 33%; height: 55%; padding: 6% 4%; background: #FFFFFF; border-radius: 16px; color: #16233F; text-align: left; }
.preview-paper strong { font-size: clamp(16px, 2.6vw, 36px); }
.preview-paper p { margin: 8px 0; font-size: clamp(10px, 1.2vw, 18px); }
.preview-bars { height: 26%; margin-top: 8%; background: repeating-linear-gradient(90deg, #16233F 0 2px, transparent 2px 4px, #16233F 4px 5px, transparent 5px 9px, #16233F 9px 13px, transparent 13px 15px); }
.preview-announcement { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -42%); width: 370px; max-width: calc(100% - 40px); padding: 30px 24px; text-align: center; border: 1px solid #FFFFFF; border-radius: 20px; background: rgb(255 255 255 / 88%); box-shadow: 0 12px 38px rgb(46 124 214 / 12%); color: #0C2148; }
.preview-announcement p { margin: 0 0 12px; font-size: 16px; font-weight: 500; }
.preview-announcement strong { display: block; font-size: 40px; line-height: 1.4; letter-spacing: .06em; }
.preview-announcement span { display: block; margin-top: 8px; font-size: 14px; color: #5B6C8F; }
@media (max-width: 768px) {
  .preview-announcement { width: 300px; padding: 22px 16px; }
  .preview-announcement strong { font-size: 32px; }
  .preview-announcement p { font-size: 14px; }
  .preview-paper p { margin: 4px 0; }
}
</style>
