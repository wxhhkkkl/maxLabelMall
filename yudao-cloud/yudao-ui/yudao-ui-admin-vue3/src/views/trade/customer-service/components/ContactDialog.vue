<template>
  <Dialog v-model="visible" title="记录电话联系" width="520px">
    <el-form label-width="90px">
      <el-form-item label="维修申请">
        <span>{{ row?.deviceModel }}（{{ row?.memberNickname || '—' }}）</span>
      </el-form-item>
      <el-form-item label="联系结果" required>
        <el-input
          v-model="contactResult"
          :rows="4"
          maxlength="500"
          placeholder="如：已电话联系，确认可寄修，报价 100 元 + 寄回运费 20 元"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :loading="saving" type="primary" @click="submit">保存</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'

defineOptions({ name: 'CustomerServiceContactDialog' })
const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const row = ref<Api.CustomerServiceRepairVO>()
const contactResult = ref('')

const open = (target: Api.CustomerServiceRepairVO) => {
  row.value = target
  contactResult.value = ''
  visible.value = true
}

const submit = async () => {
  if (!row.value || !contactResult.value.trim()) {
    message.warning('请填写联系结果')
    return
  }
  saving.value = true
  try {
    await Api.recordCustomerServiceRepairContact({
      id: row.value.id,
      contactResult: contactResult.value.trim()
    })
    message.success('联系结果已保存')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
