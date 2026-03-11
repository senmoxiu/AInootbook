<template>
  <div>
    <BasicTable @register="registerTable">
      <template #toolbar>
        <!-- 教师/管理员才能新增和批量删除 -->
        <a-button v-if="!isStudent" type="primary" @click="handleCreate" preIcon="ant-design:plus-outlined">
          新增选课
        </a-button>
        <a-button v-if="!isStudent" type="primary" danger @click="batchHandleDelete" preIcon="ant-design:delete-outlined">
          批量删除
        </a-button>
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
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { useDrawer } from '/@/components/Drawer';
  import { useUserStore } from '/@/store/modules/user';
  import { studentColumns, teacherColumns, studentSearchSchema, teacherSearchSchema } from './selection.data';
  import { getSelectionList, deleteSelection, batchDeleteSelection, dropCourse } from '/@/api/ainote/selection.api';
  import SelectionDrawer from './SelectionDrawer.vue';
  import { useMessage } from '/@/hooks/web/useMessage';

  const { createMessage } = useMessage();
  const userStore = useUserStore();

  // 解析当前用户角色集合
  const roleCode = computed(() => userStore.getUserInfo?.roleCode || '');
  const roles = computed(() => new Set((roleCode.value || '').split(',')));

  // 学生：有 student 角色且无 admin/teacher 角色
  const isStudent = computed(
    () => roles.value.has('student') && !roles.value.has('admin') && !roles.value.has('teacher'),
  );

  // 根据角色动态切换列和搜索表单
  const dynamicColumns = computed(() => (isStudent.value ? studentColumns : teacherColumns));
  const dynamicSearch = computed(() => (isStudent.value ? studentSearchSchema : teacherSearchSchema));

  const [registerTable, { reload, getSelectRowKeys, setSelectedRowKeys }] = useTable({
    title: '选课管理',
    api: getSelectionList,
    columns: dynamicColumns.value,
    formConfig: { schemas: dynamicSearch.value },
    useSearchForm: true,
    showTableSetting: true,
    bordered: true,
    showIndexColumn: false,
    rowSelection: { type: 'checkbox' },
    actionColumn: { width: 150, title: '操作', dataIndex: 'action', fixed: 'right' },
  });

  const [registerDrawer, { openDrawer }] = useDrawer();

  /** 根据角色和记录状态生成操作按钮 */
  function getActions(record: Recordable) {
    if (isStudent.value) {
      // 学生：仅在正常状态下显示退课按钮
      if (record.status !== 1) return [];
      return [
        {
          label: '退课',
          color: 'error',
          popConfirm: {
            title: '确认退课？退课后将无法恢复',
            confirm: () => handleDropCourse(record),
            okButtonProps: { danger: true },
          },
        },
      ];
    }
    // 教师/管理员：编辑 + 删除
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

  async function handleDropCourse(record: Recordable) {
    await dropCourse({ id: record.id });
    createMessage.success('退课成功');
    reload();
  }

  function handleSuccess() {
    reload();
  }
</script>
