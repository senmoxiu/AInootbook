import { BasicColumn, FormSchema } from '/@/components/Table';
import { render } from '/@/utils/common/renderUtils';

/**
 * 章节树形表格列定义
 */
export const columns: BasicColumn[] = [
  {
    title: '章节名称',
    dataIndex: 'chapterName',
    width: 250,
    align: 'left',
  },
  {
    title: '排序号',
    dataIndex: 'chapterOrder',
    width: 80,
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 80,
    customRender: ({ text }) => {
      const color = text == 1 ? 'green' : 'red';
      return render.renderTag(text == 1 ? '启用' : '禁用', color);
    },
  },
  {
    title: '章节描述',
    dataIndex: 'chapterDesc',
    width: 200,
    align: 'left',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 150,
  },
];

/**
 * 章节编辑表单 Schema
 */
export const formSchema: FormSchema[] = [
  {
    label: 'ID',
    field: 'id',
    component: 'Input',
    show: false,
  },
  {
    label: '课程ID',
    field: 'courseId',
    component: 'Input',
    show: false,
  },
  {
    label: '章节名称',
    field: 'chapterName',
    component: 'Input',
    required: true,
    componentProps: {
      placeholder: '请输入章节名称',
      maxlength: 200,
    },
  },
  {
    label: '父级节点',
    field: 'parentId',
    component: 'ApiTreeSelect',
    componentProps: ({ formModel }) => ({
      // 动态加载当前课程的章节树（防空：courseId 未填充时返回空数组）
      api: () => {
        if (!formModel.courseId) return Promise.resolve([]);
        return import('/@/api/ainote/chapter.api').then((m) =>
          m.getChapterTreeList({ courseId: formModel.courseId })
        );
      },
      fieldNames: { label: 'chapterName', value: 'id', children: 'children' },
      treeDefaultExpandAll: true,
      placeholder: '请选择父级节点（不选则为根节点）',
      allowClear: true,
    }),
  },
  {
    label: '排序号',
    field: 'chapterOrder',
    component: 'InputNumber',
    required: true,
    componentProps: {
      placeholder: '请输入排序号',
      min: 1,
      max: 999,
    },
  },
  {
    label: '状态',
    field: 'status',
    component: 'RadioButtonGroup',
    defaultValue: 1,
    componentProps: {
      options: [
        { label: '启用', value: 1 },
        { label: '禁用', value: 0 },
      ],
    },
  },
  {
    label: '章节描述',
    field: 'chapterDesc',
    component: 'InputTextArea',
    componentProps: {
      placeholder: '请输入章节描述',
      rows: 3,
      maxlength: 500,
      showCount: true,
    },
  },
];
