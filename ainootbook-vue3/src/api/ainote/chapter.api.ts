import { defHttp } from '/@/utils/http/axios';
import { useMessage } from '/@/hooks/web/useMessage';

const { createConfirm } = useMessage();

enum Api {
  list = '/teaching/chapter/list',
  tree = '/teaching/chapter/tree',
  save = '/teaching/chapter/add',
  edit = '/teaching/chapter/edit',
  delete = '/teaching/chapter/delete',
  deleteBatch = '/teaching/chapter/deleteBatch',
  queryById = '/teaching/chapter/queryById',
}

/** 章节树节点 VO */
export interface AinoteChapterTreeVO {
  id: string;
  courseId: string;
  parentId?: string;
  chapterName: string;
  chapterOrder: number;
  chapterDesc?: string;
  status: number;
  createTime?: string;
  children?: AinoteChapterTreeVO[];
}

/** 章节记录 */
export interface ChapterRecord {
  id?: string;
  courseId?: string;
  parentId?: string;
  chapterName?: string;
  chapterOrder?: number;
  chapterDesc?: string;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 获取章节树（按课程ID）
 */
export const getChapterTreeList = (params: { courseId: string }) => {
  return defHttp.get<AinoteChapterTreeVO[]>({ url: Api.tree, params });
};

/**
 * 章节分页列表
 */
export const getChapterList = (params: Record<string, unknown>) => {
  return defHttp.get<{ records: ChapterRecord[]; total: number }>({ url: Api.list, params });
};

/**
 * 根据 ID 查询章节详情
 */
export const getChapterById = (params: { id: string }) => {
  return defHttp.get<ChapterRecord>({ url: Api.queryById, params });
};

/**
 * 保存或更新章节
 */
export const saveOrUpdateChapter = (params: Partial<ChapterRecord>, isUpdate: boolean) => {
  const url = isUpdate ? Api.edit : Api.save;
  return defHttp.post({ url, params });
};

/**
 * 删除单个章节
 */
export const deleteChapter = (params: { id: string }, handleSuccess: () => void) => {
  return defHttp.delete({ url: Api.delete, params }, { joinParamsToUrl: true }).then(() => {
    handleSuccess();
  });
};

/**
 * 批量删除章节
 */
export const batchDeleteChapter = (params: { ids: string }, handleSuccess: () => void) => {
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
