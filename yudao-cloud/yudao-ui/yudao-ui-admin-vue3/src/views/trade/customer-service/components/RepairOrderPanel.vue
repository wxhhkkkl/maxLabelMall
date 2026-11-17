<template>
  <div class="ro-wrap">
    <div v-if="repair.repairOrderId" class="ro-card">
      <div class="ro-head">
        <b>维修订单</b>
        <el-tag size="small" :type="repair.payStatus === 20 || repair.payStatus === 30 ? 'success' : 'warning'">
          {{ repair.payStatusName }}
        </el-tag>
      </div>
      <div class="ro-row"><span>维修费</span><b>¥{{ ((repair.repairFee ?? 0) / 100).toFixed(2) }}</b></div>
      <div class="ro-row"><span>寄回运费</span><b>¥{{ ((repair.returnFreight ?? 0) / 100).toFixed(2) }}</b></div>
      <div class="ro-row ro-total"><span>应付合计</span><b>¥{{ ((repair.payableAmount ?? 0) / 100).toFixed(2) }}</b></div>
      <p v-if="repair.feeRemark" class="ro-remark">{{ repair.feeRemark }}</p>
      <el-button v-if="repair.payStatus === 10" class="mt-8px" type="primary" @click="confirmVisible = true">
        确认收款
      </el-button>
    </div>

    <div class="ro-actions">
      <el-button
        v-if="repair.status === 30 && repair.refundStatus !== 20"
        type="primary"
        @click="advance(40, '维修中')"
      >标记维修中</el-button>
      <el-button
        v-if="repair.status === 40 && repair.refundStatus !== 20"
        type="primary"
        @click="completeRepair"
      >完成维修</el-button>
      <el-button v-if="canCancel" type="danger" @click="cancel">取消申请</el-button>
    </div>

    <Dialog v-model="confirmVisible" title="确认对公转账收款" width="480px">
      <el-form label-width="100px">
        <el-form-item label="银行流水号">
          <el-input v-model="confirmForm.bankSerialNo" placeholder="选填" />
        </el-form-item>
        <el-form-item label="付款人">
          <el-input v-model="confirmForm.payerName" placeholder="选填" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="confirmForm.remark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submitConfirm">确认收款</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'
import { ElMessageBox } from 'element-plus'

defineOptions({ name: 'CustomerServiceRepairOrderPanel' })
const props = defineProps<{ repair: Api.CustomerServiceRepairVO }>()
const emit = defineEmits(['refresh'])
const message = useMessage()

const confirmVisible = ref(false)
const saving = ref(false)
const confirmForm = reactive({ bankSerialNo: '', payerName: '', remark: '' })

const canCancel = computed(() => [0, 10, 20, 30, 40].includes(props.repair.status))

const advance = async (status: number, label: string) => {
  await ElMessageBox.confirm(`确认将维修申请标记为「${label}」？`, '提示', { type: 'warning' })
  await Api.updateCustomerServiceRepairStatus({ id: props.repair.id, status })
  message.success(`已标记为${label}`)
  emit('refresh')
}

/** 完成维修必须走维修订单接口，记录完成人与时间 */
const completeRepair = async () => {
  await ElMessageBox.confirm('确认完成维修？完成后会记录当前操作人。', '完成维修', { type: 'warning' })
  if (props.repair.repairOrderId) {
    await Api.completeCustomerServiceRepairOrder(props.repair.repairOrderId)
  } else {
    await Api.updateCustomerServiceRepairStatus({ id: props.repair.id, status: 50 })
  }
  message.success('已标记完成维修')
  emit('refresh')
}

const cancel = async () => {
  const { value } = await ElMessageBox.prompt('请填写取消原因', '取消维修申请', {
    inputValidator: (v) => (!!v && v.length <= 255) || '请填写取消原因（不超过 255 字）'
  })
  await Api.cancelCustomerServiceRepair({ id: props.repair.id, reason: value })
  message.success('已取消')
  emit('refresh')
}

const submitConfirm = async () => {
  saving.value = true
  try {
    await Api.confirmCustomerServiceRepairOffline({ id: props.repair.repairOrderId!, ...confirmForm })
    message.success('已确认收款')
    confirmVisible.value = false
    emit('refresh')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ro-wrap { margin-top: 16px; }
.ro-card { padding: 14px 16px; border: 1px solid #e3e9f4; border-radius: 10px; background: #f9fbff; }
.ro-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.ro-row { display: flex; justify-content: space-between; font-size: 13px; color: #5b6c8f; padding: 3px 0; }
.ro-row b { color: #16233f; }
.ro-total { border-top: 1px solid #e3e9f4; margin-top: 6px; padding-top: 8px; }
.ro-total b { color: #ff5c22; font-size: 16px; }
.ro-remark { margin: 8px 0 0; font-size: 12px; color: #8a97b5; }
.ro-actions { display: flex; gap: 8px; margin-top: 14px; }
</style>
