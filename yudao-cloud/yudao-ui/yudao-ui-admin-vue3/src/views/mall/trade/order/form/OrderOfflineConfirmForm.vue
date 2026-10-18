<template>
  <Dialog v-model="dialogVisible" title="确认对公转账收款" width="30%">
    <!--
      ⚠️ 这是**资金动作**：确认即生效、没有二级复核（008 / Q42）。
      所以先把"要确认的是哪一单、多少钱"摆出来让财务核对，再填流水号。
    -->
    <el-alert type="warning" :closable="false" class="mb-10px">
      <template #title>
        确认后订单将进入「待发货」，且**不可撤销**。请先核对银行到账金额与订单应付金额一致。
      </template>
    </el-alert>

    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="订单号">
        <span>{{ order?.no }}</span>
      </el-form-item>
      <el-form-item label="应付金额">
        <span>¥{{ fenToYuan(order?.payPrice ?? 0) }}</span>
      </el-form-item>
      <el-form-item label="银行流水号" prop="bankSerialNo">
        <el-input v-model="formData.bankSerialNo" maxlength="64" placeholder="照银行流水填写，用于事后对账" />
      </el-form-item>
      <el-form-item label="打款方">
        <el-input v-model="formData.payerName" maxlength="128" placeholder="选填（母公司/财务公司代付时填）" />
      </el-form-item>
      <el-form-item label="到账时间">
        <el-date-picker
          v-model="formData.paidTime"
          type="datetime"
          value-format="x"
          placeholder="选填，照银行流水填"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="formData.remark" maxlength="255" placeholder="选填，例如「分两笔打的，这是第一笔」" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as TradeOrderApi from '@/api/mall/trade/order'
import { fenToYuan } from '@/utils' // 分 → 元（订单金额一律以分为单位存储）

defineOptions({ name: 'OrderOfflineConfirmForm' })

const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中
const order = ref<TradeOrderApi.OrderVO | null>(null) // 正在确认的那笔订单（只读展示用）
const formRef = ref() // 表单 Ref
const formData = ref<TradeOrderApi.OfflineConfirmVO>({
  id: undefined,
  bankSerialNo: '',
  payerName: '',
  paidTime: null,
  remark: ''
})

const rules = {
  // ⚠️ 必填（FR-204）：没有流水号，这笔确认就对不上银行账；
  //    服务端也会再拦一次（前端这层只是不让财务白填一遍）
  bankSerialNo: [{ required: true, message: '请填写银行流水号', trigger: 'blur' }]
}

/** 打开弹窗 */
const open = async (row: TradeOrderApi.OrderVO) => {
  resetForm()
  order.value = row
  formData.value.id = row.id!
  dialogVisible.value = true
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  await formRef.value.validate()
  // 提交请求
  formLoading.value = true
  try {
    await TradeOrderApi.confirmOfflinePaid(unref(formData))
    message.success('已确认收款，订单进入「待发货」')
    dialogVisible.value = false
    emit('success', true)
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    bankSerialNo: '',
    payerName: '',
    paidTime: null,
    remark: ''
  }
  formRef.value?.resetFields()
}
</script>
