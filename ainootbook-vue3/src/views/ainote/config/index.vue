<template>
  <PageWrapper title="AI 智能配置">
    <a-card :bordered="false" class="mb-16">
      <a-tabs v-model:activeKey="activeKey" type="card">
        <a-tab-pane key="service" forceRender>
          <template #tab>
            <a-badge :dot="tabErrors.service">服务配置</a-badge>
          </template>
          <BasicForm @register="registerServiceForm" />
        </a-tab-pane>

        <a-tab-pane key="prompt" forceRender>
          <template #tab>
            <a-badge :dot="tabErrors.prompt">提示词配置</a-badge>
          </template>

          <div class="prompt-config-wrap">
            <div class="prompt-header">
              <a-button @click="handleNewPrompt" style="margin-right: 8px">
                <template #icon><PlusOutlined /></template>
                新建提示词
              </a-button>
              <a-button type="link" @click="showVariableHelp = true">
                <template #icon><QuestionCircleOutlined /></template>
                可用变量
              </a-button>
            </div>

            <!-- 三个提示词选择器 -->
            <div v-for="item in promptFields" :key="item.field" class="prompt-selector-item">
              <div class="prompt-selector-row">
                <span class="prompt-selector-label">{{ item.label }}</span>
                <a-select
                  :value="promptState[item.field]"
                  placeholder="请选择提示词"
                  allow-clear
                  style="flex: 1"
                  :options="allPrompts.map((p) => ({ label: p.name, value: p.promptKey }))"
                  @change="(val) => handlePromptSelect(item.field, val)"
                />
              </div>

              <!-- 选中后展示 Markdown 编辑器 -->
              <div v-if="editingPrompt[item.field]" class="prompt-editor-wrap">
                <div class="prompt-editor-header">
                  <span class="prompt-editor-name">{{ editingPrompt[item.field].name }}</span>
                  <a-tag v-if="!editingPrompt[item.field].content?.includes('{{content}}')" color="orange">
                    ⚠ 缺少 &#123;&#123;content&#125;&#125;
                  </a-tag>
                  <a-tag v-else color="green">✓ 包含 &#123;&#123;content&#125;&#125;</a-tag>
                  <a-button
                    type="primary"
                    size="small"
                    :loading="savingPrompt[item.field]"
                    style="margin-left: auto"
                    @click="handleSavePromptContent(item.field)"
                  >
                    保存提示词
                  </a-button>
                </div>
                <MarkDown
                  :key="item.field + '_' + editingPrompt[item.field].id"
                  :value="editingPrompt[item.field].content"
                  :height="280"
                  @change="(val) => handleEditorChange(item.field, val)"
                />
              </div>
            </div>

            <a-divider />

            <a-row :gutter="24" class="mt-8">
              <a-col :span="12">
                <a-form-item label="最大摘要长度" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                  <a-input-number v-model:value="promptState.maxSummaryLength" :min="1" :max="200" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="最大关键词数" :label-col="{ span: 8 }" :wrapper-col="{ span: 16 }">
                  <a-input-number v-model:value="promptState.maxKeywordsCount" :min="1" :max="5" style="width: 100%" />
                </a-form-item>
              </a-col>
            </a-row>
          </div>

          <!-- 新建提示词弹窗 -->
          <a-modal
            v-model:open="newPromptModal.visible"
            title="新建提示词"
            width="520px"
            @ok="handleNewPromptConfirm"
            :confirm-loading="newPromptModal.loading"
            ok-text="创建"
            cancel-text="取消"
          >
            <a-form layout="vertical" :colon="false" style="margin-top: 8px; padding: 0 24px 8px">
              <a-form-item label="名称" style="margin-bottom: 16px">
                <a-input
                  v-model:value="newPromptModal.name"
                  placeholder="例如：笔记摘要提示词"
                  :maxlength="40"
                  show-count
                />
              </a-form-item>
              <a-form-item style="margin-bottom: 16px">
                <template #label>
                  <span>编码 <span style="color: #999; font-size: 12px; font-weight: 400">（promptKey，创建后不可修改）</span></span>
                </template>
                <a-input
                  v-model:value="newPromptModal.promptKey"
                  placeholder="例如：note_summary_v2，只允许小写字母、数字、下划线"
                  :maxlength="50"
                />
              </a-form-item>
              <a-form-item label="功能描述" style="margin-bottom: 0">
                <a-textarea
                  v-model:value="newPromptModal.description"
                  placeholder="简单描述这个提示词的用途（可选）"
                  :rows="3"
                  :maxlength="200"
                  show-count
                />
              </a-form-item>
            </a-form>
          </a-modal>

          <!-- 变量说明弹窗 -->
          <a-modal
            v-model:open="showVariableHelp"
            title="提示词可用变量说明"
            :footer="null"
            width="560px"
          >
            <a-table
              :data-source="variableHelpData"
              :columns="variableHelpColumns"
              :pagination="false"
              size="small"
              row-key="variable"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'variable'">
                  <code class="var-code">{{ record.variable }}</code>
                </template>
                <template v-if="column.key === 'required'">
                  <a-tag :color="record.required ? 'red' : 'default'">
                    {{ record.required ? '必填' : '可选' }}
                  </a-tag>
                </template>
              </template>
            </a-table>
            <a-alert type="warning" show-icon class="mt-12">
              <template #message>
                <code>&#123;&#123;maxLength&#125;&#125;</code> 和 <code>&#123;&#123;maxCount&#125;&#125;</code> 的实际值来自下方「最大摘要长度」和「最大关键词数」配置，提示词里写了这两个变量时，配置项才真正生效。
              </template>
            </a-alert>
          </a-modal>
        </a-tab-pane>

        <a-tab-pane key="flow" forceRender>
          <template #tab>
            <a-badge :dot="tabErrors.flow">处理流程配置</a-badge>
          </template>
          <BasicForm @register="registerFlowForm" />
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <div class="fixed bottom-0 left-0 right-0 p-4 bg-white border-t border-gray-200 shadow-md z-50 flex justify-center gap-4">
      <a-button type="primary" @click="handleSave" :loading="saving">保存流水线配置</a-button>
      <a-button @click="handleRefreshCache">刷新缓存</a-button>
    </div>
  </PageWrapper>
