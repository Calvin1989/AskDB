import type { QueryResult } from '../types'

/**
 * 根据查询结果决定怎么展示：
 * - value：只有一个值（比如"一共多少单"），直接大字显示
 * - line：第一列像时间（日期、年月、或列名里有"月""year"等），画折线
 * - bar：一列分类 + 数值列，画柱状图
 * - none：不适合画图，只显示表格
 */
export type ChartPlan =
  | { kind: 'value'; label: string; value: unknown }
  | { kind: 'bar' | 'line'; dimension: number; measures: number[] }
  | { kind: 'none' }

/** 超过这么多行，柱子太密看不清，只显示表格 */
const MAX_POINTS = 40
/** 最多画几条数值序列 */
const MAX_MEASURES = 3

const TIME_NAME = /(month|year|date|day|week|hour|月|年|日|周|时间|日期)/i
const TIME_VALUE = /^\d{4}-\d{2}(-\d{2})?([ T]\d{2}:\d{2}.*)?$/

export function planChart(result: QueryResult | null): ChartPlan {
  if (!result || result.rows.length === 0 || result.columns.length === 0) {
    return { kind: 'none' }
  }
  const { columns, rows } = result

  if (rows.length === 1 && columns.length === 1) {
    return { kind: 'value', label: columns[0], value: rows[0][0] }
  }
  if (rows.length === 1 || rows.length > MAX_POINTS) {
    return { kind: 'none' }
  }

  const numeric = columns.map((_, c) => isNumericColumn(rows, c))
  const timeLike = columns.map((name, c) => TIME_NAME.test(name) || isTimeValueColumn(rows, c))

  // 维度列：第一个非数值列；没有的话，取第一个像时间的数值列（比如月份 1-12）
  let dimension = numeric.findIndex((isNum) => !isNum)
  if (dimension === -1) {
    dimension = timeLike.findIndex((isTime) => isTime)
  }
  if (dimension === -1) {
    return { kind: 'none' }
  }

  const measures = columns
    .map((_, c) => c)
    .filter((c) => c !== dimension && numeric[c])
    .slice(0, MAX_MEASURES)
  if (measures.length === 0) {
    return { kind: 'none' }
  }

  return { kind: timeLike[dimension] ? 'line' : 'bar', dimension, measures }
}

function isNumericColumn(rows: unknown[][], c: number): boolean {
  let seen = false
  for (const row of rows) {
    const v = row[c]
    if (v === null || v === undefined) continue
    if (typeof v !== 'number') return false
    seen = true
  }
  return seen
}

function isTimeValueColumn(rows: unknown[][], c: number): boolean {
  return rows.every((row) => typeof row[c] === 'string' && TIME_VALUE.test(row[c] as string))
}
