import { defHttp } from '/@/utils/http/axios';
import { useMessage } from '/@/hooks/web/useMessage';

const { createConfirm } = useMessage();

enum Api {
  upload = '/ainote/material/upload',
  list = '/ainote/material/list',
  queryById = '/ainote/material/queryById',
  presignedUrl = '/ainote/material/presignedUrl',
  delete = '/ainote/material/delete',
  deleteBatch = '/ainote/material/deleteBatch',
}

/** 素材记录 */
export interface MaterialRecord {
  id: string;
  noteId?: string;
  fileName?: string;
  fileType?: string;
  fileSize?: number;
  filePath?: string;
  processStatus?: string;
  createBy?: string;
  createTime?: string;
}

/**
 * 上传素材（multipart）
 */
export const uploadMaterial = (params: FormData) => {
  return defHttp.post<MaterialRecord>({
    url: Api.upload,
    params,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

/**
 * 素材列表
 */
export const getMaterialList = (params: { noteId: string }) => {
  return defHttp.get<MaterialRecord[]>({ url: Api.list, params });
};

/**
 * 素材详情
 */
export const getMaterialById = (params: { id: string }) => {
  return defHttp.get<MaterialRecord>({ url: Api.queryById, params });
};

/**
 * 获取签名 URL
 */
export const getPresignedUrl = (id: string) => {
  return defHttp.get<string>({ url: `${Api.presignedUrl}/${id}` });
};

/**
 * 删除素材
 */
export const deleteMaterial = (params: { id: string }, handleSuccess: () => void) => {
  return defHttp.delete({ url: Api.delete, params }, { joinParamsToUrl: true }).then(() => {
    handleSuccess();
  });
};

/**
 * 批量删除素材
 */
export const batchDeleteMaterial = (params: { ids: string }, handleSuccess: () => void) => {
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