</template>

<script setup lang="ts">
  import { onMounted, ref, reactive, computed, watch } from 'vue';
  import { QuestionCircleOutlined, PlusOutlined } from '@ant-design/icons-vue';
  import { PageWrapper } from '/@/components/Page';
  import { BasicForm, useForm } from '/@/components/Form/index';
  import type { FormSchema } from '/@/components/Form/index';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { defHttp } from '/@/utils/http/axios';
  import { getAiConfig, updateAiConfig, refreshAiConfigCache } from '/@/api/ainote/aiConfig.api';
  import type { AiConfigRecord } from '/@/api/ainote/aiConfig.api';
  import { MarkDown } from '/@/components/Markdown';

  // ── 提示词选项类型 ──────────────────────────────────────────────────
  interface PromptOption {
    id: string;
    name: string;
    promptKey: string;
    content: string;
  }

  // ── 主组件逻辑 ────────────────────────────────────────────────────────
  const { notification, createMessage } = useMessage();
  const activeKey = ref('service');
  const saving = ref(false);
  const tabErrors = reactive({ service: false, prompt: false, flow: false });
  const configRecord = ref<AiConfigRecord | null>(null);

  // 变量说明弹窗
  const showVariableHelp = ref(false);
  const variableHelpColumns = [
    { title: '变量', key: 'variable', dataIndex: 'variable', width: 160 },
    { title: '必填', key: 'required', dataIndex: 'required', width: 70 },
    { title: '说明', key: 'desc', dataIndex: 'desc' },
    { title: '适用提示词', key: 'scope', dataIndex: 'scope', width: 140 },
  ];
  const variableHelpData = [
    { variable: '{{content}}', required: true, desc: '待处理的源文本（ASR 转写结果 / 文档解析内容 / 聚合文本）', scope: '摘要、关键词、整合' },
    { variable: '{{maxLength}}', required: false, desc: '最大摘要长度，值来自「最大摘要长度」配置项', scope: '摘要' },
    { variable: '{{maxCount}}', required: false, desc: '最大关键词数，值来自「最大关键词数」配置项', scope: '关键词' },
    { variable: '{{summary}}', required: false, desc: '已生成的摘要文本，提取关键词时可引用，让关键词更准确', scope: '关键词' },
    { variable: '{{noteTitle}}', required: false, desc: '笔记标题，由用户创建笔记时填写', scope: '整合（仅此处生效）' },
  ];

  // 提示词列表（从 airag_prompts 拉取）
  const allPrompts = ref<PromptOption[]>([]);

  async function loadPrompts() {
    try {
      const res = await defHttp.get({ url: '/airag/prompts/list', params: { pageSize: 200 } });
      allPrompts.value = (res?.records ?? []) as PromptOption[];
    } catch {
      // 加载失败不阻断页面
    }
  }

  // 提示词 Tab 状态（手动管理，不走 BasicForm）
  const promptState = reactive({
    integratePromptKey: '',
    summaryPromptKey: '',
    keywordsPromptKey: '',
    maxSummaryLength: 100,
    maxKeywordsCount: 5,
  });

  const promptFields = [
    { field: 'integratePromptKey', label: '整合提示词' },
    { field: 'summaryPromptKey', label: '摘要提示词' },
    { field: 'keywordsPromptKey', label: '关键词提示词' },
  ] as const;

  // 当前正在编辑的提示词（每个字段独立）
  const editingPrompt = reactive<Record<string, PromptOption | null>>({
    integratePromptKey: null,
    summaryPromptKey: null,
    keywordsPromptKey: null,
  });

  // 保存状态
  const savingPrompt = reactive<Record<string, boolean>>({
    integratePromptKey: false,
    summaryPromptKey: false,
    keywordsPromptKey: false,
  });

  // 新建提示词弹窗
  const newPromptModal = reactive({
    visible: false,
    loading: false,
    name: '',
    promptKey: '',
    description: '',
  });

  function handlePromptSelect(field: string, promptKey: string | undefined) {
    promptState[field] = promptKey ?? '';
    if (promptKey) {
      const prompt = allPrompts.value.find((p) => p.promptKey === promptKey);
      if (prompt) {
        editingPrompt[field] = { ...prompt };
      }
    } else {
      editingPrompt[field] = null;
    }
  }

  function handleEditorChange(field: string, val: string) {
    if (editingPrompt[field]) {
      editingPrompt[field]!.content = val;
    }
  }

  async function handleSavePromptContent(field: string) {
    const prompt = editingPrompt[field];
    if (!prompt) return;

    savingPrompt[field] = true;
    try {
      await defHttp.put({ url: '/airag/prompts/edit', params: prompt });
      createMessage.success('提示词已保存');
      // 刷新列表
      await loadPrompts();
    } catch (error: any) {
      createMessage.error(error?.response?.data?.message || '保存失败');
    } finally {
      savingPrompt[field] = false;
    }
  }

  function handleNewPrompt() {
    newPromptModal.visible = true;
    newPromptModal.name = '';
    newPromptModal.promptKey = '';
    newPromptModal.description = '';
  }

  async function handleNewPromptConfirm() {
    if (!newPromptModal.name || !newPromptModal.promptKey) {
      createMessage.error('名称和编码不能为空');
      return;
    }

    newPromptModal.loading = true;
    try {
      const res = await defHttp.post({
        url: '/airag/prompts/add',
        params: {
          name: newPromptModal.name,
          promptKey: newPromptModal.promptKey,
          description: newPromptModal.description,
          content: '请在下方编辑提示词内容，必须包含 {{content}} 变量。',
          status: '1',
        },
      });
      createMessage.success('提示词已创建，请在下方选择器中选择使用');
      await loadPrompts();
      newPromptModal.visible = false;
    } catch (error: any) {
      createMessage.error(error?.response?.data?.message || '创建失败');
    } finally {
      newPromptModal.loading = false;
    }
  }

  // Tab 1: 服务配置
  const serviceSchemas: FormSchema[] = [
    {
      field: 'asrModelId',
      label: '语音识别(ASR)大模型',
      component: 'ApiSelect',
      colProps: { span: 12 },
      componentProps: {
        api: () => defHttp.get({ url: '/airag/airagModel/list', params: { modelType: 'ASR', activateFlag: 1, pageSize: 50 } }),
        resultField: 'records',
        labelField: 'name',
        valueField: 'id',
      },
    },
    {
      field: 'ocrModelId',
      label: 'OCR 大模型',
      component: 'ApiSelect',
      colProps: { span: 12 },
      componentProps: {
        api: () => defHttp.get({ url: '/airag/airagModel/list', params: { modelType_MultiString: 'OCR,LLM', activateFlag: 1, pageSize: 50 } }),
        resultField: 'records',
        labelField: 'name',
        valueField: 'id',
      },
    },
    {
      field: 'videoModelId',
      label: '视频处理大模型',
      component: 'ApiSelect',
      colProps: { span: 12 },
      componentProps: {
        api: () => defHttp.get({ url: '/airag/airagModel/list', params: { modelType: 'ASR', activateFlag: 1, pageSize: 50 } }),
        resultField: 'records',
        labelField: 'name',
        valueField: 'id',
      },
    },
    {
      field: 'summaryModelId',
      label: '摘要大模型',
      component: 'ApiSelect',
      colProps: { span: 12 },
      componentProps: {
        api: () => defHttp.get({ url: '/airag/airagModel/list', params: { modelType: 'LLM', activateFlag: 1, pageSize: 50 } }),
        resultField: 'records',
        labelField: 'name',
        valueField: 'id',
      },
    },
    {
      field: 'keywordsModelId',
      label: '关键词提取大模型',
      component: 'ApiSelect',
      colProps: { span: 12 },
      componentProps: {
        api: () => defHttp.get({ url: '/airag/airagModel/list', params: { modelType: 'LLM', activateFlag: 1, pageSize: 50 } }),
        resultField: 'records',
        labelField: 'name',
        valueField: 'id',
      },
    },
    {
      field: 'integrateModelId',
      label: '整合处理大模型',
      component: 'ApiSelect',
      colProps: { span: 12 },
      componentProps: {
        api: () => defHttp.get({ url: '/airag/airagModel/list', params: { modelType: 'LLM', activateFlag: 1, pageSize: 50 } }),
        resultField: 'records',
        labelField: 'name',
        valueField: 'id',
      },
    },
    {
      field: 'knowledgeId',
      label: '知识库',
      component: 'ApiSelect',
      colProps: { span: 12 },
      componentProps: {
        api: () => defHttp.get({ url: '/airag/knowledge/list', params: { type: 'knowledge', status: 'enable', pageSize: 50 } }),
        resultField: 'records',
        labelField: 'name',
        valueField: 'id',
      },
    },
  ];

  // Tab 3: 处理流程配置
  const flowOptions = [
    { label: '跳过', value: 'skip' },
    { label: '重试', value: 'retry' },
    { label: '全部失败', value: 'fail_all' },
  ];

  const createFlowGroup = (prefix: string, labelPrefix: string): FormSchema[] => [
    {
      field: `${prefix}FailureMode`,
      label: `${labelPrefix}失败策略`,
      component: 'Select',
      colProps: { span: 12 },
      defaultValue: 'skip',
      componentProps: { options: flowOptions },
    },
    {
      field: `${prefix}RetryLimit`,
      label: `${labelPrefix}重试上限`,
      component: 'InputNumber',
      colProps: { span: 12 },
      defaultValue: 0,
      componentProps: ({ formModel }) => ({
        min: 0,
        max: 10,
        disabled: formModel[`${prefix}FailureMode`] !== 'retry',
      }),
    },
  ];

  const flowSchemas: FormSchema[] = [
    ...createFlowGroup('asr', 'ASR '),
    ...createFlowGroup('ocr', 'OCR '),
    ...createFlowGroup('video', '视频 '),
    ...createFlowGroup('summary', '摘要 '),
    ...createFlowGroup('integrate', '整合 '),
  ];

  const formConfig = { labelWidth: 150, showActionButtonGroup: false, baseColProps: { span: 24 } };

  const [registerServiceForm, { setFieldsValue: setServiceValues, validate: validateService }] = useForm({
    ...formConfig,
    schemas: serviceSchemas,
  });

  const [registerFlowForm, { setFieldsValue: setFlowValues, validate: validateFlow }] = useForm({
    ...formConfig,
    schemas: flowSchemas,
  });

  function syncPromptState(data: AiConfigRecord) {
    promptState.integratePromptKey = data.integratePromptKey ?? '';
    promptState.summaryPromptKey = data.summaryPromptKey ?? '';
    promptState.keywordsPromptKey = data.keywordsPromptKey ?? '';
    promptState.maxSummaryLength = data.maxSummaryLength ?? 100;
    promptState.maxKeywordsCount = data.maxKeywordsCount ?? 5;
    // 同步编辑器状态
    for (const f of ['integratePromptKey', 'summaryPromptKey', 'keywordsPromptKey'] as const) {
      const key = promptState[f];
      editingPrompt[f] = key ? (allPrompts.value.find((p) => p.promptKey === key) ?? null) : null;
      if (editingPrompt[f]) editingPrompt[f] = { ...editingPrompt[f]! };
    }
  }

  onMounted(async () => {
    await loadPrompts();
    try {
      const data = await getAiConfig();
      configRecord.value = data;
      await Promise.all([setServiceValues(data), setFlowValues(data)]);
      syncPromptState(data);
    } catch {
      // 加载失败保持空态
    }
  });

  async function handleSave() {
    tabErrors.service = false;
    tabErrors.prompt = false;
    tabErrors.flow = false;
    saving.value = true;

    let serviceValues: Partial<AiConfigRecord> | undefined;
    let flowValues: Partial<AiConfigRecord> | undefined;
    let hasError = false;

    try {
      serviceValues = await validateService();
    } catch {
      tabErrors.service = true;
      hasError = true;
    }

    try {
      flowValues = await validateFlow();
    } catch {
      tabErrors.flow = true;
      hasError = true;
    }

    if (hasError) {
      saving.value = false;
      if (tabErrors.service) activeKey.value = 'service';
      else if (tabErrors.flow) activeKey.value = 'flow';
      createMessage.error('请检查表单填写是否正确');
      return;
    }

    try {
      const allValues: Partial<AiConfigRecord> = {
        ...serviceValues,
        ...flowValues,
        integratePromptKey: promptState.integratePromptKey || undefined,
        summaryPromptKey: promptState.summaryPromptKey || undefined,
        keywordsPromptKey: promptState.keywordsPromptKey || undefined,
        maxSummaryLength: promptState.maxSummaryLength,
        maxKeywordsCount: promptState.maxKeywordsCount,
      };
      await updateAiConfig({ ...(configRecord.value || {}), ...allValues });
      notification.success({ message: '配置已保存' });
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      if (err?.response?.data?.message) {
        createMessage.error(`保存失败: ${err.response.data.message}`);
      } else {
        createMessage.error('保存流水线配置失败');
      }
    } finally {
      saving.value = false;
    }
  }

  async function handleRefreshCache() {
    try {
      const data = await refreshAiConfigCache();
      configRecord.value = data;
      await loadPrompts();
      await Promise.all([setServiceValues(data), setFlowValues(data)]);
      syncPromptState(data);
      notification.success({ message: '缓存已刷新' });
    } catch {
      notification.error({ message: '刷新缓存失败' });
    }
  }
</script>

<style scoped lang="less">
  .prompt-header {
    display: flex;
    align-items: flex-start;
    margin-bottom: 16px;
  }

  .var-code {
    background: #f5f5f5;
    border: 1px solid #e8e8e8;
    border-radius: 3px;
    padding: 1px 5px;
    font-size: 12px;
    color: #c41d7f;
  }

  .mt-12 {
    margin-top: 12px;
  }

  .prompt-config-wrap {
    padding: 8px 16px 80px;
  }

  .mb-16 {
    margin-bottom: 16px;
  }

  .mt-8 {
    margin-top: 8px;
  }

  .prompt-selector-item {
    margin-bottom: 20px;
  }

  .prompt-selector-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  .prompt-selector-label {
    font-size: 14px;
    color: #333;
    font-weight: 500;
    white-space: nowrap;
    min-width: 80px;
  }

  .prompt-editor-wrap {
    border: 1px solid #e8e8e8;
    border-radius: 6px;
    overflow: hidden;
  }

  .prompt-editor-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: #fafafa;
    border-bottom: 1px solid #e8e8e8;
    font-size: 13px;
  }

  .prompt-editor-name {
    font-weight: 500;
    color: #333;
  }
</style>
