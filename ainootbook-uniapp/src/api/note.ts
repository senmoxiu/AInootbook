import { http } from '@/utils/request'

export interface Note {
  id: string
  title: string
  content?: string
  renderedContent?: string
  summary?: string
  courseId?: string
  courseName?: string
  chapterId?: string
  chapterName?: string
  currentVersion?: number
  isPublic?: boolean
  createTime?: string
  updateTime?: string
}

export interface NoteListParams {
  pageNo?: number
  pageSize?: number
  title?: string
  courseId?: string
  isPublic?: boolean
}

export interface NoteListResult {
  records: Note[]
  total: number
}

export interface NoteVersion {
  id: string
  noteId: string
  version: number
  content: string
  renderedContent?: string
  createTime: string
}

const BASE_URL = '/jeecg-boot/ainote/note'

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
    http<{ success: boolean; message: string; result?: Note }>({
      url: `${BASE_URL}/add`,
      method: 'POST',
      data
    }),

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
    http<{ success: boolean; message: string; result?: { generationId: string } }>({
      url: `${BASE_URL}/regenerate`,
      method: 'POST',
      data
    }),

  getNoteVersions: (noteId: string) =>
    http<{ success: boolean; result: NoteVersion[] }>({
      url: `${BASE_URL}/versions`,
      method: 'GET',
      query: { noteId },
      cache: true
    }),

  rollbackNote: (data: { noteId: string; targetVersion: number }) =>
    http<{ success: boolean; message: string }>({
      url: `${BASE_URL}/rollback`,
      method: 'POST',
      data
    }),
}
