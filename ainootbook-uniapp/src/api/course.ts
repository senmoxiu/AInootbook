import { http } from '@/utils/request'
import type { CustomRequestOptions } from '@/utils/request'

export interface Course {
  id: string
  courseName: string
  courseCode?: string
  teacherId?: string
  teacherName?: string
  semester?: string
  chapterCount?: number
  studyProgress?: number
  description?: string
  createTime?: string
  updateTime?: string
  [key: string]: any
}

export interface CourseChapter {
  id: string
  chapterName?: string
  chapterCode?: string
  description?: string
  studyProgress?: number
  children?: CourseChapter[]
  [key: string]: any
}

export interface CourseDetail extends Course {
  chapterTree?: CourseChapter[]
  chapters?: CourseChapter[]
  chapterList?: CourseChapter[]
}

export interface CourseApiResponse<T> {
  success?: boolean
  message?: string
  result?: T
  data?: T
}

export interface CourseListParams {
  pageNo?: number
  pageSize?: number
  courseName?: string
  teacherName?: string
}

export interface CourseListResult {
  records: Course[]
  total: number
}

type CourseRequestOptions = Pick<
  CustomRequestOptions,
  'cache' | 'hideErrorToast' | 'retryCount' | 'retryDelay'
>

const BASE_URL = '/jeecg-boot/teaching/course'

export const courseApi = {
  getCourseList: (params?: CourseListParams, options: CourseRequestOptions = {}) =>
    http<CourseListResult | CourseApiResponse<CourseListResult>>({
      url: `${BASE_URL}/list`,
      method: 'GET',
      query: params,
      cache: options.cache ?? true,
      hideErrorToast: options.hideErrorToast,
      retryCount: options.retryCount,
      retryDelay: options.retryDelay,
    }),

  getCourseDetail: (id: string, options: CourseRequestOptions = {}) =>
    http<CourseDetail | CourseApiResponse<CourseDetail>>({
      url: `${BASE_URL}/detail`,
      method: 'GET',
      query: { id },
      cache: options.cache ?? true,
      hideErrorToast: options.hideErrorToast,
      retryCount: options.retryCount,
      retryDelay: options.retryDelay,
    }),

  addCourse: (data: Partial<Course>) =>
    http<{ success: boolean; message: string }>({
      url: `${BASE_URL}/add`,
      method: 'POST',
      data,
    }),

  editCourse: (data: Partial<Course>) =>
    http<{ success: boolean; message: string }>({
      url: `${BASE_URL}/edit`,
      method: 'PUT',
      data,
    }),

  deleteCourse: (id: string) =>
    http<{ success: boolean; message: string }>({
      url: `${BASE_URL}/delete`,
      method: 'DELETE',
      query: { id },
    }),
}
