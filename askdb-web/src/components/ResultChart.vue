<script setup lang="ts">
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { QueryResult } from '../types'
import { formatValue } from '../utils/format'

// 按需引入，只打包用到的图表类型，体积小很多
echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  result: QueryResult
  kind: 'bar' | 'line'
  dimension: number
  measures: number[]
}>()

const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null
const darkQuery = window.matchMedia('(prefers-color-scheme: dark)')

const compact = new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 })
const plain = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 })

/** 坐标轴刻度：10 万以上用"万""亿"，以下用千分位，同一根轴上不会混用两种写法 */
function axisLabel(max: number) {
  return (v: number) => (max >= 100000 ? compact.format(v) : plain.format(v))
}

/** 颜色从 CSS 变量里取，和页面保持一致，深色模式也跟着变 */
function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function render() {
  if (!chart) return
  const { result, kind, dimension, measures } = props

  let rows = result.rows.filter((row) => row[dimension] !== null)
  if (kind === 'line') {
    // 时间维度按先后排序，不管 SQL 返回的顺序
    rows = [...rows].sort((a, b) => String(a[dimension]).localeCompare(String(b[dimension]), 'zh-CN', { numeric: true }))
  }
  const categories = rows.map((row) => formatValue(row[dimension]))

  // 分类名较长或较多时用横向柱状图，标签不会挤在一起
  const horizontal = kind === 'bar' && (rows.length > 8 || categories.some((c) => c.length > 6))

  const ink2 = cssVar('--ink-2')
  const muted = cssVar('--muted')
  const grid = cssVar('--chart-grid')
  const colors = [cssVar('--chart-1'), cssVar('--chart-2'), cssVar('--chart-3')]

  const categoryAxis = {
    type: 'category' as const,
    data: categories,
    inverse: horizontal, // 横向时让第一行排在最上面
    axisLine: { lineStyle: { color: grid } },
    axisTick: { show: false },
    axisLabel: { color: ink2, fontSize: 12, hideOverlap: true },
  }
  const maxValue = Math.max(
    0,
    ...rows.flatMap((row) => measures.map((c) => Math.abs(Number(row[c]) || 0))),
  )
  const valueAxis = {
    type: 'value' as const,
    splitLine: { lineStyle: { color: grid } },
    axisLabel: { color: muted, fontSize: 12, formatter: axisLabel(maxValue) },
  }

  chart.setOption(
    {
      color: colors,
      animationDuration: 300,
      grid: { left: 8, right: 24, top: measures.length > 1 ? 36 : 16, bottom: 8, containLabel: true },
      legend: measures.length > 1 ? { top: 0, textStyle: { color: ink2 }, itemWidth: 12, itemHeight: 8 } : undefined,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: kind === 'bar' ? 'shadow' : 'line' },
        valueFormatter: (v: unknown) => formatValue(v),
      },
      xAxis: horizontal ? valueAxis : categoryAxis,
      yAxis: horizontal ? categoryAxis : valueAxis,
      series: measures.map((c) => ({
        name: result.columns[c],
        type: kind,
        data: rows.map((row) => row[c] as number | null),
        barMaxWidth: 28,
        showSymbol: rows.length <= 24,
        symbolSize: 5,
        lineStyle: { width: 2 },
      })),
    },
    true,
  )
}

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  render()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(el.value)
  darkQuery.addEventListener('change', render)
})

onBeforeUnmount(() => {
  darkQuery.removeEventListener('change', render)
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})

watch(() => [props.result, props.kind, props.dimension, props.measures], render)
</script>

<template>
  <div ref="el" class="chart" role="img" :aria-label="`${kind === 'bar' ? '柱状图' : '折线图'}：${result.columns.join('、')}`" />
</template>

<style scoped>
.chart {
  width: 100%;
  height: 340px;
  padding: 12px 8px 4px;
}
</style>
