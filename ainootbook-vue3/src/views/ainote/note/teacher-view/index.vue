<template>
  <div>
    <BasicTable @register="registerTable">
      <template #action="{ record }">
        <TableAction
          :actions="[
            {
              icon: 'ant-design:eye-outlined',
              tooltip: '查看详情',
              onClick: handleView.bind(null, record),
            },
          ]"
        />
      </template>
    </BasicTable>
  </div>
</template>

<script lang="ts" setup name="ainote-teacher-note">
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { teacherColumns, teacherSearchFormSchema } from '../note.data';
  import { getTeacherNoteList } from '/@/api/ainote/note.api';
  import { useRouter } from 'vue-router';

  const router = useRouter();

  const [registerTable] = useTable({
    title: '学生笔记列表',
    api: getTeacherNoteList,
    columns: teacherColumns,
    formConfig: {
      labelWidth: 120,
      schemas: teacherSearchFormSchema,
      autoSubmitOnEnter: true,
    },
    useSearchForm: true,
    showTableSetting: true,
    bordered: true,
    showIndexColumn: false,
    actionColumn: {
      width: 100,
      title: '操作',
      dataIndex: 'action',
      slots: { customRender: 'action' },
      fixed: 'right',
    },
  });

  function handleView(record: Recordable) {
    router.push(`/ainote/note/view/${record.id}`);
  }
</script>

<style lang="less" scoped>
</style>
