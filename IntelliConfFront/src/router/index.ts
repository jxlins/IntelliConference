import { createRouter, createWebHistory } from 'vue-router'
import AuthView from '../views/AuthView.vue'
import ConferencePortal from '../views/ConferencePortal.vue'
import DashboardView from '../views/DashboardView.vue'
import MailComposeView from '../views/MailComposeView.vue'
import ConferenceInfoView from '../views/ConferenceInfoView.vue'
import ConferenceCommitteeInitView from '../views/ConferenceCommitteeInitView.vue'
import ConferenceMailAccountView from '../views/ConferenceMailAccountView.vue'
import ConferenceSetupWizardView from '../views/ConferenceSetupWizardView.vue'
import ConferenceUserManagementView from '../views/ConferenceUserManagementView.vue'
import CommitteeInviteView from '../views/CommitteeInviteView.vue'
import MailWorkflowView from '../views/MailWorkflowView.vue'
import { getConferenceId, safeRedirectTarget } from './navigation'

const authenticated = { requiresAuth: true }
const conferenceScoped = { requiresAuth: true, requiresConference: true, conferenceNav: true }

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/author-discovery', name: 'AuthorDiscovery', component: () => import('../views/AuthorDiscoveryView.vue'), meta: conferenceScoped },
    {
      path: '/',
      redirect: '/auth'
    },
    {
      path: '/auth',
      name: 'Auth',
      component: AuthView
    },
    {
      path: '/portal',
      name: 'Portal',
      component: ConferencePortal,
      meta: authenticated
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: DashboardView,
      meta: conferenceScoped
    },
    {
      path: '/mail-compose',
      name: 'MailCompose',
      component: MailComposeView,
      meta: conferenceScoped
    },
    {
      path: '/conference-info',
      name: 'ConferenceInfo',
      component: ConferenceInfoView,
      meta: conferenceScoped
    },
    {
      path: '/conference-setup',
      name: 'ConferenceSetup',
      component: ConferenceSetupWizardView,
      meta: conferenceScoped
    },
    {
      path: '/conference-committee-init',
      name: 'ConferenceCommitteeInit',
      component: ConferenceCommitteeInitView,
      meta: conferenceScoped
    },
    {
      path: '/mail-workflow',
      name: 'MailWorkflow',
      component: MailWorkflowView,
      meta: conferenceScoped
    },
    {
      path: '/committee-invite',
      name: 'CommitteeInvite',
      component: CommitteeInviteView
    },
    {
      path: '/conference-users',
      name: 'ConferenceUsers',
      component: ConferenceUserManagementView,
      meta: conferenceScoped
    },
    {
      path: '/conference-mail-account',
      name: 'ConferenceMailAccount',
      component: ConferenceMailAccountView,
      meta: conferenceScoped
    },
    {
      path: '/electronic-seal',
      name: 'ElectronicSeal',
      component: () => import('../views/ElectronicSealView.vue'),
      meta: { requiresAuth: true, conferenceNav: true }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: () => localStorage.getItem('token') ? { name: 'Portal' } : { name: 'Auth' },
    },
  ]
})

// 简单的路由守卫（检查登录状态）
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  
  if (to.meta.requiresAuth && !token) {
    // 登录后恢复用户原本要进入的页面，会议上下文不会丢失。
    next({ name: 'Auth', query: { redirect: to.fullPath } })
  } else if (to.path === '/auth' && token) {
    next(safeRedirectTarget(to.query.redirect) || { name: 'Portal' })
  } else if (to.meta.requiresConference && !getConferenceId(to.query)) {
    // 会议内页面必须带会议标识，避免进入一个加载不出数据的空页面。
    next({ name: 'Portal' })
  } else {
    next()
  }
})

export default router
