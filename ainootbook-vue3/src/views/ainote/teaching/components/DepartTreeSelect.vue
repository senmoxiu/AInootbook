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
  import { queryTreeList } from '/@/api/common/api';

  const SHOW_PARENT = TreeSelect.SHOW_PARENT;

  interface Props {
    value?: string | string[];
    placeholder?: string;
    multiple?: boolean;
    /**
     * 允许的 orgType 列表（按层级过滤）
     * orgType: 1=公司 2=学院 3=专业 4=班级
     * 不传则展示所有节点
     */
    orgTypes?: string[];
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
      const result = await queryTreeList();
      if (result && Array.isArray(result)) {
        // 如果指定了 orgTypes，按条件过滤；否则展示所有节点
        state.treeData = props.orgTypes?.length ? filterAndTransformTree(result) : transformTree(result);
      }
    } catch (error) {
      console.error('加载部门树数据失败:', error);
    }
  }

  // 直接转换树数据（不过滤）
  function transformTree(nodes: any[]): any[] {
    return nodes.map((node) => {
      const newNode: any = {
        title: node.title || node.departName,
        value: node.key || node.id,
        key: node.key || node.id,
        orgType: node.orgType,
      };
      if (node.children && node.children.length > 0) {
        newNode.children = transformTree(node.children);
      }
      return newNode;
    });
  }

  // 过滤并转换树数据（按 orgType 过滤）
  function filterAndTransformTree(nodes: any[]): any[] {
    const allowed = props.orgTypes || [];
    const result: any[] = [];

    for (const node of nodes) {
      const orgType = String(node.orgType ?? '');
      if (allowed.includes(orgType)) {
        const newNode: any = {
          title: node.title || node.departName,
          value: node.key || node.id,
          key: node.key || node.id,
          orgType: orgType,
        };

        // 递归处理子节点（找更深层级）
        if (node.children && node.children.length > 0) {
          const children = filterAndTransformTree(node.children);
          if (children.length > 0) {
            newNode.children = children;
          }
        }

        result.push(newNode);
      } else if (node.children && node.children.length > 0) {
        // 当前节点不符合条件，继续向下找
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
