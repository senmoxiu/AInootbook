<template>
  <BasicDrawer v-bind="$attrs" @register="registerDrawer" :title="getTitle" :width="600" :showFooter="true" @ok="handleSubmit" destroyOnClose>
    <BasicForm @register="registerForm" />
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { ref, computed, unref } from 'vue';
  import { BasicForm, useForm } from '/@/components/Form/index';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { formSchema } from './chapter.data';
  import { saveOrUpdateChapter, getChapterTreeList } from '/@/api/ainote/chapter.api';

  // 声明 Emits
  const emit = defineEmits(['success', 'register']);

  const isUpdate = ref(false);

  // 注册表单
  const [registerForm, { resetFields, setFieldsValue, validate, updateSchema }] = useForm({
    labelWidth: 100,
    schemas: formSchema,
    showActionButtonGroup: false,
  });

  // 注册抽屉，接收 { record, isUpdate, courseId }
  const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
    await resetFields();
    setDrawerProps({ confirmLoading: false });
    isUpdate.value = !!data?.isUpdate;

    const courseId = data?.isUpdate ? data.record?.courseId : (data?.courseId ?? '');

    if (unref(isUpdate)) {
      // 编辑模式：回填记录数据
      await setFieldsValue({ ...data.record });
    } else {
      // 新增模式：自动填充 courseId 隐藏字段
      await setFieldsValue({ courseId });
    }

    // courseId 填充后刷新父级节点选择器的 api，使其能加载当前课程的章节树
    await updateSchema({
      field: 'parentId',
      componentProps: {
        api: () => {
          if (!courseId) return Promise.resolve([]);
          return getChapterTreeList({ courseId });
        },
        fieldNames: { label: 'chapterName', value: 'id', children: 'children' },
        treeDefaultExpandAll: true,
        placeholder: '请选择父级节点（不选则为根节点）',
        allowClear: true,
      },
    });
  });

  // 抽屉标题
  const getTitle = computed(() => (!unref(isUpdate) ? '新增章节' : '编辑章节'));

  /**
   * 提交表单
   */
  async function handleSubmit() {
    try {
      const values = await validate();
      setDrawerProps({ confirmLoading: true });
      // 调用保存或更新接口
      await saveOrUpdateChapter(values, unref(isUpdate));
      // 关闭抽屉
      closeDrawer();
      // 通知父组件刷新
      emit('success');
    } finally {
      setDrawerProps({ confirmLoading: false });
    }
  }
</script>
