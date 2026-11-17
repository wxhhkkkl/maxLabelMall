<template>
  <Dialog v-model="visible" title="选择会员" width="560px">
    <el-form inline @submit.prevent="search">
      <el-form-item label="关键字">
        <el-input
          v-model="keyword"
          class="!w-260px"
          clearable
          placeholder="11 位手机号 / 昵称"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="search">搜索</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="list" max-height="320px">
      <el-table-column label="昵称" prop="nickname" />
      <el-table-column label="手机号" prop="mobile" width="130" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="choose(row)">选择</el-button>
        </template>
      </el-table-column>
    </el-table>
    <p v-if="searched && !loading && !list.length" class="text-13px text-gray-500">
      没有匹配到会员：请确认客户已注册
    </p>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'

defineOptions({ name: 'CustomerServiceMemberPickerDialog' })
const emit = defineEmits<{ (e: 'select', member: Api.CustomerServiceMemberVO): void }>()

const visible = ref(false)
const loading = ref(false)
const searched = ref(false)
const keyword = ref('')
const list = ref<Api.CustomerServiceMemberVO[]>([])

const search = async () => {
  if (!keyword.value.trim()) return
  loading.value = true
  searched.value = true
  try {
    list.value = await Api.searchCustomerServiceMembers(keyword.value.trim())
  } finally {
    loading.value = false
  }
}

const choose = (member: Api.CustomerServiceMemberVO) => {
  visible.value = false
  emit('select', member)
}

const open = () => {
  keyword.value = ''
  list.value = []
  searched.value = false
  visible.value = true
}

defineExpose({ open })
</script>
