import type { AskResult, ProgressStep, Verdict } from '../types'

/** 请求本身失败（连不上后端、参数不对、被限流等），和"后端返回了失败状态"区分开 */
export class RequestError extends Error {}

const OFFLINE = '连不上后端服务，请确认它已经在 8088 端口启动。'

/**
 * 提问，边处理边接收进度（SSE）。
 * 浏览器自带的 EventSource 只能发 GET，这里用 fetch 读取流，自己按 SSE 格式解析：
 * 每个事件以空行分隔，包含 "event:名字" 和 "data:内容" 两行。
 */
export async function askQuestionStream(
  question: string,
  history: string[],
  onStep: (step: ProgressStep) => void,
): Promise<AskResult> {
  let response: Response
  try {
    response = await fetch('/api/ask/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ question, history }),
    })
  } catch {
    throw new RequestError(OFFLINE)
  }
  if (!response.ok || !response.body) {
    throw await errorOf(response)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    let boundary: number
    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
      const event = parseEvent(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      if (event?.name === 'step') onStep(JSON.parse(event.data))
      if (event?.name === 'result') return JSON.parse(event.data) as AskResult
    }
  }
  throw new RequestError('连接中断，没有拿到结果，请重试。')
}

export function parseEvent(block: string): { name: string; data: string } | null {
  let name = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  return data.length > 0 ? { name, data: data.join('\n') } : null
}

export async function sendFeedback(question: string, sql: string, verdict: Verdict): Promise<void> {
  let response: Response
  try {
    response = await fetch('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, sql, verdict }),
    })
  } catch {
    throw new RequestError(OFFLINE)
  }
  if (!response.ok) {
    throw await errorOf(response)
  }
}

async function errorOf(response: Response): Promise<RequestError> {
  const body = await response.json().catch(() => null)
  if (body?.detail) return new RequestError(body.detail)
  // 开发模式下后端没启动时，Vite 代理会返回 5xx 且没有内容
  return new RequestError(`连不上后端服务（HTTP ${response.status}），请确认它已经在 8088 端口启动。`)
}
