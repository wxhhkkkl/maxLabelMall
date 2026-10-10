<script setup lang="ts">
import { ref } from 'vue'

import { uploadFile } from '@/api/upload'
import { COMMENT_PIC_MAX } from '@/utils/comment'

/**
 * 评价配图上传（FR-076）。
 *
 * 与 `AvatarUploader` 是**两个组件**而不是一个参数化的：那个是"单张、圆形、可替换"，
 * 这个是"多张、九宫格、可删除"，形态与语义都不一样；而且它在个人中心已经在用，
 * 改成支持多张会动到无关页面（原则 IV）。
 *
 * 两条不能错的行为：
 * - **超过上限在上传前就拦住** —— 传完 10 张再报错，用户白等；
 * - **中途失败不丢已传成功的** —— 把已经拿到的 URL 先交出去，再报错。
 *
 * ⚠️ 上传的体积/格式上限由**后台配置**决定，这里不编数字，超限把后端文案透出来。
 */
const props = withDefaults(defineProps<{ modelValue: string[]; max?: number }>(), {
  max: COMMENT_PIC_MAX,
})

const emit = defineEmits<{ 'update:modelValue': [string[]] }>()

const uploading = ref(false)
const error = ref('')

async function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (!files.length || uploading.value) return

  error.value = ''
  const room = props.max - props.modelValue.length
  if (room <= 0) {
    error.value = `最多上传 ${props.max} 张图片`
    input.value = ''
    return
  }
  // 只取装得下的那几张，多出来的直接忽略并说明 —— 不是默默丢掉
  const take = files.slice(0, room)
  if (files.length > room) {
    error.value = `最多上传 ${props.max} 张图片，多余的已忽略`
  }

  uploading.value = true
  const urls = [...props.modelValue]
  try {
    for (const f of take) {
      urls.push(await uploadFile(f))
    }
    emit('update:modelValue', urls)
  } catch (err) {
    // 失败前已经传成功的先交出去 —— 否则用户得把传过的再传一遍
    if (urls.length > props.modelValue.length) {
      emit('update:modelValue', urls)
    }
    error.value = (err as { message?: string })?.message || '图片上传失败，请重试'
  } finally {
    uploading.value = false
    // 允许重复选同一个文件（否则第二次 change 不触发）
    input.value = ''
  }
}

function remove(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== index),
  )
}
</script>

<template>
  <div class="mi-wrap">
    <div v-if="modelValue.length" class="mi-list">
      <div v-for="(u, i) in modelValue" :key="u + i" class="mi-box">
        <img class="mi-img" :src="u" alt="评价图片" >
        <button class="mi-del" type="button" aria-label="删除这张图片" @click="remove(i)">×</button>
      </div>
    </div>

    <label class="mi-pick" :class="{ 'is-disabled': uploading || modelValue.length >= max }">
      {{ uploading ? '上传中…' : `添加图片（${modelValue.length}/${max}）` }}
      <input
        id="commentPics"
        class="mi-file"
        type="file"
        accept="image/*"
        multiple
        :disabled="uploading || modelValue.length >= max"
        @change="onPick"
      />
    </label>

    <p v-if="error" class="ml-error">{{ error }}</p>
  </div>
</template>

<style scoped>
.mi-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.mi-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.mi-box {
  position: relative;
}
.mi-img {
  width: 76px;
  height: 76px;
  object-fit: cover;
  border-radius: var(--ml-radius-field);
  border: 1px solid var(--ml-border);
}
.mi-del {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--ml-text-weak);
  color: var(--ml-white);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}
.mi-pick {
  align-self: flex-start;
  padding: 6px 14px;
  border: 1px dashed var(--ml-border);
  border-radius: var(--ml-radius-pill);
  color: var(--ml-text-sub);
  font-size: 13px;
  cursor: pointer;
}
.mi-pick.is-disabled {
  color: var(--ml-text-weak);
  cursor: not-allowed;
}
/* 原生 file input 藏起来，用上面的 label 当按钮 —— 不引 UI 组件库 */
.mi-file {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  overflow: hidden;
}
</style>
