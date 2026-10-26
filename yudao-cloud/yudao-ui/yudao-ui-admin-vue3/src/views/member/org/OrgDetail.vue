<template>
  <div>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="成员与席位" name="members">
        <el-form inline class="mb-10px">
          <el-form-item label="手机号">
            <el-input v-model="mobile" placeholder="输入已注册会员手机号" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleAddMember">加入成员</el-button>
            <el-button @click="loadMembers">刷新</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="memberLoading" :data="members" stripe>
          <el-table-column label="会员" min-width="160">
            <template #default="{ row }">
              {{ row.nickname || row.mobileMasked || row.memberId }}
              <span class="text-gray-400">（{{ row.memberId }}）</span>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="100">
            <template #default="{ row }">
              <el-tag :type="row.role === 1 ? 'danger' : row.role === 2 ? 'warning' : 'info'">
                {{ roleLabel(row.role) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="席位" width="200">
            <template #default="{ row }">
              <el-tag v-if="row.seatActive" type="success">有效至 {{ row.seatEndDate }}</el-tag>
              <el-tag v-else type="info">未分配</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="300" fixed="right">
            <template #default="{ row }">
              <el-button v-if="!row.seatActive" link type="primary" @click="handleAssign(row)">
                分配席位
              </el-button>
              <el-button v-else link type="danger" @click="handleRevoke(row)">撤销席位</el-button>
              <el-button
                v-if="row.role !== 1"
                v-hasPermi="['member:org:member:update-role']"
                link
                type="primary"
                @click="handleToggleRole(row)"
              >
                {{ row.role === 2 ? '取消管理员' : '设为管理员' }}
              </el-button>
              <el-button
                v-if="row.role !== 1"
                v-hasPermi="['member:org:owner:transfer']"
                link
                type="warning"
                @click="handleTransfer(row)"
              >
                转交 OWNER
              </el-button>
              <el-button
                v-if="row.role !== 1"
                v-hasPermi="['member:org:member:remove']"
                link
                type="danger"
                @click="handleRemove(row)"
              >
                移出
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          v-model:limit="memberQuery.pageSize"
          v-model:page="memberQuery.pageNo"
          :total="memberTotal"
          @pagination="loadMembers"
        />
      </el-tab-pane>

      <el-tab-pane label="授权批次" name="licenses">
        <el-table v-loading="licenseLoading" :data="licenses" stripe>
          <el-table-column label="周期" width="100">
            <template #default="{ row }">{{ periodLabel(row.periodMonths) }}</template>
          </el-table-column>
          <el-table-column label="席位" prop="seatsTotal" width="90" />
          <el-table-column label="有效期" min-width="220">
            <template #default="{ row }">
              {{ row.startDate }} ~ {{ row.endDate }}
              <el-tag v-if="row.active" type="success" class="ml-5px">{{ row.remainingDays }} 天</el-tag>
              <el-tag v-else type="info" class="ml-5px">已过期</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源订单" prop="sourceOrderNo" min-width="170" />
          <el-table-column label="退款" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.refunded" type="warning">来源已退款</el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          v-model:limit="licenseQuery.pageSize"
          v-model:page="licenseQuery.pageNo"
          :total="licenseTotal"
          @pagination="loadLicenses"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import {
  addOrgMember,
  assignOrgSeat,
  getOrgLicensePage,
  getOrgMemberPage,
  removeOrgMember,
  revokeOrgSeat,
  transferOrgOwner,
  updateOrgMemberRole,
  type OrgLicenseVO,
  type OrgMemberVO
} from '@/api/member/org'

defineOptions({ name: 'MemberOrgDetail' })

const props = defineProps<{ orgId: number }>()
const emit = defineEmits(['changed'])
const message = useMessage()

const activeTab = ref('members')
const mobile = ref('')
const members = ref<OrgMemberVO[]>([])
const memberTotal = ref(0)
const memberLoading = ref(false)
const memberQuery = reactive({ pageNo: 1, pageSize: 10 })
const licenses = ref<OrgLicenseVO[]>([])
const licenseTotal = ref(0)
const licenseLoading = ref(false)
const licenseQuery = reactive({ pageNo: 1, pageSize: 10 })

const roleLabel = (role: number) => (role === 1 ? 'OWNER' : role === 2 ? '管理员' : '成员')
const periodLabel = (months: number) =>
  months % 12 === 0 ? `${months / 12} 年` : `${months} 个月`

const loadMembers = async () => {
  memberLoading.value = true
  try {
    const data = await getOrgMemberPage({ ...memberQuery, orgId: props.orgId })
    members.value = data.list
    memberTotal.value = data.total
  } finally {
    memberLoading.value = false
  }
}

const loadLicenses = async () => {
  licenseLoading.value = true
  try {
    const data = await getOrgLicensePage({ ...licenseQuery, orgId: props.orgId })
    licenses.value = data.list
    licenseTotal.value = data.total
  } finally {
    licenseLoading.value = false
  }
}

const onTabChange = async (name: string) => {
  if (name === 'members') await loadMembers()
  else await loadLicenses()
}

const handleAddMember = async () => {
  if (!mobile.value.trim()) return
  await addOrgMember({ orgId: props.orgId, mobile: mobile.value.trim() })
  message.success('已加入')
  mobile.value = ''
  await loadMembers()
  emit('changed')
}

const handleAssign = async (row: OrgMemberVO) => {
  await assignOrgSeat({ orgId: props.orgId, memberId: row.memberId })
  message.success('席位已分配')
  await loadMembers()
  emit('changed')
}

const handleRevoke = async (row: OrgMemberVO) => {
  await revokeOrgSeat({ orgId: props.orgId, memberId: row.memberId })
  message.success('席位已撤销')
  await loadMembers()
  emit('changed')
}

const handleToggleRole = async (row: OrgMemberVO) => {
  await updateOrgMemberRole({
    orgId: props.orgId,
    memberId: row.memberId,
    role: row.role === 2 ? 3 : 2
  })
  await loadMembers()
}

const handleTransfer = async (row: OrgMemberVO) => {
  await message.confirm(`确定把 OWNER 转交给「${row.nickname || row.memberId}」吗？`)
  await transferOrgOwner({ orgId: props.orgId, memberId: row.memberId })
  message.success('已转交')
  await loadMembers()
  emit('changed')
}

const handleRemove = async (row: OrgMemberVO) => {
  await message.delConfirm()
  await removeOrgMember({ orgId: props.orgId, memberId: row.memberId })
  message.success('已移出')
  await loadMembers()
  emit('changed')
}

onMounted(loadMembers)
</script>
