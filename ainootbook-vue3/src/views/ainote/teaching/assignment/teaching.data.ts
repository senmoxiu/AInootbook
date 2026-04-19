import { BasicColumn, FormSchema } from '/@/components/Table';
import { render } from '/@/utils/common/renderUtils';

/**
 * 教学任务表格列定义
 */
export const columns: BasicColumn[] = [
  {
    title: '课程名称',
    dataIndex: 'courseName',
    width: 200,
    align: 'left',
  },
  {
    title: '教师姓名',
    dataIndex: 'teacherName',
    width: 120,
  },
  {
    title: '组织名称',
    dataIndex: 'departName',
    width: 150,
  },
  {
    title: '学期',
    dataIndex: 'semester',
    width: 120,
  },
  {
    title: '学年',
    dataIndex: 'academicYear',
    width: 100,
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 80,
    customRender: ({ text }) => {
      const color = text == '1' ? 'green' : 'red';
      return render.renderTag(render.renderDict(text, 'ainote_teaching_status'), color);
    },
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 150,
  },
];

/**
 * 教学任务搜索表单 Schema
 */
export const searchFormSchema: FormSchema[] = [
  {
    label: '课程名称',
    field: 'courseName',
    component: 'Input',
    colProps: { span: 6 },
  },
  {
    label: '教师姓名',
    field: 'teacherName',
    component: 'Input',
    colProps: { span: 6 },
  },
  {
    label: '学期',
    field: 'semester',
    component: 'Input',
    colProps: { span: 6 },
  },
  {
    label: '状态',
    field: 'status',
    component: 'JDictSelectTag',
    componentProps: {
      dictCode: 'ainote_teaching_status',
    },
    colProps: { span: 6 },
  },
];

/**
 * 教学任务编辑表单 Schema
 */
export const formSchema: FormSchema[] = [
  {
    label: 'ID',
    field: 'id',
    component: 'Input',
    show: false,
  },
  {
    label: '课程',
    field: 'courseId',
    component: 'JDictSelectTag',
    required: true,
    componentProps: {
      placeholder: '请选择课程',
      dictCode: 'ainote_course,course_name,id',
    },
  },
  {
    label: '教师',
    field: 'teacherId',
    component: 'JDictSelectTag',
    required: true,
    componentProps: {
      placeholder: '请选择教师',
      dictCode: 'sys_user,realname,id',
    },
  },
  {
    label: '组织',
    field: 'departId',
    component: 'Input',
    required: true,
    slot: 'departSelect',
  },
  {
    label: '学期',
    field: 'semester',
    component: 'Input',
    required: true,
    componentProps: {
      placeholder: '格式：YYYY-YYYY-NN，如 2024-2025-1',
      maxlength: 20,
    },
  },
  {
    label: '学年',
    field: 'academicYear',
    component: 'Input',
    required: true,
    componentProps: {
      placeholder: '格式：YYYY-YYYY，如 2024-2025',
      maxlength: 10,
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
    label: '备注',
    field: 'remark',
    component: 'InputTextArea',
    componentProps: {
      placeholder: '请输入备注',
      rows: 4,
      maxlength: 500,
      showCount: true,
    },
  },
];

/**
 * 批量配置表单 Schema
 */
export const batchConfigFormSchema: FormSchema[] = [
  {
    label: '课程',
    field: 'courseId',
    component: 'JDictSelectTag',
    required: true,
    componentProps: {
      placeholder: '请选择课程',
      dictCode: 'ainote_course,course_name,id',
    },
  },
  {
    label: '组织',
    field: 'departIds',
    component: 'Input',
    required: true,
    slot: 'departTreeSelect',
  },
  {
    label: '学期',
    field: 'semester',
    component: 'Input',
    required: true,
    componentProps: {
      placeholder: '格式：YYYY-YYYY-NN，如 2024-2025-1',
      maxlength: 20,
    },
  },
  {
    label: '学年',
    field: 'academicYear',
    component: 'Input',
    required: true,
    componentProps: {
      placeholder: '格式：YYYY-YYYY，如 2024-2025',
      maxlength: 10,
    },
  },
];
