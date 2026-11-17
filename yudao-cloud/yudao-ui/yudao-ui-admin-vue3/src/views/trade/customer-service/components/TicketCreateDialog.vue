<template>
  <Dialog v-model="visible" title="代建投诉咨询" width="620px">
    <el-form label-width="100px">
      <el-form-item label="会员" required>
        <div class="flex items-center gap-8px">
          <span v-if="member">{{ member.nickname }} {{ member.mobile }}</span>
          <span v-else class="text-gray-400">未选择会员</span>
          <el-button size="small" @click="pickerRef?.open()">选择会员</el-button>
        </div>
      </el-form-item>
      <el-form-item label="诉求类型" required>
        <el-radio-group v-model="form.type">
          <el-radio :value="1">投诉</el-radio>
          <el-radio :value="2">咨询</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="内容" required>
        <el-input v-model="form.content" :rows="4" maxlength="1000" show-word-limit type="textarea" />
      </el-form-item>
      <el-form-item label="联系手机号" required>
        <el-input v-model="form.contactMobile" maxlength="11" placeholder="11 位手机号" />
      </el-form-item>
      <el-form-item label="图片">
        <UploadImgs v-model="form.images" :limit="5" :file-size="10" :file-type="['image/jpeg', 'image/png', 'image/webp']" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :loading="saving" type="primary" @click="submit">提交</el-button>
    </template>
    <MemberPickerDialog ref="pickerRef" @select="onMemberSelected" />
  </Dialog>
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'
import MemberPickerDialog from './MemberPickerDialog.vue'

defineOptions({ name: 'CustomerServiceTicketCreateDialog' })
const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const pickerRef = ref()
const member = ref<Api.CustomerServiceMemberVO>()
const form = reactive({
  type: 1,
  content: '',
  contactMobile: '',
  images: [] as string[]
})

const onMemberSelected = (selected: Api.CustomerServiceMemberVO) => {
  member.value = selected
  if (!form.contactMobile) form.contactMobile = selected.mobile ?? ''
}

const open = () => {
  member.value = undefined
  Object.assign(form, { type: 1, content: '', contactMobile: '', images: [] })
  visible.value = true
}

const submit = async () => {
  if (!member.value) {
    message.warning('请先选择会员')
    return
  }
  if (!form.content.trim()) {
    message.warning('请填写内容')
    return
  }
  if (!/^1\d{10}$/.test(form.contactMobile)) {
    message.warning('请填写 11 位联系手机号')
    return
  }
  saving.value = true
  try {
    await Api.createCustomerServiceTicket({
      memberId: member.value.id,
      type: form.type,
      content: form.content.trim(),
      contactMobile: form.contactMobile,
      images: form.images.length ? form.images : undefined
    })
    message.success('已代建工单')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
