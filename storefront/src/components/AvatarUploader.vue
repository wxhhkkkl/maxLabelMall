<script setup lang="ts">
import { ref } from 'vue'

import { uploadFile } from '@/api/upload'

/**
 * 头像上传控件（最小实现）。
 *
 * 只做一件事：**选文件 → 调后台上传 → 把拿到的 URL 交出去**。组件本身不提交表单，
 * 也不负责"移除头像"（本期没有这个需求，后端 `avatar` 是可空字段但界面不提供清空入口）。
 *
 * ⚠️ **上传失败时不 emit** —— 否则会把一个还没有 URL 的头像提交上去，
 * 而后端 `avatar` 带 `@URL` 校验，收到空值/非法值会整体保存失败，
 * 用户会连带丢掉已经填好的其它字段。**保留原值**才是对的。
 *
 * ⚠️ 上传的体积/格式上限由**后台配置**决定，这里不自己编一个数字，超限就把后端文案透出来。
 */
// 只声明不给脚本变量：本组件的脚本里不需要读 props（模板直接用自动解构的值）
defineProps<{ modelValue: string; nickname?: string }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()

const uploading = ref(false)
const error = ref('')

async function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || uploading.value) return
  error.value = ''
  uploading.value = true
  try {
    const url = await uploadFile(file)
    emit('update:modelValue', url)
  } catch (err) {
    // 原样透出后端文案（如「文件大小超出限制」），并保留原来的头像
    error.value = (err as { message?: string })?.message || '头像上传失败，请重试'
  } finally {
    uploading.value = false
    // 允许重复选同一个文件（否则第二次 change 不触发）
    input.value = ''
  }
}
</script>

<template>
  <div class="av-wrap">
    <img v-if="modelValue" class="av-img" :src="modelValue" :alt="nickname || '头像'" />
    <span v-else class="av-ph">未设置</span>

    <div class="av-actions">
      <label id="avatarPick" class="av-pick" :class="{ 'is-disabled': uploading }">
        {{ uploading ? '上传中…' : modelValue ? '更换头像' : '上传头像' }}
        <input
          id="avatarFile"
          class="av-file"
          type="file"
          accept="image/*"
          :disabled="uploading"
          @change="onPick"
        />
      </label>
      <p v-if="error" class="ml-error">{{ error }}</p>
    </div>
  </div>
</template>

<style scoped>
.av-wrap {
  display: flex;
  align-items: center;
  gap: 14px;
}
.av-img {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--ml-border);
}
.av-ph {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 1px dashed var(--ml-border);
  color: var(--ml-text-ph);
  font-size: 12px;
}
.av-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.av-pick {
  display: inline-block;
  padding: 4px 12px;
  border: 1px solid var(--ml-border);
  border-radius: var(--ml-radius-pill);
  color: var(--ml-text-sub);
  font-size: 13px;
  cursor: pointer;
}
.av-pick.is-disabled {
  color: var(--ml-text-weak);
  cursor: not-allowed;
}
/* 原生 file input 藏起来，用上面的 label 当按钮 —— 不引 UI 组件库 */
.av-file {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  overflow: hidden;
}
</style>
