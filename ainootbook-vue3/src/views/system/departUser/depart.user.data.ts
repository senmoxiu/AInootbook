import { Ref } from 'vue';
import { duplicateCheckDelay } from '/@/views/system/user/user.api';
import { BasicColumn, FormSchema } from '/@/components/Table';
import { DescItem } from '/@/components/Description';
import { findTree } from '/@/utils/common/compUtils';

// 用户信息 columns
export const userInfoColumns: BasicColumn[] = [
  {
    title: '用户账号',
    dataIndex: 'username',
    width: 150,
  },
  {
    title: '用户名称',
    dataIndex: 'realname',
    width: 180,
  },
  {
    title: '组织',
    dataIndex: 'orgCode',
    width: 200,
  },
  {
    title: '性别',
    dataIndex: 'sex_dictText',
    width: 80,
  },
  {
    title: '电话',
    dataIndex: 'phone',
    width: 120,
  },
];

// 用户信息查询条件表单
export const userInfoSearchFormSchema: FormSchema[] = [
  {
    field: 'username',
    label: '用户账号',
    component: 'Input',
  },
];

// 组织角色 columns
export const departRoleColumns: BasicColumn[] = [
  {
    title: '组织角色名称',
    dataIndex: 'roleName',
    width: 100,
  },
  {
    title: '组织角色编码',
    dataIndex: 'roleCode',
    width: 100,
  },
  {
    title: '组织',
    dataIndex: 'departId_dictText',
    width: 100,
  },
  {
    title: '备注',
    dataIndex: 'description',
    width: 100,
  },
];

// 组织角色查询条件表单
export const departRoleSearchFormSchema: FormSchema[] = [
  {
    field: 'roleName',
    label: '组织角色名称',
    component: 'Input',
  },
];

// 组织角色弹窗form表单
export const departRoleModalFormSchema: FormSchema[] = [
  {
    label: 'id',
    field: 'id',
    component: 'Input',
    show: false,
  },
  {
    field: 'roleName',
    label: '组织角色名称',
    component: 'Input',
    rules: [
      { required: true, message: '组织角色名称不能为空！' },
      { min: 2, max: 30, message: '长度在 2 到 30 个字符', trigger: 'blur' },
    ],
  },
  {
    field: 'roleCode',
    label: '组织角色编码',
    component: 'Input',
    dynamicDisabled: ({ values }) => {
      return !!values.id;
    },
    dynamicRules: ({ model }) => {
      return [
        { required: true, message: '组织角色编码不能为空！' },
        { min: 0, max: 64, message: '长度不能超过 64 个字符', trigger: 'blur' },
        {
          validator: (_, value) => {
            if (/[\u4E00-\u9FA5]/g.test(value)) {
              return Promise.reject('组织角色编码不可输入汉字！');
            }
            return new Promise((resolve, reject) => {
              let params = {
                tableName: 'sys_depart_role',
                fieldName: 'role_code',
                fieldVal: value,
                dataId: model.id,
              };
              duplicateCheckDelay(params)
                .then((res) => {
                  res.success ? resolve() : reject(res.message || '校验失败');
                })
                .catch((err) => {
                  reject(err.message || '验证失败');
                });
            });
          },
        },
      ];
    },
  },
  {
    field: 'description',
    label: '描述',
    component: 'Input',
    rules: [{ min: 0, max: 126, message: '长度不能超过 126 个字符', trigger: 'blur' }],
  },
];

// 基本信息form
export function useBaseInfoForm(treeData: Ref<any[]>) {
  const descItems: DescItem[] = [
    {
      field: 'departName',
      label: '组织名称',
    },
    {
      field: 'parentId',
      label: '上级组织',
      render(val) {
        if (val) {
          let data = findTree(treeData.value, (item) => item.key == val);
          return data?.title ?? val;
        }
        return val;
      },
    },
    {
      field: 'orgCode',
      label: '组织编码',
    },
    {
      field: 'orgCategory',
      label: '组织类型',
      render(val) {
        const map: Record<string, string> = {
          '1': '学校',
          '2': '组织单元',
          '3': '岗位',
          '5': '学院',
          '6': '专业',
          '7': '班级',
        };
        return map[val] ?? val;
      },
    },
    {
      field: 'departOrder',
      label: '排序',
    },

    {
      field: 'mobile',
      label: '手机号',
    },
    {
      field: 'address',
      label: '地址',
    },
    {
      field: 'memo',
      label: '备注',
    },
  ];

  return { descItems };
}
