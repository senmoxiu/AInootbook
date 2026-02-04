import { defHttp } from '/@/utils/http/axios';
import { useMessage } from '/@/hooks/web/useMessage';

const { createConfirm } = useMessage();

enum Api {
  list = '/teaching/assignment/list',
  save = '/teaching/assignment/add',
  edit = '/teaching/assignment/edit',
  delete = '/teaching/assignment/delete',
  deleteBatch = '/teaching/assignment/deleteBatch',
  queryById = '/teaching/assignment/queryById',
  batchUpsert = '/teaching/assignment/batchUpsert',
}

/**
 * 教学任务列表查询
 * @param params 查询参数（teacherId, courseId, departId, semester, status）
 */
export const getTeachingList = (params) => {
  return defHttp.get({ url: Api.list, params });
};

/**
 * 根据 ID 查询教学任务详情
 * @param params 包含 id 的参数对象
 */
export const getTeachingById = (params) => {
  return defHttp.get({ url: Api.queryById, params });
};

/**
 * 新增教学任务
 * @param params 教学任务数据
 */
export const addTeaching = (params) => {
  return defHttp.post({ url: Api.save, params });
};

/**
 * 编辑教学任务
 * @param params 教学任务数据
 */
export const editTeaching = (params) => {
  return defHttp.put({ url: Api.edit, params });
};

/**
 * 保存或更新教学任务
 * @param params 教学任务数据
 * @param isUpdate 是否为更新操作
 */
export const saveOrUpdateTeaching = (params, isUpdate) => {
  const url = isUpdate ? Api.edit : Api.save;
  return defHttp.post({ url: url, params });
};

/**
 * 删除单个教学任务
 * @param params 包含 id 的参数对象
 * @param handleSuccess 成功回调
 */
export const deleteTeaching = (params, handleSuccess) => {
  return defHttp.delete({ url: Api.delete, params }, { joinParamsToUrl: true }).then(() => {
    handleSuccess();
  });
};

/**
 * 批量删除教学任务
 * @param params 包含 ids 的参数对象
 * @param handleSuccess 成功回调
 */
export const batchDeleteTeaching = (params, handleSuccess) => {
  createConfirm({
    iconType: 'warning',
    title: '确认删除',
    content: '是否删除选中数据',
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return defHttp.delete({ url: Api.deleteBatch, data: params }, { joinParamsToUrl: true }).then(() => {
        handleSuccess();
      });
    },
  });
};

/**
 * 批量配置教学任务（一个课程 + 多个组织节点）
 * @param params { courseId, departIds, semester, academicYear }
 * @returns { successCount, failedList }
 */
export const batchUpsertTeaching = (params) => {
  return defHttp.post({ url: Api.batchUpsert, params });
};
