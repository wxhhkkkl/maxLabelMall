<!-- 商品发布 - 库存价格 - 属性列表 -->
<!--
  属性值的添加方式（2026-11-18 重做）：
  原来用 `el-select allow-create` 让运营「边选边造值」，Enter / change / blur 都会触发提交，
  Chrome 下回车被重复触发（多出一条带空格的空值），Edge 下偶尔根本没提交上。
  现在改成**明确的输入框 + 「添加」按钮**：回车（输入法上屏时不触发）或点按钮都能加，
  空值一律忽略，添加成功后输入框保留并清空，方便连续录入；点「取消」收起。
-->
<template>
  <el-col v-for="(item, index) in attributeList" :key="index">
    <div>
      <el-text class="mx-1">属性名：</el-text>
      <el-tag :closable="!isDetail" class="mx-1" type="success" @close="handleCloseProperty(index)">
        {{ item.name }}
      </el-tag>
    </div>
    <div class="flex flex-wrap items-center gap-1">
      <el-text class="mx-1">属性值：</el-text>
      <el-tag
        v-for="(value, valueIndex) in item.values"
        :key="value.id"
        :closable="!isDetail"
        class="mx-1"
        @close="handleCloseValue(index, valueIndex)"
      >
        {{ value.name }}
      </el-tag>
      <template v-if="inputVisible(index)">
        <el-input
          :id="`input${index}`"
          :ref="(el) => setInputRef(index, el)"
          v-model="inputValue"
          :disabled="saving"
          class="!w-40"
          placeholder="输入属性值，回车或点「添加」"
          size="small"
          @keydown.enter="handleEnter($event, index, item.id)"
        />
        <el-button
          :loading="saving"
          class="ml-1"
          size="small"
          type="primary"
          @click="handleInputConfirm(index, item.id)"
        >
          添加
        </el-button>
        <el-button class="ml-1" size="small" @click="closeInput">取消</el-button>
      </template>
      <el-button
        v-show="!inputVisible(index)"
        class="button-new-tag ml-1"
        size="small"
        @click="showInput(index)"
      >
        + 添加
      </el-button>
    </div>
    <el-divider class="my-10px" />
  </el-col>
</template>

<script lang="ts" setup>
import * as PropertyApi from '@/api/mall/product/property'
import { PropertyAndValues } from '@/views/mall/product/spu/components'
import { propTypes } from '@/utils/propTypes'
import type { PropType } from 'vue'

defineOptions({ name: 'ProductAttributes' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗
const inputValue = ref('') // 输入框值
const attributeIndex = ref<number | null>(null) // 获取焦点时记录当前属性项的index
const saving = ref(false) // 正在创建属性值（防连点重复提交）
// 输入框显隐控制
const inputVisible = computed(() => (index: number) => {
  if (attributeIndex.value === null) return false
  if (attributeIndex.value === index) return true
})
const inputRefs = ref<Record<number, any>>({}) // 每行属性值的输入框 Ref
const setInputRef = (index: number, el: any) => {
  if (el) inputRefs.value[index] = el
}
const attributeList = ref<PropertyAndValues[]>([]) // 商品属性列表
const attributeOptions = ref([] as PropertyApi.PropertyValueVO[]) // 商品属性名称下拉框
const props = defineProps({
  propertyList: {
    type: Array as PropType<PropertyAndValues[]>,
    default: () => []
  },
  isDetail: propTypes.bool.def(false) // 是否作为详情组件
})

watch(
  () => props.propertyList,
  (data) => {
    if (!data) return
    attributeList.value = data
  },
  {
    deep: true,
    immediate: true
  }
)

/** 删除属性值*/
const handleCloseValue = (index: number, valueIndex: number) => {
  attributeList.value[index].values?.splice(valueIndex, 1)
}

/** 删除属性*/
const handleCloseProperty = (index: number) => {
  attributeList.value?.splice(index, 1)
  emit('success', attributeList.value)
}

/** 显示输入框并获取焦点 */
const showInput = async (index: number) => {
  attributeIndex.value = index
  inputValue.value = ''
  await nextTick()
  inputRefs.value[index]?.focus()
  // 获取属性下拉选项（用于命中「已存在」的属性值，直接复用而不是重复创建）
  await getAttributeOptions(attributeList.value[index]!.id)
}

/** 收起输入框 */
const closeInput = () => {
  attributeIndex.value = null
  inputValue.value = ''
}

/** 回车提交：输入法正在上屏时忽略，避免把候选词当成一次「添加」 */
const handleEnter = (event: Event, index: number, propertyId: number) => {
  if ((event as KeyboardEvent).isComposing) {
    return
  }
  event.preventDefault()
  handleInputConfirm(index, propertyId)
}

/** 添加属性值（回车或点「添加」） */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const handleInputConfirm = async (index: number, propertyId: number) => {
  // 1. 空值 / 纯空格直接忽略 —— 旧实现会在回车时多造一条空属性值
  const name = inputValue.value.trim()
  if (!name || saving.value) {
    return
  }
  const values = attributeList.value[index]!.values || []
  // 2. 重复添加校验
  if (values.find((item) => item.name === name)) {
    message.warning('已存在相同属性值，请重试')
    return
  }

  // 3.1 情况一：属性值已存在（商品属性管理里配过），则直接使用
  const existValue = attributeOptions.value.find((item) => item.name === name)
  if (existValue) {
    values.push({ id: existValue.id!, name: existValue.name })
    attributeList.value[index]!.values = values
    emit('success', attributeList.value)
    resetInputAfterAdd(index)
    return
  }

  // 3.2 情况二：新属性值，则进行保存
  saving.value = true
  try {
    const id = await PropertyApi.createPropertyValue({ propertyId, name })
    values.push({ id, name })
    attributeList.value[index]!.values = values
    message.success(t('common.createSuccess'))
    emit('success', attributeList.value)
    resetInputAfterAdd(index)
  } catch {
    message.error('添加失败，请重试')
  } finally {
    saving.value = false
  }
}

/** 添加成功后清空输入并保留焦点，方便连续录入 */
const resetInputAfterAdd = async (index: number) => {
  inputValue.value = ''
  await nextTick()
  inputRefs.value[index]?.focus()
}

/** 获取商品属性下拉选项 */
const getAttributeOptions = async (propertyId: number) => {
  attributeOptions.value = await PropertyApi.getPropertyValueSimpleList(propertyId)
}
</script>
