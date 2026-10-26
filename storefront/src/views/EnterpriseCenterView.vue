<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  addOrgMember,
  assignOrgSeat,
  getMyOrg,
  leaveOrg,
  listOrgLicenses,
  listOrgMembers,
  removeOrgMember,
  revokeOrgSeat,
  transferOrgOwner,
  updateOrgMemberRole,
  type MemberOrg,
  type OrgLicense,
  type OrgMember,
} from '@/api/org'

const router = useRouter()
const org = ref<MemberOrg>({ orgId: null })
const members = ref<OrgMember[]>([])
const licenses = ref<OrgLicense[]>([])
const tab = ref<'members' | 'licenses'>('members')
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const mobile = ref('')

const canManage = computed(() => org.value.role === 1 || org.value.role === 2)
const isOwner = computed(() => org.value.role === 1)

function roleLabel(role: number): string {
  if (role === 1) return '所有者'
  if (role === 2) return '管理员'
  return '成员'
}

function periodLabel(months: number): string {
  if (months % 12 === 0) return `${months / 12} 年`
  return `${months} 个月`
}

async function loadOrg(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    org.value = await getMyOrg()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '企业信息加载失败'
  } finally {
    loading.value = false
  }
}

async function loadMembers(): Promise<void> {
  if (!org.value.orgId) return
  try {
    const page = await listOrgMembers({ pageNo: 1, pageSize: 50 })
    members.value = page.list
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '成员加载失败'
  }
}

async function loadLicenses(): Promise<void> {
  if (!org.value.orgId) return
  try {
    const page = await listOrgLicenses({ pageNo: 1, pageSize: 20 })
    licenses.value = page.list
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '授权批次加载失败'
  }
}

async function switchTab(next: 'members' | 'licenses'): Promise<void> {
  tab.value = next
  actionError.value = ''
  if (next === 'members') await loadMembers()
  else await loadLicenses()
}

async function refresh(): Promise<void> {
  await loadOrg()
  if (tab.value === 'members') await loadMembers()
  else await loadLicenses()
}

async function onAddMember(): Promise<void> {
  actionError.value = ''
  try {
    await addOrgMember(mobile.value.trim())
    mobile.value = ''
    await refresh()
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '加入成员失败'
  }
}

async function onAssign(member: OrgMember): Promise<void> {
  actionError.value = ''
  try {
    await assignOrgSeat(member.memberId)
    await refresh()
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '分配席位失败'
  }
}

async function onRevoke(member: OrgMember): Promise<void> {
  actionError.value = ''
  try {
    await revokeOrgSeat(member.memberId)
    await refresh()
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '撤销席位失败'
  }
}

async function onToggleRole(member: OrgMember): Promise<void> {
  actionError.value = ''
  try {
    await updateOrgMemberRole(member.memberId, member.role === 2 ? 3 : 2)
    await refresh()
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '调整角色失败'
  }
}

async function onRemove(member: OrgMember): Promise<void> {
  if (!window.confirm(`确定移出「${member.nickname ?? member.memberId}」吗？`)) return
  actionError.value = ''
  try {
    await removeOrgMember(member.memberId)
    await refresh()
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '移出成员失败'
  }
}

async function onTransfer(member: OrgMember): Promise<void> {
  if (!window.confirm('转交后你将变为管理员，确定继续？')) return
  actionError.value = ''
  try {
    await transferOrgOwner(member.memberId)
    await refresh()
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '转交失败'
  }
}

async function onLeave(): Promise<void> {
  if (!window.confirm('确定退出企业吗？席位会立即释放。')) return
  try {
    await leaveOrg()
    await loadOrg()
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '退出企业失败'
  }
}

onMounted(async () => {
  await loadOrg()
  if (org.value.orgId) await loadMembers()
})
</script>

