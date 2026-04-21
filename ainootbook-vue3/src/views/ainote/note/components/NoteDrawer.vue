<template>
  <BasicDrawer
    v-bind="$attrs"
    @register="registerDrawer"
    :title="getTitle"
    :width="isUpdate ? 900 : 600"
    :showFooter="true"
    @ok="handleSubmit"
    destroyOnClose
  >
    <BasicForm @register="registerForm">
      <!-- 章节选择插槽（新增模式时可用） -->
      <template #chapterSelect="{ model, field }">
        <a-tree-select
          v-model:value="model[field]"
          :tree-data="chapterTreeData"
          placeholder="请先选择课程，再选择章节（可选）"
          allow-clear
          :disabled="isUpdate || !currentCourseId"
          :dropdown-style="{ maxHeight: '300px', overflow: 'auto' }"
          :field-names="{ label: 'chapterName', value: 'id', children: 'children' }"
        />
      </template>
    </BasicForm>

    <template #footer>
      <a-button v-if="isUpdate && canRegenerate" @click="handleRegenerate" :loading="isRegenerating">
        AI 重新生成
      </a-button>
      <a-button @click="closeDrawer">取消</a-button>
      <a-button type="primary" @click="handleSubmit" :loading="isSubmitting">确定</a-button>
    </template>

    <a-modal
      v-model:open="regenerateModalVisible"
      title="AI 重新生成进度"
      :footer="null"
      :closable="false"
      :maskClosable="false"
    >
      <AiRegenerateProgress
        v-if="regenerateModalVisible"
        :note-id="currentNoteId"
        :knowledge-id="currentKnowledgeId"
        @completed="handleRegenerateCompleted"
        @failed="handleRegenerateFailed"
      />
    </a-modal>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { ref, computed, unref, nextTick } from 'vue';
  import { BasicForm, useForm } from '/@/components/Form/index';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { createFormSchema, editFormSchema } from '../note.data';
  import { saveOrUpdateNote, getNoteById, triggerRegeneration } from '/@/api/ainote/note.api';
  import { getChapterTreeList } from '/@/api/ainote/chapter.api';
  import { useMessage } from '/@/hooks/web/useMessage';
  import AiRegenerateProgress from './AiRegenerateProgress.vue';

  const emit = defineEmits(['success', 'register']);
  const { createMessage } = useMessage();

  const isUpdate = ref(true);
  const currentCourseId = ref('');
  const chapterTreeData = ref<any[]>([]);
  const isSubmitting = ref(false);
  const isRegenerating = ref(false);
  const regenerateModalVisible = ref(false);
  const currentNoteId = ref('');
  const currentKnowledgeId = ref('');
  const canRegenerate = ref(false);

  // 根据模式选择不同的表单 Schema
  const currentSchema = computed(() => (unref(isUpdate) ? editFormSchema : createFormSchema));

  const [registerForm, { resetFields, setFieldsValue, validate, resetSchema, updateSchema, getFieldsValue }] = useForm({
    labelWidth: 100,
    schemas: createFormSchema,
    showActionButtonGroup: false,
  });

  const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
    await resetFields();
    setDrawerProps({ confirmLoading: false });
    isUpdate.value = !!data?.isUpdate;
    chapterTreeData.value = [];
    currentCourseId.value = '';
    canRegenerate.value = false;
    currentNoteId.value = '';
    currentKnowledgeId.value = '';

    // 动态切换表单 Schema（必须用 resetSchema 完整替换，updateSchema 只合并已有字段）
    await resetSchema(unref(currentSchema));

    // 注入课程字段的 onChange 回调（新增模式）
    if (!unref(isUpdate)) {
      await updateSchema({
        field: 'courseId',
        componentProps: {
          onChange: handleCourseChange,
        },
      });
    }

    if (unref(isUpdate)) {
      // 编辑模式：通过 queryById 获取完整数据（列表 VO 不含 noteContent 等大字段）
      const recordId = data.record?.id;
      if (recordId) {
        try {
          const fullRecord = await getNoteById({ id: recordId });
          await nextTick();
          await setFieldsValue({
            ...fullRecord,
          });
          currentNoteId.value = fullRecord.id || '';
          currentKnowledgeId.value = fullRecord.knowledgeId || '';
          canRegenerate.value = !!(fullRecord.knowledgeId && fullRecord.id);
        } catch {
          // 降级：使用列表传入的 record
          await nextTick();
          await setFieldsValue({
            ...data.record,
          });
          currentNoteId.value = data.record?.id || '';
          currentKnowledgeId.value = data.record?.knowledgeId || '';
          canRegenerate.value = !!(data.record?.knowledgeId && data.record?.id);
        }
      }
    }
  });

  const getTitle = computed(() => (!unref(isUpdate) ? '新增笔记' : '编辑笔记'));

  // 课程选择变化 → 联动加载章节树
  async function handleCourseChange(courseId: string) {
    if (courseId && courseId !== currentCourseId.value) {
      currentCourseId.value = courseId;
      try {
        const tree = await getChapterTreeList({ courseId });
        chapterTreeData.value = tree || [];
      } catch {
        chapterTreeData.value = [];
      }
    } else if (!courseId) {
      currentCourseId.value = '';
      chapterTreeData.value = [];
    }
  }

  async function handleSubmit() {
    try {
      const values = await validate();
      isSubmitting.value = true;
      setDrawerProps({ confirmLoading: true });
      await saveOrUpdateNote(values, unref(isUpdate));
      closeDrawer();
      emit('success');
    } finally {
      isSubmitting.value = false;
      setDrawerProps({ confirmLoading: false });
    }
  }

  async function handleRegenerate() {
    if (!currentNoteId.value || !currentKnowledgeId.value) {
      createMessage.warning('缺少必要参数，无法重新生成');
      return;
    }

    try {
      isRegenerating.value = true;
      await triggerRegeneration({
        noteId: currentNoteId.value,
        knowledgeId: currentKnowledgeId.value,
      });
      regenerateModalVisible.value = true;
    } catch (error: unknown) {
      createMessage.error((error as Error).message || '触发重新生成失败');
    } finally {
      isRegenerating.value = false;
    }
  }

  async function handleRegenerateCompleted() {
    regenerateModalVisible.value = false;
    createMessage.success('AI 重新生成完成');
    emit('success');
    closeDrawer();
  }

  function handleRegenerateFailed(errorMsg: string) {
    regenerateModalVisible.value = false;
    createMessage.error(errorMsg || 'AI 重新生成失败');
  }
</script>
