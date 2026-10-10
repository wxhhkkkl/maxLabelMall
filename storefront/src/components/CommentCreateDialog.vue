<script setup lang="ts">
import { reactive, ref, watch } from 'vue'

import { createOrderItemComment } from '@/api/tradeComment'
import MlField from '@/components/base/MlField.vue'
import MlModal from '@/components/base/MlModal.vue'
import MlRate from '@/components/base/MlRate.vue'
import MultiImageUploader from '@/components/MultiImageUploader.vue'
import { validateComment } from '@/utils/comment'

/**
 * 写评价弹层（FR-074~079）。仿 `ProfileEditDialog` 的范式：`MlModal` + 打开时重置 + 提交后 emit。
 *
 * 三处不能省：
 *
 * 1. **提交前必须校验** —— 后端对评分范围与内容长度**一个都不校验**，
 *    这里是唯一的拦截点（contracts §3.2）。不拦就是"用户点了提交、收到一条数据库报错"。
 * 2. **失败要把后端原话透出来**（FR-077）—— 例如"订单不是【已完成】状态"是用户能看懂、
 *    也能据此判断的；换成"提交失败请重试"就把信息抹掉了。
 * 3. **成功提示必须写"审核通过后展示"**（FR-078）—— 评价默认不可见是既有行为（Q8），
 *    不说清用户会以为没提交上，然后再提一次，第二次会被后端以"订单已评价"拒绝。
 */
interface CommentTarget {
  id: number
  spuName: string
  picUrl?: string
  properties?: Array<{ propertyName: string; valueName: string }>
}

const props = defineProps<{ open: boolean; item: CommentTarget | null }>()
const emit = defineEmits<{ close: []; submitted: [] }>()

const form = reactive({
  descriptionScores: 5,
  benefitScores: 5,
  content: '',
  picUrls: [] as string[],
  anonymous: false,
})

const error = ref('')
const submitting = ref(false)
const done = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    // 每次打开都重置：默认 5 星（后端要求评分必填，给个合法初值才不会一打开就不可提交）
    form.descriptionScores = 5
    form.benefitScores = 5
    form.content = ''
    form.picUrls = []
    form.anonymous = false
    error.value = ''
    submitting.value = false
    done.value = false
  },
)

async function submit() {
  const item = props.item
  if (!item) return
  error.value = ''

  const check = validateComment(form)
  if (!check.ok) {
    error.value = check.message
    return
  }

  submitting.value = true
  try {
    await createOrderItemComment({
      orderItemId: item.id,
      descriptionScores: form.descriptionScores,
      benefitScores: form.benefitScores,
      content: form.content.trim(),
      picUrls: form.picUrls,
      anonymous: form.anonymous,
    })
    done.value = true
    emit('submitted')
  } catch (e) {
    // 原样展示后端文案（「订单不是【已完成】状态」「订单已评价」这类用户得看得到），弹层不关
    error.value = (e as { message?: string })?.message || '评价提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <MlModal :open="open" title="评价商品" @close="emit('close')">
    <MlField label="商品">
      <div class="cd-goods">
        <img v-if="item?.picUrl" class="cd-pic" :src="item.picUrl" :alt="item.spuName" >
        <div>
          <div class="cd-name">{{ item?.spuName }}</div>
          <div v-if="item?.properties?.length" class="cd-spec">
            <template v-for="p in item.properties" :key="p.valueName">
              {{ p.propertyName }}：{{ p.valueName }}
            </template>
          </div>
        </div>
      </div>
    </MlField>

    <MlField label="商品质量" required>
      <MlRate v-model="form.descriptionScores" />
    </MlField>

    <MlField label="服务态度" required>
      <MlRate v-model="form.benefitScores" />
    </MlField>

    <MlField label="评价内容" required>
      <textarea
        id="commentContent"
        v-model="form.content"
        class="ml-textarea"
        placeholder="说说这件商品怎么样"
      />
    </MlField>

    <MlField label="添加图片（选填）">
      <MultiImageUploader v-model="form.picUrls" />
    </MlField>

    <label class="cd-anon">
      <input id="commentAnonymous" v-model="form.anonymous" type="checkbox" >
      匿名评价
    </label>

    <p v-if="error" class="ml-error">{{ error }}</p>

    <!-- 提交成功后留在弹层里告知"审核通过后展示"——这条不能省（FR-078） -->
    <p v-if="done" class="cd-done">评价已提交，审核通过后展示</p>

    <template #foot>
      <button
        v-if="done"
        id="commentDone"
        class="btn-primary"
        type="button"
        @click="emit('close')"
      >
        知道了
      </button>
      <button
        v-else
        id="commentSubmit"
        class="btn-primary"
        type="button"
        :disabled="submitting"
        @click="submit"
      >
        {{ submitting ? '提交中…' : '提交评价' }}
      </button>
    </template>
  </MlModal>
</template>

<style scoped>
.cd-goods {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: var(--ml-bg-soft);
  border-radius: var(--ml-radius-field);
}
.cd-pic {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: var(--ml-radius-field);
}
.cd-name {
  font-size: 14px;
  color: var(--ml-text);
}
.cd-spec {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ml-text-sub);
}
.cd-anon {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ml-text-sub);
  cursor: pointer;
}
.cd-done {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ml-primary);
}
</style>
