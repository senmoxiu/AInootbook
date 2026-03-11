<template>
  <BasicDrawer v-bind="$attrs" @register="registerDrawer" :title="getTitle" :width="600" :showFooter="true" @ok="handleSubmit" destroyOnClose>
    <BasicForm @register="registerForm" />
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { ref, computed, unref } from 'vue';
  import { BasicForm, useForm } from '/@/components/Form/index';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { formSchema } from './selection.data';
  import { saveOrUpdateSelection } from '/@/api/ainote/selection.api';

  const emit = defineEmits(['success', 'register']);

  const isUpdate = ref(true);

  const [registerForm, { resetFields, setFieldsValue, validate }] = useForm({
    labelWidth: 100,
    schemas: formSchema,
    showActionButtonGroup: false,
  });

  const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
    await resetFields();
    setDrawerProps({ confirmLoading: false });
    isUpdate.value = !!data?.isUpdate;
    if (unref(isUpdate)) {
      await setFieldsValue({ ...data.record });
    }
  });

  const getTitle = computed(() => (!unref(isUpdate) ? '新增选课' : '编辑选课'));

  async function handleSubmit() {
    try {
      const values = await validate();
      setDrawerProps({ confirmLoading: true });
      await saveOrUpdateSelection(values, unref(isUpdate));
      closeDrawer();
      emit('success');
    } finally {
      setDrawerProps({ confirmLoading: false });
    }
  }
</script>
