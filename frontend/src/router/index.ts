import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

export const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/views/AppShell.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'feed', component: () => import('@/views/FeedView.vue') },
      { path: 'post/:id', name: 'post-detail', component: () => import('@/views/PostDetailView.vue') },
      { path: 'profile/:id?', name: 'profile', component: () => import('@/views/ProfileView.vue') },
      { path: 'liked', name: 'liked', component: () => import('@/views/LikedPostsView.vue') },
      { path: 'following/:id?', name: 'following', component: () => import('@/views/FollowingView.vue') },
      { path: 'followers/:id?', name: 'followers', component: () => import('@/views/FollowersView.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'login' } },
]

export function createAppRouter(history: RouterHistory = createWebHistory(import.meta.env.BASE_URL)) {
  const router = createRouter({ history, routes })

  router.beforeEach(async (to) => {
    const auth = useAuthStore()

    if (to.meta.requiresAuth && auth.isAuthenticated && !auth.user) {
      try {
        await auth.fetchMe()
      } catch {
        auth.logout()
        return { name: 'login' }
      }
    }

    if (to.meta.requiresAuth && !auth.isAuthenticated) {
      return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
    }

    if (auth.isAuthenticated && to.name === 'login') {
      return { name: 'feed' }
    }
  })

  return router
}

const router = createAppRouter()

export default router