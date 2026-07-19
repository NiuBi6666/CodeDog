export default [
  {
    path: '/test',
    meta: { requireLogin: false },
    component: () => import(/* webpackChunkName: 'root' */ '@/views/test')
  },
  {
    path: '/redirect/:type',
    meta: { requireLogin: false },
    component: () => import(/* webpackChunkName: 'root' */ '@/views/redirect')
  },
  {
    path: '/account/validate',
    meta: { requireLogin: false },
    component: () => import(/* webpackChunkName: 'root' */ '@/views/account/validate')
  },
  {
    path: '/forget/password',
    meta: { requireLogin: false },
    component: () => import(/* webpackChunkName: 'root' */ '@/views/account/ForgetPwd')
  },
  {
    path: '/',
    meta: { requireLogin: false },
    component: () => import(/* webpackChunkName: 'root' */ '@/views/home/index.vue')
  },
  {
    path: '/codedog/sso/complete',
    meta: { requireLogin: false },
    component: () => import(/* webpackChunkName: 'root' */ '@/views/account/CodeDogSsoComplete')
  },
  {
    path: '/login',
    meta: { requireLogin: false },
    component: () => import(/* webpackChunkName: 'root' */ '@/views/account/CodeDogLoginRedirect')
  },
  {
    path: '/home',
    meta: { requireLogin: true },
    component: () => import(/* webpackChunkName: 'root' */ '@/views/home'),
    children: [
      {
        path: '/',
        meta: { requireLogin: true },
        component: () => import(/* webpackChunkName: 'root' */ '@/views/project/my/index')
      },
      {
        path: 'member',
        meta: { requireLogin: true },
        component: () => import(/* webpackChunkName: 'root' */ '@/views/account/member')
      }
    ]
  },
  {
    path: '/manage',
    meta: { requireLogin: true },
    component: () => import(/* webpackChunkName: 'root' */ '@/views/home'),
    children: [
      {
        path: 'user',
        meta: { requireLogin: true },
        component: () => import(/* webpackChunkName: 'root' */ '@/views/manage/user')
      },
      {
        path: 'system',
        meta: { requireLogin: true },
        component: () => import(/* webpackChunkName: 'root' */ '@/views/manage/system')
      }
    ]
  }
]
