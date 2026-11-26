<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
    <el-form-item label="票据类型" prop="type">
      <el-radio-group v-model="form.type">
        <el-radio :value="INVOICE_TYPE.NORMAL">电子普通发票</el-radio>
        <!-- 个人抬头只能开普票 / 收据（增值税专票按税法只能开给企业） -->
        <el-radio :value="INVOICE_TYPE.SPECIAL" :disabled="form.titleKind === TITLE_KIND.PERSONAL">
          电子增值税专用发票
        </el-radio>
        <el-radio :value="INVOICE_TYPE.RECEIPT">收据</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="抬头类型" prop="titleKind">
      <el-radio-group v-model="form.titleKind">
        <el-radio :value="TITLE_KIND.COMPANY">企业</el-radio>
        <el-radio :value="TITLE_KIND.PERSONAL" :disabled="disablePersonal">个人</el-radio>
      </el-radio-group>
      <span v-if="disablePersonal" class="tip">企业授权订单必须使用企业抬头</span>
    </el-form-item>
    <el-form-item label="抬头名称" prop="titleName">
      <el-input v-model="form.titleName" maxlength="255" placeholder="公司名或个人名" />
    </el-form-item>
    <!-- 税号：企业开普票 / 专票必填，个人与收据选填（与后端 InvoiceValidator 一致） -->
    <el-form-item label="纳税人识别号" prop="taxNo">
      <el-input v-model="form.taxNo" maxlength="64" placeholder="15 / 18 / 20 位数字或大写字母" />
    </el-form-item>
    <el-form-item label="注册地址">
      <el-input v-model="form.titleAddress" maxlength="255" placeholder="选填" />
    </el-form-item>
    <el-form-item label="注册电话">
      <el-input v-model="form.titlePhone" maxlength="64" placeholder="选填" />
    </el-form-item>
    <el-form-item label="开户行">
      <el-input v-model="form.bankName" maxlength="255" placeholder="选填，银行名称 / 支行" />
    </el-form-item>
    <el-form-item label="银行账户">
      <el-input v-model="form.bankAccount" maxlength="64" placeholder="选填，账号" />
    </el-form-item>
    <el-form-item label="开户行行号">
      <el-input v-model="form.bankCode" maxlength="32" placeholder="选填，联行号 / CNAPS" />
    </el-form-item>
    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" maxlength="128" placeholder="选填，接收发票 / 收据的邮箱" />
    </el-form-item>
  </el-form>
</template>
<script lang="ts" setup>
import type { InvoiceApplyFields } from '@/api/mall/trade/invoice'
import type { FormInstance, FormRules } from 'element-plus'
import { watch } from 'vue'

defineOptions({ name: 'InvoiceFieldsForm' })

/**
 * 发票字段（023，从 `InvoiceEditForm` 抽取）
 *
 * 三处共用：C 端同款的字段矩阵（后台修改开票信息 / 代客下单带票 / 后台补申请）。
 * ⚠️ 真正的门是后端 `InvoiceValidator`；这里只是提前提示，规则必须与它逐字一致。
 */
const props = defineProps<{
  form: InvoiceApplyFields
  /** 企业授权订单：禁用「个人」抬头（FR-296） */
  disablePersonal?: boolean
}>()

/** 票种 / 抬头类型：取值必须与后端 `InvoiceTypeEnum` / `InvoiceTitleKindEnum` 一致 */
const INVOICE_TYPE = { NORMAL: 1, SPECIAL: 2, RECEIPT: 3 } as const
const TITLE_KIND = { COMPANY: 1, PERSONAL: 2 } as const

/** 与后端 `InvoiceValidator` 逐字一致 */
const TAX_NO_PATTERN = /^([0-9A-Z]{15}|[0-9A-Z]{18}|[0-9A-Z]{20})$/
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

const formRef = ref<FormInstance>()

/** 税号：企业开普票 / 专票必填，**个人与收据选填**（个人可以用身份证号当税号，不是禁止） */
const validateTaxNo = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const taxNo = (value ?? '').trim()
  if (!taxNo) {
    if (props.form.titleKind === TITLE_KIND.COMPANY && props.form.type !== INVOICE_TYPE.RECEIPT) {
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

// 企业授权订单：强制企业抬头（外部改动 / 回填也要纠正）
watch(
  () => [props.disablePersonal, props.form.titleKind] as const,
  ([disabled, titleKind]) => {
    if (disabled && titleKind === TITLE_KIND.PERSONAL) {
      props.form.titleKind = TITLE_KIND.COMPANY
    }
  },
  { immediate: true }
)

// 选「个人」时把已选的专票回落到普票（后端会直接拒掉这个组合）
watch(
  () => [props.form.titleKind, props.form.type] as const,
  ([titleKind, type]) => {
    if (titleKind === TITLE_KIND.PERSONAL && type === INVOICE_TYPE.SPECIAL) {
      props.form.type = INVOICE_TYPE.NORMAL
    }
  },
  { immediate: true }
)

/** 校验；失败时抛出 Element Plus 的字段错误对象 */
const validate = async () => {
  await formRef.value?.validate()
}

const clearValidate = () => formRef.value?.clearValidate()

defineExpose({ validate, clearValidate })
</script>

<style lang="scss" scoped>
.tip {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
