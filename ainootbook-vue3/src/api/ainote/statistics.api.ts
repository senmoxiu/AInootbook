import { defHttp } from '/@/utils/http/axios';

enum Api {
  course = '/ainote/statistics/course',
  keywords = '/ainote/statistics/keywords',
  materials = '/ainote/statistics/materials',
}

export interface CourseStatParams {
  courseId: string;
  semester?: string;
  chapterId?: string;
}

export const getCourseStatistics = (params: CourseStatParams) => {
  return defHttp.get({ url: Api.course, params });
};

export const getTopKeywords = (params: { courseId: string; topN?: number; semester?: string }) => {
  return defHttp.get({ url: Api.keywords, params });
};

export const getMaterialTypeStats = (params: { courseId: string; semester?: string }) => {
  return defHttp.get({ url: Api.materials, params });
};
