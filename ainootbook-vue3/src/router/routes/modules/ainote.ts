import type { AppRouteModule } from '/@/router/types';
import { LAYOUT } from '/@/router/constant';

/**
 * AI笔记路由模块
 * 菜单由数据库（sys_permission）动态控制；
 * 此文件仅负责注册需要动态参数的非菜单路由（详情页）。
 */
const ainote: AppRouteModule = {
  path: '/ainote',
  name: 'Ainote',
  component: LAYOUT,
  redirect: '/ainote/note',
  meta: {
    title: 'AI笔记',
    hideMenu: true,
  },
  children: [
    {
      path: 'note/view/:id',
      name: 'AinoteNoteView',
      component: () => import('/@/views/ainote/note/detail.vue'),
      meta: {
        title: '笔记详情',
        hideMenu: true,
        currentActiveMenu: '/ainote/note',
      },
    },
    {
      path: 'note/teacher',
      name: 'AinoteTeacherNote',
      component: () => import('/@/views/ainote/note/teacher-view/index.vue'),
      meta: {
        title: '学生笔记',
        hideMenu: true,
        currentActiveMenu: '/ainote/note',
      },
    },
  ],
};

export default ainote;
