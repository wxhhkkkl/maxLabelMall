<script setup lang="ts">
import { ref, watch } from 'vue'

import {
  createDimension,
  deleteDimension,
  getDimensionDeleteImpact,
  getDimensionList,
  updateDimension,
  type FilterDimensionListVO,
  type FilterDimensionVO
} from '@/api/mall/product/filterDimension'
import {
  createValue,
  deleteValue,
  getValueDeleteImpact,
  getValueList,
  updateValue,
  type FilterValueVO
} from '@/api/mall/product/filterValue'
import { useMessage } from '@/hooks/web/useMessage'

/**
 * 筛选维度配置抽屉（特性 003 / US13 / T268）。
 *
 * 分**两段**，这是本组件唯一需要理解的东西：
 *   · 上段「本分类自有」—— 可增删改（改的是这个分类身上的配置）
 *   · 下段「继承自父分类」—— **只读**，逐条标明来源
 *
 * 为什么必须分两段：维度是沿分类树**向下继承**的（决策 Q10=B）。「宽度」配在「碳带」上，
 * 蜡基 / 混合基 / 全树脂三个子分类页就都会出现。如果这里把它们混成一个可编辑列表，
 * 运营在子分类页"删掉"一条，实际删的是父分类的配置 —— 另外两个兄弟分类会**静默**跟着变。
 * 所以继承段只读、且必须把来源写出来（FR-110）。
 *
 * ⚠️ 重名检测在服务端做，且查的是**整条祖先链**（FR-091）—— 前端不做这个判断，
 * 它拿不到祖先链（宪法原则 III：业务规则由服务端裁决）。
 */
