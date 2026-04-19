<template>
  <Choose v-if="isStudent" />
  <div v-else>
    <BasicTable @register="registerTable">
      <template #toolbar>
        <a-button type="primary" @click="handleCreate" preIcon="ant-design:plus-outlined"> 新增选课 </a-button>
        <a-button type="primary" danger @click="batchHandleDelete" preIcon="ant-design:delete-outlined"> 批量删除 </a-button>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <TableAction :actions="getActions(record)" />
        </template>
      </template>
    </BasicTable>
    <SelectionDrawer @register="registerDrawer" @success="handleSuccess" />
  </div>
</template>

<script lang="ts" setup>
  import { computed } from 'vue';
  import Choose from './choose.vue';
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { useDrawer } from '/@/components/Drawer';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { teacherColumns, teacherSearchSchema } from './selection.data';
  import { getSelectionList, deleteSelection, batchDeleteSelection } from '/@/api/ainote/selection.api';
  import SelectionDrawer from './SelectionDrawer.vue';
  import { useMessage } from '/@/hooks/web/useMessage';

  const { createMessage } = useMessage();
  const { hasPermission } = usePermission();

  // 有 edit 权限 = 管理员，无 edit 权限 = 学生
  const isStudent = computed(() => !hasPermission('teaching:selection:edit'));

  const [registerTable, { reload, getSelectRowKeys, setSelectedRowKeys }] = useTable({
    title: '选课管理',
    api: getSelectionList,
    columns: teacherColumns,
    formConfig: { schemas: teacherSearchSchema },
    useSearchForm: true,
    showTableSetting: true,
    bordered: true,
    showIndexColumn: false,
    rowSelection: { type: 'checkbox' },
    actionColumn: { width: 150, title: '操作', dataIndex: 'action', fixed: 'right' },
  });

  const [registerDrawer, { openDrawer }] = useDrawer();

  function getActions(record: Recordable) {
    return [
      { label: '编辑', onClick: () => handleEdit(record) },
      {
        label: '删除',
        color: 'error',
        popConfirm: {
          title: '是否确认删除',
          confirm: () => handleDelete(record),
        },
      },
    ];
  }

  function handleCreate() {
    openDrawer(true, { isUpdate: false });
  }

  function handleEdit(record: Recordable) {
    openDrawer(true, { record, isUpdate: true });
  }

  async function handleDelete(record: Recordable) {
    await deleteSelection({ id: record.id }, reload);
  }

  async function batchHandleDelete() {
    const keys = getSelectRowKeys();
    if (!keys || keys.length === 0) {
      createMessage.warning('请选择要删除的数据');
      return;
    }
    await batchDeleteSelection({ ids: keys.join(',') }, () => {
      setSelectedRowKeys([]);
      reload();
    });
  }

  function handleSuccess() {
    reload();
  }
</script>
