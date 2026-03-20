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
          <BasicForm @register="registerPromptForm" />
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
  import { onMounted, ref, reactive } from 'vue';
  import { PageWrapper } from '/@/components/Page';
  import { BasicForm, useForm } from '/@/components/Form/index';
  import type { FormSchema } from '/@/components/Form/index';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { defHttp } from '/@/utils/http/axios';
  import { getAiConfig, updateAiConfig, refreshAiConfigCache } from '/@/api/ainote/aiConfig.api';
  import type { AiConfigRecord } from '/@/api/ainote/aiConfig.api';

  const { notification, createMessage } = useMessage();
  const activeKey = ref('service');
  const saving = ref(false);
  const tabErrors = reactive({
    service: false,
    prompt: false,
    flow: false,
  });

  const configRecord = ref<AiConfigRecord | null>(null);

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

  // Tab 2: 提示词配置
  const promptSchemas: FormSchema[] = [
    {
      field: 'integratePromptKey',
      label: '整合 Prompt Key',
      component: 'Input',
      colProps: { span: 24 },
    },
    {
      field: 'summaryPromptKey',
      label: '摘要 Prompt Key',
      component: 'Input',
      colProps: { span: 24 },
    },
    {
      field: 'keywordsPromptKey',
      label: '关键词 Prompt Key',
      component: 'Input',
      colProps: { span: 24 },
    },
    {
      field: 'maxSummaryLength',
      label: '最大摘要长度',
      component: 'InputNumber',
      required: true,
      colProps: { span: 12 },
      componentProps: { min: 1, max: 200 },
    },
    {
      field: 'maxKeywordsCount',
      label: '最大关键词数',
      component: 'InputNumber',
      required: true,
      colProps: { span: 12 },
      componentProps: { min: 1, max: 5 },
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
      componentProps: {
        options: flowOptions,
      },
    },
    {
      field: `${prefix}RetryLimit`,
      label: `${labelPrefix}重试上限`,
      component: 'InputNumber',
      colProps: { span: 12 },
      defaultValue: 0,
      componentProps: ({ formModel }) => {
        return {
          min: 0,
          max: 10,
          disabled: formModel[`${prefix}FailureMode`] !== 'retry',
        };
      },
    },
  ];

  const flowSchemas: FormSchema[] = [
    ...createFlowGroup('asr', 'ASR '),
    ...createFlowGroup('ocr', 'OCR '),
    ...createFlowGroup('video', '视频 '),
    ...createFlowGroup('summary', '摘要 '),
    ...createFlowGroup('integrate', '整合 '),
  ];

  const formConfig = {
    labelWidth: 150,
    showActionButtonGroup: false,
    baseColProps: { span: 24 },
  };

  const [registerServiceForm, { setFieldsValue: setServiceValues, validate: validateService }] = useForm({
    ...formConfig,
    schemas: serviceSchemas,
  });

  const [registerPromptForm, { setFieldsValue: setPromptValues, validate: validatePrompt }] = useForm({
    ...formConfig,
    schemas: promptSchemas,
  });

  const [registerFlowForm, { setFieldsValue: setFlowValues, validate: validateFlow }] = useForm({
    ...formConfig,
    schemas: flowSchemas,
  });

  onMounted(async () => {
    try {
      const data = await getAiConfig();
      configRecord.value = data;
      await Promise.all([setServiceValues(data), setPromptValues(data), setFlowValues(data)]);
    } catch {
      // 加载失败时保持表单空态，用户可手动刷新
    }
  });

  async function handleSave() {
    tabErrors.service = false;
    tabErrors.prompt = false;
    tabErrors.flow = false;
    saving.value = true;

    let serviceValues: Partial<AiConfigRecord> | undefined;
    let promptValues: Partial<AiConfigRecord> | undefined;
    let flowValues: Partial<AiConfigRecord> | undefined;
    let hasError = false;

    try {
      serviceValues = await validateService();
    } catch {
      tabErrors.service = true;
      hasError = true;
    }

    try {
      promptValues = await validatePrompt();
    } catch {
      tabErrors.prompt = true;
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
      else if (tabErrors.prompt) activeKey.value = 'prompt';
      else if (tabErrors.flow) activeKey.value = 'flow';

      createMessage.error('请检查表单填写是否正确');
      return;
    }

    try {
      const allValues = { ...serviceValues, ...promptValues, ...flowValues };
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
      await Promise.all([setServiceValues(data), setPromptValues(data), setFlowValues(data)]);
      notification.success({ message: '缓存已刷新' });
    } catch {
      notification.error({ message: '刷新缓存失败' });
    }
  }
</script>
