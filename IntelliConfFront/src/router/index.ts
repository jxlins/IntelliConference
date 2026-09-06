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

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/author-discovery', name: 'AuthorDiscovery', component: () => import('../views/AuthorDiscoveryView.vue'), meta: { requiresAuth: true } },
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
      meta: { requiresAuth: true }
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: DashboardView,
      meta: { requiresAuth: true }
    },
    {
      path: '/mail-compose',
      name: 'MailCompose',
      component: MailComposeView,
      meta: { requiresAuth: true }
    },
    {
      path: '/conference-info',
      name: 'ConferenceInfo',
      component: ConferenceInfoView,
      meta: { requiresAuth: true }
    },
    {
      path: '/conference-setup',
      name: 'ConferenceSetup',
      component: ConferenceSetupWizardView,
      meta: { requiresAuth: true }
    },
    {
      path: '/conference-committee-init',
      name: 'ConferenceCommitteeInit',
      component: ConferenceCommitteeInitView,
      meta: { requiresAuth: true }
    },
    {
      path: '/mail-workflow',
      name: 'MailWorkflow',
      component: MailWorkflowView,
      meta: { requiresAuth: true }
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
      meta: { requiresAuth: true }
    },
    {
      path: '/conference-mail-account',
      name: 'ConferenceMailAccount',
      component: ConferenceMailAccountView,
      meta: { requiresAuth: true }
    }
  ]
})

// 简单的路由守卫（检查登录状态）
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  
  if (to.meta.requiresAuth && !token) {
    // 需要登录但未登录，跳转到登录页
    next('/auth')
  } else if (to.path === '/auth' && token) {
    // 已登录访问登录页，跳转到会议门户
    next('/portal')
  } else {
    next()
  }
})

export default router
