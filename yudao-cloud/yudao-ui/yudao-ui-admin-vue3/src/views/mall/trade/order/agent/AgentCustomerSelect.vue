<template>
  <Dialog v-model="visible" title="选择客户" width="820px" append-to-body>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="手机号">
        <el-input
          v-model="query.mobile"
          class="!w-200px"
          clearable
          placeholder="客户手机号"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input
          v-model="query.nickname"
          class="!w-200px"
          clearable
          placeholder="客户昵称"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-button type="primary" @click="search">
        <Icon class="mr-5px" icon="ep:search" />
        搜索
      </el-button>
    </el-form>

    <el-table
      v-loading="loading"
      :data="list"
      highlight-current-row
      row-key="id"
      @current-change="onCurrentChange"
    >
      <el-table-column width="50">
        <template #default="{ row }">
          <el-radio v-model="selectedId" :value="row.id">&nbsp;</el-radio>
        </template>
      </el-table-column>
      <el-table-column label="会员编号" prop="id" width="100" />
      <el-table-column label="昵称" prop="nickname" min-width="140" />
      <el-table-column label="手机号" prop="mobile" min-width="140" />
      <el-table-column label="等级" prop="levelName" min-width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
            {{ row.status === 0 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:limit="query.pageSize"
      v-model:page="query.pageNo"
      :total="total"
      @pagination="getList"
    />

    <template #footer>
      <el-button type="primary" :disabled="!selected" @click="confirm">确定</el-button>
      <el-button @click="visible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as MemberUserApi from '@/api/member/user'

defineOptions({ name: 'AgentCustomerSelect' })

const emit = defineEmits<{
  (e: 'select', user: MemberUserApi.UserVO): void
}>()

const visible = ref(false)
const loading = ref(false)
const total = ref(0)
const list = ref<MemberUserApi.UserVO[]>([])
const selectedId = ref<number>()
const selected = ref<MemberUserApi.UserVO>()
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  mobile: '',
  nickname: ''
})

const open = () => {
  visible.value = true
  search()
}
defineExpose({ open })

const search = () => {
  query.pageNo = 1
  getList()
}

const getList = async () => {
  loading.value = true
  try {
    const data = await MemberUserApi.getUserPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      mobile: query.mobile || undefined,
      nickname: query.nickname || undefined
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const onCurrentChange = (row?: MemberUserApi.UserVO) => {
  selected.value = row
  selectedId.value = row?.id
}

const confirm = () => {
  if (!selected.value) {
    return
  }
  emit('select', selected.value)
  visible.value = false
}
</script>
