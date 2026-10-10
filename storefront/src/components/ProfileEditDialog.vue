<script setup lang="ts">
import { ref, watch } from 'vue'

import { updateProfile } from '@/api/member'
import type { MemberUser } from '@/types'
import { SEX_LABELS, buildProfileUpdate, validateProfile, type ProfileForm } from '@/utils/profile'

import AvatarUploader from './AvatarUploader.vue'
import MlField from './base/MlField.vue'
import MlModal from './base/MlModal.vue'

/**
 * 编辑资料弹层（FR-011b）。
 *
 * 做成弹层而非独立页：本项目既有的弹层表单范式就是 `AddressFormDialog`（结算页与地址页共用），
 * 沿用比新造一致；改资料是"轻动作"，弹层还省掉"改完还要返回"的导航成本。
 *
 * ⚠️ **提交只带改动过的字段**（`buildProfileUpdate` 负责挑）—— 后端四个字段都不是必填，
 * 把没改的也发回去会用**陈旧值覆盖**别处的改动。**清空邮箱要发空串**。
 *
 * ⚠️ 保存成功后由**调用方**刷新登录态（`userStore.loadMember()`）：顶栏的昵称/头像读的是
 * store 里的 member，只改本地副本不会让顶栏变 —— 那是 SC-022 要验的点。
 */
const props = defineProps<{ open: boolean; member: MemberUser | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const form = ref<ProfileForm>({ nickname: '', avatar: '', email: '', sex: undefined })
const error = ref('')
const saving = ref(false)

/**
 * 弹层打开时用当前会员回填（每次打开都重置，避免残留上一次的编辑）。
 *
 * ⚠️ `immediate: true` 不能省：调用方可能一开始就传 `open: true`
 * （例如页面挂载时弹层就是开的），没有 immediate 的话 watch 不会触发 → **表单是空的**。
 */
watch(
  () => props.open,
  (open) => {
    if (!open) return
    const m = props.member
    form.value = {
      nickname: m?.nickname ?? '',
      avatar: m?.avatar ?? '',
      email: m?.email ?? '',
      sex: m?.sex,
    }
    error.value = ''
    saving.value = false
  },
  { immediate: true },
)

async function save() {
  error.value = ''
  const check = validateProfile(form.value)
  if (!check.ok) {
    error.value = check.message
    return
  }
  const body = buildProfileUpdate(
    {
      nickname: props.member?.nickname,
      avatar: props.member?.avatar,
      email: props.member?.email,
      sex: props.member?.sex,
    },
    form.value,
  )
  // 什么都没改：不发这次请求
  if (!Object.keys(body).length) {
    emit('close')
    return
  }

  saving.value = true
  try {
    await updateProfile(body)
    emit('saved')
    emit('close')
  } catch (e) {
    // 原样透出后端文案（如「邮箱格式不正确」），弹层不关，用户可改后重试
    error.value = (e as { message?: string })?.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <MlModal :open="open" title="编辑资料" @close="emit('close')">
    <MlField label="头像">
      <AvatarUploader v-model="form.avatar" :nickname="form.nickname" />
    </MlField>

    <MlField label="昵称" required>
      <input id="profileNickname" v-model="form.nickname" class="ml-input" placeholder="请输入昵称" />
    </MlField>

    <MlField label="手机号" hint="手机号是登录账号，如需更换请联系客服">
      <input class="ml-input" :value="member?.mobile ?? ''" disabled />
    </MlField>

    <MlField label="邮箱" hint="可留空">
      <input
        id="profileEmail"
        v-model.trim="form.email"
        class="ml-input"
        type="email"
        placeholder="用于接收通知，可留空"
      />
    </MlField>

    <MlField label="性别">
      <select id="profileSex" v-model="form.sex" class="ml-select">
        <option v-for="s in SEX_LABELS" :key="s.value" :value="s.value">{{ s.label }}</option>
      </select>
    </MlField>

    <p v-if="error" class="ml-error">{{ error }}</p>

    <template #foot>
      <button
        id="profileSave"
        class="btn-primary"
        type="button"
        :disabled="saving"
        @click="save"
      >
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </template>
  </MlModal>
</template>