<template>
  <main class="ent-center">
    <section class="ent-hero">
      <div class="ent-container">
        <p class="ent-eyebrow">
          企业中心
        </p>
        <h1>{{ org.orgName || '企业授权' }}</h1>
        <p v-if="org.role" class="ent-lead">
          你的角色：{{ roleLabel(org.role) }}
        </p>
      </div>
    </section>

    <section class="ent-container ent-content" aria-live="polite">
      <p v-if="loading" class="ent-state">
        正在加载企业信息…
      </p>
      <p v-else-if="error" class="ent-state ent-error">
        {{ error }}
      </p>
      <div v-else-if="!org.orgId" class="ent-state">
        <p>你还没有加入企业。</p>
        <RouterLink class="ent-link" to="/enterprise">
          了解企业授权
        </RouterLink>
      </div>

      <template v-else>
        <div class="ent-cards">
          <article class="ent-card">
            <p class="ent-label">企业授权</p>
            <p class="ent-value">
              {{ org.licenseEndDate ? `有效至 ${org.licenseEndDate}` : '暂无有效批次' }}
            </p>
          </article>
          <article class="ent-card">
            <p class="ent-label">我的席位</p>
            <p class="ent-value">
              {{ org.seatActive ? `有效至 ${org.seatEndDate}` : '未分配' }}
            </p>
          </article>
          <article class="ent-card">
            <p class="ent-label">席位容量</p>
            <p class="ent-value">
              已用 {{ org.seatUsed ?? 0 }} / 共 {{ org.seatTotal ?? 0 }}（剩余 {{ org.seatAvailable ?? 0 }}）
            </p>
          </article>
        </div>

        <p v-if="actionError" class="ent-state ent-error">
          {{ actionError }}
        </p>

        <div class="ent-tabs">
          <button type="button" :class="{ active: tab === 'members' }" @click="switchTab('members')">
            成员与席位
          </button>
          <button type="button" :class="{ active: tab === 'licenses' }" @click="switchTab('licenses')">
            授权批次
          </button>
        </div>

        <section v-if="tab === 'members'" class="ent-panel">
          <form v-if="canManage" class="ent-add" @submit.prevent="onAddMember">
            <input v-model="mobile" type="tel" placeholder="输入已注册会员的手机号" />
            <button type="submit">
              加入成员
            </button>
          </form>

          <p v-if="members.length === 0" class="ent-state">
            暂无成员
          </p>
          <table v-else class="ent-table">
            <thead>
              <tr>
                <th>成员</th>
                <th>角色</th>
                <th>席位</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in members" :key="member.memberId">
                <td>
                  <span>{{ member.nickname || member.mobileMasked || member.memberId }}</span>
                  <small v-if="member.mobileMasked">{{ member.mobileMasked }}</small>
                </td>
                <td>{{ roleLabel(member.role) }}</td>
                <td>
                  <span v-if="member.seatActive" class="ent-ok">有效至 {{ member.seatEndDate }}</span>
                  <span v-else class="ent-muted">未分配</span>
                </td>
                <td class="ent-actions">
                  <template v-if="canManage">
                    <button v-if="!member.seatActive" type="button" @click="onAssign(member)">
                      分配席位
                    </button>
                    <button v-else type="button" @click="onRevoke(member)">
                      撤销席位
                    </button>
                    <button v-if="isOwner && member.role !== 1" type="button" @click="onToggleRole(member)">
                      {{ member.role === 2 ? '取消管理员' : '设为管理员' }}
                    </button>
                    <button v-if="isOwner && member.role !== 1" type="button" @click="onTransfer(member)">
                      转交 OWNER
                    </button>
                    <button v-if="member.role !== 1" type="button" class="danger" @click="onRemove(member)">
                      移出
                    </button>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>

          <button v-if="!isOwner" type="button" class="ent-leave" @click="onLeave">
            退出企业
          </button>
        </section>

        <section v-else class="ent-panel">
          <p v-if="licenses.length === 0" class="ent-state">
            暂无授权批次
          </p>
          <table v-else class="ent-table">
            <thead>
              <tr>
                <th>周期</th>
                <th>席位</th>
                <th>有效期</th>
                <th>来源订单</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="license in licenses" :key="license.id">
                <td>{{ periodLabel(license.periodMonths) }}</td>
                <td>{{ license.seatsTotal }}</td>
                <td>
                  {{ license.startDate }} ~ {{ license.endDate }}
                  <small v-if="license.active">{{ license.remainingDays }} 天</small>
                  <small v-else>已过期</small>
                </td>
                <td>
                  {{ license.sourceOrderNo }}
                  <small v-if="license.refunded" class="ent-warn">来源已退款</small>
                </td>
                <td class="ent-actions">
                  <button type="button" @click="router.push(`/enterprise/plan/${license.skuId}`)">
                    续费
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </template>
    </section>
  </main>
