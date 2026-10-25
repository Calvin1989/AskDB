import { describe, expect, it } from 'vitest'
import { planChart } from './chart'
import { formatValue, toCsv } from './format'
import { hintFor } from './hints'
import { parseEvent } from '../api/ask'
import { tokenizeSql } from './sql'

const result = (columns: string[], rows: unknown[][]) => ({ columns, rows, truncated: false })

describe('planChart 选择展示方式', () => {
  it('只有一个值时大字显示', () => {
    expect(planChart(result(['COUNT(*)'], [[99441]]))).toEqual({ kind: 'value', label: 'COUNT(*)', value: 99441 })
  })

  it('分类 + 数值画柱状图', () => {
    const plan = planChart(result(['order_status', 'cnt'], [['delivered', 96478], ['shipped', 1107]]))
    expect(plan).toEqual({ kind: 'bar', dimension: 0, measures: [1] })
  })

  it('年月字符串画折线', () => {
    const plan = planChart(result(['ym', 'orders'], [['2018-01', 7269], ['2018-02', 6728]]))
    expect(plan.kind).toBe('line')
  })

  it('列名是月份的数字列当作维度画折线', () => {
    const plan = planChart(result(['month', 'order_count'], [[1, 7269], [2, 6728], [3, 7211]]))
    expect(plan).toEqual({ kind: 'line', dimension: 0, measures: [1] })
  })

  it('全是数值且看不出维度时只显示表格', () => {
    expect(planChart(result(['a', 'b'], [[1, 2], [3, 4]])).kind).toBe('none')
  })

  it('只有一行多列时只显示表格', () => {
    expect(planChart(result(['a', 'b'], [['x', 2]])).kind).toBe('none')
  })

  it('行数太多时只显示表格', () => {
    const rows = Array.from({ length: 41 }, (_, i) => [`c${i}`, i])
    expect(planChart(result(['k', 'v'], rows)).kind).toBe('none')
  })

  it('没有结果时不画图', () => {
    expect(planChart(null).kind).toBe('none')
    expect(planChart(result(['a'], [])).kind).toBe('none')
  })
})

describe('formatValue 显示格式', () => {
  it('整数加千分位，小数最多 4 位', () => {
    expect(formatValue(99441)).toBe('99,441')
    expect(formatValue(4.070891)).toBe('4.0709')
  })

  it('日期时间去掉 T，空值显示横线', () => {
    expect(formatValue('2017-10-02T10:56:33')).toBe('2017-10-02 10:56:33')
    expect(formatValue(null)).toBe('—')
  })
})

describe('toCsv 导出', () => {
  it('含逗号、引号、换行的值会加引号转义', () => {
    const csv = toCsv(result(['name', 'note'], [['a,b', 'say "hi"'], ['c', 'line1\nline2'], ['d', null]]))
    expect(csv).toBe('name,note\r\n"a,b","say ""hi"""\r\nc,"line1\nline2"\r\nd,')
  })
})

describe('tokenizeSql 着色', () => {
  it('区分关键字、字符串和数字', () => {
    const tokens = tokenizeSql("SELECT COUNT(*) FROM orders WHERE order_status = 'delivered' LIMIT 5")
    expect(tokens.filter((t) => t.type === 'keyword').map((t) => t.text)).toEqual(['SELECT', 'COUNT', 'FROM', 'WHERE', 'LIMIT'])
    expect(tokens.find((t) => t.type === 'string')?.text).toBe("'delivered'")
    expect(tokens.find((t) => t.type === 'number')?.text).toBe('5')
    expect(tokens.map((t) => t.text).join('')).toBe("SELECT COUNT(*) FROM orders WHERE order_status = 'delivered' LIMIT 5")
  })
})


describe('hintFor 报错提示', () => {
  it('能认出常见配置问题', () => {
    expect(hintFor("读取表结构失败：Access denied for user 'askdb_reader'@'localhost' (using password: NO)")).toContain('DB_PASSWORD')
    expect(hintFor('401 - {"error":{"message":"Authentication Fails, Your api key is invalid"}}')).toContain('模型设置')
    expect(hintFor('something else')).toBeNull()
  })
})

describe('parseEvent 解析 SSE', () => {
  it('取出事件名和数据，兼容冒号后有无空格', () => {
    expect(parseEvent('event:step\ndata:{"stage":"SCHEMA"}')).toEqual({ name: 'step', data: '{"stage":"SCHEMA"}' })
    expect(parseEvent('event: result\ndata: {"a":1}')).toEqual({ name: 'result', data: '{"a":1}' })
  })

  it('多行 data 合并，没有 data 时忽略', () => {
    expect(parseEvent('data:a\ndata:b')).toEqual({ name: 'message', data: 'a\nb' })
    expect(parseEvent(':heartbeat')).toBeNull()
  })
})
