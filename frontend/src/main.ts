import { createApp } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setOnSessionExpired } from './api/client'
import { useAuthStore } from './stores/auth'
import { registerPixelicons } from './assets/icons/pixelarticons'
import './assets/main.css'

registerPixelicons()

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
setActivePinia(pinia)

setOnSessionExpired(() => {
  const auth = useAuthStore()
  auth.logout()
  if (router.currentRoute.value.name !== 'login') {
    void router.push({ name: 'login' })
  }
})

app.use(router)

app.mount('#app')