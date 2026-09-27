<template>
  <div>
    <el-card shadow="never">
      <el-skeleton :loading="loading" animated>
        <el-row :gutter="16" justify="space-between">
          <el-col :span="24">
            <div class="flex items-center">
              <el-avatar :src="avatar" :size="70" class="mr-16px">
                <img src="@/assets/imgs/avatar.gif" alt="" />
              </el-avatar>
              <div>
                <div class="text-20px">
                  {{ t('workplace.welcome') }} {{ username }} {{ t('workplace.happyDay') }}
                </div>
                <div class="mt-10px text-14px text-gray-500">
                  {{ t('workplace.toady') }}，20℃ - 32℃！
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-skeleton>
    </el-card>
  </div>

  <el-row class="mt-8px" :gutter="8" justify="space-between">
    <el-col :span="24" class="mb-8px">

      <el-card shadow="never" class="mt-8px">
        <el-skeleton :loading="loading" animated>
          <el-row :gutter="20" justify="space-between">
            <el-col :xl="10" :lg="10" :md="24" :sm="24" :xs="24">
              <el-card shadow="hover" class="mb-8px">
                <el-skeleton :loading="loading" animated>
                  <Echart :options="pieOptionsData" :height="280" />
                </el-skeleton>
              </el-card>
            </el-col>
            <el-col :xl="14" :lg="14" :md="24" :sm="24" :xs="24">
              <el-card shadow="hover" class="mb-8px">
                <el-skeleton :loading="loading" animated>
                  <Echart :options="barOptionsData" :height="280" />
                </el-skeleton>
              </el-card>
            </el-col>
          </el-row>
        </el-skeleton>
      </el-card>
    </el-col>
  </el-row>
</template>
<script lang="ts" setup>
import dayjs from 'dayjs'
import { set } from 'lodash-es'
import { EChartsOption } from 'echarts'

import {
  getMemberTerminalStatisticsList,
  getOrderCountTrend,
  TIME_RANGE_TYPE
} from '@/api/statistics'
import { useUserStore } from '@/store/modules/user'
// import { useWatermark } from '@/hooks/web/useWatermark'
import { pieOptions, barOptions } from './echarts-data'

defineOptions({ name: 'Index' })

const { t } = useI18n()
const userStore = useUserStore()
// const { setWatermark } = useWatermark()
const loading = ref(true)
const avatar = userStore.getUser.avatar
const username = userStore.getUser.nickname
const pieOptionsData = reactive<EChartsOption>(pieOptions) as EChartsOption
const barOptionsData = reactive<EChartsOption>(barOptions) as EChartsOption

/**
 * 后端 `TerminalEnum` → 展示名。
 * `null` 与 `0`（`UNKNOWN`）在后端都是「未知」，这里统一成一个标签，
 * 免得图例里出现两个几乎同名的扇区。表里没有的值兜底成「其他(N)」。
 */
const TERMINAL_LABEL: Record<string, string> = {
  '0': '未知',
  '10': '微信小程序',
  '11': '微信公众号',
  '20': 'H5 网页',
  '31': '手机 App'
}

/**
 * 会员来源（按终端）—— **真实统计**：`/statistics/member/terminal-statistics-list`。
 * 此前这里是一组写死的假数据（335/310/234…）。
 */
const getUserAccessSource = async () => {
  const list = (await getMemberTerminalStatisticsList()) || []
  const data = list
    .filter((v) => v.userCount > 0) // 0 人的终端不占扇区
    .map((v) => ({
      name:
        TERMINAL_LABEL[String(v.terminal ?? 0)] ??
        `${t('analysis.terminalOther')}(${v.terminal})`,
      value: v.userCount
    }))
  const title = t('analysis.memberTerminalSource')
  set(pieOptionsData, 'title.text', title)
  set(pieOptionsData, 'series[0].name', title)
  set(pieOptionsData, 'legend.data', data.map((v) => v.name))
  pieOptionsData!.series![0].data = data
}

/**
 * 近 7 日订单量 —— **真实统计**：`/statistics/trade/order-count-trend`。
 * 此前这里是写死的「每周用户活跃量」假数据（13253/34235/…）。
 */
const getWeeklyUserActivity = async () => {
  const type = TIME_RANGE_TYPE.WEEK
  const end = dayjs().endOf('day')
  const begin = end.subtract(type - 1, 'day').startOf('day')
  const list =
    (await getOrderCountTrend({
      type,
      beginTime: begin.format('YYYY-MM-DDTHH:mm:ss'),
      endTime: end.format('YYYY-MM-DDTHH:mm:ss')
    })) || []

  // 后端只返回**有订单**的日期，缺失的日期要补齐，否则柱子会错位
  const byDate = new Map(list.map((v) => [v.value?.date, v.value?.orderPayCount ?? 0]))
  const days = Array.from({ length: type }, (_, i) => begin.add(i, 'day').format('YYYY-MM-DD'))
  const title = t('analysis.orderCountTrend7d')
  set(barOptionsData, 'title.text', title)
  set(
    barOptionsData,
    'xAxis.data',
    days.map((d) => d.slice(5)) // MM-DD，省地方
  )
  set(barOptionsData, 'series', [
    {
      name: title,
      data: days.map((d) => byDate.get(d) ?? 0),
      type: 'bar'
    }
  ])
}

const getAllApi = async () => {
  // 统计接口出错不该让整个工作台白屏：单个失败就退化成空图，其余照常渲染
  await Promise.allSettled([getUserAccessSource(), getWeeklyUserActivity()])
  loading.value = false
}

getAllApi()
</script>
