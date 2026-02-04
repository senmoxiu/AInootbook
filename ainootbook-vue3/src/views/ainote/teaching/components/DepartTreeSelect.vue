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
        // 过滤并转换树数据
        state.treeData = filterAndTransformTree(result);
      }
    } catch (error) {
      console.error('加载部门树数据失败:', error);
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
    return treeNode.title.toLowerCase().indexOf(inputValue.toLowerCase()) >= 0;
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
