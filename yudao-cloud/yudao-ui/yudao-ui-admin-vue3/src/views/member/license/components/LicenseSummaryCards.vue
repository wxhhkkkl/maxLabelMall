<template>
  <el-row :gutter="16" class="summary-cards">
    <el-col :span="6">
      <!-- 数字可下钻到明细并带上筛选（FR-543 / Q163：只显示订阅会员） -->
      <el-card
        class="summary-card card-clickable"
        shadow="never"
        @click="emit('drill', { targetType: 1, rightsType: 1 })"
      >
        <div class="card-title">个人授权</div>
        <div class="metric-row">
          <div class="metric">
            <span class="metric-label">订阅会员</span>
            <span class="metric-value">{{ props.summary?.personalActiveCount ?? 0 }}</span>
          </div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card
        class="summary-card card-clickable"
        shadow="never"
        @click="emit('drill', { targetType: 2 })"
      >
        <div class="card-title">企业有效授权</div>
        <div class="metric-row">
          <div class="metric">
            <span class="metric-label">企业</span>
            <span class="metric-value">{{ props.summary?.orgActiveCount ?? 0 }}</span>
          </div>
          <div class="metric">
            <span class="metric-label">席位占用</span>
            <span class="metric-value">
              {{ props.summary?.orgSeatActiveCount ?? 0 }} /
              {{ props.summary?.orgSeatTotalCount ?? 0 }}
            </span>
          </div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="12">
      <el-card class="summary-card card-clickable" shadow="never" @click="emit('drill', {})">
        <div class="card-title">{{ props.summary?.year ?? '' }} 年授权（个人会员 + 企业席位）</div>
        <!-- Q163：新获得与延续各自拆成 个人会员 / 企业席位 / 合计；两者单位不同，合计不是客户数 -->
        <div v-for="group in yearGroups" :key="group.title" class="year-group">
          <span class="group-title">{{ group.title }}</span>
          <div class="metric-row">
            <div class="metric">
              <span class="metric-label">个人会员</span>
              <span class="metric-value">{{ group.personal }}</span>
            </div>
            <div class="metric">
              <span class="metric-label">企业席位</span>
              <span class="metric-value">{{ group.orgSeats }}</span>
            </div>
            <div class="metric">
              <span class="metric-label">合计</span>
              <span class="metric-value">{{ group.personal + group.orgSeats }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script lang="ts" setup>
import type { LicenseSummaryVO } from '@/api/member/license'

defineOptions({ name: 'LicenseSummaryCards' })

const props = defineProps<{ summary?: LicenseSummaryVO }>()
/** drill 参数：1 个人 / 2 企业 / 0 当年客户（不带类型筛选） */
const emit = defineEmits<{
  (e: 'drill', filter: { targetType?: number; rightsType?: number }): void
}>()

const yearGroups = computed(() => [
  {
    title: '新获得授权',
    personal: props.summary?.yearNewPersonalCount ?? 0,
    orgSeats: props.summary?.yearNewOrgSeatCount ?? 0
  },
  {
    title: '延续授权',
    personal: props.summary?.yearRenewPersonalCount ?? 0,
    orgSeats: props.summary?.yearRenewOrgSeatCount ?? 0
  }
])
</script>

<style lang="scss" scoped>
.summary-cards :deep(.el-col) {
  display: flex;
}
.card-clickable {
  cursor: pointer;
}
.summary-card {
  flex: 1;
}

.card-title {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.metric-row {
  display: flex;
  gap: 32px;
  margin-top: 12px;
}
.metric {
  display: flex;
  flex-direction: column;
}
.metric-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1;
}
.metric-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}
.year-group {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-top: 12px;
}
.year-group .metric-row {
  margin-top: 0;
}
.group-title {
  width: 72px;
  flex: none;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1;
}
</style>
