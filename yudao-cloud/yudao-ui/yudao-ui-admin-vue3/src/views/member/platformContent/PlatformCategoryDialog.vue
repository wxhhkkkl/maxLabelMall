<template>
  <Dialog :model-value="visible" title="平台共享内容分类管理" width="480px"
          @update:model-value="(value: boolean) => emit('update:visible', value)">
    <div style="display: flex; gap: 8px; margin-bottom: 12px">
      <el-input v-model="newName" maxlength="64" placeholder="新分类名称" @keyup.enter="add" />
      <el-button type="primary" :loading="busy" @click="add">添加</el-button>
    </div>
    <el-table v-loading="busy" :data="list" :stripe="true" max-height="360">
      <el-table-column label="分类名称" prop="name" />
      <el-table-column label="排序" prop="sort" width="90" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { PlatformContentApi, type PlatformContentCategoryVO } from '@/api/member/platformContent'

defineOptions({ name: 'PlatformCategoryDialog' })

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [boolean]; changed: [] }>()

const message = useMessage()
const list = ref<PlatformContentCategoryVO[]>([])
const newName = ref('')
const busy = ref(false)

const load = async () => {
  busy.value = true
  try {
    list.value = (await PlatformContentApi.getCategories()) ?? []
  } finally {
    busy.value = false
  }
}

watch(() => props.visible, (visible) => { if (visible) void load() })

const add = async () => {
  const name = newName.value.trim()
  if (!name) return
  busy.value = true
  try {
    await PlatformContentApi.createCategory(name)
    newName.value = ''
    message.success('已添加分类')
    await load()
    emit('changed')
  } finally {
    busy.value = false
  }
}

const remove = async (row: PlatformContentCategoryVO) => {
  if (!row.id) return
  await message.delConfirm(`确认删除分类「${row.name}」？已有内容的分类文本不会改变。`)
  busy.value = true
  try {
    await PlatformContentApi.deleteCategory(row.id)
    message.success('已删除分类')
    await load()
    emit('changed')
  } finally {
    busy.value = false
  }
}
</script>
