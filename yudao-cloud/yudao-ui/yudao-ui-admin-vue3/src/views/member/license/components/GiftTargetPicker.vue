<template>
  <Dialog v-model="dialogVisible" title="赠送授权" width="820px">
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="客户类型">
        <el-select
          v-model="queryParams.targetType"
          class="!w-120px"
          clearable
          placeholder="全部"
          @change="handleQuery"
        >
          <el-option label="个人" :value="1" />
          <el-option label="企业" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input
          v-model="queryParams.keyword"
          class="!w-240px"
          clearable
          placeholder="昵称 / 手机号 / 企业名"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" class="mt-16px" row-key="rowKey">
      <el-table-column label="客户" min-width="220">
        <template #default="{ row }">
          <span>{{ row.targetName || '—' }}</span>
          <el-tag class="ml-8px" size="small" type="info">
            {{ row.targetType === 2 ? '企业' : '个人' }}
          </el-tag>
          <el-tag v-if="row.status === 1" class="ml-4px" size="small" type="danger">已停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="手机号" width="130">
        <template #default="{ row }">{{ row.targetMobile || '—' }}</template>
      </el-table-column>
      <el-table-column label="当前到期" width="130">
        <template #default="{ row }">{{ formatDate(row.endTime) || '无有效授权' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.active" type="success">有效</el-tag>
          <el-tag v-else-if="row.endTime" type="info">已过期</el-tag>
          <el-tag v-else type="info">无授权</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleSelect(row)">赠送</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </Dialog>
</template>

<script lang="ts" setup>
import * as LicenseApi from '@/api/member/license'
import { formatDate } from '@/utils/formatTime'

/**
 * 赠送对象选择（Q162）
 *
 * 赠送是独立入口：列出**全部**个人与企业，不限是否已有授权 ——
 * 之前只能从「客户授权明细」里点赠送，没授权的新客户根本选不到。
 */
defineOptions({ name: 'LicenseGiftTargetPicker' })
const emit = defineEmits<{ (e: 'select', row: LicenseApi.LicenseGiftTargetVO): void }>()

const dialogVisible = ref(false)
const loading = ref(false)
const list = ref<(LicenseApi.LicenseGiftTargetVO & { rowKey: string })[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  targetType: undefined as number | undefined,
  keyword: ''
})

const getList = async () => {
  loading.value = true
  try {
    const data = await LicenseApi.getLicenseGiftTargetPage(queryParams)
    list.value = data.list.map((row) => ({ ...row, rowKey: `${row.targetType}-${row.targetId}` }))
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const handleSelect = (row: LicenseApi.LicenseGiftTargetVO) => {
  dialogVisible.value = false
  emit('select', row)
}

const open = () => {
  queryParams.pageNo = 1
  dialogVisible.value = true
  getList()
}

defineExpose({ open })
</script>
