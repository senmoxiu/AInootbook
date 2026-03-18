<template>
  <a-tree-select
    v-model:value="state.value"
    :tree-data="state.treeData"
    :placeholder="placeholder"
    :multiple="multiple"
    :tree-checkable="multiple"
    :check-strictly="true"
    :show-checked-strategy="SHOW_PARENT"
    :dropdown-style="{ maxHeight: '400px', overflow: 'auto' }"
    :filter-tree-node="filterTreeNode"
    allow-clear
    show-search
    tree-default-expand-all
    @change="handleChange"
  >
  </a-tree-select>
</template>

<script lang="ts" setup>
  import { reactive, watch, onMounted } from 'vue';
  import { TreeSelect } from 'ant-design-vue';
  import { queryDepartTreeSync } from '/@/api/common/api';

  const SHOW_PARENT = TreeSelect.SHOW_PARENT;

  interface Props {
    value?: string | string[];
    placeholder?: string;
    multiple?: boolean;
    /** 允许的 orgCategory 列表，不传则展示所有组织 */
    orgCategories?: string[];
  }

  const props = withDefaults(defineProps<Props>(), {
    placeholder: '请选择组织',
    multiple: false,
  });

  const emit = defineEmits(['update:value', 'change']);

  const state = reactive({
    value: props.value,
    treeData: [] as any[],
  });

  // 监听 value 变化
  watch(
    () => props.value,
    (val) => {
      state.value = val;
    }
  );

  // 加载部门树数据
  async function loadTreeData() {
    try {
      const result = await queryDepartTreeSync();
      if (result && Array.isArray(result)) {
        // 如果指定了 orgCategories，按条件过滤；否则展示所有节点
        state.treeData = props.orgCategories?.length ? filterAndTransformTree(result) : transformTree(result);
      }
    } catch (error) {
      console.error('加载部门树数据失败:', error);
    }
  }

  // 直接转换树数据（不过滤 orgCategory）
  function transformTree(nodes: any[]): any[] {
    return nodes.map((node) => {
      const newNode: any = {
        title: node.title || node.departName,
        value: node.key || node.id,
        key: node.key || node.id,
        orgCategory: node.orgCategory || node.org_category,
      };
      if (node.children && node.children.length > 0) {
        newNode.children = transformTree(node.children);
      }
      return newNode;
    });
  }

  // 过滤并转换树数据（仅保留指定 orgCategory 的节点）
  function filterAndTransformTree(nodes: any[]): any[] {
    const allowed = props.orgCategories || [];
    const result: any[] = [];

    for (const node of nodes) {
      const orgCategory = node.orgCategory || node.org_category;
      if (allowed.includes(orgCategory)) {
        const newNode: any = {
          title: node.title || node.departName,
          value: node.key || node.id,
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

  // 树节点过滤（搜索）
  function filterTreeNode(inputValue: string, treeNode: any) {
    return (treeNode.title || '').toLowerCase().indexOf(inputValue.toLowerCase()) >= 0;
  }

  // 处理值变化
  function handleChange(value: any) {
    state.value = value;
    emit('update:value', value);
    emit('change', value);
  }

  // 组件挂载时加载数据
  onMounted(() => {
    loadTreeData();
  });
</script>
