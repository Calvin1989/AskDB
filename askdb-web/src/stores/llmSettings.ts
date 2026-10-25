import { reactive, readonly } from 'vue'
import { RequestError } from '../api/ask'
import { getLlmSettings, saveLlmSettings, testLlmSettings, type LlmSettingsForm, type LlmSettingsView } from '../api/settings'

/** 当前模型设置。页面打开时读一次，保存后更新 */
const state = reactive({
  view: null as LlmSettingsView | null,
  loadError: '',
})

export function useLlmSettings() {
  async function load() {
    try {
      state.view = await getLlmSettings()
      state.loadError = ''
    } catch (e) {
      state.loadError = e instanceof RequestError ? e.message : '读取模型设置失败。'
    }
  }

  async function save(form: LlmSettingsForm) {
    state.view = await saveLlmSettings(form)
  }

  return { state: readonly(state), load, save, test: testLlmSettings }
}

/** 常用服务商。地址不含 /v1/chat/completions，程序会自动拼上 */
export const PROVIDERS = [
  { id: 'deepseek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat' },
  { id: 'qwen', name: '通义千问', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode', model: 'qwen-plus' },
  { id: 'custom', name: '其他', baseUrl: '', model: '' },
] as const

export type ProviderId = (typeof PROVIDERS)[number]['id']

export function providerOf(baseUrl: string): ProviderId {
  return PROVIDERS.find((p) => p.baseUrl && p.baseUrl === baseUrl)?.id ?? 'custom'
}
