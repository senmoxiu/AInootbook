<template>
  <PageWrapper title="AI 智能配置">
    <a-card :bordered="false">
      <BasicForm @register="registerForm" />
      <div class="flex justify-center mt-4 gap-3">
        <a-button type="primary" @click="handleSave">保存配置</a-button>
        <a-button @click="handleRefreshCache">刷新缓存</a-button>
      </div>
    </a-card>
  </PageWrapper>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue';
  import { PageWrapper } from '/@/components/Page';
  import { BasicForm, useForm } from '/@/components/Form/index';
  import type { FormSchema } from '/@/components/Form/index';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { defHttp } from '/@/utils/http/axios';
  import { getAiConfig, updateAiConfig, refreshAiConfigCache } from '/@/api/ainote/aiConfig.api';
  import type { AiConfigRecord } from '/@/api/ainote/aiConfig.api';

  const { notification } = useMessage();

  const configRecord = ref<AiConfigRecord | null>(null);

  const schemas: FormSchema[] = [
    // ── 大模型与知识库 ──────────────────────────────────
    {
      field: 'summaryModelId',
      label: '摘要大模型',
      component: 'ApiSelect',
      colProps: { span: 12 },
      componentProps: {
        api: () =>
          defHttp.get({ url: '/airag/airagModel/list', params: { modelType: 'LLM', activateFlag: 1, pageSize: 50 } }),
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
        api: () =>
          defHttp.get({ url: '/airag/airagModel/list', params: { modelType: 'llm', activateFlag: 1, pageSize: 50 } }),
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
        api: () =>
          defHttp.get({ url: '/airag/knowledge/list', params: { type: 'knowledge', status: 'enable', pageSize: 50 } }),
        resultField: 'records',
        labelField: 'name',
        valueField: 'id',
      },
    },
    // ── 生成策略 ────────────────────────────────────────
    {
      field: 'summaryPromptKey',
      label: '摘要 Prompt Key',
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
    // ── 高级工作流 ──────────────────────────────────────
    {
      field: 'summaryFlowEnabled',
      label: '启用工作流',
      component: 'Switch',
      colProps: { span: 12 },
      componentProps: {
        checkedValue: 1,
        unCheckedValue: 0,
      },
    },
    {
      field: 'summaryFlowId',
      label: '工作流 ID',
      component: 'Input',
      colProps: { span: 12 },
      ifShow: ({ values }) => values.summaryFlowEnabled === 1,
    },
  ];

  const [registerForm, { setFieldsValue, validate }] = useForm({
    labelWidth: 130,
    schemas,
    showActionButtonGroup: false,
    baseColProps: { span: 24 },
  });

  onMounted(async () => {
    try {
      const data = await getAiConfig();
      configRecord.value = data;
      await setFieldsValue(data);
    } catch {
      // 加载失败时保持表单空态，用户可手动刷新
    }
  });

  async function handleSave() {
    try {
      const values = await validate();
      if (!configRecord.value) return;
      await updateAiConfig({ ...configRecord.value, ...values });
      notification.success({ message: '配置已保存' });
    } catch {
      // validate 校验失败时 BasicForm 已内联提示
    }
  }

  async function handleRefreshCache() {
    try {
      const data = await refreshAiConfigCache();
      configRecord.value = data;
      await setFieldsValue(data);
      notification.success({ message: '缓存已刷新' });
    } catch {
      notification.error({ message: '刷新缓存失败' });
    }
  }
</script>
