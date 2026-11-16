<template>
  <Dialog v-model="dialogVisible" title="修改发票信息" width="600px">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
      <el-form-item label="订单号">
        <span>{{ formData.orderNo || '—' }}</span>
      </el-form-item>
      <el-form-item label="票据类型" prop="type">
        <el-radio-group v-model="formData.type">
          <el-radio :value="INVOICE_TYPE.NORMAL">电子普通发票</el-radio>
          <!-- 个人抬头只能开普票 / 收据（增值税专票按税法只能开给企业） -->
          <el-radio :value="INVOICE_TYPE.SPECIAL" :disabled="formData.titleKind === TITLE_KIND.PERSONAL">
            电子增值税专用发票
          </el-radio>
          <el-radio :value="INVOICE_TYPE.RECEIPT">收据</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="抬头类型" prop="titleKind">
        <el-radio-group v-model="formData.titleKind" @change="handleTitleKindChange">
          <el-radio :value="TITLE_KIND.COMPANY">企业</el-radio>
          <el-radio :value="TITLE_KIND.PERSONAL">个人</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="抬头名称" prop="titleName">
        <el-input v-model="formData.titleName" maxlength="255" placeholder="公司名或个人名" />
      </el-form-item>
      <!-- 税号：企业开普票 / 专票必填，个人与收据选填（与后端 InvoiceValidator 一致） -->
      <el-form-item label="纳税人识别号" prop="taxNo">
        <el-input v-model="formData.taxNo" maxlength="64" placeholder="15 / 18 / 20 位数字或大写字母" />
      </el-form-item>
      <el-form-item label="注册地址">
        <el-input v-model="formData.titleAddress" maxlength="255" placeholder="选填" />
      </el-form-item>
      <el-form-item label="注册电话">
        <el-input v-model="formData.titlePhone" maxlength="64" placeholder="选填" />
      </el-form-item>
      <el-form-item label="开户行">
        <el-input v-model="formData.bankName" maxlength="255" placeholder="选填，银行名称 / 支行" />
      </el-form-item>
      <el-form-item label="银行账户">
        <el-input v-model="formData.bankAccount" maxlength="64" placeholder="选填，账号" />
      </el-form-item>
      <el-form-item label="开户行行号">
        <el-input v-model="formData.bankCode" maxlength="32" placeholder="选填，联行号 / CNAPS" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="formData.email" maxlength="128" placeholder="选填，接收发票 / 收据的邮箱" />
      </el-form-item>
      <el-form-item>
        <span class="tip">只有「待开票」的申请可以修改；已开票的发票文件已经生成，请先重新上传。</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="saving" type="primary" @click="submitForm">提交</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as InvoiceApi from '@/api/mall/trade/invoice/index'
import type { FormInstance, FormRules } from 'element-plus'

defineOptions({ name: 'InvoiceEditForm' })
const emit = defineEmits(['success'])
const message = useMessage()

/** 票种 / 抬头类型：取值必须与后端 `InvoiceTypeEnum` / `InvoiceTitleKindEnum` 一致 */
const INVOICE_TYPE = { NORMAL: 1, SPECIAL: 2, RECEIPT: 3 } as const
const TITLE_KIND = { COMPANY: 1, PERSONAL: 2 } as const

/** 与后端 `InvoiceValidator` 逐字一致 */
const TAX_NO_PATTERN = /^([0-9A-Z]{15}|[0-9A-Z]{18}|[0-9A-Z]{20})$/
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const formData = ref<InvoiceApi.TradeOrderInvoiceVO>({})

/**
 * 税号：企业开普票 / 专票必填，**个人与收据选填**（个人可以用身份证号当税号，不是禁止）
 *
 * 与后端同一套矩阵 —— 前端这层只是不让运营白提交一次，真正的门在 `InvoiceValidator`。
 */
const validateTaxNo = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const taxNo = (value ?? '').trim()
  if (!taxNo) {
    if (formData.value.titleKind === TITLE_KIND.COMPANY && formData.value.type !== INVOICE_TYPE.RECEIPT) {
      callback(new Error('请填写纳税人识别号'))
      return
    }
    callback()
    return
  }
  if (!TAX_NO_PATTERN.test(taxNo)) {
    callback(new Error('纳税人识别号格式不正确（应为 15 / 18 / 20 位数字或大写字母）'))
    return
  }
  callback()
}

const rules: FormRules = {
  type: [{ required: true, message: '请选择票据类型', trigger: 'change' }],
  titleKind: [{ required: true, message: '请选择抬头类型', trigger: 'change' }],
  titleName: [
    { required: true, message: '请填写抬头名称', trigger: 'blur' },
    { max: 255, message: '抬头名称最多 255 个字符', trigger: 'blur' }
  ],
  taxNo: [{ validator: validateTaxNo, trigger: 'blur' }],
  email: [{ pattern: EMAIL_PATTERN, message: '邮箱格式不正确', trigger: 'blur' }]
}

/** 选「个人」时把已选的专票回落到普票（后端会直接拒掉这个组合） */
const handleTitleKindChange = () => {
  if (formData.value.titleKind === TITLE_KIND.PERSONAL && formData.value.type === INVOICE_TYPE.SPECIAL) {
    formData.value.type = INVOICE_TYPE.NORMAL
  }
}

/** 打开弹窗：从订单详情已带出的 `invoice` 回填，不额外查一次接口 */
const open = async (row: InvoiceApi.TradeOrderInvoiceVO) => {
  formData.value = {
    id: row.id,
    orderNo: row.orderNo,
    type: row.type,
    titleKind: row.titleKind,
    titleName: row.titleName,
    taxNo: row.taxNo,
    titleAddress: row.titleAddress,
    titlePhone: row.titlePhone,
    bankName: row.bankName,
    bankAccount: row.bankAccount,
    bankCode: row.bankCode,
    email: row.email
  }
  dialogVisible.value = true
  // 上一次的红字别带过来
  await nextTick()
  formRef.value?.clearValidate()
}
defineExpose({ open })

const submitForm = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await InvoiceApi.updateInvoice(formData.value)
    message.success('发票信息已修改')
    dialogVisible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
</style>
