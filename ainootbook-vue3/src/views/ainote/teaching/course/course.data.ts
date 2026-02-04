import { BasicColumn, FormSchema } from '/@/components/Table';
import { render } from '/@/utils/common/renderUtils';

/**
 * 课程库表格列定义
 */
export const columns: BasicColumn[] = [
  {
    title: '课程编码',
    dataIndex: 'courseCode',
    width: 120,
    align: 'left',
  },
  {
    title: '课程名称',
    dataIndex: 'courseName',
    width: 200,
    align: 'left',
  },
  {
    title: '学分',
    dataIndex: 'credit',
    width: 80,
  },
  {
    title: '课程类型',
    dataIndex: 'courseType',
    width: 100,
    customRender: ({ text }) => {
      return render.renderDict(text, 'course_type');
    },
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 80,
    customRender: ({ text }) => {
      const color = text == '1' ? 'green' : 'red';
      return render.renderTag(render.renderDict(text, 'course_status'), color);
    },
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 150,
  },
];

/**
 * 课程库搜索表单 Schema
 */
export const searchFormSchema: FormSchema[] = [
  {
    label: '课程编码',
    field: 'courseCode',
    component: 'Input',
    colProps: { span: 6 },
  },
  {
    label: '课程名称',
    field: 'courseName',
    component: 'Input',
    colProps: { span: 6 },
  },
  {
    label: '课程类型',
    field: 'courseType',
    component: 'JDictSelectTag',
    componentProps: {
      dictCode: 'course_type',
    },
    colProps: { span: 6 },
  },
  {
    label: '状态',
    field: 'status',
    component: 'JDictSelectTag',
    componentProps: {
      dictCode: 'course_status',
    },
    colProps: { span: 6 },
  },
];

/**
 * 课程库编辑表单 Schema
 */
export const formSchema: FormSchema[] = [
  {
    label: 'ID',
    field: 'id',
    component: 'Input',
    show: false,
  },
  {
    label: '课程编码',
    field: 'courseCode',
    component: 'Input',
    required: true,
    componentProps: {
      placeholder: '请输入课程编码',
      maxlength: 32,
    },
  },
  {
    label: '课程名称',
    field: 'courseName',
    component: 'Input',
    required: true,
    componentProps: {
      placeholder: '请输入课程名称',
      maxlength: 100,
    },
  },
  {
    label: '学分',
    field: 'credit',
    component: 'InputNumber',
    required: true,
    componentProps: {
      placeholder: '请输入学分',
      min: 0,
      max: 10,
      step: 0.5,
    },
  },
  {
    label: '课程类型',
    field: 'courseType',
    component: 'JDictSelectTag',
    required: true,
    componentProps: {
      dictCode: 'course_type',
      placeholder: '请选择课程类型',
    },
  },
  {
    label: '状态',
    field: 'status',
    component: 'RadioButtonGroup',
    defaultValue: '1',
    componentProps: {
      options: [
        { label: '启用', value: '1' },
        { label: '禁用', value: '0' },
      ],
    },
  },
  {
    label: '课程描述',
    field: 'description',
    component: 'InputTextArea',
    componentProps: {
      placeholder: '请输入课程描述',
      rows: 4,
      maxlength: 500,
      showCount: true,
    },
  },
];
