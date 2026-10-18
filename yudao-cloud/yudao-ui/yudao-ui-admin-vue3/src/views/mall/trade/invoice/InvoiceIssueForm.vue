<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
    <el-form :model="formData" label-width="90px">
      <el-form-item label="订单号">
        <span>{{ formData.orderNo }}</span>
      </el-form-item>
      <el-form-item label="抬头">
        <span>{{ formData.titleName }}</span>
      </el-form-item>
      <!-- 抬头类型（Q38）：开给个人时税号本来就是空的，标注出来省得运营去猜 -->
      <el-form-item label="抬头类型">
        <span>{{ formData.titleKindName || '—' }}</span>
      </el-form-item>
      <el-form-item label="开票金额">
        <span>{{ fenToYuan(formData.amount) }} 元</span>
      </el-form-item>
      <el-form-item label="发票文件" prop="invoiceFileUrl">
        <!--
          ⚠️ `disabled` 由**权限**决定：没拿到 `trade:order-invoice:issue` 的人连传都传不了。
          前端这层只是体验，真正的门在接口的 @PreAuthorize（Q29）。
        -->
        <UploadFile
          v-model="formData.invoiceFileUrl"
          :disabled="!canIssuePermi"
          :file-type="ALLOWED_FILE_TYPES"
          :limit="1"
          :is-show-tip="false"
          :name-formatter="invoiceFileName"
        />
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
import { INVOICE_STATUS, invoiceFileName } from '@/utils/invoice'
import { checkPermi } from '@/utils/permission'
import { fenToYuan } from '@/utils'

defineOptions({ name: 'InvoiceIssueForm' })
const emit = defineEmits(['success'])
// ⚠️ 这里**不能写成 `const { message } = useMessage()`**：`useMessage()` 返回的就是
// 一层直接的方法（`success` / `warning` / ...），解构 `message` 会拿到 undefined，
// 提交时报 `Cannot read properties of undefined (reading 'warning')`。
// （003 的 FilterDimensionDrawer 踩过同一个坑，单元测试当场抓到 —— 这次也是。）
const message = useMessage()

/** 只收 PDF 与图片 —— 与后端 `issueInvoice` 的白名单一致（服务端是权威，这里只是不让人白传一遍） */
const ALLOWED_FILE_TYPES = ['pdf', 'jpg', 'jpeg', 'png']

const dialogVisible = ref(false)
const saving = ref(false)
const formData = ref<InvoiceApi.TradeOrderInvoiceVO>({})

/** 权限：能不能开具（与按钮的 `v-hasPermi` 同一个权限点） */
const canIssuePermi = computed(() => checkPermi(['trade:order-invoice:issue']))

const dialogTitle = computed(() =>
  formData.value.status === INVOICE_STATUS.ISSUED ? '重新上传发票' : '开具发票'
)

/** 打开弹窗（列表页点「开具发票 / 重新上传」时调用） */
const open = (row: InvoiceApi.TradeOrderInvoiceVO) => {
  // 每次打开都从行数据重新开始：**不保留上一次选的文件**，
  // 否则"传错了又打开一次"会看到上次的 URL，以为已经换好了
  formData.value = { ...row, invoiceFileUrl: undefined }
  dialogVisible.value = true
}
defineExpose({ open })

const submitForm = async () => {
  if (!formData.value.invoiceFileUrl) {
    message.warning('请先上传发票文件')
    return
  }
  saving.value = true
  try {
    await InvoiceApi.issueInvoice(formData.value.id!, formData.value.invoiceFileUrl)
    message.success(formData.value.status === INVOICE_STATUS.ISSUED ? '发票已重新上传' : '已标记为已开票')
    dialogVisible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
</script>