const props = defineProps<{
  modelValue: boolean
  categoryId?: number
  categoryName?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const message = useMessage() // 消息弹窗

const loading = ref(false)
const ownDimensions = ref<FilterDimensionVO[]>([])
const inheritedDimensions = ref<FilterDimensionVO[]>([])
/** dimensionId → 该维度下的可选值 */
const valuesMap = ref<Record<number, FilterValueVO[]>>({})

async function load() {
  if (!props.categoryId) {
    return
  }
  loading.value = true
  try {
    const data = (await getDimensionList(props.categoryId)) as unknown as FilterDimensionListVO
    ownDimensions.value = data?.own ?? []
    inheritedDimensions.value = data?.inherited ?? []
    // 两段都要取值：继承段的值同样是只读展示，不显示的话运营看不到前台到底会渲染出什么
    await Promise.all([...ownDimensions.value, ...inheritedDimensions.value].map((d) => loadValues(d)))
  } catch {
    // 失败就让列表空着，不要把整个抽屉搞崩（沿用本项目"局部降级"的一贯做法）
    ownDimensions.value = []
    inheritedDimensions.value = []
  } finally {
    loading.value = false
  }
}

async function loadValues(dimension: FilterDimensionVO) {
  if (!dimension.id) {
    return
  }
  valuesMap.value[dimension.id] = (await getValueList(dimension.id)) as unknown as FilterValueVO[]
}

watch(
  () => [props.modelValue, props.categoryId],
  ([visible]) => {
    if (visible) {
      load()
    }
  },
  { immediate: true }
)

function close() {
  emit('update:modelValue', false)
}

// ==================== 维度 ====================

async function onCreateDimension() {
  const { value } = await ElMessageBox.prompt('维度名称（如：宽度）', '新增筛选维度', {
    inputPattern: /\S/,
    inputErrorMessage: '名称不能为空'
  })
  await createDimension({
    categoryId: props.categoryId!,
    name: value,
    sort: ownDimensions.value.length + 1
  })
  message.success('已新增')
  await load()
}

async function onRenameDimension(dimension: FilterDimensionVO) {
  const { value } = await ElMessageBox.prompt('维度名称', '改名', {
    inputValue: dimension.name,
    inputPattern: /\S/,
    inputErrorMessage: '名称不能为空'
  })
  await updateDimension({ id: dimension.id, name: value, sort: dimension.sort })
  message.success('已保存')
  await load()
}

/** 与相邻项交换 `sort` —— 顺序就是前台展现顺序（FR-112），所以这里必须让运营能调 */
async function onMoveDimension(index: number, offset: number) {
  const list = ownDimensions.value
  const target = index + offset
  if (target < 0 || target >= list.length) {
    return
  }
  const current = list[index]
  const other = list[target]
  await updateDimension({ id: current.id, name: current.name, sort: target + 1 })
  await updateDimension({ id: other.id, name: other.name, sort: index + 1 })
  await load()
}

async function onDeleteDimension(dimension: FilterDimensionVO) {
  const impact = await getDimensionDeleteImpact(dimension.id!)
  await ElMessageBox.confirm(
    `删除「${dimension.name}」会同时删掉它的 ${impact.valueCount} 个可选值，` +
      `并解除 ${impact.spuCount} 个商品上的相关标签。此操作不可撤销。`,
    '确认删除',
    { type: 'warning' }
  )
  await deleteDimension(dimension.id!)
  message.success('已删除')
  await load()
}

// ==================== 可选值 ====================

async function onCreateValue(dimension: FilterDimensionVO) {
  const { value } = await ElMessageBox.prompt('可选值名称（如：110）', '新增可选值', {
    inputPattern: /\S/,
    inputErrorMessage: '名称不能为空'
  })
  const values = valuesMap.value[dimension.id!] ?? []
  await createValue({ dimensionId: dimension.id!, name: value, sort: values.length + 1 })
  message.success('已新增')
  await loadValues(dimension)
}

async function onDeleteValue(dimension: FilterDimensionVO, value: FilterValueVO) {
  const impact = await getValueDeleteImpact(value.id!)
  await ElMessageBox.confirm(
    `删除「${value.name}」会解除 ${impact.spuCount} 个商品上的这个标签。此操作不可撤销。`,
    '确认删除',
    { type: 'warning' }
  )
  await deleteValue(value.id!)
  message.success('已删除')
  await loadValues(dimension)
}

async function onMoveValue(dimension: FilterDimensionVO, index: number, offset: number) {
  const list = valuesMap.value[dimension.id!] ?? []
  const target = index + offset
  if (target < 0 || target >= list.length) {
    return
  }
  await updateValue({ id: list[index].id, name: list[index].name, dimensionId: dimension.id, sort: target + 1 })
  await updateValue({ id: list[target].id, name: list[target].name, dimensionId: dimension.id, sort: index + 1 })
  await loadValues(dimension)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="`筛选维度 —— ${categoryName ?? ''}`"
    size="640px"
    @close="close"
  >
    <div v-loading="loading" class="filter-dimension-drawer">
      <!-- 上段：本分类自有，可增删改 -->
      <section class="filter-section filter-section--own">
        <div class="filter-section__head">
          <span class="filter-section__title">本分类自有</span>
          <el-button size="small" type="primary" class="filter-dimension-create" @click="onCreateDimension">
            新增维度
          </el-button>
        </div>

        <el-empty v-if="!ownDimensions.length" description="还没有配置任何维度" :image-size="60" />

        <div
          v-for="(dimension, index) in ownDimensions"
          :key="dimension.id"
          class="filter-dimension-item"
        >
          <div class="filter-dimension-item__head">
            <span class="filter-dimension-item__name">{{ dimension.name }}</span>
            <span class="filter-dimension-item__actions">
              <el-button link size="small" class="filter-dimension-rename" @click="onRenameDimension(dimension)">
                改名
              </el-button>
              <el-button link size="small" :disabled="index === 0" @click="onMoveDimension(index, -1)">
                上移
              </el-button>
              <el-button
                link
                size="small"
                :disabled="index === ownDimensions.length - 1"
                @click="onMoveDimension(index, 1)"
              >
                下移
              </el-button>
              <el-button link size="small" class="filter-dimension-delete" @click="onDeleteDimension(dimension)">
                删除
              </el-button>
            </span>
          </div>
          <div class="filter-dimension-item__values">
            <el-tag
              v-for="(value, valueIndex) in valuesMap[dimension.id!] ?? []"
              :key="value.id"
              class="filter-value-tag"
              closable
              @close="onDeleteValue(dimension, value)"
              @click="onMoveValue(dimension, valueIndex, 1)"
            >
              {{ value.name }}
            </el-tag>
            <el-button link size="small" class="filter-value-create" @click="onCreateValue(dimension)">
              + 添加可选值
            </el-button>
          </div>
        </div>
      </section>

      <!-- 下段：继承自父分类，只读 —— 这里的每一项都不属于本分类 -->
      <section class="filter-section filter-section--inherited">
        <div class="filter-section__head">
          <span class="filter-section__title">继承自父分类</span>
          <span class="filter-section__hint">这些维度在祖先分类上配置，本页只读</span>
        </div>

        <div class="filter-inherited-empty" v-if="!inheritedDimensions.length">没有继承来的维度</div>

        <div
          v-for="dimension in inheritedDimensions"
          :key="dimension.id"
          class="filter-dimension-item filter-dimension-item--inherited"
        >
          <div class="filter-dimension-item__head">
            <span class="filter-dimension-item__name">{{ dimension.name }}</span>
            <span class="filter-dimension-item__source">继承自「{{ dimension.inheritedFromCategoryName }}」</span>
          </div>
          <div class="filter-dimension-item__values">
            <el-tag
              v-for="value in valuesMap[dimension.id!] ?? []"
              :key="value.id"
              class="filter-value-tag"
            >
              {{ value.name }}
            </el-tag>
          </div>
        </div>
      </section>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.filter-dimension-drawer {
  .filter-section {
    &--inherited {
      margin-top: 24px;
      border-top: 1px solid var(--el-border-color-lighter);
      padding-top: 16px;
    }
  }

  .filter-section__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .filter-section__title {
    font-weight: 600;
  }

  .filter-section__hint,
  .filter-dimension-item__source {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  .filter-dimension-item {
    padding: 10px 12px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    margin-bottom: 10px;

    &--inherited {
      background: var(--el-fill-color-lighter);
    }
  }

  .filter-dimension-item__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .filter-dimension-item__name {
    font-weight: 500;
  }

  .filter-dimension-item__values {
    margin-top: 8px;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }
}
</style>
