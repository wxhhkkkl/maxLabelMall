<template>
  <el-alert v-if="getEnable()" type="success" show-icon>
    <template #title>
      <div @click="goToUrl">{{ '【' + title + '】文档地址：' + url }}</div>
    </template>
  </el-alert>
</template>
<script setup lang="tsx">
import { propTypes } from '@/utils/propTypes'

defineOptions({ name: 'DocAlert' })

const props = defineProps({
  title: propTypes.string,
  url: propTypes.string
})

/** 跳转 URL 链接 */
const goToUrl = () => {
  window.open(props.url)
}

/**
 * 是否开启。
 *
 * ⚠️ **本项目把默认值反转成了「默认关闭」**（上游是 `!== 'false'`，即默认开）。
 * 原因：上游在每个内页顶部都插了这个组件（**350 个页面**），渲染成绿色条指向
 * `doc.iocoder.cn` 的官方文档 —— 对本项目的使用者没有意义，管理端不该长这样。
 *
 * 另外，上游靠 `VITE_APP_DOCALERT_ENABLE` 这个 env 变量控制，但管理端的 `.env`
 * **不在版本控制里**（被根 .gitignore 的 `*.env` 忽略），靠改它「永久关闭」是做不到的 ——
 * 换个 clone 就失效。所以把决定落到**代码**里，它才真的随仓库走、对所有构建模式生效。
 *
 * 需要临时打开时：把 `VITE_APP_DOCALERT_ENABLE` 设为 `'true'`（.env / .env.local 均可）。
 */
const getEnable = () => {
  return import.meta.env.VITE_APP_DOCALERT_ENABLE === 'true'
}
</script>
<style scoped>
.el-alert--success.is-light {
  margin-bottom: 10px;
  cursor: pointer;
  border: 1px solid green;
}
</style>
