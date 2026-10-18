<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { wechatScanCallback } from '@/api/wechatLogin'
import { closeWeixinWindow } from '@/utils/weixin'

/**
 * 微信授权的**回跳页** —— 它跑在**手机上**（微信里），不是电脑上。
 *
 * 链路：电脑出二维码 → 手机微信扫码 → 客户点「允许」→ 微信把浏览器跳到本页
 * （带 `?code=..&state=..`）→ **本页**把这两个值交给服务端 → 服务端只识别身份、写进票据。
 *
 * ⚠️ 本页**不建会员、不发令牌** —— 那些要么等客户在电脑上补全（Q26），
 * 要么由电脑端轮询取走。所以本页的职责到"回执"为止：告诉客户"回电脑上继续"。
 *
 * ⚠️ 本页**必须落在公众号后台配的「网页授权域名」下**，否则微信会拒绝跳转。
 *
 * ## 成功之后自己关掉
 *
 * 确认成功就**主动关掉这一页**（微信内置浏览器里 `WeixinJSBridge` 的 `closeWindow`），
 * 客户因此不必再点左上角退回微信。三条都有理由：
 *
 * 1. **只在成功时关**。失败的场合客户得看见原因，闪没了只剩一脸茫然。
 * 2. **先显示一会儿再关**（{@link CLOSE_DELAY_MS}）。立即关的话客户来不及确认成功；
 *    而且电脑端那边可能还在等他补手机号，闪现一下更容易让人以为没成。
 * 3. **关不掉就留着那句提示**。不在微信里、或某个微信版本不认这个 API 时，
 *    页面照旧停在「请回到电脑上继续」—— 这里**不弹错**，因为关不上不是客户要处理的事。
 */
const route = useRoute()

/** 「已确认」停留多久再自己关掉 */
const CLOSE_DELAY_MS = 1200

const phase = ref<'doing' | 'ok' | 'fail'>('doing')
const message = ref('')

/** 收尾定时器。组件卸载要清掉，否则会去关客户随后打开的那一页 */
let closeTimer: ReturnType<typeof setTimeout> | null = null

onBeforeUnmount(() => {
  if (closeTimer) clearTimeout(closeTimer)
  closeTimer = null
})

onMounted(async () => {
  const code = String(route.query.code ?? '')
  const state = String(route.query.state ?? '')
  if (!code || !state) {
    phase.value = 'fail'
    message.value = '授权信息不完整，请回到电脑上重新扫码'
    return
  }
  try {
    await wechatScanCallback(code, state)
    phase.value = 'ok'
    closeTimer = setTimeout(() => {
      void closeWeixinWindow()
    }, CLOSE_DELAY_MS)
  } catch (e) {
    phase.value = 'fail'
    message.value = (e as { message?: string })?.message || '授权失败，请回到电脑上重新扫码'
  }
})
</script>

<template>
  <div class="wx-callback">
    <template v-if="phase === 'doing'">
      <p class="wx-callback-title">
        正在确认…
      </p>
    </template>

    <template v-else-if="phase === 'ok'">
      <p class="wx-callback-title">
        已确认
      </p>
      <p class="wx-callback-sub">
        请回到电脑上继续
      </p>
    </template>

    <template v-else>
      <p class="wx-callback-title">
        授权失败
      </p>
      <p class="wx-callback-sub">
        {{ message }}
      </p>
    </template>
  </div>
</template>

<style scoped>
.wx-callback {
  max-width: 420px;
  margin: 80px auto;
  padding: 0 20px;
  text-align: center;
}
.wx-callback-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: var(--ml-text);
}
.wx-callback-sub {
  margin: 0;
  font-size: 14px;
  color: var(--ml-text-sub);
}
</style>
