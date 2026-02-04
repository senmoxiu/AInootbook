import type { AppRouteModule } from '/@/router/types';
import { LAYOUT } from '/@/router/constant';

const teaching: AppRouteModule = {
  path: '/teaching',
  name: 'Teaching',
  component: LAYOUT,
  redirect: '/teaching/course',
  meta: {
    orderNo: 50,
    icon: 'ant-design:book-outlined',
    title: '教学管理',
  },
  children: [
    {
      path: 'course',
      name: 'TeachingCourse',
      component: () => import('/@/views/ainote/teaching/course/index.vue'),
      meta: {
        title: '课程库管理',
        icon: 'ant-design:book-outlined',
      },
    },
    {
      path: 'assignment',
      name: 'TeachingAssignment',
      component: () => import('/@/views/ainote/teaching/assignment/index.vue'),
      meta: {
        title: '教学任务配置',
        icon: 'ant-design:setting-outlined',
      },
    },
  ],
};

export default teaching;
