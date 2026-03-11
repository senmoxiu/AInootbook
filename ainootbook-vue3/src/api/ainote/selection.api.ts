import { defHttp } from '/@/utils/http/axios';
import { useMessage } from '/@/hooks/web/useMessage';

const { createConfirm } = useMessage();

enum Api {
  list = '/teaching/selection/list',
  save = '/teaching/selection/add',
  edit = '/teaching/selection/edit',
  delete = '/teaching/selection/delete',
  deleteBatch = '/teaching/selection/deleteBatch',
  queryById = '/teaching/selection/queryById',
  dropCourse = '/teaching/selection/dropCourse',
}

/** 分页查询选课列表 */
export const getSelectionList = (params: Record<string, unknown>) => defHttp.get({ url: Api.list, params });

/** 新增选课 */
export const addSelection = (params: Record<string, unknown>) => defHttp.post({ url: Api.save, params });

/** 编辑选课 */
export const editSelection = (params: Record<string, unknown>) => defHttp.put({ url: Api.edit, params });

/** 新增或编辑选课（统一入口） */
export const saveOrUpdateSelection = (params: Record<string, unknown>, isUpdate: boolean) => {
  const url = isUpdate ? Api.edit : Api.save;
  return defHttp.post({ url, params });
};

/** 根据 ID 查询选课详情 */
export const querySelectionById = (params: { id: string }) => defHttp.get({ url: Api.queryById, params });

/** 删除选课（软删除） */
export const deleteSelection = (params: { id: string }, handleSuccess: () => void) => {
  return defHttp.delete({ url: Api.delete, params }, { joinParamsToUrl: true }).then(() => handleSuccess());
};

/** 批量删除选课 */
export const batchDeleteSelection = (params: { ids: string }, handleSuccess: () => void) => {
  return new Promise<void>((resolve) => {
    createConfirm({
      iconType: 'warning',
      title: '确认删除',
      content: '是否删除选中数据',
      okText: '确认',
      cancelText: '取消',
      onOk: () =>
        defHttp.delete({ url: Api.deleteBatch, data: params }, { joinParamsToUrl: true }).then(() => {
          handleSuccess();
          resolve();
        }),
    });
  });
};

/** 学生退课 */
export const dropCourse = (params: { id: string }) => defHttp.post({ url: Api.dropCourse, params }, { joinParamsToUrl: true });
