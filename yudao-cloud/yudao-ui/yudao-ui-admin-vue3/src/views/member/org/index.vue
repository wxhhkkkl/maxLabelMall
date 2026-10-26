<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="企业名称" prop="name">
        <el-input
          v-model="queryParams.name"
          class="!w-240px"
          clearable
          placeholder="请输入企业名称"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-160px" clearable placeholder="全部">
          <el-option label="启用" :value="0" />
          <el-option label="停用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          v-hasPermi="['member:org:create']"
          type="primary"
          @click="openForm()"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增企业
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="编号" prop="id" width="80" />
      <el-table-column label="企业名称" prop="name" min-width="200" />
      <el-table-column label="OWNER" min-width="160">
        <template #default="{ row }">
          {{ row.ownerNickname || '—' }}
          <span class="text-gray-400">{{ row.ownerMobile || '' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="成员" prop="memberCount" width="80" />
      <el-table-column label="席位" width="120">
        <template #default="{ row }">{{ row.seatUsed ?? 0 }} / {{ row.seatTotal ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="授权到期" prop="licenseEndDate" width="120" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">成员 / 席位</el-button>
          <el-button
            v-hasPermi="['member:org:update']"
            link
            type="primary"
            @click="openForm(row)"
          >
            编辑
          </el-button>
          <el-button
            v-hasPermi="['member:org:update']"
            link
            type="danger"
            @click="toggleStatus(row)"
          >
            {{ row.status === 0 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="formVisible" :title="formTitle">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="企业名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入企业名称" />
      </el-form-item>
      <el-form-item label="OWNER" prop="ownerMemberId">
        <el-select
          v-model="formData.ownerMemberId"
          :disabled="!!formData.id"
          :loading="memberLoading"
          class="!w-400px"
          clearable
          filterable
          remote
          :remote-method="searchMembers"
          placeholder="输入手机号搜索会员"
        >
          <el-option
            v-for="member in memberOptions"
            :key="member.id"
            :label="memberLabel(member)"
            :value="member.id"
          />
        </el-select>
        <span class="ml-10px text-12px text-gray-500">按手机号搜索，选中即设为该企业 OWNER</span>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button :loading="formLoading" type="primary" @click="submitForm">确定</el-button>
    </template>
  </Dialog>

  <Dialog v-model="detailVisible" title="企业成员与授权" width="900px">
    <OrgDetail v-if="detailOrgId" :key="detailOrgId" :org-id="detailOrgId" @changed="getList" />
  </Dialog>
</template>

<script setup lang="ts">
import {
  createOrg,
  getOrg,
  getOrgPage,
  updateOrg,
  updateOrgStatus,
  type OrgSaveVO,
  type OrgVO
} from '@/api/member/org'
import { getUserPage, type UserVO } from '@/api/member/user'
import OrgDetail from './OrgDetail.vue'

defineOptions({ name: 'MemberOrg' })

const message = useMessage()
const loading = ref(false)
const list = ref<OrgVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive<{
  pageNo: number
  pageSize: number
  name?: string
  status?: number
}>({ pageNo: 1, pageSize: 10, name: '', status: undefined })

const formVisible = ref(false)
const formTitle = ref('')
const formLoading = ref(false)
const formRef = ref()
const formData = reactive<OrgSaveVO>({ name: '', ownerMemberId: undefined as never, remark: '' })
const formRules = reactive({
  name: [{ required: true, message: '企业名称不能为空', trigger: 'blur' }],
  ownerMemberId: [{ required: true, message: 'OWNER 不能为空', trigger: 'blur' }]
})

const detailVisible = ref(false)
const detailOrgId = ref<number>()

// OWNER 选择：按手机号远程搜索会员，不要求运营手填会员 ID
const memberOptions = ref<UserVO[]>([])
const memberLoading = ref(false)
const memberLabel = (member: UserVO) =>
  `${member.nickname || '未命名'}（${member.mobile || '无手机号'}）`
const searchMembers = async (query: string) => {
  if (!query || query.trim().length < 3) return
  memberLoading.value = true
  try {
    const data = await getUserPage({ pageNo: 1, pageSize: 20, mobile: query.trim() })
    memberOptions.value = data.list
  } finally {
    memberLoading.value = false
  }
}

const getList = async () => {
  loading.value = true
  try {
    const data = await getOrgPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.name = ''
  queryParams.status = undefined
  handleQuery()
}

const openForm = async (row?: OrgVO) => {
  formVisible.value = true
  formTitle.value = row ? '编辑企业' : '新增企业'
  formLoading.value = true
  try {
    if (row) {
      const data = await getOrg(row.id)
      Object.assign(formData, data)
      // 编辑时回显当前 OWNER（手机号是脱敏的，仅用于显示）
      memberOptions.value = data.ownerMemberId
        ? [{ id: data.ownerMemberId, nickname: data.ownerNickname, mobile: data.ownerMobile }]
        : []
    } else {
      Object.assign(formData, { id: undefined, name: '', ownerMemberId: undefined, remark: '' })
      memberOptions.value = []
    }
  } finally {
    formLoading.value = false
  }
}

const submitForm = async () => {
  await formRef.value?.validate()
  formLoading.value = true
  try {
    if (formData.id) await updateOrg(formData)
    else await createOrg(formData)
    message.success('保存成功')
    formVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

const toggleStatus = async (row: OrgVO) => {
  const next = row.status === 0 ? 1 : 0
  await message.confirm(`确定${next === 1 ? '停用' : '启用'}「${row.name}」吗？`)
  await updateOrgStatus(row.id, next)
  message.success('操作成功')
  await getList()
}

const openDetail = (row: OrgVO) => {
  detailOrgId.value = row.id
  detailVisible.value = true
}

onMounted(getList)
</script>
