<template>
  <BasicDrawer
    v-bind="$attrs"
    @register="registerDrawer"
    title="批量配置教学任务"
    :width="600"
    @ok="handleSubmit"
    destroyOnClose
  >
    <BasicForm @register="registerForm">
      <template #departTreeSelect="{ model, field }">
        <DepartTreeSelect
          v-model:value="model[field]"
          placeholder="请选择组织（可多选）"
          :multiple="true"
        />
      </template>
    </BasicForm>

    <!-- 失败明细弹窗 -->
    <a-modal
      v-model:open="failedModalVisible"
      title="批量配置结果"
      :footer="null"
      width="600px"
    >
      <a-alert
        :message="`成功：${batchResult.successCount} 条，失败：${batchResult.failedList.length} 条`"
        type="info"
        show-icon
        style="margin-bottom: 16px"
      />
      <a-table
        v-if="batchResult.failedList.length > 0"
        :columns="failedColumns"
        :data-source="batchResult.failedList"
        :pagination="false"
        size="small"
      >
      </a-table>
    </a-modal>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { ref, reactive } from 'vue';
  import { BasicForm, useForm } from '/@/components/Form/index';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { batchConfigFormSchema } from './teaching.data';
  import { batchUpsertTeaching } from '/@/api/ainote/teaching.api';
  import DepartTreeSelect from '../components/DepartTreeSelect.vue';
  import { message } from 'ant-design-vue';

  // 声明 Emits
  const emit = defineEmits(['success', 'register']);

  const failedModalVisible = ref(false);
  const batchResult = reactive({
    successCount: 0,
    failedList: [] as any[],
  });

  // 失败明细表格列定义
  const failedColumns = [
    {
      title: '组织 ID',
      dataIndex: 'departId',
      width: 200,
    },
    {
      title: '失败原因',
      dataIndex: 'reason',
    },
  ];

  // 注册表单
  const [registerForm, { resetFields, validate }] = useForm({
    labelWidth: 100,
    schemas: batchConfigFormSchema,
    showActionButtonGroup: false,
  });

  // 注册抽屉
  const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async () => {
    await resetFields();
    setDrawerProps({ confirmLoading: false });
  });

  // 提交事件
  async function handleSubmit() {
    try {
      const values = await validate();
      setDrawerProps({ confirmLoading: true });

      // 调用批量配置接口
      const result = await batchUpsertTeaching(values);

      // 处理返回结果
      if (result) {
        batchResult.successCount = result.successCount || 0;
        batchResult.failedList = result.failedList || [];

        if (batchResult.failedList.length > 0) {
          // 有失败记录，显示明细弹窗
          failedModalVisible.value = true;
          message.warning(`批量配置完成，成功 ${batchResult.successCount} 条，失败 ${batchResult.failedList.length} 条`);
        } else {
          // 全部成功
          message.success(`批量配置成功，共 ${batchResult.successCount} 条`);
          closeDrawer();
        }

        // 刷新列表
        emit('success');
      }
    } finally {
      setDrawerProps({ confirmLoading: false });
    }
  }
</script>
