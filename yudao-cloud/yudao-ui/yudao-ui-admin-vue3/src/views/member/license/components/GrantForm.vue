<template>
  <Dialog v-model="dialogVisible" title="赠送授权" width="560px">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="赠送对象">
        <span>
          {{ target?.targetName || '—' }}
          <el-tag class="ml-8px" size="small" type="info">
            {{ target?.targetType === 2 ? '企业' : '个人' }}
          </el-tag>
        </span>
      </el-form-item>
      <el-form-item label="当前到期">
        <span>{{ formatDate(target?.endTime) || '无有效授权' }}</span>
      </el-form-item>
      <el-form-item label="赠送内容" prop="contentType">
        <el-radio-group v-model="formData.contentType">
          <el-radio :value="1">时长</el-radio>
          <el-radio v-if="isOrg" :value="2">席位</el-radio>
        </el-radio-group>
        <div v-if="isPersonal" class="ml-2 text-12px text-gray-500">个人会员只能赠送时长</div>
      </el-form-item>
      <!-- 时长档位：超出剩余额度的档位置灰（Q154 / FR-555） -->
      <el-form-item v-if="needsMonths" label="赠送时长" prop="months">
        <el-radio-group v-model="formData.months">
          <el-radio v-for="tier in TIERS" :key="tier" :value="tier" :disabled="tier > maxMonths">
            {{ tier }} 个月
          </el-radio>
        </el-radio-group>
        <div class="ml-2 text-12px text-gray-500">最多还能赠送 {{ maxMonths }} 个月</div>
      </el-form-item>
      <el-form-item v-if="formData.contentType === 2" label="赠送席位" prop="seats">
        <el-input-number v-model="formData.seats" :min="1" :max="10000" />
        <div v-if="seatNeedsMonths" class="ml-2 text-12px text-gray-500">
          该企业暂无有效授权，赠送席位需同时指定时长
        </div>
      </el-form-item>
      <el-form-item label="赠送理由" prop="reason">
        <el-input
          v-model="formData.reason"
          :rows="2"
          maxlength="255"
          placeholder="必填：为什么给这位客户赠送授权"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="saving" type="primary" @click="submitForm">确认赠送</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as LicenseApi from '@/api/member/license'
import type { FormInstance, FormRules } from 'element-plus'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'LicenseGrantForm' })
const emit = defineEmits(['success'])
const message = useMessage()

const TIERS = [1, 3, 6, 12]

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const target = ref<LicenseApi.LicenseGiftTargetVO>()
const formData = reactive<LicenseApi.LicenseGrantReqVO>({
  targetType: 1,
  targetId: 0,
  contentType: 1,
  months: 1,
  seats: 1,
  reason: ''
})

const isOrg = computed(() => formData.targetType === 2)
const isPersonal = computed(() => formData.targetType === 1)
/** 后端下发的「最多还能送」——前端只做置灰，真正的门在服务端 */
const maxMonths = computed(() => target.value?.maxGiftMonths ?? 0)
/** 企业当前没有有效授权时，送席位必须同时给时长，否则席位无处生效（Q157 / FR-548） */
const seatNeedsMonths = computed(() => isOrg.value && !target.value?.active)
/** 何时需要选择时长：送时长，或「企业无授权 + 送席位」 */
const needsMonths = computed(() => formData.contentType === 1 || seatNeedsMonths.value)

const rules: FormRules = {
  months: [
    {
      validator: (_r, value, callback) => {
        if (!needsMonths.value) return callback()
        if (!value) return callback(new Error('请选择赠送时长'))
        if (value > maxMonths.value) {
          return callback(new Error(`最多还能赠送 ${maxMonths.value} 个月`))
        }
        callback()
      },
      trigger: 'change'
    }
  ],
  seats: [
    {
      validator: (_r, value, callback) => {
        if (formData.contentType !== 2) return callback()
        if (!value || value < 1) return callback(new Error('赠送席位数必须大于 0'))
        callback()
      },
      trigger: 'change'
    }
  ],
  reason: [{ required: true, message: '请填写赠送理由', trigger: 'blur' }]
}

const open = (row: LicenseApi.LicenseGiftTargetVO) => {
  target.value = row
  formData.targetType = row.targetType
  formData.targetId = row.targetId
  formData.contentType = 1
  formData.months = TIERS.find((tier) => tier <= (row.maxGiftMonths ?? 0))
  formData.seats = 1
  formData.reason = ''
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

const submitForm = async () => {
  // 连点守卫：校验是异步的，必须**在入口**就挡住第二次点击（T088）
  if (saving.value) {
    return
  }
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await LicenseApi.grantLicense({ ...formData })
    message.success('赠送成功，已立即生效')
    dialogVisible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

defineExpose({ open, maxMonths })
</script>
