<template>
  <a-row :gutter="10" style="padding: 16px">
    <!-- 左侧组织树 -->
    <a-col :xl="6" :lg="8" :md="10" :sm="24" style="min-height: 600px">
      <a-card :bordered="false" style="height: 100%">
        <a-input-search placeholder="按组织名称搜索..." style="margin-bottom: 10px" @search="onSearch" />
        <a-spin :spinning="treeLoading">
          <a-tree
            v-if="treeData.length > 0"
            :tree-data="treeData"
            :selected-keys="selectedKeys"
            :expanded-keys="expandedKeys"
            @select="onTreeSelect"
            @expand="onExpand"
          >
          </a-tree>
          <a-empty v-else description="暂无数据" />
        </a-spin>
      </a-card>
    </a-col>

    <!-- 右侧教学任务列表 -->
    <a-col :xl="18" :lg="16" :md="14" :sm="24">
      <a-card :bordered="false" style="height: 100%">
        <BasicTable @register="registerTable">
          <template #toolbar>
            <a-button type="primary" @click="handleCreate" preIcon="ant-design:plus-outlined">新增</a-button>
            <a-button type="primary" @click="handleBatchConfig" preIcon="ant-design:setting-outlined">批量配置</a-button>
            <a-button type="primary" @click="batchHandleDelete" preIcon="ant-design:delete-outlined">批量删除</a-button>
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
      </a-card>
    </a-col>
  </a-row>

  <!-- 教学任务编辑抽屉 -->
  <TeachingDrawer @register="registerDrawer" @success="handleSuccess" />

  <!-- 批量配置抽屉 -->
  <TeachingConfigDrawer @register="registerConfigDrawer" @success="handleSuccess" />
</template>

<script lang="ts" setup>
  import { ref, reactive, onMounted } from 'vue';
  import { BasicTable, useTable, TableAction } from '/@/components/Table';
  import { useDrawer } from '/@/components/Drawer';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { columns, searchFormSchema } from './teaching.data';
  import { getTeachingList, deleteTeaching, batchDeleteTeaching } from '/@/api/ainote/teaching.api';
  import { queryDepartTreeSync } from '/@/api/common/api';
  import TeachingDrawer from './TeachingDrawer.vue';
  import TeachingConfigDrawer from './TeachingConfigDrawer.vue';

  // 树相关状态
  const treeLoading = ref(false);
  const treeData = ref<any[]>([]);
  const selectedKeys = ref<string[]>([]);
  const expandedKeys = ref<string[]>([]);
  const currentDepartId = ref<string>('');

  // 列表页面公共参数、方法
  const { prefixCls, tableContext } = useListPage({
    tableProps: {
      title: '教学任务列表',
      api: getTeachingList,
      columns: columns,
      formConfig: {
        schemas: searchFormSchema,
      },
      actionColumn: {
        width: 150,
        title: '操作',
        dataIndex: 'action',
        fixed: 'right',
      },
      beforeFetch(params) {
        // 添加当前选中的组织 ID
        if (currentDepartId.value) {
          params.departId = currentDepartId.value;
        }
        return params;
      },
      immediate: false, // 不立即加载，等待选择组织后再加载
    },
  });

  // 注册 table
  const [registerTable, { reload }, { rowSelection, selectedRowKeys }] = tableContext;

  // 注册抽屉
  const [registerDrawer, { openDrawer }] = useDrawer();
  const [registerConfigDrawer, { openDrawer: openConfigDrawer }] = useDrawer();

  // 加载组织树数据
  async function loadTreeData() {
    try {
      treeLoading.value = true;
      const result = await queryDepartTreeSync();
      if (result && Array.isArray(result)) {
        // 过滤并转换树数据（仅保留 org_category ∈ {5,6,7} 的节点）
        treeData.value = filterAndTransformTree(result);
      }
    } catch (error) {
      console.error('加载组织树数据失败:', error);
    } finally {
      treeLoading.value = false;
    }
  }

  // 过滤并转换树数据（仅保留 org_category ∈ {5,6,7} 的节点）
  function filterAndTransformTree(nodes: any[]): any[] {
    const result: any[] = [];

    for (const node of nodes) {
      // 检查 org_category 是否为 5（院系）、6（专业）、7（班级）
      const orgCategory = node.orgCategory || node.org_category;
      if (orgCategory === '5' || orgCategory === '6' || orgCategory === '7') {
        const newNode: any = {
          title: node.title || node.departName,
          key: node.key || node.id,
          orgCategory: orgCategory,
        };

        // 递归处理子节点
        if (node.children && node.children.length > 0) {
          const children = filterAndTransformTree(node.children);
          if (children.length > 0) {
            newNode.children = children;
          }
        }

        result.push(newNode);
      } else if (node.children && node.children.length > 0) {
        // 如果当前节点不符合条件，但有子节点，继续递归查找
        const children = filterAndTransformTree(node.children);
        result.push(...children);
      }
    }

    return result;
  }

  // 搜索关键词
  const searchValue = ref('');

  // 搜索组织树（前端过滤 + 自动展开匹配节点）
  function onSearch(value: string) {
    searchValue.value = value;
    if (!value) {
      loadTreeData();
      return;
    }
    const matched = filterTreeByKeyword(treeData.value, value);
    treeData.value = matched;
    expandedKeys.value = getAllKeys(matched);
  }

  // 按关键词过滤树节点
  function filterTreeByKeyword(nodes: any[], keyword: string): any[] {
    const result: any[] = [];
    for (const node of nodes) {
      const children = node.children ? filterTreeByKeyword(node.children, keyword) : [];
      if ((node.title || '').includes(keyword) || children.length > 0) {
        result.push({ ...node, children: children.length > 0 ? children : undefined });
      }
    }
    return result;
  }

  // 获取树所有 key（用于展开）
  function getAllKeys(nodes: any[]): string[] {
    const keys: string[] = [];
    for (const node of nodes) {
      keys.push(node.key);
      if (node.children) keys.push(...getAllKeys(node.children));
    }
    return keys;
  }

  // 树节点选择事件
  function onTreeSelect(keys: string[], event: any) {
    if (keys.length > 0) {
      selectedKeys.value = keys;
      currentDepartId.value = keys[0];
      // 刷新列表
      reload();
    }
  }

  // 树节点展开事件
  function onExpand(keys: string[]) {
    expandedKeys.value = keys;
  }

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
   * 批量配置事件
   */
  function handleBatchConfig() {
    openConfigDrawer(true);
  }

  /**
   * 删除事件
   */
  async function handleDelete(record) {
    await deleteTeaching({ id: record.id }, reload);
  }

  /**
   * 批量删除事件
   */
  async function batchHandleDelete() {
    await batchDeleteTeaching({ ids: selectedRowKeys.value }, () => {
      selectedRowKeys.value = [];
      reload();
    });
  }

  /**
   * 成功回调
   */
  function handleSuccess() {
    reload();
  }

  // 组件挂载时加载树数据
  onMounted(() => {
    loadTreeData();
  });
</script>
