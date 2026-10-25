import type { QueryResult } from '../types'

const integerFormat = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 })
const decimalFormat = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 })

/** 表格和大数字里显示的值：数字加千分位，日期时间去掉中间的 T */
export function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'number') {
    return Number.isInteger(value) ? integerFormat.format(value) : decimalFormat.format(value)
  }
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) {
    return value.replace('T', ' ')
  }
  return String(value)
}

export function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms} 毫秒` : `${(ms / 1000).toFixed(1)} 秒`
}

/** 导出 CSV：值里有逗号、引号、换行时加引号并转义 */
export function toCsv(result: QueryResult): string {
  const lines = [result.columns, ...result.rows].map((row) => row.map(csvCell).join(','))
  return lines.join('\r\n')
}

function csvCell(value: unknown): string {
  if (value === null || value === undefined) return ''
  const text = String(value)
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** 浏览器下载 CSV。开头加 BOM，否则 Excel 打开中文会乱码 */
export function downloadCsv(result: QueryResult, filename: string): void {
  const blob = new Blob(['﻿' + toCsv(result)], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