</template>

<style scoped>
.ent-center {
  color: #16233f;
  background: #f5f8ff;
  min-height: 60vh;
}
.ent-container {
  width: min(1100px, calc(100% - 48px));
  margin: 0 auto;
}
.ent-hero {
  padding: 64px 0 40px;
  background: #eaf1ff;
}
.ent-eyebrow {
  color: #2e7cd6;
  font-size: 14px;
  letter-spacing: 0.08em;
}
.ent-hero h1 {
  margin: 12px 0;
  font-size: 40px;
  line-height: 1.2;
}
.ent-lead {
  color: #5b6c8f;
  font-size: 16px;
}
.ent-content {
  padding: 40px 0 72px;
}
.ent-state {
  padding: 32px;
  text-align: center;
  color: #5b6c8f;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
}
.ent-error {
  color: #ff5c22;
}
.ent-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}
.ent-card {
  padding: 24px;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
}
.ent-label {
  margin: 0 0 8px;
  color: #8a97b5;
  font-size: 13px;
}
.ent-value {
  margin: 0;
  color: #0c2148;
  font-size: 18px;
  font-weight: 600;
}
.ent-tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.ent-tabs button {
  padding: 8px 16px;
  border: 1px solid #e3e9f4;
  border-radius: 8px;
  background: #ffffff;
  color: #5b6c8f;
  cursor: pointer;
}
.ent-tabs button.active {
  border-color: #2e7cd6;
  color: #2e7cd6;
}
.ent-panel {
  background: #ffffff;
  border: 1px solid #e3e9f4;
  border-radius: 12px;
  padding: 24px;
}
.ent-add {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.ent-add input {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid #e3e9f4;
  border-radius: 8px;
}
.ent-add button,
.ent-actions button,
.ent-leave {
  padding: 8px 14px;
  border: 0;
  border-radius: 8px;
  background: #2e7cd6;
  color: #ffffff;
  cursor: pointer;
}
.ent-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.ent-actions button.danger {
  background: #ff5c22;
}
.ent-leave {
  margin-top: 16px;
  background: #ffffff;
  border: 1px solid #e3e9f4;
  color: #5b6c8f;
}
.ent-table {
  width: 100%;
  border-collapse: collapse;
}
.ent-table th,
.ent-table td {
  padding: 12px 8px;
  border-bottom: 1px solid #e3e9f4;
  text-align: left;
  font-size: 14px;
}
.ent-table th {
  color: #8a97b5;
  font-weight: 500;
}
.ent-table small {
  display: block;
  color: #8a97b5;
  font-size: 12px;
}
.ent-ok {
  color: #2e7cd6;
}
.ent-muted {
  color: #8a97b5;
}
.ent-warn {
  color: #ff5c22;
}
.ent-link {
  display: inline-block;
  margin-top: 12px;
  color: #2e7cd6;
}
@media (max-width: 1100px) {
  .ent-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .ent-hero h1 { font-size: 30px; }
  .ent-cards { grid-template-columns: 1fr; }
  .ent-table { display: block; overflow-x: auto; }
}
@media (max-width: 480px) {
  .ent-container { width: calc(100% - 32px); }
  .ent-hero { padding: 44px 0 28px; }
  .ent-add { flex-direction: column; }
}
</style>
