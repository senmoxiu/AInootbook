<template>
  <div>
    <div v-if="searchMode === 'smart'" class="smart-search-panel">
      <a-alert v-if="searchDisabled" type="warning" message="请先在AI配置页配置知识库" show-icon class="!mb-4" />
      <a-space>
        <RobotOutlined class="ai-icon" />
        <a-select v-model:value="searchScope" :options="scopeOptions" placeholder="搜索范围" style="width: 160px" />
        <a-input
          v-model:value="searchQuery"
          placeholder="输入语义搜索关键词"
          style="width: 300px"
          :disabled="searchDisabled"
          @press-enter="handleSmartSearch"
        />
        <a-button type="primary" :disabled="searchDisabled" @click="handleSmartSearch">搜索</a-button>
        <a-button @click="handleClearSmartSearch">重置</a-button>
      </a-space>
    </div>

    <BasicTable @register="registerTable">
      <template #toolbar>
        <a-radio-group v-model:value="searchMode" button-style="solid" class="!mr-2">
          <a-radio-button value="normal">普通</a-radio-button>
          <a-radio-button value="smart">智能</a-radio-button>
        </a-radio-group>
        <a-button v-if="!isTeacher" type="primary" @click="handleCreate" preIcon="ant-design:plus-outlined">新增</a-button>
        <a-button v-if="!isTeacher" type="primary" @click="batchHandleDelete" preIcon="ant-design:delete-outlined">批量删除</a-button>
        <a-button type="primary" @click="onExportXls" preIcon="ant-design:download-outlined">导出</a-button>
        <a-upload v-if="!isTeacher" name="file" :showUploadList="false" :customRequest="onImportXls">
          <a-button type="primary" preIcon="ant-design:upload-outlined">导入</a-button>
        </a-upload>
      </template>
      <template #action="{ record }">
        <TableAction
          v-if="searchMode === 'normal'"
          :actions="[
            {
              label: '编辑',
              onClick: handleEdit.bind(null, record),
            },
            {
              label: '详情',
              onClick: handleDetail.bind(null, record),
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
        <TableAction
          v-else
          :actions="[
            {
              label: '查看',
              onClick: handleSemanticDetail.bind(null, record),
            },
          ]"
        />
      </template>
    </BasicTable>
    <NoteDrawer @register="registerDrawer" @success="handleSuccess" />
  </div>
</template>

<script lang="ts" setup>
  import { ref, computed, watch } from 'vue';
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { useDrawer } from '/@/components/Drawer';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { useRouter } from 'vue-router';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { useUserStore } from '/@/store/modules/user';
  import { RobotOutlined } from '@ant-design/icons-vue';
  import { columns, searchFormSchema } from './note.data';
  import { getNoteList, deleteNote, batchDeleteNote, exportNoteXls, importNoteExcel, searchNotes, semanticSearchNotes } from '/@/api/ainote/note.api';
  import { getAiConfig } from '/@/api/ainote/aiConfig.api';
  import NoteDrawer from './components/NoteDrawer.vue';

  const router = useRouter();
  const { createMessage } = useMessage();
  const userStore = useUserStore();

  const searchMode = ref<'normal' | 'smart'>('normal');
  const searchQuery = ref('');
  const searchScope = ref('PERSONAL');
  const searchDisabled = ref(false);

  const roleCode = computed(() => userStore.getUserInfo?.roleCode || '');
  const isTeacher = computed(() => roleCode.value.includes('teacher'));

  const scopeOptions = computed(() => {
    const roles = roleCode.value.split(',');
    const opts = [
      { label: '个人笔记', value: 'PERSONAL' },
      { label: '公开笔记', value: 'PUBLIC' },
    ];
    if (roles.includes('teacher') || roles.includes('admin')) {
      opts.push({ label: '课程笔记', value: 'COURSE' });
    }
    if (roles.includes('admin')) {
      opts.push({ label: '所有笔记', value: 'ALL' });
    }
    return opts;
  });

  const semanticColumns = [
    { title: '笔记标题', dataIndex: 'noteTitle', width: 200 },
    { title: '摘要片段', dataIndex: 'snippet', ellipsis: true },
    {
      title: '相关度',
      dataIndex: 'score',
      width: 100,
      customRender: ({ text }) => (text !== null && text !== undefined ? `${(text * 100).toFixed(0)}%` : '-'),
    },
    { title: '操作', dataIndex: 'action', width: 100, fixed: 'right', flag: 'ACTION' },
  ];

  const { prefixCls, tableContext, onExportXls, onImportXls } = useListPage({
    tableProps: {
      title: '笔记列表',
      api: getNoteList,
      columns: columns,
      formConfig: {
        schemas: searchFormSchema,
      },
      actionColumn: {
        width: 200,
        title: '操作',
        dataIndex: 'action',
        fixed: 'right',
      },
    },
    exportConfig: {
      name: '笔记列表',
      url: '/ainote/note/exportXls',
    },
    importConfig: {
      url: '/ainote/note/importExcel',
    },
  });

  const [registerTable, { reload, setTableData, setColumns }, { rowSelection, selectedRowKeys }] = tableContext;

  const [registerDrawer, { openDrawer }] = useDrawer();

  watch(searchMode, async (newVal) => {
    searchQuery.value = '';
    if (newVal === 'smart') {
      setColumns(semanticColumns);
      setTableData([]);
      try {
        const config = await getAiConfig();
        searchDisabled.value = !config?.knowledgeId;
      } catch {
        searchDisabled.value = true;
      }
    } else {
      setColumns(columns);
      searchDisabled.value = false;
      reload();
    }
  });

  async function handleSmartSearch() {
    const q = searchQuery.value.trim();
    if (!q) {
      createMessage.warning('请输入搜索关键词');
      return;
    }
    try {
      const result = await semanticSearchNotes({ q, scope: searchScope.value, topN: 20 });
      if (!result || result.length === 0) {
        createMessage.info('暂无语义搜索结果，换个关键词试试');
      }
      setTableData(result || []);
    } catch {
      createMessage.error('语义搜索失败');
    }
  }

  function handleClearSmartSearch() {
    searchQuery.value = '';
    setTableData([]);
  }

  function handleSemanticDetail(record) {
    router.push(`/ainote/note/view/${record.noteId}`);
  }

  function handleCreate() {
    openDrawer(true, { isUpdate: false });
  }

  function handleEdit(record) {
    openDrawer(true, { record, isUpdate: true });
  }

  function handleDetail(record) {
    router.push(`/ainote/note/view/${record.id}`);
  }

  async function handleDelete(record) {
    await deleteNote({ id: record.id }, reload);
  }

  async function batchHandleDelete() {
    await batchDeleteNote({ ids: selectedRowKeys.value }, () => {
      selectedRowKeys.value = [];
      reload();
    });
  }

  function handleSuccess() {
    reload();
  }
</script>

<style lang="less" scoped>
  .smart-search-panel {
    padding: 16px;
    margin-bottom: 16px;
    border-radius: 8px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border: 1px solid rgba(102, 126, 234, 0.3);

    .ai-icon {
      font-size: 20px;
      color: #fff;
    }

    :deep(.ant-select-selector),
    :deep(.ant-input),
    :deep(.ant-btn) {
      background: rgba(255, 255, 255, 0.95);
    }
    :deep(.ant-btn-primary) {
      background: rgba(255, 255, 255, 0.95);
      color: #764ba2;
    }
  }
</style>
