<template>
  <ContentWrap>
    <el-form :model="form" label-width="110px" class="max-w-640px">
      <el-form-item label="收件人" required>
        <el-input v-model="form.receiverName" maxlength="50" placeholder="公司收件人" />
      </el-form-item>
      <el-form-item label="联系电话" required>
        <el-input v-model="form.receiverMobile" maxlength="20" placeholder="收件电话" />
      </el-form-item>
      <el-form-item label="省市区" required>
        <el-input v-model="form.areaName" maxlength="100" placeholder="如 北京市 北京市 海淀区" />
      </el-form-item>
      <el-form-item label="详细地址" required>
        <el-input v-model="form.detailAddress" maxlength="255" placeholder="街道、门牌号" />
      </el-form-item>
      <el-form-item label="寄件说明">
        <el-input
          v-model="form.shipRemark"
          :rows="3"
          maxlength="500"
          placeholder="如：请在外箱注明维修单号，寄出后回填运单号"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
      <el-form-item>
        <el-button :loading="saving" type="primary" @click="save">保存</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Api from '@/api/trade/customerService'

defineOptions({ name: 'CustomerServiceReturnAddressForm' })
const message = useMessage()

const saving = ref(false)
const form = reactive<Api.CustomerServiceAddressVO>({
  receiverName: '',
  receiverMobile: '',
  areaName: '',
  detailAddress: '',
  shipRemark: ''
})

const load = async () => {
  const data = await Api.getCustomerServiceAddress()
  if (!data) return
  form.receiverName = data.receiverName
  form.receiverMobile = data.receiverMobile
  form.areaName = data.areaName
  form.detailAddress = data.detailAddress
  form.shipRemark = data.shipRemark ?? ''
}

const save = async () => {
  if (!form.receiverName.trim() || !form.receiverMobile.trim()
    || !form.areaName.trim() || !form.detailAddress.trim()) {
    message.warning('收件人、联系电话、省市区与详细地址均为必填')
    return
  }
  saving.value = true
  try {
    await Api.updateCustomerServiceAddress({ ...form })
    message.success('回收地址已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
