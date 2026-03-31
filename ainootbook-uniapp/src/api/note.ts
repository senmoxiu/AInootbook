import { http } from '@/utils/request'

export interface Note { id: string; noteTitle: string; noteContent?: string; renderedContent?: string; aiSummary?: string; keywords?: string; courseId?: string; courseName?: string; chapterId?: string; chapterName?: string; currentVersion?: number; baseVersion?: number; noteStatus?: number; isPublic?: boolean; createTime?: string; updateTime?: string; }

export interface NoteListParams {
  pageNo?: number
  pageSize?: number
  title?: string
  courseId?: string
  isPublic?: boolean
  [key: string]: unknown
}

export interface NoteListResult {
  records: Note[]
  total: number
}

export interface NoteVersion { id?: string; noteId?: string; version?: number; noteContent?: string; renderedContent?: string; aiSummary?: string; keywords?: string; createTime?: string; createBy?: string; }

const BASE_URL = '/ainote/note'

export const noteApi = {
  getNoteList: (params?: NoteListParams) =>
    http<NoteListResult>({
      url: `${BASE_URL}/list`,
      method: 'GET',
      query: params,
      cache: true
    }),

  getNoteDetail: (id: string) =>
    http<Note>({
      url: `${BASE_URL}/queryById`,
      method: 'GET',
      query: { id },
      cache: true
    }),

  addNote: (data: Partial<Note>) =>
    http<{success: boolean; message: string; result: string | { id: string }}>({
      url: `${BASE_URL}/add`,
      method: 'POST',
      data
    }).then(res => res.result && typeof res.result === 'string' ? res.result : ((res.result as any)?.id || '')),

  editNote: (data: Partial<Note>) =>
    http<{ success: boolean; message: string }>({
      url: `${BASE_URL}/edit`,
      method: 'PUT',
      data
    }),

  deleteNote: (id: string) =>
    http<{ success: boolean; message: string }>({
      url: `${BASE_URL}/delete`,
      method: 'DELETE',
      query: { id }
    }),

  regenerateNote: (data: { noteId: string; baseVersion: number; additionalContent?: string }) =>
    http<{success: boolean; message: string; result: { version: number; noteContent: string }}>({
      url: `${BASE_URL}/regenerate`,
      method: 'POST',
      data
    }).then(res => res.result),

  getNoteVersions: (params: { noteId: string; pageNo?: number; pageSize?: number }) =>
    http<{ success: boolean; result: { records: NoteVersion[]; total: number } }>({
      url: `${BASE_URL}/versions`,
      method: 'GET',
      query: params,
      cache: true
    }),

  getNoteVersionDetail: (versionId: string) =>
    http<NoteVersion>({
      url: `${BASE_URL}/version/${versionId}`,
      method: 'GET',
      cache: true
    }),

  rollbackNote: (data: { noteId: string; targetVersion: number }) =>
    http<{ success: boolean; message: string }>({
      url: `${BASE_URL}/rollback`,
      method: 'POST',
      data
    }),

  triggerGeneration: (generationId: string) =>
    http<string>({
      url: `${BASE_URL}/triggerGeneration`,
      method: 'POST',
      query: { noteId: generationId }
    }),

  getProgress: (generationId: string) =>
    http<{ progress: number; status: 'idle' | 'processing' | 'completed' | 'failed'; errorMsg?: string }>({
      url: `${BASE_URL}/progress`,
      method: 'GET',
      query: { noteId: generationId }
    }),

  cancelGeneration: (generationId: string) =>
    http<string>({
      url: `${BASE_URL}/cancelGeneration`,
      method: 'POST',
      query: { noteId: generationId }
    }),
}
