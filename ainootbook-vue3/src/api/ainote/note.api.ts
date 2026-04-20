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
  shareDetail = '/ainote/note/share',
  publicList = '/ainote/note/public',
  triggerGeneration = '/ainote/note/triggerGeneration',
  cancelGeneration = '/ainote/note/cancelGeneration',
  regenerate = '/ainote/note/regenerate',
  progress = '/ainote/note/progress',
  search = '/ainote/note/search',
  semanticSearch = '/ainote/note/semanticSearch',
  teacherList = '/ainote/note/teacherList',
  versions = '/ainote/note/versions',
  versionDetail = '/ainote/note/version',
  rollback = '/ainote/note/rollback',
  like = '/ainote/note/like',
  unlike = '/ainote/note/unlike',
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
  viewCount?: number;
  likeCount?: number;
  isLiked?: boolean;
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

/** 笔记版本记录 */
export interface NoteVersionRecord {
  id?: string;
  noteId?: string;
  version: number;
  noteTitle?: string;
  noteContent?: string;
  aiSummary?: string;
  summary?: string;       // 后端 VO 字段名
  keywords?: string;
  createTime?: string;
  createdAt?: string;     // 后端 VO 字段名
  createBy?: string;
  createdBy?: string;     // 后端 VO 字段名
}

/** AI 重新生成参数 */
export interface RegenerateParams {
  noteId: string;
  baseVersion?: number;
  additionalContent?: string;
}

/** AI 重新生成结果 */
export interface RegenerateResult {
  version: number;
  noteContent: string;
}

/**
 * 笔记分页列表
 */
export const getNoteList = (params: Record<string, unknown>) => {
  return defHttp.get<{ records: NoteRecord[]; total: number }>({ url: Api.list, params });
};

/**
 * 教师端笔记分页列表
 */
export const getTeacherNoteList = (params: Record<string, unknown>) => {
  return defHttp.get<{ records: NoteRecord[]; total: number }>({ url: Api.teacherList, params });
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

/**
 * 获取笔记版本列表
 */
export const getNoteVersions = (params: { noteId: string; pageNo?: number; pageSize?: number }) => {
  return defHttp.get<{ records: NoteVersionRecord[]; total: number }>({ url: Api.versions, params });
};

/**
 * 获取笔记版本详情
 */
export const getNoteVersionDetail = (versionId: string) => {
  return defHttp.get<NoteVersionRecord>({ url: `${Api.versionDetail}/${versionId}` });
};

/**
 * 回滚笔记到指定版本
 */
export const rollbackNote = (data: { noteId: string; targetVersion: number }) => {
  return defHttp.post<void>({ url: Api.rollback, data });
};

/**
 * AI 重新生成笔记
 */
export const regenerateNote = (data: RegenerateParams) => {
  return defHttp.post<RegenerateResult>({ url: Api.regenerate, data });
};

/**
 * 触发 AI 重新生成（异步任务）
 */
export const triggerRegeneration = (params: { noteId: string; knowledgeId: string }) => {
  return defHttp.post<void>({ url: Api.regenerate, params });
};

/**
 * 点赞笔记
 */
export const likeNote = (params: { noteId: string }) => {
  return defHttp.post<void>({ url: Api.like, params }, { joinParamsToUrl: true });
};

/**
 * 取消点赞
 */
export const unlikeNote = (params: { noteId: string }) => {
  return defHttp.post<void>({ url: Api.unlike, params }, { joinParamsToUrl: true });
};

//TODO：后续实现
// /**
//  * 获取分享详情
//  */
// export const getShareDetail = (shareCode: string) => {
//   return defHttp.get<NoteRecord>({ url: `${Api.shareDetail}/${shareCode}` });
// };
