<template>
  <BasicDrawer
    v-bind="$attrs"
    @register="registerDrawer"
    :title="getTitle"
    :width="800"
    :showFooter="false"
    destroyOnClose
  >
    <BasicTable @register="registerTable">
      <template #toolbar>
        <a-button type="primary" @click="handleCreate" preIcon="ant-design:plus-outlined">
          新增章节
        </a-button>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <TableAction
            :actions="[
              {
                label: '编辑',
                onClick: handleEdit.bind(null, record),
              },
              {
                label: '删除',
                color: 'error',
                popConfirm: {
                  title: '是否确认删除',
                  confirm: handleDelete.bind(null, record),
                },
              },
            ]"
          />
        </template>
      </template>
    </BasicTable>
    <!-- 章节表单子抽屉 -->
    <ChapterFormDrawer @register="registerFormDrawer" @success="handleSuccess" />
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { ref, computed, nextTick } from 'vue';
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { BasicDrawer, useDrawer, useDrawerInner } from '/@/components/Drawer';
  import { columns } from './chapter.data';
  import { getChapterTreeList, deleteChapter, type ChapterRecord } from '/@/api/ainote/chapter.api';
  import ChapterFormDrawer from './ChapterFormDrawer.vue';

  // 声明 Emits
  const emit = defineEmits(['register']);

  // 当前课程 ID
  const courseId = ref<string>('');
  // 当前课程名称（用于标题显示）
  const courseName = ref<string>('');

  // 注册章节表单子抽屉
  const [registerFormDrawer, { openDrawer: openFormDrawer }] = useDrawer();

  // 注册树形表格（无分页，直接展示树结构）
  const [registerTable, { reload }] = useTable({
    api: getChapterTreeList,
    searchInfo: { courseId },
    columns,
    isTreeTable: true,
    pagination: false,
    immediate: false,
    showIndexColumn: false,
    canResize: false,
    actionColumn: {
      width: 120,
      title: '操作',
      dataIndex: 'action',
      fixed: 'right',
    },
  });

  // 注册抽屉，接收 courseId 和 courseName
  const [registerDrawer] = useDrawerInner(async (data) => {
    courseId.value = data?.courseId ?? '';
    courseName.value = data?.courseName ?? '';
    // 等待 DOM 更新后再加载数据，避免表格实例未挂载
    await nextTick();
    reload();
  });

  // 抽屉标题
  const getTitle = computed(() =>
    courseName.value ? `章节管理 - ${courseName.value}` : '章节管理'
  );

  /**
   * 新增章节
   */
  function handleCreate() {
    openFormDrawer(true, {
      isUpdate: false,
      courseId: courseId.value,
    });
  }

  /**
   * 编辑章节
   */
  function handleEdit(record: ChapterRecord) {
    openFormDrawer(true, {
      record,
      isUpdate: true,
      courseId: courseId.value,
    });
  }

  /**
   * 删除章节
   */
  async function handleDelete(record: ChapterRecord) {
    await deleteChapter({ id: record.id }, reload);
  }

  /**
   * 子抽屉成功回调，刷新树形表格
   */
  function handleSuccess() {
    reload();
  }
</script>
