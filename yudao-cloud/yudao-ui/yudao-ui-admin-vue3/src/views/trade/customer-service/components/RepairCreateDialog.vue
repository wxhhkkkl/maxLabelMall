<template>
  <Dialog v-model="visible" title="代建维修申请" width="680px">
    <el-form label-width="110px">
      <el-form-item label="会员" required>
        <div class="flex items-center gap-8px">
          <span v-if="member">{{ member.nickname }} {{ member.mobile }}</span>
          <span v-else class="text-gray-400">未选择会员</span>
          <el-button size="small" @click="pickerRef?.open()">选择会员</el-button>
        </div>
      </el-form-item>
      <el-form-item label="设备型号" required>
        <el-input v-model="form.deviceModel" maxlength="100" placeholder="如 Zebra ZT411" />
      </el-form-item>
      <el-form-item label="故障描述" required>
        <el-input v-model="form.faultDesc" :rows="3" maxlength="1000" show-word-limit type="textarea" />
      </el-form-item>
      <el-form-item label="联系手机号" required>
        <el-input v-model="form.contactMobile" maxlength="11" placeholder="11 位手机号" />
      </el-form-item>
      <el-form-item label="返修收件信息" required>
        <div class="grid grid-cols-2 gap-8px w-full">
          <el-input v-model="form.receiverName" maxlength="50" placeholder="收件人" />
          <el-input v-model="form.receiverMobile" maxlength="20" placeholder="联系电话" />
          <el-input v-model="form.receiverArea" maxlength="100" placeholder="省市区" />
          <el-input v-model="form.receiverDetail" maxlength="255" placeholder="详细地址" />
        </div>
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

defineOptions({ name: 'CustomerServiceRepairCreateDialog' })
const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const pickerRef = ref()
const member = ref<Api.CustomerServiceMemberVO>()
const form = reactive({
  deviceModel: '',
  faultDesc: '',
  contactMobile: '',
  receiverName: '',
  receiverMobile: '',
  receiverArea: '',
  receiverDetail: '',
  images: [] as string[]
})

const onMemberSelected = (selected: Api.CustomerServiceMemberVO) => {
  member.value = selected
  if (!form.contactMobile) form.contactMobile = selected.mobile ?? ''
}

const open = () => {
  member.value = undefined
  Object.assign(form, {
    deviceModel: '', faultDesc: '', contactMobile: '',
    receiverName: '', receiverMobile: '', receiverArea: '', receiverDetail: '', images: []
  })
  visible.value = true
}

const submit = async () => {
  if (!member.value) {
    message.warning('请先选择会员')
    return
  }
  if (!form.deviceModel.trim() || !form.faultDesc.trim()) {
    message.warning('请填写设备型号与故障描述')
    return
  }
  if (!/^1\d{10}$/.test(form.contactMobile)) {
    message.warning('请填写 11 位联系手机号')
    return
  }
  if (!form.receiverName.trim() || !form.receiverMobile.trim()
    || !form.receiverArea.trim() || !form.receiverDetail.trim()) {
    message.warning('请填写返修收件信息')
    return
  }
  saving.value = true
  try {
    await Api.createCustomerServiceRepair({
      memberId: member.value.id,
      deviceModel: form.deviceModel.trim(),
      faultDesc: form.faultDesc.trim(),
      contactMobile: form.contactMobile,
      receiverName: form.receiverName.trim(),
      receiverMobile: form.receiverMobile.trim(),
      receiverArea: form.receiverArea.trim(),
      receiverDetail: form.receiverDetail.trim(),
      images: form.images.length ? form.images : undefined
    })
    message.success('已代建维修申请')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
