<template>
  <Dialog v-model="dialogVisible" title="导出订单" width="30%">
    <el-form label-width="90px" :model="formData">
      <el-form-item label="快捷选择">
        <el-button
          v-for="option in EXPORT_QUICK_RANGES"
          :key="option.key"
          :type="quickKey === option.key ? 'primary' : 'default'"
          size="small"
          @click="chooseQuickRange(option.key)"
        >
          {{ option.label }}
        </el-button>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="formData.createTime"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-full"
          end-placeholder="结束时间"
          start-placeholder="开始时间"
          type="daterange"
          value-format="YYYY-MM-DD HH:mm:ss"
          @change="quickKey = ''"
        />
      </el-form-item>
      <div class="export-tip">导出该时间范围内的全部订单，不受列表当前筛选条件影响。</div>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">导 出</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as TradeOrderApi from '@/api/mall/trade/order'
import download from '@/utils/download'
import { EXPORT_QUICK_RANGES, buildExportQuickRange, type ExportQuickRangeKey } from './orderExport'

defineOptions({ name: 'OrderExportForm' })

const message = useMessage()

const dialogVisible = ref(false)
const formLoading = ref(false)
const formData = ref<{ createTime: [string, string] | [] }>({ createTime: [] })
const quickKey = ref<ExportQuickRangeKey | ''>('')

/** 打开弹窗 */
const open = () => {
  formData.value = { createTime: [] }
  quickKey.value = ''
  dialogVisible.value = true
}
defineExpose({ open })

/** 快捷范围：选完直接回填时间 */
const chooseQuickRange = (key: ExportQuickRangeKey) => {
  quickKey.value = key
  formData.value.createTime = buildExportQuickRange(key)
}

/** 提交导出 */
const submitForm = async () => {
  const [start, end] = formData.value.createTime
  if (!start || !end) {
    message.warning('请选择导出时间范围')
    return
  }
  formLoading.value = true
  try {
    const createTime: [string, string] = [start, end]
    const data = await TradeOrderApi.exportOrderExcel({ createTime })
    download.excel(data as Blob, `交易订单_${start.slice(0, 10)}_${end.slice(0, 10)}.xls`)
    message.success('导出成功')
    dialogVisible.value = false
  } finally {
    formLoading.value = false
  }
}
</script>
<style lang="scss" scoped>
.export-tip {
  padding-left: 90px;
  font-size: 12px;
  color: #909399;
}
</style>
