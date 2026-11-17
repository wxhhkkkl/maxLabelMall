<script setup lang="ts">
import { ref } from 'vue'

import { uploadFile } from '@/api/upload'
import { useToasts } from '@/components/base/useToasts'

/**
 * 客户服务图片上传（021 / FR-580）
 *
 * 投诉咨询与维修申请共用；最多 5 张，只选 JPG/PNG/WEBP。
 * 单张大小由上传接口兜底，前端不自造上限。
 */
const props = defineProps<{ modelValue: string[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string[]): void }>()

const toast = useToasts()
const uploading = ref(false)
const MAX_IMAGES = 5
const MAX_SIZE = 10 * 1024 * 1024

async function onPick(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return
  const remain = MAX_IMAGES - props.modelValue.length
  if (remain <= 0) {
    toast.error(`最多上传 ${MAX_IMAGES} 张图片`)
    return
  }
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  const typed = files.filter((file) => allowed.includes(file.type)).slice(0, remain)
  if (typed.length < files.length) {
    toast.error('只支持 JPG / PNG / WEBP，且最多 5 张')
  }
  const picked = typed.filter((file) => file.size <= MAX_SIZE)
  if (picked.length < typed.length) {
    toast.error('单张图片不能超过 10MB')
  }
  if (!picked.length) return
  uploading.value = true
  try {
    const next = [...props.modelValue]
    for (const file of picked) {
      next.push(await uploadFile(file))
    }
    emit('update:modelValue', next)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function remove(index: number) {
  const next = [...props.modelValue]
  next.splice(index, 1)
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="cs-images">
    <div v-for="(url, index) in modelValue" :key="url" class="cs-image">
      <img :src="url" alt="" />
      <button type="button" aria-label="移除图片" @click="remove(index)">×</button>
    </div>
    <label v-if="modelValue.length < MAX_IMAGES" class="cs-add-image">
      <input type="file" accept="image/jpeg,image/png,image/webp" multiple hidden @change="onPick" />
      {{ uploading ? '上传中…' : '+ 添加图片' }}
    </label>
  </div>
</template>

<style scoped>
.cs-images { display: flex; flex-wrap: wrap; gap: 10px; }
.cs-image { position: relative; width: 84px; height: 84px; border-radius: 8px; overflow: hidden; border: 1px solid #e3e9f4; }
.cs-image img { width: 100%; height: 100%; object-fit: cover; }
.cs-image button { position: absolute; top: 2px; right: 4px; border: 0; background: rgba(0, 0, 0, .45); color: #fff; border-radius: 50%; width: 18px; height: 18px; cursor: pointer; }
.cs-add-image { display: flex; align-items: center; justify-content: center; width: 84px; height: 84px; border: 1px dashed #b4c0d8; border-radius: 8px; color: #2e7cd6; font-size: 12px; cursor: pointer; text-align: center; }
</style>
