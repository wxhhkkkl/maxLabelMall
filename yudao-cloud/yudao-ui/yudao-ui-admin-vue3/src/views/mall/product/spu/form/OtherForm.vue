<!-- 商品发布 - 其它设置 -->
<template>
  <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px" :disabled="isDetail">
    <el-form-item label="商品排序" prop="sort">
      <el-input-number
        v-model="formData.sort"
        :min="0"
        placeholder="请输入商品排序"
        class="w-80!"
      />
    </el-form-item>
    <el-form-item label="赠送积分" prop="giveIntegral">
      <el-input-number
        v-model="formData.giveIntegral"
        :min="0"
        placeholder="请输入赠送积分"
        class="w-80!"
      />
    </el-form-item>
    <el-form-item label="虚拟销量" prop="virtualSalesCount">
      <el-input-number
        v-model="formData.virtualSalesCount"
        :min="0"
        placeholder="请输入虚拟销量"
        class="w-80!"
      />
    </el-form-item>
    <el-form-item label="企业专享" prop="enterpriseOnly">
      <el-switch v-model="formData.enterpriseOnly" />
      <span class="ml-10px text-12px text-gray-500">
        勾选后不在公开商城展示，仅企业中心可见
      </span>
    </el-form-item>
    <el-form-item label="浏览等级" prop="browseLevelType">
      <div>
        <el-radio-group v-model="formData.browseLevelType">
          <el-radio :value="0">公开</el-radio>
          <el-radio :value="1">全部登录会员</el-radio>
          <el-radio :value="2">指定等级会员</el-radio>
        </el-radio-group>
        <div class="mt-6px text-12px text-gray-500">
          公开：含未登录访客；全部登录会员：任意已登录会员；指定等级：仅勾选等级会员可见
        </div>
      </div>
    </el-form-item>
    <el-form-item
      v-if="formData.browseLevelType === 2"
      label="指定等级"
      prop="browseLevelIds"
    >
      <el-select
        v-model="formData.browseLevelIds"
        multiple
        placeholder="请选择会员等级"
        class="w-80!"
      >
        <el-option
          v-for="level in levelOptions"
          :key="level.id"
          :label="level.name"
          :value="level.id"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="关联驱动下载" prop="driverSoftwareId">
      <el-select
        v-model="formData.driverSoftwareId"
        clearable
        placeholder="不关联"
        class="w-80!"
      >
        <el-option
          v-for="item in softwareOptions"
          :key="item.id"
          :label="softwareOptionLabel(item)"
          :value="item.id"
        />
      </el-select>
      <span class="ml-10px text-12px text-gray-500">
        关联后商品详情页显示「下载驱动 / 下载软件」，直接下载该软件的当前发布版本
      </span>
    </el-form-item>
  </el-form>
</template>
<script lang="ts" setup>
import type { Spu } from '@/api/mall/product/spu'
import * as LevelApi from '@/api/member/level'
import * as SoftwareApi from '@/api/mall/software'
import { PropType } from 'vue'
import { propTypes } from '@/utils/propTypes'
import { copyValueToTarget } from '@/utils'

defineOptions({ name: 'ProductOtherForm' })

const message = useMessage() // 消息弹窗

const props = defineProps({
  propFormData: {
    type: Object as PropType<Spu>,
    default: () => {}
  },
  isDetail: propTypes.bool.def(false) // 是否作为详情组件
})

const formRef = ref() // 表单Ref
// 表单数据
const formData = ref<Spu>({
  sort: 0, // 商品排序
  giveIntegral: 0, // 赠送积分
  virtualSalesCount: 0, // 虚拟销量
  enterpriseOnly: false, // 是否企业专享
  browseLevelType: 0, // 浏览等级：默认公开
  browseLevelIds: [], // 指定等级会员的等级编号
  driverSoftwareId: undefined // 关联软件/驱动（2026-11-18）
})
// 表单规则
const rules = reactive({
  sort: [required],
  giveIntegral: [required],
  virtualSalesCount: [required],
  browseLevelType: [required],
  browseLevelIds: [
    {
      validator: (_rule: any, value: number[] | undefined, callback: (error?: Error) => void) => {
        if (formData.value.browseLevelType === 2 && (!value || value.length === 0)) {
          callback(new Error('请选择至少一个会员等级'))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ]
})

// 会员等级选项（仅启用等级）
const loadedLevels = ref<LevelApi.LevelVO[]>([])
// 回显时把「已选中但已停用」的等级补成占位项，避免下拉里只剩裸 ID
const levelOptions = computed<LevelApi.LevelVO[]>(() => {
  const options = [...loadedLevels.value]
  const selectedIds = formData.value.browseLevelIds ?? []
  selectedIds.forEach((id) => {
    if (!options.some((level) => level.id === id)) {
      options.push({ id, name: `等级 #${id}（已停用）` } as LevelApi.LevelVO)
    }
  })
  return options
})
const getLevelList = async () => {
  loadedLevels.value = await LevelApi.getSimpleLevelList()
}

// 关联驱动/软件选项（2026-11-18）：全部未删除的软件/驱动，含停用项
const loadedSoftware = ref<SoftwareApi.SoftwareSimpleVO[]>([])
// 回显时把「已关联但已删除」的软件补成占位项，避免下拉只剩裸 ID
const softwareOptions = computed<SoftwareApi.SoftwareSimpleVO[]>(() => {
  const options = [...loadedSoftware.value]
  const selectedId = formData.value.driverSoftwareId
  if (selectedId && !options.some((item) => item.id === selectedId)) {
    options.push({ id: selectedId, title: `软件 #${selectedId}（已删除）`, type: 2, status: 1 })
  }
  return options
})
const softwareOptionLabel = (item: SoftwareApi.SoftwareSimpleVO) =>
  `${item.title}（${item.type === 1 ? '桌面端软件' : '打印驱动'}${item.status === 1 ? '，已停用' : ''}）`
const getSoftwareList = async () => {
  loadedSoftware.value = await SoftwareApi.getSoftwareSimpleList()
}
onMounted(() => {
  getLevelList()
  getSoftwareList()
})

/** 将传进来的值赋值给 formData */
watch(
  () => props.propFormData,
  (data) => {
    if (!data) {
      return
    }
    copyValueToTarget(formData.value, data)
    // 旧数据 / 接口缺省时归一化，避免单选框与多选框拿到 null
    if (formData.value.browseLevelType === null || formData.value.browseLevelType === undefined) {
      formData.value.browseLevelType = 0
    }
    if (!Array.isArray(formData.value.browseLevelIds)) {
      formData.value.browseLevelIds = []
    }
  },
  {
    immediate: true
  }
)

/** 表单校验 */
const emit = defineEmits(['update:activeName'])
const validate = async () => {
  if (!formRef) return
  try {
    await unref(formRef)?.validate()
    // 校验通过更新数据
    Object.assign(props.propFormData, formData.value)
  } catch (e) {
    message.error('【其它设置】不完善，请填写相关信息')
    emit('update:activeName', 'other')
    throw e // 目的截断之后的校验
  }
}
defineExpose({ validate })
</script>
