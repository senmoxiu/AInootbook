<template>
  <Choose v-if="isStudent" />
  <div v-else>
    <BasicTable @register="registerTable">
      <template #toolbar>
        <a-button type="primary" @click="handleCreate" preIcon="ant-design:plus-outlined"> 新增选课 </a-button>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'studentCount'">
          <a @click="showStudents(record)">{{ record.studentCount }} 人</a>
        </template>
        <template v-if="column.key === 'action'">
          <TableAction :actions="getActions(record)" />
        </template>
      </template>
    </BasicTable>

    <!-- 学生明细抽屉 -->
    <a-drawer
      v-model:open="drawerVisible"
      :title="`「${currentCourse}」选课学生`"
      width="700"
      :body-style="{ padding: '16px' }"
    >
      <a-table
        :dataSource="studentList"
        :columns="studentDetailColumns"
        :loading="studentLoading"
        :pagination="false"
        size="small"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-popconfirm
              title="确认让该学生退课？"
              @confirm="handleDropStudent(record.id)"
            >
              <a style="color: red">退课</a>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-drawer>

    <SelectionDrawer @register="registerDrawer" @success="handleSuccess" />
  </div>
</template>

<script lang="ts" setup>
  import { computed, ref } from 'vue';
  import Choose from './choose.vue';
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { useDrawer } from '/@/components/Drawer';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { groupedColumns, groupedSearchSchema, studentDetailColumns } from './selection.data';
  import { getGroupedSelectionList, getStudentsByTeaching, clearSelectionByTeaching, deleteSelection } from '/@/api/ainote/selection.api';
  import SelectionDrawer from './SelectionDrawer.vue';

  const { createMessage, createConfirm } = useMessage();

  const { hasPermission } = usePermission();
  const isStudent = computed(() => !hasPermission('teaching:selection:edit'));

  const [registerTable, { reload }] = useTable({
    title: '选课管理',
    api: getGroupedSelectionList,
    columns: groupedColumns,
    formConfig: { schemas: groupedSearchSchema },
    useSearchForm: true,
    showTableSetting: true,
    bordered: true,
    showIndexColumn: false,
    actionColumn: { width: 100, title: '操作', dataIndex: 'action', fixed: 'right' },
  });

  const [registerDrawer, { openDrawer }] = useDrawer();

  // 学生明细抽屉
  const drawerVisible = ref(false);
  const currentCourse = ref('');
  const currentTeachingId = ref('');
  const studentList = ref<Recordable[]>([]);
  const studentLoading = ref(false);

  async function showStudents(record: Recordable) {
    currentCourse.value = record.courseName;
    currentTeachingId.value = record.teachingId;
    drawerVisible.value = true;
    studentLoading.value = true;
    try {
      studentList.value = await getStudentsByTeaching({ teachingId: record.teachingId });
    } finally {
      studentLoading.value = false;
    }
  }

  async function handleDropStudent(id: string) {
    await deleteSelection({ id }, () => {});
    studentList.value = studentList.value.filter((s) => s.id !== id);
    reload();
  }

  function getActions(record: Recordable) {
    return [
      {
        label: '查看学生',
        onClick: () => showStudents(record),
      },
      {
        label: '清空选课',
        color: 'error',
        popConfirm: {
          title: `确认清空「${record.courseName}」的所有选课记录？`,
          confirm: async () => {
            await clearSelectionByTeaching(record.teachingId);
            createMessage.success('已清空');
            reload();
            if (drawerVisible.value && currentTeachingId.value === record.teachingId) {
              studentList.value = [];
            }
          },
        },
      },
    ];
  }

  function handleCreate() {
    openDrawer(true, { isUpdate: false });
  }

  function handleSuccess() {
    reload();
  }
</script>
