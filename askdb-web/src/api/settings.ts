import { RequestError } from './ask'

/** 后端返回的模型设置。完整的 API Key 不会返回，只有打码后的 */
export interface LlmSettingsView {
  baseUrl: string
  model: string
  apiKeyMasked: string
  configured: boolean
  editable: boolean
}

/** 提交的设置。apiKey 留空表示沿用已保存的 Key */
export interface LlmSettingsForm {
  baseUrl: string
  model: string
  apiKey: string
}

export interface TestResult {
  ok: boolean
  message: string
  elapsedMs: number
}

const URL = '/api/settings/llm'

export const getLlmSettings = () => request<LlmSettingsView>('GET', URL)
export const saveLlmSettings = (form: LlmSettingsForm) => request<LlmSettingsView>('PUT', URL, form)
export const testLlmSettings = (form: LlmSettingsForm) => request<TestResult>('POST', `${URL}/test`, form)

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new RequestError('连不上后端服务，请确认它已经在 8088 端口启动。')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw new RequestError(data?.detail ?? `连不上后端服务（HTTP ${response.status}），请确认它已经在 8088 端口启动。`)
  }
  return data as T
}
