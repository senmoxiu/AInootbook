<template>
  <BasicDrawer v-bind="$attrs" @register="registerDrawer" :title="getTitle" :width="600" :showFooter="true" @ok="handleSubmit" destroyOnClose>
    <BasicForm @register="registerForm">
      <template #departSelect="{ model, field }">
        <DepartTreeSelect
          v-model:value="model[field]"
          placeholder="请选择组织（学院/专业）"
          :multiple="false"
          :orgTypes="['2', '3', '4']"
        />
      </template>
    </BasicForm>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { ref, computed, unref } from 'vue';
  import { BasicForm, useForm } from '/@/components/Form/index';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { formSchema } from './teaching.data';
  import { saveOrUpdateTeaching } from '/@/api/ainote/teaching.api';
  import DepartTreeSelect from '../components/DepartTreeSelect.vue';

  // 声明 Emits
  const emit = defineEmits(['success', 'register']);

  const isUpdate = ref(true);

  // 注册表单
  const [registerForm, { resetFields, setFieldsValue, validate }] = useForm({
    labelWidth: 100,
    schemas: formSchema,
    showActionButtonGroup: false,
  });

  // 注册抽屉
  const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
    await resetFields();
    setDrawerProps({ confirmLoading: false });
    isUpdate.value = !!data?.isUpdate;

    if (unref(isUpdate)) {
      await setFieldsValue({
        ...data.record,
      });
    }
  });

  // 标题
  const getTitle = computed(() => (!unref(isUpdate) ? '新增教学任务' : '编辑教学任务'));

  // 提交事件
  async function handleSubmit() {
    try {
      const values = await validate();
      setDrawerProps({ confirmLoading: true });
      // 提交表单
      await saveOrUpdateTeaching(values, unref(isUpdate));
      // 关闭抽屉
      closeDrawer();
      // 刷新列表
      emit('success');
    } finally {
      setDrawerProps({ confirmLoading: false });
    }
  }
</script>
