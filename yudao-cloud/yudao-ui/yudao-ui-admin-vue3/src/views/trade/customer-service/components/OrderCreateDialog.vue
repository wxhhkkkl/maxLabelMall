<template>
  <Dialog v-model="visible" title="生成维修订单" width="560px">
    <el-form label-width="110px">
      <el-form-item label="维修申请">
        <span>{{ row?.deviceModel }}（{{ row?.memberNickname || '—' }}）</span>
      </el-form-item>
      <el-form-item label="维修费（元）" required>
        <el-input id="orderRepairFee" v-model.number="repairFeeYuan" type="number" placeholder="0.00" />
      </el-form-item>
      <el-form-item label="寄回运费（元）" required>
        <el-input id="orderReturnFreight" v-model.number="returnFreightYuan" type="number" placeholder="0.00" />
      </el-form-item>
      <el-form-item label="应付合计">
        <b class="text-18px">{{ payableYuan.toFixed(2) }} 元</b>
        <span class="ml-8px text-12px text-gray-500">客户寄出运费自理</span>
      </el-form-item>
      <el-form-item label="费用说明" required>
        <el-input
          id="orderFeeRemark"
          v-model="feeRemark"
          :rows="3"
          maxlength="500"
          placeholder="如：更换打印头；含寄回运费"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :loading="saving" type="primary" @click="submit">确认开单</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'

defineOptions({ name: 'CustomerServiceOrderCreateDialog' })
const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const row = ref<Api.CustomerServiceRepairVO>()
const repairFeeYuan = ref<number>()
const returnFreightYuan = ref<number>()
const feeRemark = ref('')

const payableYuan = computed(() => (repairFeeYuan.value ?? 0) + (returnFreightYuan.value ?? 0))

const open = (target: Api.CustomerServiceRepairVO) => {
  row.value = target
  repairFeeYuan.value = undefined
  returnFreightYuan.value = undefined
  feeRemark.value = ''
  visible.value = true
}

const submit = async () => {
  if (!row.value) return
  if (repairFeeYuan.value === undefined || repairFeeYuan.value < 0
    || returnFreightYuan.value === undefined || returnFreightYuan.value < 0) {
    message.warning('维修费与寄回运费必须 ≥ 0')
    return
  }
  if (!feeRemark.value.trim()) {
    message.warning('请填写费用说明')
    return
  }
  saving.value = true
  try {
    await Api.createCustomerServiceRepairOrder({
      repairRequestId: row.value.id,
      repairFee: Math.round(repairFeeYuan.value * 100),
      returnFreight: Math.round(returnFreightYuan.value * 100),
      feeRemark: feeRemark.value.trim()
    })
    message.success('维修订单已生成')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
