<!-- 商品发布 - 筛选标签（特性 003 / US14） -->
<template>
  <el-row>
    <el-col :span="24">
      <el-alert v-if="!loading && !optionList.length" type="info" :closable="false" class="mb-10px">
        该分类（含它的父分类）还没有配置任何筛选维度，因此没有可挂的标签。
        在「商城系统 → 商品分类」里点某个分类的「筛选维度」即可配置。
      </el-alert>

      <div v-for="option in optionList" :key="option.dimensionId" class="filter-tag-group">
        <div class="filter-tag-group__head">
          <span class="filter-tag-group__name">{{ option.dimensionName }}</span>
          <!-- 标出来源：这个维度挂在哪个分类上。不标的话运营不知道改它会影响谁（FR-110） -->
          <span class="filter-tag-group__source" v-if="option.fromCategoryName">
            （来自「{{ option.fromCategoryName }}」）
          </span>
        </div>
        <el-checkbox-group v-model="selectedIds" :disabled="isDetail" class="filter-tag-group__values">
          <el-checkbox
            v-for="value in option.values"
            :key="value.valueId"
            :value="value.valueId"
            :label="value.name"
          />
        </el-checkbox-group>
      </div>
    </el-col>
  </el-row>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'

import * as ProductSpuApi from '@/api/mall/product/spu'
import { useMessage } from '@/hooks/web/useMessage'
import { diffRemovedFilterValues } from '@/utils/filterTag'

/**
 * 筛选标签子表单（特性 003 / US14 / T279）。
 *
 * ## 商品侧是**多选**
 * 一个商品可以同时挂「宽度=110」和「宽度=100」—— 那表达的正是"这件货有这两个规格"。
 * 与前台的**单选**（顾客每行只能选一个，Q9=B）是两件事，别混。
 *
 * ## 什么时候会"丢标签"，以及为什么要说出来
 * 可挂的值限定在「商品分类自身 + 全部祖先分类」上（FR-095）。运营一改分类，
 * 原先挂的标签就可能掉出这个范围。**服务端会静默清理掉它们**（FR-097）—— 不报错。
 *
 * 所以这里做两件事：
 *   1. 分类一变，重新拉该分类可挂的选项，并把已选中里**不再适用**的直接从勾选里去掉
 *      （不这样做，界面上会留着一批"勾着但保存后不存在"的项，运营会以为自己选上了）；
 *   2. 把被去掉的**个数**明确说出来 —— 否则这是**没有报错的数据变化**，
 *      运营会以为标签还在，直到顾客反馈"筛选里搜不到这件商品"。
 *
 * 这里不需要额外的网络往返：本表单本来就拿到了"这个分类可挂哪些值"这份清单，
 * 拿它当第二个集合做差集即可（`diffRemovedFilterValues`）。
 */
defineOptions({ name: 'FilterTagForm' })

const props = defineProps<{
  propFormData: any
  isDetail?: boolean
}>()

const message = useMessage() // 消息弹窗

const loading = ref(false)
const optionList = ref<ProductSpuApi.FilterValueOptionVO[]>([])

/** 勾选的值 id —— 直接读写 formData，与其它子表单的用法一致 */
const selectedIds = computed({
  get: () => props.propFormData?.filterValueIds ?? [],
  set: (value: number[]) => {
    props.propFormData.filterValueIds = value
  }
})

/** 当前分类可挂的全部值 id */
const allowedValueIds = computed(() =>
  optionList.value.flatMap((option) => option.values.map((value) => value.valueId))
)

async function loadOptions(previousCategoryId?: number) {
  const categoryId = props.propFormData?.categoryId
  if (!categoryId) {
    optionList.value = []
    return
  }
  loading.value = true
  try {
    optionList.value = ((await ProductSpuApi.getSpuFilterValueOptions({
      spuId: props.propFormData?.id,
      categoryId
    })) ?? []) as unknown as ProductSpuApi.FilterValueOptionVO[]
    // 分类变了才做剔除 —— 首次加载时，库里存的标签本来就是合法的，不该误报
    if (previousCategoryId !== undefined && previousCategoryId !== categoryId) {
      dropOutOfScopeSelections()
    }
  } catch {
    // 拉不到就当作"没有可挂的标签"，不把整个商品编辑页搞崩
    optionList.value = []
  } finally {
    loading.value = false
  }
}

function dropOutOfScopeSelections() {
  const before = selectedIds.value
  const removed = diffRemovedFilterValues(before, allowedValueIds.value)
  if (!removed.length) {
    return
  }
  selectedIds.value = before.filter((id) => allowedValueIds.value.includes(id))
  message.warning(
    `已移除 ${removed.length} 个不再适用的筛选标签 —— 它们属于原来的分类，` +
      `在新分类（及其父分类）下不存在。保存后即生效。`
  )
}

watch(
  () => props.propFormData?.categoryId,
  (categoryId, previousCategoryId) => loadOptions(previousCategoryId as number | undefined),
  { immediate: true }
)
</script>

<style lang="scss" scoped>
.filter-tag-group {
  margin-bottom: 12px;

  &__head {
    margin-bottom: 4px;
  }

  &__name {
    font-weight: 500;
  }

  &__source {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__values {
    display: flex;
    flex-wrap: wrap;
  }
}
</style>
