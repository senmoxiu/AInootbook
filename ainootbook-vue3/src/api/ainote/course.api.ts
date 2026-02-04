import { defHttp } from '/@/utils/http/axios';
import { useMessage } from '/@/hooks/web/useMessage';

const { createConfirm } = useMessage();

enum Api {
  list = '/teaching/course/list',
  save = '/teaching/course/add',
  edit = '/teaching/course/edit',
  delete = '/teaching/course/delete',
  deleteBatch = '/teaching/course/deleteBatch',
  exportXls = '/teaching/course/exportXls',
  importExcel = '/teaching/course/importExcel',
  queryById = '/teaching/course/queryById',
}

/**
 * 课程列表查询
 * @param params 查询参数
 */
export const getCourseList = (params) => {
  return defHttp.get({ url: Api.list, params });
};

/**
 * 根据 ID 查询课程详情
 * @param params 包含 id 的参数对象
 */
export const getCourseById = (params) => {
  return defHttp.get({ url: Api.queryById, params });
};

/**
 * 新增课程
 * @param params 课程数据
 */
export const addCourse = (params) => {
  return defHttp.post({ url: Api.save, params });
};

/**
 * 编辑课程
 * @param params 课程数据
 */
export const editCourse = (params) => {
  return defHttp.put({ url: Api.edit, params });
};

/**
 * 保存或更新课程
 * @param params 课程数据
 * @param isUpdate 是否为更新操作
 */
export const saveOrUpdateCourse = (params, isUpdate) => {
  const url = isUpdate ? Api.edit : Api.save;
  return defHttp.post({ url: url, params });
};

/**
 * 删除单个课程
 * @param params 包含 id 的参数对象
 * @param handleSuccess 成功回调
 */
export const deleteCourse = (params, handleSuccess) => {
  return defHttp.delete({ url: Api.delete, params }, { joinParamsToUrl: true }).then(() => {
    handleSuccess();
  });
};

/**
 * 批量删除课程
 * @param params 包含 ids 的参数对象
 * @param handleSuccess 成功回调
 */
export const batchDeleteCourse = (params, handleSuccess) => {
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
 * 导出课程列表为 Excel
 * @param params 查询参数
 */
export const exportCourseXls = (params) => {
  return defHttp.get({ url: Api.exportXls, params }, { responseType: 'blob' });
};

/**
 * 导入 Excel 数据
 * @param params 包含文件的参数
 */
export const importCourseExcel = (params) => {
  return defHttp.post({ url: Api.importExcel, params });
};
