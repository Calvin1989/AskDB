<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RequestError } from '../api/ask'
import type { TestResult } from '../api/settings'
import { PROVIDERS, providerOf, useLlmSettings, type ProviderId } from '../stores/llmSettings'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const { state, save, test } = useLlmSettings()

const dialog = ref<HTMLDialogElement | null>(null)
const form = reactive({ baseUrl: '', model: '', apiKey: '' })
const provider = ref<ProviderId>('deepseek')
const busy = ref<'' | 'test' | 'save'>('')
const testResult = ref<TestResult | null>(null)
const error = ref('')

const view = computed(() => state.view)
const readOnly = computed(() => view.value !== null && !view.value.editable)

/** 打开时用当前设置填表。Key 不回显，留空表示沿用已保存的 */
watch(
  () => props.open,
  (open) => {
    if (open) {
      form.baseUrl = view.value?.baseUrl ?? PROVIDERS[0].baseUrl
      form.model = view.value?.model ?? PROVIDERS[0].model
      form.apiKey = ''
      provider.value = providerOf(form.baseUrl)
      testResult.value = null
      error.value = ''
      dialog.value?.showModal()
    } else {
      dialog.value?.close()
    }
  },
)

function pickProvider(id: ProviderId) {
  provider.value = id
  const p = PROVIDERS.find((x) => x.id === id)
  if (p && p.id !== 'custom') {
    form.baseUrl = p.baseUrl
    form.model = p.model
  }
  testResult.value = null
}

const keyPlaceholder = computed(() =>
  view.value?.apiKeyMasked ? `已保存 ${view.value.apiKeyMasked}，不修改请留空` : '粘贴 API Key',
)

async function run(kind: 'test' | 'save') {
  busy.value = kind
  error.value = ''
  testResult.value = null
  try {
    if (kind === 'test') {
      testResult.value = await test({ ...form })
    } else {
      await save({ ...form })
      emit('close')
    }
  } catch (e) {
    error.value = e instanceof RequestError ? e.message : '操作失败，请稍后再试。'
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <dialog ref="dialog" class="dialog" aria-labelledby="settings-title" @close="emit('close')">
    <form class="body" @submit.prevent="run('save')">
      <h2 id="settings-title">模型设置</h2>
      <p class="intro">
        AskDB 通过 OpenAI 兼容接口调用大模型。保存后立即生效，并保存在后端，重启后也不用再填。
      </p>

      <p v-if="readOnly" class="locked">这个环境不允许在页面上修改模型设置，需要改服务端配置。</p>

      <fieldset :disabled="readOnly">
        <div class="field">
          <span class="label" id="provider-label">服务商</span>
          <div class="providers" role="radiogroup" aria-labelledby="provider-label">
            <button
              v-for="p in PROVIDERS"
              :key="p.id"
              type="button"
              role="radio"
              :aria-checked="provider === p.id"
              :class="{ on: provider === p.id }"
              @click="pickProvider(p.id)"
            >
              {{ p.name }}
            </button>
          </div>
        </div>

        <label class="field">
          <span class="label">接口地址</span>
          <input v-model="form.baseUrl" type="url" required placeholder="https://api.deepseek.com" @input="provider = providerOf(form.baseUrl)" />
          <span class="help">不用加 /v1/chat/completions，程序会自动拼上</span>
        </label>

        <label class="field">
          <span class="label">模型</span>
          <input v-model="form.model" required placeholder="deepseek-chat" />
        </label>

        <label class="field">
          <span class="label">API Key</span>
          <input v-model="form.apiKey" type="password" autocomplete="off" :placeholder="keyPlaceholder" />
          <span class="help">Key 只保存在后端，页面上只显示头尾几位</span>
        </label>
      </fieldset>

      <p v-if="testResult" class="result" :class="testResult.ok ? 'ok' : 'fail'" role="status">
        {{ testResult.message }}<template v-if="testResult.ok">（{{ (testResult.elapsedMs / 1000).toFixed(1) }} 秒）</template>
      </p>
      <p v-if="error" class="result fail" role="alert">{{ error }}</p>

      <div class="actions">
        <button type="button" class="secondary" :disabled="readOnly || busy !== ''" @click="run('test')">
          {{ busy === 'test' ? '测试中…' : '测试连接' }}
        </button>
        <span class="spacer" />
        <button type="button" class="plain" @click="emit('close')">取消</button>
        <button type="submit" class="primary" :disabled="readOnly || busy !== ''">
          {{ busy === 'save' ? '保存中…' : '保存' }}
        </button>
      </div>
    </form>
  </dialog>
</template>

<style scoped>
.dialog {
  width: min(520px, calc(100vw - 32px));
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  color: var(--ink);
}

.dialog::backdrop {
  background: rgb(15 20 25 / 0.35);
}

.body {
  padding: 22px 24px 20px;
}

h2 {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 650;
}

.intro {
  margin: 0 0 18px;
  color: var(--ink-2);
  font-size: 14px;
}

.locked {
  margin: 0 0 16px;
  padding: 8px 12px;
  border-left: 3px solid var(--warn);
  background: var(--warn-soft);
  font-size: 14px;
}

fieldset {
  margin: 0;
  padding: 0;
  border: 0;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 14px;
}

.label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
}

input {
  padding: 7px 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 14px;
}

input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-soft);
}

.help {
  color: var(--muted);
  font-size: 12.5px;
}

.providers {
  display: inline-flex;
  align-self: flex-start;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.providers button {
  padding: 4px 14px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--ink-2);
  font-size: 13px;
}

.providers button.on {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.result {
  margin: 4px 0 0;
  padding: 8px 12px;
  border-left: 3px solid;
  font-size: 13.5px;
  overflow-wrap: anywhere;
}

.result.ok {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.result.fail {
  border-color: var(--danger);
  background: var(--danger-soft);
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
}

.spacer {
  flex: 1;
}

.actions button {
  padding: 6px 16px;
  border-radius: var(--radius);
  font-size: 14px;
}

.primary {
  border: 0;
  background: var(--accent);
  color: #fff;
  font-weight: 500;
}

.primary:hover:not(:disabled) {
  background: var(--accent-hover);
}

.secondary {
  border: 1px solid var(--line-strong);
  background: var(--surface);
}

.secondary:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
}

.plain {
  border: 0;
  background: none;
  color: var(--ink-2);
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (prefers-color-scheme: dark) {
  .primary {
    color: #10161a;
  }
}
</style>
