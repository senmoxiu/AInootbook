import { defHttp } from '/@/utils/http/axios';
import { useMessage } from '/@/hooks/web/useMessage';

const { createConfirm } = useMessage();

enum Api {
  list = '/ainote/note/list',
  queryById = '/ainote/note/queryById',
  save = '/ainote/note/add',
  edit = '/ainote/note/edit',
  delete = '/ainote/note/delete',
  deleteBatch = '/ainote/note/deleteBatch',
  exportXls = '/ainote/note/exportXls',
  importExcel = '/ainote/note/importExcel',
  share = '/ainote/note/share',
  publicList = '/ainote/note/public',
  triggerGeneration = '/ainote/note/triggerGeneration',
  cancelGeneration = '/ainote/note/cancelGeneration',
  progress = '/ainote/note/progress',
  search = '/ainote/note/search',
  semanticSearch = '/ainote/note/semanticSearch',
}

/** 笔记记录 */
export interface NoteRecord {
  id: string;
  courseId?: string;
  chapterId?: string;
  teachingId?: string;
  studentId?: string;
  noteTitle?: string;
  noteContent?: string;
  aiSummary?: string;
  keywords?: string;
  noteStatus?: number;
  isPublic?: number;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
}

/** 语义搜索请求参数 */
export interface SemanticSearchParams {
  q: string;
  scope?: string;
  topN?: number;
}

/** 语义搜索结果 */
export interface SemanticSearchResultDTO {
  noteId: string;
  noteTitle: string;
  snippet?: string;
  score?: number;
  isPublic?: number;
}

/** 分享结果 */
export interface NoteShareResult {
  shareId: string;
  noteId: string;
  shareCode: string;
  shareType: number;
  expireTime?: string;
}

/** AI 生成进度 */
export interface NoteProgressResult {
  progress: number;
  status: 'idle' | 'processing' | 'completed' | 'failed';
  errorMsg?: string;
}

/**
 * 笔记分页列表
 */
export const getNoteList = (params: Record<string, unknown>) => {
  return defHttp.get<{ records: NoteRecord[]; total: number }>({ url: Api.list, params });
};

/**
 * 根据 ID 查询笔记详情
 */
export const getNoteById = (params: { id: string }) => {
  return defHttp.get<NoteRecord>({ url: Api.queryById, params });
};

/**
 * 新增笔记
 */
export const addNote = (params: Partial<NoteRecord>) => {
  return defHttp.post<string>({ url: Api.save, params });
};

/**
 * 编辑笔记
 */
export const editNote = (params: Partial<NoteRecord>) => {
  return defHttp.put<void>({ url: Api.edit, params });
};

/**
 * 保存或更新笔记
 */
export const saveOrUpdateNote = (params: Partial<NoteRecord>, isUpdate: boolean) => {
  const url = isUpdate ? Api.edit : Api.save;
  return defHttp.post({ url: url, params });
};

/**
 * 删除单个笔记
 */
export const deleteNote = (params: { id: string }, handleSuccess: () => void) => {
  return defHttp.delete({ url: Api.delete, params }, { joinParamsToUrl: true }).then(() => {
    handleSuccess();
  });
};

/**
 * 批量删除笔记
 */
export const batchDeleteNote = (params: { ids: string }, handleSuccess: () => void) => {
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
 * 创建分享
 */
export const shareNote = (params: { noteId: string; expireHours?: number }) => {
  return defHttp.post<NoteShareResult>({ url: Api.share, params });
};

/**
 * 查询分享详情
 */
export const getShareDetail = (shareCode: string) => {
  return defHttp.get({ url: `${Api.share}/${shareCode}` });
};

/**
 * 公开笔记广场
 */
export const getPublicNotes = (params: Record<string, unknown>) => {
  return defHttp.get<{ records: NoteRecord[]; total: number }>({ url: Api.publicList, params });
};

/**
 * 导出笔记列表为 Excel
 */
export const exportNoteXls = (params: Record<string, unknown>) => {
  return defHttp.get({ url: Api.exportXls, params }, { responseType: 'blob' });
};

/**
 * 导入 Excel 数据
 */
export const importNoteExcel = (params: FormData) => {
  return defHttp.post({ url: Api.importExcel, params });
};

/**
 * 触发 AI 生成
 */
export const triggerGeneration = (params: { noteId: string; knowledgeId?: string }) => {
  return defHttp.post<string>({ url: Api.triggerGeneration, params }, { joinParamsToUrl: true });
};

/**
 * 取消 AI 生成
 */
export const cancelGeneration = (params: { noteId: string }) => {
  return defHttp.post<string>({ url: Api.cancelGeneration, params }, { joinParamsToUrl: true });
};

/**
 * 查询 AI 生成进度
 */
export const getGenerationProgress = (params: { noteId: string; knowledgeId?: string }) => {
  return defHttp.get<NoteProgressResult>({ url: Api.progress, params });
};

/**
 * 语义搜索笔记
 */
export const searchNotes = (params: { knowledgeId: string; q: string; topN?: number }) => {
  return defHttp.get<NoteRecord[]>({ url: Api.search, params });
};

/**
 * 语义搜索笔记（新端点，服务端自动读取 knowledgeId 配置）
 */
export const semanticSearchNotes = (params: SemanticSearchParams) => {
  return defHttp.get<SemanticSearchResultDTO[]>({ url: Api.semanticSearch, params });
};
