import { BasicColumn, FormSchema } from '/@/components/Table';
import { render } from '/@/utils/common/renderUtils';

/**
 * 笔记表格列定义
 */
export const columns: BasicColumn[] = [
  {
    title: '笔记标题',
    dataIndex: 'noteTitle',
    width: 200,
    align: 'left',
  },
  {
    title: '笔记状态',
    dataIndex: 'noteStatus',
    width: 100,
    customRender: ({ text }) => {
      return render.renderDict(text, 'ainote_note_status');
    },
  },
  {
    title: '是否公开',
    dataIndex: 'isPublic',
    width: 80,
    customRender: ({ text }) => {
      return render.renderDict(text, 'yn');
    },
  },
  {
    title: 'AI摘要',
    dataIndex: 'aiSummary',
    width: 300,
    ellipsis: true,
  },
  {
    title: '关键词',
    dataIndex: 'keywords',
    width: 200,
  },
  {
    title: '创建人',
    dataIndex: 'createBy',
    width: 120,
    customRender: ({ record }) => (record as any).createBy_dictText || (record as any).createBy,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 150,
  },
];

/**
 * 笔记搜索表单 Schema
 */
export const searchFormSchema: FormSchema[] = [
  {
    label: '笔记标题',
    field: 'noteTitle',
    component: 'Input',
    colProps: { span: 6 },
  },
  {
    label: '笔记状态',
    field: 'noteStatus',
    component: 'JDictSelectTag',
    componentProps: {
      dictCode: 'ainote_note_status',
    },
    colProps: { span: 6 },
  },
  {
    label: '是否公开',
    field: 'isPublic',
    component: 'JDictSelectTag',
    componentProps: {
      dictCode: 'yn',
    },
    colProps: { span: 6 },
  },
];

/**
 * 笔记新增表单 Schema（简洁模式：标题 + 课程 + 章节 + 公开）
 */
export const createFormSchema: FormSchema[] = [
  {
    label: '笔记标题',
    field: 'noteTitle',
    component: 'Input',
    required: true,
    componentProps: {
      placeholder: '请输入笔记标题',
      maxlength: 200,
    },
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
    label: '章节',
    field: 'chapterId',
    component: 'Input',
    slot: 'chapterSelect',
  },
  {
    label: '是否公开',
    field: 'isPublic',
    component: 'RadioButtonGroup',
    defaultValue: '0',
    componentProps: {
      options: [
        { label: '公开', value: '1' },
        { label: '私密', value: '0' },
      ],
    },
  },
];

/**
 * 笔记编辑表单 Schema（完整模式：含 MD 编辑器）
 */
export const editFormSchema: FormSchema[] = [
  {
    label: 'ID',
    field: 'id',
    component: 'Input',
    show: false,
  },
  {
    label: '笔记标题',
    field: 'noteTitle',
    component: 'Input',
    required: true,
    componentProps: {
      placeholder: '请输入笔记标题',
      maxlength: 200,
    },
  },
  {
    label: '笔记状态',
    field: 'noteStatus',
    component: 'JDictSelectTag',
    required: true,
    componentProps: {
      dictCode: 'ainote_note_status',
      placeholder: '请选择笔记状态',
    },
  },
  {
    label: '是否公开',
    field: 'isPublic',
    component: 'RadioButtonGroup',
    defaultValue: '0',
    componentProps: {
      options: [
        { label: '公开', value: '1' },
        { label: '私密', value: '0' },
      ],
    },
  },
  {
    label: '笔记内容',
    field: 'noteContent',
    component: 'JMarkdownEditor',
    componentProps: {
      placeholder: '请输入笔记内容（支持 Markdown）',
    },
  },
];
