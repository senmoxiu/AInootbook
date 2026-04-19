import { BasicColumn, FormSchema } from '/@/components/Table';
import { render } from '/@/utils/common/renderUtils';
import { getTeachingList } from '/@/api/ainote/teaching.api';

/** 可选课程列（学生选课用） */
export const availableColumns: BasicColumn[] = [
  { title: '课程名称', dataIndex: 'courseName', width: 180 },
  { title: '课程代码', dataIndex: 'courseCode', width: 120 },
  { title: '教师', dataIndex: 'teacherName', width: 100 },
  { title: '组织', dataIndex: 'departName', width: 140 },
  { title: '学期', dataIndex: 'semester', width: 120 },
];

/** 学生视图列：仅展示与自身选课相关的字段 */
export const studentColumns: BasicColumn[] = [
  { title: '课程名称', dataIndex: 'courseName', width: 150 },
  { title: '课程代码', dataIndex: 'courseCode', width: 120 },
  { title: '教师', dataIndex: 'teacherName', width: 100 },
  { title: '学期', dataIndex: 'semester', width: 120 },
  { title: '选课时间', dataIndex: 'selectedAt', width: 150 },
  {
    title: '状态',
    dataIndex: 'status',
    width: 80,
    customRender: ({ text }) => {
      const color = text == 1 ? 'green' : 'red';
      return render.renderTag(text == 1 ? '正常' : '已退课', color);
    },
  },
];

/** 教师/管理员视图列：额外展示学生和组织信息 */
export const teacherColumns: BasicColumn[] = [
  { title: '学生姓名', dataIndex: 'studentName', width: 100 },
  { title: '课程名称', dataIndex: 'courseName', width: 150 },
  { title: '课程代码', dataIndex: 'courseCode', width: 120 },
  { title: '组织', dataIndex: 'departName', width: 120 },
  { title: '学期', dataIndex: 'semester', width: 120 },
  { title: '选课时间', dataIndex: 'selectedAt', width: 150 },
  {
    title: '状态',
    dataIndex: 'status',
    width: 80,
    customRender: ({ text }) => {
      const color = text == 1 ? 'green' : 'red';
      return render.renderTag(text == 1 ? '正常' : '已退课', color);
    },
  },
];

/** 学生搜索表单：按课程名称、学期、状态筛选 */
export const studentSearchSchema: FormSchema[] = [
  { label: '课程名称', field: 'courseName', component: 'Input', colProps: { span: 6 } },
  { label: '学期', field: 'semester', component: 'Input', colProps: { span: 6 } },
  {
    label: '状态',
    field: 'status',
    component: 'JDictSelectTag',
    colProps: { span: 6 },
    componentProps: {
      dictCode: 'ainote_selection_status',
      placeholder: '请选择状态',
    },
  },
];

/** 教师/管理员搜索表单：额外支持按学生姓名筛选 */
export const teacherSearchSchema: FormSchema[] = [
  { label: '学生姓名', field: 'studentName', component: 'Input', colProps: { span: 6 } },
  { label: '课程名称', field: 'courseName', component: 'Input', colProps: { span: 6 } },
  { label: '学期', field: 'semester', component: 'Input', colProps: { span: 6 } },
  {
    label: '状态',
    field: 'status',
    component: 'JDictSelectTag',
    colProps: { span: 6 },
    componentProps: {
      dictCode: 'ainote_selection_status',
      placeholder: '请选择状态',
    },
  },
];

/** 选课表单 Schema（管理员新增或编辑选课使用） */
export const formSchema: FormSchema[] = [
  { label: 'ID', field: 'id', component: 'Input', show: false },
  {
    label: '教学任务',
    field: 'teachingId',
    component: 'ApiSelect',
    required: true,
    dynamicDisabled: ({ values }) => !!values.id,
    componentProps: {
      api: getTeachingList,
      params: { pageSize: 200, status: 1 },
      resultField: 'records',
      valueField: 'id',
      labelField: '_label',
      showSearch: true,
      filterOption: (input: string, option: { label?: string }) =>
        (option.label ?? '').toLowerCase().includes(input.toLowerCase()),
      afterFetch: (list: Recordable[]) =>
        list.map((item) => ({
          ...item,
          _label: `${item.courseName ?? ''} - ${item.teacherName ?? ''} - ${item.semester ?? ''}`,
        })),
    },
  },
  {
    label: '学生',
    field: 'studentId',
    component: 'JSelectUser',
    required: true,
    dynamicDisabled: ({ values }) => !!values.id,
    componentProps: {
      placeholder: '请选择学生',
      labelKey: 'realname',
      rowKey: 'id',
      maxSelectCount: 1,
    },
  },
];
