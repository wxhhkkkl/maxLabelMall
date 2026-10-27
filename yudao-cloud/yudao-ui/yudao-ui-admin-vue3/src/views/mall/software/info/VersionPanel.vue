<template>
  <Dialog v-model="dialogVisible" :title="`版本管理 · ${title}`" width="1000px">
    <el-form :inline="true" class="mb-10px">
      <el-form-item label="版本号">
        <el-input v-model="form.version" class="!w-130px" placeholder="1.0.23" />
      </el-form-item>
      <el-form-item label="平台">
        <el-select v-model="form.platform" class="!w-130px">
          <el-option v-for="p in PLATFORMS" :key="p" :label="p" :value="p" />
        </el-select>
      </el-form-item>
      <el-form-item label="架构">
        <el-select v-model="form.arch" class="!w-110px">
          <el-option v-for="a in ARCHES" :key="a" :label="a" :value="a" />
        </el-select>
      </el-form-item>
      <el-form-item label="文件">
        <input type="file" data-testid="version-file" @change="onFileChange" />
      </el-form-item>
      <el-form-item>
        <el-button :loading="uploading" type="primary" @click="handleUpload">
          上传并登记
        </el-button>
      </el-form-item>
    </el-form>
    <div v-if="uploading" class="mb-10px text-12px text-gray-500" data-testid="upload-status">
      正在处理：计算校验和 → 获取直传地址 → 上传到对象存储 → 登记…
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="版本" prop="version" width="110" />
      <el-table-column label="平台" prop="platform" width="100" />
      <el-table-column label="架构" prop="arch" width="90" />
      <el-table-column label="大小" width="120">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.current" type="success">当前发布</el-tag>
          <el-tag v-else-if="row.status === 1" type="info">草稿</el-tag>
          <el-tag v-else-if="row.status === 2" type="warning">已下架</el-tag>
          <el-tag v-else type="info">历史</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="220">
        <template #default="{ row }">
          <el-button v-if="row.status !== 0" link type="primary" @click="handlePublish(row, false)">
            发布
          </el-button>
          <el-button v-if="!row.current && row.status === 0" link type="success" @click="handlePublish(row, true)">
            设为当前
          </el-button>
          <el-button v-if="row.status === 0" link type="warning" @click="handleUnpublish(row)">下架</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="query.pageSize"
      v-model:page="query.pageNo"
      :total="total"
      @pagination="loadList"
    />
  </Dialog>
</template>

<script lang="ts" setup>
import {
  deleteSoftwareVersion,
  getPresignedUploadUrl,
  getSoftwareVersionPage,
  publishSoftwareVersion,
  registerSoftwareVersion,
  unpublishSoftwareVersion,
  type SoftwareVersionVO
} from '@/api/mall/software'
import { sha512OfFile } from './sha512'

defineOptions({ name: 'SoftwareVersionPanel' })

const PLATFORMS = ['windows', 'macos', 'linux']
const ARCHES = ['x64', 'arm64', 'x86']

const dialogVisible = ref(false)
const title = ref('')
const softwareId = ref<number>()
const loading = ref(false)
const uploading = ref(false)
const list = ref<SoftwareVersionVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })
const file = ref<File>()
const message = useMessage()
const form = reactive({
  version: '',
  platform: 'windows',
  arch: 'x64',
  releaseNotes: ''
})

const onFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0]
}

const formatSize = (size: number) => {
  if (!size) return '—'
  const mb = size / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(size / 1024).toFixed(0)} KB`
}

const loadList = async () => {
  if (!softwareId.value) return
  loading.value = true
  try {
    const data = await getSoftwareVersionPage({ ...query, softwareId: softwareId.value })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/**
 * 上传时序（013 contracts/admin-api §3.1）：
 * 算 sha512 → 取 infra 预签名地址 → PUT 直传对象存储 → 登记（草稿）→ 可选设为当前发布。
 */
const handleUpload = async () => {
  if (!file.value) return message.warning('请先选择文件')
  if (!form.version.trim()) return message.warning('请填写版本号')
  uploading.value = true
  try {
    const sha512 = await sha512OfFile(file.value)
    const directory = `software/${form.platform}-${form.arch}/${form.version.trim()}`
    const presign = await getPresignedUploadUrl({ name: file.value.name, directory })
    const put = await fetch(presign.uploadUrl, {
      method: 'PUT',
      body: file.value,
      headers: { 'Content-Type': 'application/octet-stream' }
    })
    if (!put.ok) throw new Error(`上传失败（HTTP ${put.status}）`)

    await registerSoftwareVersion({
      softwareId: softwareId.value!,
      version: form.version.trim(),
      platform: form.platform,
      arch: form.arch,
      fileName: file.value.name,
      fileSize: file.value.size,
      sha512,
      objectKey: presign.path,
      fileUrl: presign.url,
      releaseNotes: form.releaseNotes,
      releaseTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
    })
    message.success('已登记为草稿，确认无误后请发布')
    form.version = ''
    await loadList()
  } catch (e) {
    message.error(String((e as { message?: string }).message ?? e))
  } finally {
    uploading.value = false
  }
}

const handlePublish = async (row: SoftwareVersionVO, current: boolean) => {
  await publishSoftwareVersion(row.id, current)
  message.success(current ? '已设为当前发布' : '已发布')
  await loadList()
}

const handleUnpublish = async (row: SoftwareVersionVO) => {
  await message.confirm(`确定下架 ${row.version}（${row.platform}/${row.arch}）吗？`)
  await unpublishSoftwareVersion(row.id)
  message.success('已下架')
  await loadList()
}

const handleDelete = async (row: SoftwareVersionVO) => {
  await message.delConfirm('删除后记录不再出现，但已发出的下载链接仍可用，确定吗？')
  await deleteSoftwareVersion(row.id)
  message.success('已删除')
  await loadList()
}

const open = async (id: number, softwareTitle: string) => {
  softwareId.value = id
  title.value = softwareTitle
  query.pageNo = 1
  dialogVisible.value = true
  await loadList()
}

defineExpose({ open })
</script>
