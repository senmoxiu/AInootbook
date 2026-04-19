<template>
  <a-card :bordered="false" class="h-full">
    <a-tabs v-model:activeKey="activeKey">
      <a-tab-pane key="available" tab="可选课程">
        <BasicTable @register="registerAvailableTable">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <TableAction :actions="getAvailableActions(record)" />
            </template>
          </template>
        </BasicTable>
      </a-tab-pane>
      <a-tab-pane key="mine" tab="我的选课">
        <BasicTable @register="registerMineTable">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <TableAction :actions="getMineActions(record)" />
            </template>
          </template>
        </BasicTable>
      </a-tab-pane>
    </a-tabs>
  </a-card>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { availableColumns, studentColumns, studentSearchSchema } from './selection.data';
  import { getAvailableTeachings, getSelectionList, addSelection, dropCourse } from '/@/api/ainote/selection.api';

  const activeKey = ref('available');
  const { createMessage } = useMessage();

  const [registerAvailableTable, { reload: reloadAvailable }] = useTable({
    title: '可选课程',
    api: getAvailableTeachings,
    columns: availableColumns,
    formConfig: {
      schemas: [
        { label: '课程名称', field: 'courseName', component: 'Input', colProps: { span: 6 } },
      ],
    },
    useSearchForm: true,
    showTableSetting: true,
    bordered: true,
    showIndexColumn: false,
    rowKey: 'teachingId',
    actionColumn: { width: 100, title: '操作', dataIndex: 'action' },
  });

  const [registerMineTable, { reload: reloadMine }] = useTable({
    title: '我的选课',
    api: getSelectionList,
    columns: studentColumns,
    formConfig: { schemas: studentSearchSchema },
    useSearchForm: true,
    showTableSetting: true,
    bordered: true,
    showIndexColumn: false,
    rowKey: 'id',
    actionColumn: { width: 100, title: '操作', dataIndex: 'action' },
  });

  function getAvailableActions(record: Recordable) {
    return [
      {
        label: '选课',
        icon: 'ant-design:plus-outlined',
        color: 'success',
        popConfirm: {
          title: `确定选修「${record.courseName}」？`,
          confirm: async () => {
            await addSelection({ teachingId: record.teachingId });
            createMessage.success('选课成功');
            await reloadAvailable();
            await reloadMine();
          },
        },
      },
    ];
  }

  function getMineActions(record: Recordable) {
    if (record.status !== 1) return [];
    return [
      {
        label: '退课',
        icon: 'ant-design:delete-outlined',
        color: 'error',
        popConfirm: {
          title: '确定退选这门课程吗？退课后将无法恢复',
          confirm: async () => {
            await dropCourse({ id: record.id });
            createMessage.success('退课成功');
            await reloadAvailable();
            await reloadMine();
          },
        },
      },
    ];
  }
</script>