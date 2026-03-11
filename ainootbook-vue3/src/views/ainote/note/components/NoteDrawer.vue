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
          :disabled="!currentCourseId"
          :dropdown-style="{ maxHeight: '300px', overflow: 'auto' }"
          :field-names="{ label: 'chapterName', value: 'id', children: 'children' }"
        />
      </template>
    </BasicForm>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { ref, computed, unref, watch } from 'vue';
  import { BasicForm, useForm } from '/@/components/Form/index';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { createFormSchema, editFormSchema } from '../note.data';
  import { saveOrUpdateNote } from '/@/api/ainote/note.api';
  import { getChapterTreeList } from '/@/api/ainote/chapter.api';

  const emit = defineEmits(['success', 'register']);

  const isUpdate = ref(true);
  const currentCourseId = ref('');
  const chapterTreeData = ref<any[]>([]);

  // 根据模式选择不同的表单 Schema
  const currentSchema = computed(() => (unref(isUpdate) ? editFormSchema : createFormSchema));

  const [registerForm, { resetFields, setFieldsValue, validate, updateSchema, getFieldsValue }] = useForm({
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

    // 动态切换表单 Schema
    await updateSchema(unref(currentSchema));

    if (unref(isUpdate)) {
      await setFieldsValue({
        ...data.record,
      });
    }
  });

  const getTitle = computed(() => (!unref(isUpdate) ? '新增笔记' : '编辑笔记'));

  // 监听课程选择变化，联动加载章节树
  watch(
    () => {
      try {
        const values = getFieldsValue();
        return values?.courseId;
      } catch {
        return undefined;
      }
    },
    async (courseId) => {
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
    },
    { deep: true }
  );

  async function handleSubmit() {
    try {
      const values = await validate();
      setDrawerProps({ confirmLoading: true });
      await saveOrUpdateNote(values, unref(isUpdate));
      closeDrawer();
      emit('success');
    } finally {
      setDrawerProps({ confirmLoading: false });
    }
  }
</script>
