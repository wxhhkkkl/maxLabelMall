<template>
  <Dialog v-model="visible" title="导入历史模板" width="860px">
    <div v-loading="loading">
      <el-alert
        v-if="precheck && precheck.orgStatus !== 0"
        type="error"
        :closable="false"
        show-icon
        title="企业已停用，不能导入"
        class="mb-10px"
      />
      <el-alert
        v-if="precheck && !precheck.licenseActive"
        type="error"
        :closable="false"
        show-icon
        title="企业授权已到期，不能导入"
        class="mb-10px"
      />
      <el-alert
        v-if="!isMember && precheck && precheck.ownerMemberId && !precheck.ownerNickname && !precheck.ownerMobile"
        type="warning"
        :closable="false"
        show-icon
        title="企业 OWNER 账号信息不完整，请先检查企业成员"
        class="mb-10px"
      />

      <el-descriptions v-if="precheck" :column="2" border size="small" class="mb-10px">
        <el-descriptions-item v-if="isMember" label="会员">
          {{ precheck.ownerNickname || precheck.ownerMemberId }}
          <span v-if="precheck.ownerMobile" class="text-gray-400">（{{ precheck.ownerMobile }}）</span>
        </el-descriptions-item>
        <template v-else>
          <el-descriptions-item label="企业">
            {{ precheck.orgName || precheck.orgId }}
          </el-descriptions-item>
          <el-descriptions-item label="OWNER">
            {{ precheck.ownerNickname || precheck.ownerMemberId }}
            <span v-if="precheck.ownerMobile" class="text-gray-400">（{{ precheck.ownerMobile }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="本次新建文件夹">{{ precheck.folderName }}</el-descriptions-item>
          <el-descriptions-item label="持席成员数">{{ precheck.seatCount }}</el-descriptions-item>
        </template>
        <el-descriptions-item label="个人空间">
          {{ sizeText(precheck.personalUsedBytes) }} / {{ sizeText(precheck.personalQuotaBytes) }}
          <span class="text-gray-400">（{{ editionText(precheck.personalEdition) }}）</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="!isMember" label="企业空间">
          {{ sizeText(precheck.enterpriseUsedBytes) }} / {{ sizeText(precheck.enterpriseQuotaBytes) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form v-if="!running" label-width="120px" class="mb-10px">
        <el-form-item label="历史系统账号">
          <el-input v-model="account" placeholder="输入历史系统账号" clearable autocomplete="off" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="仅用于本次只读迁移，不会保存"
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="authorized">
            我已获得该历史系统账号所有人的授权，仅用于本次只读模板迁移
          </el-checkbox>
        </el-form-item>
      </el-form>

      <div v-if="task" class="mb-10px">
        <el-progress
          :percentage="percent"
          :status="progressStatus"
          :stroke-width="14"
          class="mb-8px"
        />
        <div class="mb-8px text-13px text-gray-600">
          状态：{{ statusText(task.status) }} · 阶段：{{ phaseText(task.phase) }}
          · 已处理 {{ task.processedCount }} / {{ task.totalCount }}
        </div>
        <el-descriptions :column="3" border size="small" class="mb-10px">
          <el-descriptions-item label="成功">{{ task.successCount }}</el-descriptions-item>
          <el-descriptions-item label="跳过">{{ task.skipCount }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ task.failCount }}</el-descriptions-item>
          <el-descriptions-item label="个人空间写入">{{ sizeText(task.personalBytes) }}</el-descriptions-item>
          <el-descriptions-item v-if="!isMember" label="企业空间写入">
            {{ sizeText(task.enterpriseBytes) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="!isMember" label="归档 JSON">
            {{ task.archiveJsonCount }} 条
          </el-descriptions-item>
          <el-descriptions-item label="个人空间用量">
            {{ sizeText(task.personalUsedBytes) }} / {{ sizeText(task.personalQuotaBytes) }}
            （剩余 {{ sizeText(remainingBytes(task.personalUsedBytes, task.personalQuotaBytes)) }}）
          </el-descriptions-item>
          <el-descriptions-item v-if="!isMember" label="企业空间用量">
            {{ sizeText(task.enterpriseUsedBytes) }} / {{ sizeText(task.enterpriseQuotaBytes) }}
            （剩余 {{ sizeText(remainingBytes(task.enterpriseUsedBytes, task.enterpriseQuotaBytes)) }}）
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="task.personalExceeded || task.enterpriseExceeded"
          type="warning"
          :closable="false"
          show-icon
          title="本次导入已使空间占用超过上限；导入已完成，常规新增上传将被拒绝，直到删到上限以下"
          class="mb-10px"
        />
        <el-table :data="librariesForDisplay" size="small" border class="mb-8px">
          <el-table-column label="来源库" width="150">
            <template #default="{ row }">{{ libraryText(row.library) }}</template>
          </el-table-column>
          <el-table-column label="总数" prop="total" width="90" />
          <el-table-column label="成功" prop="success" width="90" />
          <el-table-column label="跳过" prop="skip" width="90" />
          <el-table-column label="失败" prop="fail" width="90" />
        </el-table>
        <div class="mb-8px text-12px text-gray-500">
          原样导入、未清洗{{ isMember ? '。' : '；企业/组织私有 JSON 暂不可直接编辑，可在共享空间中另存为原文件。' }}
        </div>
        <div v-if="task.errorMessage" class="mb-8px text-12px text-red-500">
          {{ task.errorMessage }}
        </div>
        <div v-if="task.failCount > 0">
          <div class="mb-5px font-600">失败清单</div>
          <el-table :data="failures" size="small" border>
            <el-table-column label="名称" prop="sourceName" min-width="160" />
            <el-table-column label="来源库" width="130">
              <template #default="{ row }">{{ libraryText(row.sourceLibrary) }}</template>
            </el-table-column>
            <el-table-column label="分类" prop="sourceCategory" min-width="140" />
            <el-table-column label="原因" prop="errorMessage" min-width="180" />
          </el-table>
          <Pagination
            v-model:limit="failureQuery.pageSize"
            v-model:page="failureQuery.pageNo"
            :total="failureTotal"
            @pagination="loadFailures"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="starting" :disabled="!canStart" @click="handleStart">
        开始导入
      </el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import {
  getLabelshopItems,
  getLabelshopPrecheck,
  getLabelshopTask,
  getLatestLabelshopTask,
  getMemberLabelshopItems,
  getMemberLabelshopPrecheck,
  getMemberLabelshopTask,
  getMemberLatestLabelshopTask,
  startLabelshopImport,
  startMemberLabelshopImport,
  type LabelshopItemVO,
  type LabelshopPrecheckVO,
  type LabelshopTaskVO
} from '@/api/member/labelshopImport'

defineOptions({ name: 'LabelshopImportDialog' })

const props = defineProps<{ modelValue: boolean; orgId?: number; memberId?: number }>()
const emit = defineEmits(['update:modelValue', 'completed'])
const message = useMessage()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})
const loading = ref(false)
const starting = ref(false)
const precheck = ref<LabelshopPrecheckVO>()
const task = ref<LabelshopTaskVO>()
const account = ref('')
const password = ref('')
const authorized = ref(false)
const failures = ref<LabelshopItemVO[]>([])
const failureTotal = ref(0)
const failureQuery = reactive({ pageNo: 1, pageSize: 10 })
let pollTimer: number | undefined
let refreshing = false
let completedEmitted = false

const isMember = computed(() => props.memberId != null)
const subjectId = computed(() => (isMember.value ? props.memberId! : props.orgId!))
const librariesForDisplay = computed(() =>
  isMember.value
    ? (task.value?.libraries || []).filter((item) => item.library === 12)
    : task.value?.libraries || []
)

const running = computed(() => !!task.value && (task.value.status === 0 || task.value.status === 1))
const percent = computed(() => {
  if (!task.value || !task.value.totalCount) return 0
  return Math.min(100, Math.round((task.value.processedCount / task.value.totalCount) * 100))
})
const progressStatus = computed(() => {
  if (!task.value) return ''
  if (task.value.status === 2) return 'success'
  if (task.value.status === 3 || task.value.status === 4) return 'exception'
  return ''
})
const canStart = computed(
  () =>
    !!precheck.value &&
    precheck.value.orgStatus === 0 &&
    precheck.value.licenseActive === true &&
    !precheck.value.runningTaskId &&
    !!account.value.trim() &&
    !!password.value &&
    authorized.value &&
    !starting.value
)

const libraryText = (library: number) =>
  ({ 12: '我的模板库', 13: '组模板库', 1: '企业/组织模板' } as Record<number, string>)[library] || '未知'
const statusText = (status: number) =>
  ({ 0: '待执行', 1: '进行中', 2: '已完成', 3: '已失败', 4: '已中断' } as Record<number, string>)[status] ||
  '未知'
const phaseText = (phase?: string) =>
  ({ LOGIN: '登录中', LIST: '读取模板列表', IMPORT: '导入中', FINISH: '收尾' } as Record<string, string>)[
    phase || ''
  ] || '等待中'
const editionText = (edition?: string) =>
  ({ standard: '标准版', professional: '专业版', enterprise: '企业版' } as Record<string, string>)[
    edition || 'standard'
  ] || '标准版'
const sizeText = (bytes?: number) => {
  if (!bytes) return '0 B'
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}
const remainingBytes = (used?: number, quota?: number) => Math.max(0, (quota || 0) - (used || 0))

const stopPolling = () => {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}
const startPolling = () => {
  stopPolling()
  pollTimer = window.setInterval(() => void refreshTask(), 2500)
}
const refreshTask = async () => {
  if (!task.value || refreshing) return
  refreshing = true
  try {
    task.value = isMember.value
      ? await getMemberLabelshopTask(task.value.id)
      : await getLabelshopTask(task.value.id)
    if (!running.value) {
      stopPolling()
      if (!completedEmitted) {
        completedEmitted = true
        emit('completed')
      }
      await loadFailures()
      precheck.value = isMember.value
        ? await getMemberLabelshopPrecheck(subjectId.value)
        : await getLabelshopPrecheck(subjectId.value)
    }
  } catch (error: any) {
    message.error(error?.message || '读取导入任务失败')
  } finally {
    refreshing = false
  }
}
const loadFailures = async () => {
  if (!task.value) return
  try {
    const params = {
      taskId: task.value.id,
      status: 3,
      pageNo: failureQuery.pageNo,
      pageSize: failureQuery.pageSize
    }
    const page = isMember.value
      ? await getMemberLabelshopItems(params)
      : await getLabelshopItems(params)
    failures.value = page.list
    failureTotal.value = page.total
  } catch {
    failures.value = []
    failureTotal.value = 0
  }
}
const open = async () => {
  completedEmitted = false
  task.value = undefined
  failures.value = []
  failureTotal.value = 0
  failureQuery.pageNo = 1
  loading.value = true
  try {
    precheck.value = isMember.value
      ? await getMemberLabelshopPrecheck(subjectId.value)
      : await getLabelshopPrecheck(subjectId.value)
    const latest = isMember.value
      ? await getMemberLatestLabelshopTask(subjectId.value)
      : await getLatestLabelshopTask(subjectId.value)
    if (latest) {
      task.value = latest
      if (latest.status === 0 || latest.status === 1) startPolling()
      else await loadFailures()
    }
  } catch (error: any) {
    message.error(error?.message || '读取导入前置条件失败')
  } finally {
    loading.value = false
  }
}
const handleStart = async () => {
  if (!canStart.value) return
  starting.value = true
  try {
    const common = {
      account: account.value.trim(),
      password: password.value,
      authorized: authorized.value
    }
    const result = isMember.value
      ? await startMemberLabelshopImport({ memberId: subjectId.value, ...common })
      : await startLabelshopImport({ orgId: subjectId.value, ...common })
    password.value = ''
    completedEmitted = false
    task.value = isMember.value
      ? await getMemberLabelshopTask(result.taskId)
      : await getLabelshopTask(result.taskId)
    startPolling()
  } catch (error: any) {
    message.error(error?.message || '发起导入失败')
  } finally {
    starting.value = false
  }
}

watch(
  () => props.modelValue,
  (value) => {
    if (value) void open()
    else stopPolling()
  },
  { immediate: true }
)
onBeforeUnmount(stopPolling)
</script>
