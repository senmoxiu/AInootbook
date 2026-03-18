<template>
  <div>
    <BasicTable @register="registerTable">
      <template #toolbar>
        <a-button type="primary" @click="handleCreate" preIcon="ant-design:plus-outlined">新增</a-button>
        <a-button type="primary" @click="batchHandleDelete" preIcon="ant-design:delete-outlined">批量删除</a-button>
        <a-button type="primary" @click="onExportXls" preIcon="ant-design:download-outlined">导出</a-button>
        <a-upload name="file" :showUploadList="false" :customRequest="onImportXls">
          <a-button type="primary" preIcon="ant-design:upload-outlined">导入</a-button>
        </a-upload>
      </template>
      <template #action="{ record }">
        <TableAction
          :actions="[
            {
              label: '编辑',
              onClick: handleEdit.bind(null, record),
            },
            {
              label: '章节管理',
              onClick: handleChapterManage.bind(null, record),
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
    </BasicTable>
    <CourseDrawer @register="registerDrawer" @success="handleSuccess" />
    <!-- 章节管理抽屉 -->
    <ChapterDrawer @register="registerChapterDrawer" />
  </div>
</template>

<script lang="ts" setup>
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { useDrawer } from '/@/components/Drawer';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { columns, searchFormSchema } from './course.data';
  import { getCourseList, deleteCourse, batchDeleteCourse, exportCourseXls, importCourseExcel } from '/@/api/ainote/course.api';
  import CourseDrawer from './CourseDrawer.vue';
  import ChapterDrawer from '../chapter/ChapterDrawer.vue';

  // 列表页面公共参数、方法
  const { prefixCls, tableContext, onExportXls, onImportXls } = useListPage({
    tableProps: {
      title: '课程库列表',
      api: getCourseList,
      columns: columns,
      formConfig: {
        schemas: searchFormSchema,
      },
      actionColumn: {
        width: 220,
        title: '操作',
        dataIndex: 'action',
        fixed: 'right',
      },
    },
    exportConfig: {
      name: '课程库列表',
      url: '/ainote/course/exportXls',
    },
    importConfig: {
      url: '/ainote/course/importExcel',
    },
  });

  // 注册 table
  const [registerTable, { reload }, { rowSelection, selectedRowKeys }] = tableContext;

  // 注册课程抽屉
  const [registerDrawer, { openDrawer }] = useDrawer();

  // 注册章节管理抽屉
  const [registerChapterDrawer, { openDrawer: openChapterDrawer }] = useDrawer();

  /**
   * 新增事件
   */
  function handleCreate() {
    openDrawer(true, {
      isUpdate: false,
    });
  }

  /**
   * 编辑事件
   */
  function handleEdit(record) {
    openDrawer(true, {
      record,
      isUpdate: true,
    });
  }

  /**
   * 删除事件
   */
  async function handleDelete(record) {
    await deleteCourse({ id: record.id }, reload);
  }

  /**
   * 批量删除事件
   */
  async function batchHandleDelete() {
    await batchDeleteCourse({ ids: selectedRowKeys.value }, () => {
      selectedRowKeys.value = [];
      reload();
    });
  }

  /**
   * 章节管理事件
   */
  function handleChapterManage(record) {
    openChapterDrawer(true, {
      courseId: record.id,
      courseName: record.courseName,
    });
  }

  /**
   * 成功回调
   */
  function handleSuccess() {
    reload();
  }
</script>
